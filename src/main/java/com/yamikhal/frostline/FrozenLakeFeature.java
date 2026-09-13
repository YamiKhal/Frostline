package com.yamikhal.frostline;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinks and floods the lake that frostline:lake_field promised, across as many chunks as it spans.
 *
 * Why this is not PondFeature: a lake is bigger than the 3x3 chunk write window, so each
 * chunk it touches must agree on the water level and basin shape without seeing the others'
 * blocks. Everything shared is derived from the site and from noise-only terrain heights
 * (ChunkGenerator#getBaseHeight), which are identical from any chunk.
 *
 * Model:
 *   sites     read from the seeded lake field behind the router's `continents`, so the
 *             lake always sits inside the biome the field painted
 *   t         site distance / (edge * basin_fraction * scale), measured after a domain warp
 *             from the field noise, so the basin is lobed rather than round; t < 1 is basin.
 *             Never below the unwarped distance / BIOME_LIMIT, so it stays in the biome.
 *   relief    a coarse grid of base heights (GRID_SPACING blocks, bilinear) over the whole
 *             site, built once per lake. How much a column sinks is read from this smooth
 *             relief, never from the column's own height: per-column heights turned every
 *             1-block step into a multi-block ledge.
 *   waterTop  one level per lake: the LEVEL_PERCENTILE relief height inside the basin,
 *             capped at max_berm_height - 1 above the lowest rim sample so a berm can seal it
 *   scale     BASIN_SCALES are tried largest first; the first where at least MIN_FLOOD_SHARE
 *             of basin grid points end up underwater wins. Hopeless sites log why and stay dry.
 *   hollow    basin columns sink by depth * Hollow.profile(t) * heightFade(relief), surface
 *             carried along. heightFade is 1 at the waterline and 0 from shore_height above
 *             it, so hills keep their shape and become islands or banks.
 *   berm      ground below waterTop + 1 is pulled towards it: rising over BERM_START..
 *             BERM_FULL inside the basin, full to the edge, then easing back to natural
 *             ground over SKIRT_BLOCKS outside it. Pulling (not adding) compresses bumps
 *             instead of amplifying them, and the skirt means the berm never ends in a step.
 *   water     only columns whose reshaped ground is below waterTop; the shore is a contour
 *   cap       floe past `shoreline`, where field noise crosses floe_threshold, or where
 *             water is 1 deep (thin ice needs water under it); otherwise `surface`
 *   seal      a non-sturdy neighbour outside the basin gets a terrain-matching plug; only
 *             fires where a rim gap was deeper than max_berm_height
 *
 * Each call rewrites only its own chunk's columns, plus sealing one block outside them.
 * Place it after minecraft:freeze_top_layer so vanilla freezing leaves the thin ice alone.
 */
public class FrozenLakeFeature extends Feature<FrozenLakeConfiguration> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MIN_RIM_RAYS = 48;
    /** Blocks of rim circumference per height sample. */
    private static final double RIM_SPACING = 3.0D;
    private static final int GRID_SPACING = 10;
    private static final double[] BASIN_SCALES = {1.0D, 0.8D, 0.62D, 0.48D};
    private static final double LEVEL_PERCENTILE = 0.35D;
    /** Share of basin grid points that must end up underwater for a scale to be accepted. */
    private static final double MIN_FLOOD_SHARE = 0.25D;
    private static final int MIN_BASIN_POINTS = 12;
    /** Share of the biome radius the basin may never cross, whatever the warp does. */
    private static final double BIOME_LIMIT = 0.92D;
    private static final double BERM_START = 0.8D;
    private static final double BERM_FULL = 0.96D;
    /** Blocks outside the basin edge over which a berm eases back to natural ground. */
    private static final double SKIRT_BLOCKS = 10.0D;
    private static final int PLAN_CACHE_LIMIT = 1024;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** Coarse noise-terrain heights over one site, sampled bilinearly. */
    private record Relief(int minX, int minZ, int size, int[] heights) {

        double sample(double x, double z) {
            double gx = Mth.clamp((x - minX) / GRID_SPACING, 0.0D, size - 1.0001D);
            double gz = Mth.clamp((z - minZ) / GRID_SPACING, 0.0D, size - 1.0001D);
            int ix = Mth.floor(gx);
            int iz = Mth.floor(gz);
            double fx = gx - ix;
            double fz = gz - iz;
            return Mth.lerp2(fx, fz, at(ix, iz), at(ix + 1, iz), at(ix, iz + 1), at(ix + 1, iz + 1));
        }

        int at(int ix, int iz) {
            return heights[ix * size + iz];
        }
    }

    /** Per-lake decisions every chunk must share. waterTop == DRY means leave the site alone. */
    private record Plan(int waterTop, int depth, double scale, Relief relief) {
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

        double reach = siteReach(site, config);
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
        double skirt = SKIRT_BLOCKS / (site.radius() * config.basinFraction() * plan.scale());

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double t = basinT(field, config, site, plan.scale(), x + 0.5D, z + 0.5D);
                if (t >= 1.0D + skirt) {
                    continue;
                }
                int g = PondFeature.findGround(level, config.ground(), cursor, x, z);
                if (g == PondFeature.NO_GROUND) {
                    continue;
                }

                int lower = 0;
                if (t < 1.0D) {
                    double relief = plan.relief().sample(x + 0.5D, z + 0.5D);
                    lower = (int) Math.round(plan.depth() * Hollow.profile(t)
                            * heightFade(relief - waterTop, config.shoreHeight()));
                }
                int lift = 0;
                if (g < waterTop + 1) {
                    double weight = t < 1.0D
                            ? smoothstep((t - BERM_START) / (BERM_FULL - BERM_START))
                            : 1.0D - smoothstep((t - 1.0D) / skirt);
                    lift = Math.min(config.maxBermHeight(), (int) Math.round((waterTop + 1 - g) * weight));
                }
                int shift = lift - lower;
                boolean shaped = shift >= 0
                        ? Hollow.raise(level, (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS),
                                x, z, g, shift)
                        : Hollow.sink(level, (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS),
                                x, z, g, -shift);
                if (!shaped) {
                    continue;
                }
                placed = true;
                int surface = g + shift;
                if (t >= 1.0D || surface >= waterTop) {
                    continue;
                }

                BlockPos floorPos = new BlockPos(x, surface, z);
                BlockState floor = config.floor().getState(random, floorPos);
                BlockPos below = floorPos.below();
                if (floor.getBlock() instanceof FallingBlock
                        && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    floor = config.wall().getState(random, floorPos);
                }
                level.setBlock(floorPos, floor, Block.UPDATE_CLIENTS);

                for (int y = surface + 1; y <= waterTop; y++) {
                    cursor.set(x, y, z);
                    BlockState state = water;
                    if (y == waterTop) {
                        boolean floe = t > config.shoreline()
                                || waterTop - surface < 2
                                || field.noise().getValue(x * config.floeScale(), 0.0D, z * config.floeScale())
                                        > config.floeThreshold();
                        state = floe ? config.floe().getState(random, cursor) : config.surface();
                    }
                    level.setBlock(cursor, state, Block.UPDATE_CLIENTS);
                    seal(level, random, field, config, site, plan.scale(), cursor.immutable());
                }
            }
        }
        return placed;
    }

    /**
     * Neighbours inside the basin are left to their own chunk: they end up either water or
     * ground at or above waterTop, and both hold. Only the outside can leak.
     */
    private static void seal(WorldGenLevel level, RandomSource random, LakeFieldDensityFunction field,
                             FrozenLakeConfiguration config, LakeFieldDensityFunction.Site site, double scale,
                             BlockPos pos) {
        for (Direction direction : HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (basinT(field, config, site, scale, neighbor.getX() + 0.5D, neighbor.getZ() + 0.5D) < 1.0D) {
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

    /** Furthest any basin or skirt column can be from the site centre. */
    private static double siteReach(LakeFieldDensityFunction.Site site, FrozenLakeConfiguration config) {
        // Edge wobble tops out at +17%, warp adds its share of the radius on top.
        return site.radius() * (config.basinFraction() * 1.2D + config.warp()) + SKIRT_BLOCKS + 2.0D;
    }

    private static double basinT(LakeFieldDensityFunction field, FrozenLakeConfiguration config,
                                 LakeFieldDensityFunction.Site site, double scale, double x, double z) {
        double amount = site.radius() * config.warp() * scale;
        double noiseScale = config.warpScale();
        double wx = x + field.noise().getValue(x * noiseScale, 11.0D, z * noiseScale) * amount;
        double wz = z + field.noise().getValue(x * noiseScale, -23.0D, z * noiseScale) * amount;
        // The warp scales with the radius, so on a big lake it could push the basin past the
        // biome edge. Chunks out there never run this feature and the hollow would end in a
        // step; the unwarped term pins the basin inside BIOME_LIMIT of the biome radius.
        return Math.max(site.normalized(wx, wz) / (config.basinFraction() * scale),
                site.normalized(x, z) / BIOME_LIMIT);
    }

    /**
     * 1 up to one block above the water, fading to 0 at shore_height above it. Only ground near
     * the water level is reshaped; hills inside the basin keep their shape.
     */
    private static double heightFade(double above, int shoreHeight) {
        if (above <= 1.0D) {
            return 1.0D;
        }
        return Mth.clamp(1.0D - (above - 1.0D) / shoreHeight, 0.0D, 1.0D);
    }

    private static double smoothstep(double v) {
        double c = Mth.clamp(v, 0.0D, 1.0D);
        return c * c * (3.0D - 2.0D * c);
    }

    private static Plan measure(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState,
                                LakeFieldDensityFunction field, FrozenLakeConfiguration config,
                                LakeFieldDensityFunction.Site site) {
        int depth = Math.min(config.maxDepth(),
                config.minDepth() + Mth.floor(site.roll() * (config.maxDepth() - config.minDepth() + 1)));

        double reach = siteReach(site, config) + GRID_SPACING;
        int size = Mth.ceil(reach * 2.0D / GRID_SPACING) + 2;
        int minX = Mth.floor(site.x() - reach);
        int minZ = Mth.floor(site.z() - reach);
        int[] grid = new int[size * size];
        for (int ix = 0; ix < size; ix++) {
            for (int iz = 0; iz < size; iz++) {
                grid[ix * size + iz] = baseGround(generator, heights, randomState,
                        minX + ix * GRID_SPACING, minZ + iz * GRID_SPACING);
            }
        }
        Relief relief = new Relief(minX, minZ, size, grid);

        double limit = site.radius() * 2.0D;
        String reason = "no scale tried";
        for (double scale : BASIN_SCALES) {
            // Dense rim sampling: a missed valley is a leak.
            int rays = Math.max(MIN_RIM_RAYS,
                    Mth.ceil(Math.PI * 2.0D * site.radius() * config.basinFraction() * scale / RIM_SPACING));
            int rimLow = Integer.MAX_VALUE;
            for (int k = 0; k < rays; k++) {
                double theta = Math.PI * 2.0D * k / rays;
                double cos = Math.cos(theta);
                double sin = Math.sin(theta);
                double r = 0.0D;
                while (r < limit && basinT(field, config, site, scale, site.x() + cos * r, site.z() + sin * r) < 1.0D) {
                    r += 1.0D;
                }
                rimLow = Math.min(rimLow, baseGround(generator, heights, randomState,
                        site.x() + cos * r, site.z() + sin * r));
            }

            int[] inside = new int[size * size];
            double[] ts = new double[size * size];
            int n = 0;
            for (int ix = 0; ix < size; ix++) {
                for (int iz = 0; iz < size; iz++) {
                    double t = basinT(field, config, site, scale, minX + ix * GRID_SPACING, minZ + iz * GRID_SPACING);
                    if (t < 1.0D) {
                        inside[n] = relief.at(ix, iz);
                        ts[n] = t;
                        n++;
                    }
                }
            }
            if (n < MIN_BASIN_POINTS) {
                reason = String.format("at scale %.2f the basin covers only %d grid points", scale, n);
                continue;
            }
            int[] sorted = Arrays.copyOf(inside, n);
            Arrays.sort(sorted);
            int interiorLevel = sorted[Math.min(n - 1, (int) (n * LEVEL_PERCENTILE))];
            int waterTop = Math.min(interiorLevel, rimLow + config.maxBermHeight() - 1);
            if (waterTop - depth - 8 <= heights.getMinBuildHeight()) {
                reason = "too close to the world bottom";
                continue;
            }

            int flooded = 0;
            for (int i = 0; i < n; i++) {
                double lower = depth * Hollow.profile(ts[i]) * heightFade(inside[i] - waterTop, config.shoreHeight());
                if (inside[i] - Math.round(lower) < waterTop) {
                    flooded++;
                }
            }
            if (flooded >= n * MIN_FLOOD_SHARE) {
                LOGGER.info("Frostline lake at {} {}: water at y={}, basin scale {}, depth {}, {}/{} grid points flooded",
                        Mth.floor(site.x()), Mth.floor(site.z()), waterTop, scale, depth, flooded, n);
                return new Plan(waterTop, depth, scale, relief);
            }
            reason = String.format("at scale %.2f level y=%d (interior y=%d, rim low y=%d) floods %d/%d grid points",
                    scale, waterTop, interiorLevel, rimLow, flooded, n);
        }
        LOGGER.info("Frostline lake at {} {} stays dry: {}", Mth.floor(site.x()), Mth.floor(site.z()), reason);
        return new Plan(Plan.DRY, depth, 1.0D, relief);
    }

    private static int baseGround(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState,
                                  double x, double z) {
        return generator.getBaseHeight(Mth.floor(x), Mth.floor(z), Heightmap.Types.OCEAN_FLOOR_WG,
                heights, randomState) - 1;
    }
}
