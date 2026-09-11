package com.frostline.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Graded snow accumulation.
 *
 * Java Edition's freeze_top_layer places exactly one layer, always. Bedrock has
 * a real accumulation algorithm; this is a port of it, with the pieces that
 * matter for making terrain read as smooth:
 *
 *   1. a low-frequency "bare" noise carves wind-scoured patches with no snow
 *   2. a low-frequency "deep" noise creates drifts several layers thick
 *   3. +1 layer for every horizontally bordering block that stands higher
 *      (Bedrock's own rule — this is what banks snow against cliffs and
 *      staircases, softening the 1-block terracing of noise terrain)
 *   4. extra layers in hollows, proportional to how far below the neighbours
 *      the column sits
 *
 * Depth is tracked in eighths, so a value of 20 is two full snow blocks plus a
 * four-layer step. Full blocks are placed as snow blocks and the remainder as a
 * snow layer, which is what lets drifts exceed one block and actually bury the
 * steps rather than tracing them.
 *
 * Self-gating: a column is skipped unless its biome reports SNOW precipitation
 * at that position, so this feature is safe to list in every biome. Ashlands
 * (has_precipitation false) and the green region are skipped automatically.
 */
public class SnowDriftFeature extends Feature<SnowDriftFeature.Config> {

    public SnowDriftFeature() {
        super(Config.CODEC);
    }

    public record Config(
            int baseLayers,
            int maxLayers,
            double bareScale,
            double bareThreshold,
            double deepScale,
            double deepStrength,
            double borderBonus,
            double hollowBonus,
            double powderThreshold
    ) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("base_layers", 2).forGetter(Config::baseLayers),
                Codec.INT.optionalFieldOf("max_layers", 20).forGetter(Config::maxLayers),
                Codec.DOUBLE.optionalFieldOf("bare_scale", 0.022).forGetter(Config::bareScale),
                Codec.DOUBLE.optionalFieldOf("bare_threshold", 0.58).forGetter(Config::bareThreshold),
                Codec.DOUBLE.optionalFieldOf("deep_scale", 0.011).forGetter(Config::deepScale),
                Codec.DOUBLE.optionalFieldOf("deep_strength", 9.0).forGetter(Config::deepStrength),
                Codec.DOUBLE.optionalFieldOf("border_bonus", 1.6).forGetter(Config::borderBonus),
                Codec.DOUBLE.optionalFieldOf("hollow_bonus", 2.4).forGetter(Config::hollowBonus),
                Codec.DOUBLE.optionalFieldOf("powder_threshold", 0.80).forGetter(Config::powderThreshold)
        ).apply(i, Config::new));
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        Config cfg = ctx.config();
        ChunkPos chunk = new ChunkPos(ctx.origin());
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        long seed = level.getSeed();

        // 18x18 so every in-chunk column has real neighbour heights. The FEATURES
        // chunk status gives a 3x3 region, so reading one block outside is safe.
        int[][] h = new int[18][18];
        for (int dx = -1; dx <= 16; dx++) {
            for (int dz = -1; dz <= 16; dz++) {
                h[dx + 1][dz + 1] = level.getHeight(
                        Heightmap.Types.OCEAN_FLOOR_WG, x0 + dx, z0 + dz);
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState powder = Blocks.POWDER_SNOW.defaultBlockState();
        boolean placedAny = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int y = h[dx + 1][dz + 1];

                pos.set(x, y, z);

                Biome biome = level.getBiome(pos).value();
                if (biome.getPrecipitationAt(pos) != Biome.Precipitation.SNOW) {
                    continue;
                }
                if (!level.isEmptyBlock(pos)) {
                    continue;
                }
                if (!Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                    continue;
                }

                // 1. wind-scoured bare patches
                double bare = fbm(seed, x * cfg.bareScale(), z * cfg.bareScale(), 2);
                if (bare > cfg.bareThreshold()) {
                    continue;
                }

                // 2. drift depth
                double deep = fbm(seed ^ 0x5DEECE66DL,
                        x * cfg.deepScale(), z * cfg.deepScale(), 3);
                double depth = cfg.baseLayers() + Math.max(0.0D, deep) * cfg.deepStrength();

                // 3. bank against anything standing higher (Bedrock's rule)
                int higher = 0;
                int tallest = y;
                int[][] n = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : n) {
                    int nh = h[dx + 1 + d[0]][dz + 1 + d[1]];
                    if (nh > y) {
                        higher++;
                    }
                    if (nh > tallest) {
                        tallest = nh;
                    }
                }
                depth += higher * cfg.borderBonus();

                // 4. fill hollows
                depth += Mth.clamp(tallest - y, 0, 3) * cfg.hollowBonus();

                int eighths = Mth.clamp((int) Math.round(depth), 1, cfg.maxLayers());
                boolean trap = deep > cfg.powderThreshold();

                int full = eighths / 8;
                int rem = eighths % 8;
                int yy = y;

                for (int i = 0; i < full; i++) {
                    pos.set(x, yy, z);
                    if (!level.isEmptyBlock(pos)) {
                        break;
                    }
                    setBlock(level, pos, trap ? powder : snowBlock);
                    yy++;
                    placedAny = true;
                }
                if (rem > 0) {
                    pos.set(x, yy, z);
                    if (level.isEmptyBlock(pos)) {
                        setBlock(level, pos, Blocks.SNOW.defaultBlockState()
                                .setValue(SnowLayerBlock.LAYERS, rem));
                        placedAny = true;
                    }
                }

                pos.set(x, y - 1, z);
                BlockState below = level.getBlockState(pos);
                if (below.hasProperty(BlockStateProperties.SNOWY)) {
                    setBlock(level, pos, below.setValue(BlockStateProperties.SNOWY, true));
                }
            }
        }
        return placedAny;
    }

    // ---- self-contained value noise; no dependence on vanilla noise constructors

    private static double fbm(long seed, double x, double z, int octaves) {
        double sum = 0.0D;
        double amp = 1.0D;
        double norm = 0.0D;
        for (int o = 0; o < octaves; o++) {
            sum += valueNoise(seed + o * 7919L, x, z) * amp;
            norm += amp;
            amp *= 0.5D;
            x *= 2.0D;
            z *= 2.0D;
        }
        return sum / norm;
    }

    private static double valueNoise(long seed, double x, double z) {
        int xi = Mth.floor(x);
        int zi = Mth.floor(z);
        double xf = smooth(x - xi);
        double zf = smooth(z - zi);
        double a = hash(seed, xi, zi);
        double b = hash(seed, xi + 1, zi);
        double c = hash(seed, xi, zi + 1);
        double d = hash(seed, xi + 1, zi + 1);
        return Mth.lerp(zf, Mth.lerp(xf, a, b), Mth.lerp(xf, c, d));
    }

    private static double smooth(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private static double hash(long seed, int x, int z) {
        long n = seed + x * 341873128712L + z * 132897987541L;
        n ^= n >>> 33;
        n *= 0xFF51AFD7ED558CCDL;
        n ^= n >>> 33;
        n *= 0xC4CEB9FE1A85EC53L;
        n ^= n >>> 33;
        return ((double) (n >>> 11) / (double) (1L << 53)) * 2.0D - 1.0D;
    }
}
