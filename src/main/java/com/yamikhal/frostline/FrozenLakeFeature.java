package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinks and floods the lake that frostline:lake_field promised, across as many chunks as it spans.
 *
 * Why this is not PondFeature: a lake is bigger than the 3x3 chunk write window, so each
 * chunk it touches must agree on the water level and basin shape without seeing the others'
 * blocks. Everything shared is derived from the site and from noise-only terrain heights.
 *
 * Model:
 *   sites     read from the seeded lake field behind the router's `continents`, so the
 *             lake always sits inside the biome the field painted
 *   t         site distance / (edge * basin_fraction), measured after a domain warp from
 *             the field noise, so the basin is lobed rather than round; t < 1 is basin.
 *             Never below the unwarped distance / BIOME_LIMIT, so it stays in the biome.
 *   rim       48 rays march out to t = 1; ChunkGenerator#getBaseHeight there gives the
 *             rim heights. Noise-only heights are identical from any chunk.
 *   waterTop  lowest rim height minus one: water sits a block under the lowest bank
 *   dry lake  rim spread above max_bank_height, or the sunken centre not at least 2 under
 *             waterTop: nothing is touched and the biome is frozen flats
 *   hollow    every basin column sinks by depth * Hollow.profile(t), surface carried along
 *   water     only columns whose sunken ground is below waterTop, so the shore is a
 *             contour of the real terrain and higher ground stays dry bank
 *   cap       floe past `shoreline`, where field noise crosses floe_threshold, or where
 *             water is 1 deep (thin ice needs water under it); otherwise `surface`
 *   seal      a non-sturdy neighbour outside the basin gets a terrain-matching plug
 *
 * Each call rewrites only its own chunk's columns, plus sealing one block outside them.
 * Place it after minecraft:freeze_top_layer so vanilla freezing leaves the thin ice alone.
 */
public class FrozenLakeFeature extends Feature<FrozenLakeConfiguration> {

    private static final int RIM_RAYS = 48;
    /** Share of the biome radius the basin may never cross, whatever the warp does. */
    private static final double BIOME_LIMIT = 0.92D;
    private static final int PLAN_CACHE_LIMIT = 4096;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** Per-lake decisions every chunk must share. waterTop == DRY means leave the site alone. */
    private record Plan(int waterTop, int depth) {
        static final int DRY = Integer.MIN_VALUE;
    }

    private final Map<LakeFieldDensityFunction.Site, Plan> plans = new ConcurrentHashMap<>();

    public FrozenLakeFeature(Codec<FrozenLakeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FrozenLakeConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        Optional<LakeFieldDensityFunction> field = LakeFieldDensityFunction.find(randomState);
        if (field.isEmpty()) {
            return false;
        }

        ChunkPos chunk = new ChunkPos(ctx.origin());
        int cell = field.get().cellSize();
        boolean placed = false;
        for (int cellX = Math.floorDiv(chunk.getMinBlockX(), cell) - 1;
             cellX <= Math.floorDiv(chunk.getMaxBlockX(), cell) + 1; cellX++) {
            for (int cellZ = Math.floorDiv(chunk.getMinBlockZ(), cell) - 1;
                 cellZ <= Math.floorDiv(chunk.getMaxBlockZ(), cell) + 1; cellZ++) {
                Optional<LakeFieldDensityFunction.Site> site = field.get().siteAt(cellX, cellZ);
                if (site.isPresent()) {
                    placed |= carve(ctx, field.get(), site.get(), chunk, randomState);
                }
            }
        }
        return placed;
    }

    private boolean carve(FeaturePlaceContext<FrozenLakeConfiguration> ctx, LakeFieldDensityFunction field,
                          LakeFieldDensityFunction.Site site, ChunkPos chunk, RandomState randomState) {
        FrozenLakeConfiguration config = ctx.config();
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();

        // Edge wobble tops out at +17%, warp adds its share of the radius on top.
        double reach = site.radius() * (config.basinFraction() * 1.2D + config.warp()) + 2.0D;
        double nearestX = Mth.clamp(site.x(), chunk.getMinBlockX(), chunk.getMaxBlockX() + 1);
        double nearestZ = Mth.clamp(site.z(), chunk.getMinBlockZ(), chunk.getMaxBlockZ() + 1);
        if (Mth.square(nearestX - site.x()) + Mth.square(nearestZ - site.z()) > reach * reach) {
            return false;
        }

        if (plans.size() > PLAN_CACHE_LIMIT) {
            plans.clear();
        }
        Plan plan = plans.computeIfAbsent(site,
                s -> measure(ctx.chunkGenerator(), level, randomState, field, config, s));
        if (plan.waterTop() == Plan.DRY) {
            return false;
        }
        int waterTop = plan.waterTop();

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double t = basinT(field, config, site, x + 0.5D, z + 0.5D);
                if (t >= 1.0D) {
                    continue;
                }
                int g = PondFeature.findGround(level, config.ground(), cursor, x, z);
                if (g == PondFeature.NO_GROUND) {
                    continue;
                }
                int lower = (int) Math.round(plan.depth() * Hollow.profile(t));
                if (!Hollow.sink(level, (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS),
                        x, z, g, lower)) {
                    continue;
                }
                placed = true;
                int sunk = g - lower;
                if (sunk >= waterTop) {
                    continue;
                }

                BlockPos floorPos = new BlockPos(x, sunk, z);
                BlockState floor = config.floor().getState(random, floorPos);
                BlockPos below = floorPos.below();
                if (floor.getBlock() instanceof FallingBlock
                        && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    floor = config.wall().getState(random, floorPos);
                }
                level.setBlock(floorPos, floor, Block.UPDATE_CLIENTS);

                for (int y = sunk + 1; y <= waterTop; y++) {
                    cursor.set(x, y, z);
                    BlockState state = water;
                    if (y == waterTop) {
                        boolean floe = t > config.shoreline()
                                || waterTop - sunk < 2
                                || field.noise().getValue(x * config.floeScale(), 0.0D, z * config.floeScale())
                                        > config.floeThreshold();
                        state = floe ? config.floe().getState(random, cursor) : config.surface();
                    }
                    level.setBlock(cursor, state, Block.UPDATE_CLIENTS);
                    seal(level, random, field, config, site, cursor.immutable());
                }
            }
        }
        return placed;
    }

    /**
     * Neighbours inside the basin are left to their own chunk: they sink to either water or
     * dry ground at or above waterTop, and both hold. Only the outside can leak.
     */
    private static void seal(WorldGenLevel level, RandomSource random, LakeFieldDensityFunction field,
                             FrozenLakeConfiguration config, LakeFieldDensityFunction.Site site, BlockPos pos) {
        for (Direction direction : HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (basinT(field, config, site, neighbor.getX() + 0.5D, neighbor.getZ() + 0.5D) < 1.0D) {
                continue;
            }
            BlockState state = level.getBlockState(neighbor);
            if (state.isFaceSturdy(level, neighbor, direction.getOpposite())
                    || state.getFluidState().isSourceOfType(Fluids.WATER)
                    || !PondFeature.canReplace(state)) {
                continue;
            }
            level.setBlock(neighbor, Hollow.terrainLike(level, neighbor, level.getBlockState(neighbor.below()),
                    config.wall().getState(random, neighbor)), Block.UPDATE_CLIENTS);
        }
    }

    private static double basinT(LakeFieldDensityFunction field, FrozenLakeConfiguration config,
                                 LakeFieldDensityFunction.Site site, double x, double z) {
        double amount = site.radius() * config.warp();
        double scale = config.warpScale();
        double wx = x + field.noise().getValue(x * scale, 11.0D, z * scale) * amount;
        double wz = z + field.noise().getValue(x * scale, -23.0D, z * scale) * amount;
        // The warp scales with the radius, so on a big lake it could push the basin past the
        // biome edge. Chunks out there never run this feature and the hollow would end in a
        // step; the unwarped term pins the basin inside BIOME_LIMIT of the biome radius.
        return Math.max(site.normalized(wx, wz) / config.basinFraction(), site.normalized(x, z) / BIOME_LIMIT);
    }

    private static Plan measure(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState,
                                LakeFieldDensityFunction field, FrozenLakeConfiguration config,
                                LakeFieldDensityFunction.Site site) {
        int depth = Math.min(config.maxDepth(),
                config.minDepth() + Mth.floor(site.roll() * (config.maxDepth() - config.minDepth() + 1)));
        double limit = site.radius() * 2.0D;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int k = 0; k < RIM_RAYS; k++) {
            double theta = Math.PI * 2.0D * k / RIM_RAYS;
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double r = 0.0D;
            while (r < limit && basinT(field, config, site, site.x() + cos * r, site.z() + sin * r) < 1.0D) {
                r += 1.0D;
            }
            int g = baseGround(generator, heights, randomState, site.x() + cos * r, site.z() + sin * r);
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        int waterTop = low - 1;
        int centre = baseGround(generator, heights, randomState, site.x(), site.z());
        if (high - low > config.maxBankHeight()
                || waterTop - (centre - depth) < 2
                || waterTop - depth - 8 <= heights.getMinBuildHeight()) {
            return new Plan(Plan.DRY, depth);
        }
        return new Plan(waterTop, depth);
    }

    private static int baseGround(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState,
                                  double x, double z) {
        return generator.getBaseHeight(Mth.floor(x), Mth.floor(z), Heightmap.Types.OCEAN_FLOOR_WG,
                heights, randomState) - 1;
    }
}
