package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pond sunk into the terrain, with one level water plane and a shoreline that follows the ground.
 *
 * Why this is Java: minecraft:lake is locked to a 16x8x16 box with exactly 4 water
 * layers and gives up on most slopes, and vegetation patches follow each column's
 * own height, so their water steps down hills and leaks.
 *
 * Model:
 *   region     spacing x spacing chunk regions; one seed-chosen chunk per region may try,
 *              with region_chance, so ponds are spread out rather than clustered
 *   outline    stretched, rotated ellipse with two edge harmonics, then domain-warped by
 *              simplex noise so the basin is a blob, not a circle
 *   t          warped distance / outline radius at that angle; t < 1 is the basin
 *   waterTop   lowest ground on the ring just outside the basin, minus surface_offset
 *   hollow     each basin column sinks by depth * Hollow.profile(t), surface carried
 *              along; nothing is ever cut flat
 *   water      only columns whose sunken ground is below waterTop. On a slope the uphill
 *              half stays a dry dent and the shoreline is a terrain contour.
 *   reject     rim spread above max_bank_height, a basin touching REACH, or less than
 *              MIN_WATER_SHARE of the basin flooding: the site is skipped untouched
 *   seal       non-sturdy neighbours of the water (caves) get a terrain-matching plug;
 *              needing many is a reject
 *
 * Planned in memory and validated before the first setBlock. The basin is centred on its
 * chunk's middle block, so it can reach 22 blocks inside the writable 3x3 chunk window.
 *
 * Place it at the END of top_layer_modification. Earlier, minecraft:freeze_top_layer
 * turns the water plane into vanilla ice and drops snow layers onto thin ice.
 */
public class PondFeature extends Feature<PondConfiguration> {

    /** From a chunk's centre (local 8), REACH + 1 for the rim stays inside the writable 3x3 chunks. */
    private static final int REACH = 22;
    private static final int SIZE = (REACH + 1) * 2 + 1;
    static final int NO_GROUND = Integer.MIN_VALUE;
    /** How far under the heightmap to look for terrain below snow, plants and canopy. */
    private static final int GROUND_SCAN = 32;
    /** Domain warp strength as a share of the radius. */
    private static final double WARP = 0.25D;
    /** Share of the basin that must end up underwater, or the site reads as a random dent. */
    private static final double MIN_WATER_SHARE = 0.3D;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public PondFeature(Codec<PondConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PondConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        PondConfiguration config = ctx.config();
        ChunkPos chunk = new ChunkPos(ctx.origin());
        if (!chosen(level.getSeed(), chunk, config)) {
            return false;
        }
        BlockPos origin = new BlockPos(chunk.getMiddleBlockX(), ctx.origin().getY(), chunk.getMiddleBlockZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int radius = config.radius().sample(random);
        int depth = config.depth().sample(random);

        double stretch = 1.0D + random.nextDouble() * 0.5D;
        double rotation = random.nextDouble() * Math.PI;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double wobble2 = 0.06D + random.nextDouble() * 0.10D;
        double wobble3 = 0.03D + random.nextDouble() * 0.07D;
        double phase2 = random.nextDouble() * Math.PI * 2.0D;
        double phase3 = random.nextDouble() * Math.PI * 2.0D;
        SimplexNoise warp = new SimplexNoise(random);
        double warpScale = 1.0D / Math.max(4.0D, radius);
        double warpAmount = radius * WARP;

        double[][] t = new double[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int dx = i - REACH - 1;
                int dz = j - REACH - 1;
                if (Math.abs(dx) > REACH || Math.abs(dz) > REACH) {
                    t[i][j] = Double.MAX_VALUE;
                    continue;
                }
                double wx = dx + warp.getValue(dx * warpScale, dz * warpScale) * warpAmount;
                double wz = dz + warp.getValue(dx * warpScale + 31.7D, dz * warpScale - 17.3D) * warpAmount;
                double u = wx * cos + wz * sin;
                double v = wz * cos - wx * sin;
                double theta = Math.atan2(v, u);
                double edge = radius * (1.0D
                        + wobble2 * Math.sin(2.0D * theta + phase2)
                        + wobble3 * Math.sin(3.0D * theta + phase3));
                t[i][j] = Math.sqrt(u * u / stretch + v * v * stretch) / edge;
            }
        }

        int[][] ground = new int[SIZE][SIZE];
        int rimLow = Integer.MAX_VALUE;
        int rimHigh = Integer.MIN_VALUE;
        int basin = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                boolean inside = t[i][j] < 1.0D;
                boolean rim = !inside && touchesInside(t, i, j);
                if (!inside && !rim) {
                    continue;
                }
                // A basin clipped by REACH would end in a straight step; never place one.
                if (inside && (i == 1 || j == 1 || i == SIZE - 2 || j == SIZE - 2)) {
                    return false;
                }
                int g = findGround(level, config.ground(), cursor, columnX(origin, i), columnZ(origin, j));
                if (g == NO_GROUND) {
                    return false;
                }
                ground[i][j] = g;
                if (inside) {
                    basin++;
                } else {
                    rimLow = Math.min(rimLow, g);
                    rimHigh = Math.max(rimHigh, g);
                }
            }
        }
        if (basin == 0 || rimHigh - rimLow > config.maxBankHeight()) {
            return false;
        }
        int waterTop = rimLow - config.surfaceOffset();
        if (waterTop - depth - 8 <= level.getMinBuildHeight()) {
            return false;
        }

        int[][] lower = new int[SIZE][SIZE];
        int flooded = 0;
        int deepest = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (t[i][j] >= 1.0D) {
                    continue;
                }
                lower[i][j] = (int) Math.round(depth * Hollow.profile(t[i][j]));
                int sunk = ground[i][j] - lower[i][j];
                if (sunk < waterTop) {
                    flooded++;
                    deepest = Math.max(deepest, waterTop - sunk);
                }
            }
        }
        if (flooded < Math.max(9, basin * MIN_WATER_SHARE) || deepest < Math.min(2, depth)) {
            return false;
        }

        BlockState water = Blocks.WATER.defaultBlockState();
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        Set<BlockPos> body = new LinkedHashSet<>();

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (t[i][j] >= 1.0D) {
                    continue;
                }
                int x = columnX(origin, i);
                int z = columnZ(origin, j);
                if (!Hollow.sink(level, plan::put, x, z, ground[i][j], lower[i][j])) {
                    return false;
                }
                int sunk = ground[i][j] - lower[i][j];
                if (sunk >= waterTop) {
                    continue;
                }

                if (config.floor().isPresent()) {
                    BlockPos floorPos = new BlockPos(x, sunk, z);
                    BlockState floor = config.floor().get().getState(random, floorPos);
                    BlockPos below = floorPos.below();
                    if (floor.getBlock() instanceof FallingBlock
                            && !planned(level, plan, below).isFaceSturdy(level, below, Direction.UP)) {
                        floor = config.wall().getState(random, floorPos);
                    }
                    plan.put(floorPos, floor);
                }

                for (int y = sunk + 1; y <= waterTop; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = water;
                    if (y == waterTop && config.cap().isPresent() && random.nextFloat() < config.capChance()) {
                        boolean shallow = waterTop - sunk < 2 && config.shallowCap().isPresent();
                        state = (shallow ? config.shallowCap().get() : config.cap().get()).getState(random, pos);
                    }
                    plan.put(pos, state);
                    body.add(pos);
                }
            }
        }

        Map<BlockPos, BlockState> walls = new LinkedHashMap<>();
        for (BlockPos pos : body) {
            for (Direction direction : HORIZONTAL) {
                BlockPos neighbor = pos.relative(direction);
                if (body.contains(neighbor) || walls.containsKey(neighbor)) {
                    continue;
                }
                BlockState state = planned(level, plan, neighbor);
                if (state.isFaceSturdy(level, neighbor, direction.getOpposite())
                        || state.getFluidState().isSourceOfType(Fluids.WATER)) {
                    continue;
                }
                if (!canReplace(state)) {
                    return false;
                }
                walls.put(neighbor, Hollow.terrainLike(level, neighbor, planned(level, plan, neighbor.below()),
                        config.wall().getState(random, neighbor)));
            }
        }
        if (walls.size() > Math.max(2, flooded / 6)) {
            return false;
        }

        plan.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        walls.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        return true;
    }

    /**
     * Structure-style spread: the chunk is the one seed-chosen chunk of its region, and the
     * region won its roll. A one-chunk margin keeps neighbouring regions' ponds apart.
     */
    private static boolean chosen(long seed, ChunkPos chunk, PondConfiguration config) {
        int spacing = config.spacing();
        if (spacing <= 1) {
            return true;
        }
        int regionX = Math.floorDiv(chunk.x, spacing);
        int regionZ = Math.floorDiv(chunk.z, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, regionX, regionZ, config.salt());
        if (random.nextFloat() >= config.regionChance()) {
            return false;
        }
        int span = Math.max(1, spacing - 2);
        int chosenX = regionX * spacing + 1 + random.nextInt(span);
        int chosenZ = regionZ * spacing + 1 + random.nextInt(span);
        return chunk.x == chosenX && chunk.z == chosenZ;
    }

    private static BlockState planned(WorldGenLevel level, Map<BlockPos, BlockState> plan, BlockPos pos) {
        BlockState state = plan.get(pos);
        return state != null ? state : level.getBlockState(pos);
    }

    /** Topmost terrain block under snow, plants and canopy, or NO_GROUND if something else is in the way. */
    static int findGround(WorldGenLevel level, TagKey<Block> ground,
                          BlockPos.MutableBlockPos cursor, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int floor = Math.max(top - GROUND_SCAN, level.getMinBuildHeight());
        for (int y = top; y > floor; y--) {
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (state.is(ground)) {
                return y;
            }
            boolean passable = state.isAir()
                    || state.is(BlockTags.LEAVES)
                    || (state.canBeReplaced() && state.getFluidState().isEmpty());
            if (!passable) {
                return NO_GROUND;
            }
        }
        return NO_GROUND;
    }

    static boolean canReplace(BlockState state) {
        return !state.is(BlockTags.FEATURES_CANNOT_REPLACE)
                && !state.is(BlockTags.LOGS)
                && !state.hasBlockEntity();
    }

    private static boolean touchesInside(double[][] t, int i, int j) {
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int ni = i + di;
                int nj = j + dj;
                if (ni >= 0 && nj >= 0 && ni < SIZE && nj < SIZE && t[ni][nj] < 1.0D) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int columnX(BlockPos origin, int i) {
        return origin.getX() + i - REACH - 1;
    }

    private static int columnZ(BlockPos origin, int j) {
        return origin.getZ() + j - REACH - 1;
    }
}
