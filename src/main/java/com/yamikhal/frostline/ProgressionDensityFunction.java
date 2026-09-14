package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.List;
import java.util.Optional;

/**
 * Coordinate-aware progression value, remapped into the climate temperature axis.
 *
 * Everything about WHAT generates lives in the datapack. This class only answers
 * "how far along the journey is this column?" — which is the one thing vanilla
 * density functions cannot express, because they can read Y, noise and splines
 * but never X/Z position.
 *
 * Model:
 *   z       = blockZ * z_direction                          -1 => payoff is at -Z
 *   along   = |z| / (z >= 0 ? north_range : south_range)     unbounded, clamped later
 *   lateral = min(|x| / x_range, x_cap)                      capped so pure-east
 *                                                            travel can never
 *                                                            reach the final band
 *   t       = min(1, along + lateral)
 *   blocks  = t * range + jitter                             jitter in blocks, below
 *   v       = lerp(start_value, end_value, blocks / range)   no knots (clamped 0..1)
 *           = piecewise_linear(knots, blocks)                with knots
 *   if z < 0: v = min(v, south_cap)                          southern hemisphere
 *                                                            never reaches the
 *                                                            payoff region
 *
 * knots: optional [[blocks, value], ...] or [[blocks, value, jitter], ...],
 * blocks ascending. Lets regions have different widths while every band
 * threshold stays in value space: region N begins at the knot whose value is its
 * band start. Before the first knot the first value holds, past the last knot the
 * last value holds. With knots, start_value/end_value are ignored.
 *
 * Jitter: jitter_noise sampled at (x, z) * jitter_xz_scale, times an amplitude in
 * BLOCKS, added to the distance before it becomes a value. A border therefore
 * wanders by the same number of blocks whichever region it sits in, and every
 * term reading this value (biome bands, gates, the wall) wanders together. The
 * amplitude is the knots' third column interpolated at the column's distance,
 * or jitter_blocks where a knot has none.
 *
 * Output is monotonic in |z| as long as the jitter noise changes by less than a
 * block per block, which a low-frequency noise always does, so region ordering
 * is a guarantee, not a tuning artifact.
 *
 * z_direction is the flip: -1.0 sends the payoff toward compass north (-Z) and
 * the infinite tail toward compass south, with the south_cap following along.
 * Swapping north_range/south_range would NOT work, because the cap is keyed to
 * the sign of z, not to which range was used.
 */
public record ProgressionDensityFunction(
        double northRange,
        double southRange,
        double xRange,
        double xCap,
        double southCap,
        double startValue,
        double endValue,
        double zDirection,
        List<List<Double>> knots,
        Optional<DensityFunction.NoiseHolder> jitterNoise,
        double jitterXzScale,
        double jitterBlocks
) implements DensityFunction.SimpleFunction {

    private static final Codec<List<List<Double>>> KNOTS_CODEC = Codec.DOUBLE.listOf().listOf().flatXmap(
            ProgressionDensityFunction::validateKnots, ProgressionDensityFunction::validateKnots);

    public static final KeyDispatchDataCodec<ProgressionDensityFunction> CODEC =
            KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("north_range", 50000.0D)
                            .forGetter(ProgressionDensityFunction::northRange),
                    Codec.DOUBLE.optionalFieldOf("south_range", 50000.0D)
                            .forGetter(ProgressionDensityFunction::southRange),
                    Codec.DOUBLE.optionalFieldOf("x_range", 250000.0D)
                            .forGetter(ProgressionDensityFunction::xRange),
                    Codec.DOUBLE.optionalFieldOf("x_cap", 0.30D)
                            .forGetter(ProgressionDensityFunction::xCap),
                    Codec.DOUBLE.optionalFieldOf("south_cap", 0.50D)
                            .forGetter(ProgressionDensityFunction::southCap),
                    Codec.DOUBLE.optionalFieldOf("start_value", -1.0D)
                            .forGetter(ProgressionDensityFunction::startValue),
                    Codec.DOUBLE.optionalFieldOf("end_value", 1.0D)
                            .forGetter(ProgressionDensityFunction::endValue),
                    Codec.DOUBLE.optionalFieldOf("z_direction", 1.0D)
                            .forGetter(ProgressionDensityFunction::zDirection),
                    KNOTS_CODEC.optionalFieldOf("knots", List.of())
                            .forGetter(ProgressionDensityFunction::knots),
                    DensityFunction.NoiseHolder.CODEC.optionalFieldOf("jitter_noise")
                            .forGetter(ProgressionDensityFunction::jitterNoise),
                    Codec.DOUBLE.optionalFieldOf("jitter_xz_scale", 0.35D)
                            .forGetter(ProgressionDensityFunction::jitterXzScale),
                    Codec.doubleRange(0.0D, 16384.0D).optionalFieldOf("jitter_blocks", 0.0D)
                            .forGetter(ProgressionDensityFunction::jitterBlocks)
            ).apply(instance, ProgressionDensityFunction::new)));

    private static DataResult<List<List<Double>>> validateKnots(List<List<Double>> knots) {
        for (int i = 0; i < knots.size(); i++) {
            int size = knots.get(i).size();
            if (size != 2 && size != 3) {
                return DataResult.error(() -> "progression knots must be [blocks, value] or [blocks, value, jitter]");
            }
            if (i > 0 && knots.get(i).get(0) <= knots.get(i - 1).get(0)) {
                return DataResult.error(() -> "progression knots must have strictly ascending blocks");
            }
        }
        return DataResult.success(knots);
    }

    @Override
    public double compute(FunctionContext ctx) {
        double x = ctx.blockX();
        // z_direction = -1 puts the payoff at compass north (-Z).
        double z = ctx.blockZ() * zDirection;

        double range = z >= 0.0D ? northRange : southRange;
        double along = range == 0.0D ? 0.0D : Math.abs(z) / range;

        double lateral = xRange == 0.0D ? 0.0D : Math.abs(x) / xRange;
        if (lateral > xCap) {
            lateral = xCap;
        }

        double t = along + lateral;
        if (t > 1.0D) {
            t = 1.0D;
        }

        double blocks = t * range;
        if (jitterNoise.isPresent()) {
            double amplitude = knots.isEmpty() ? jitterBlocks : interpolate(blocks, 2, jitterBlocks);
            if (amplitude != 0.0D) {
                blocks += amplitude * jitterNoise.get().getValue(x * jitterXzScale, 0.0D, ctx.blockZ() * jitterXzScale);
            }
        }

        double v;
        if (knots.isEmpty()) {
            double s = range == 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, blocks / range));
            v = startValue + (endValue - startValue) * s;
        } else {
            v = interpolate(blocks, 1, startValue);
        }

        if (z < 0.0D && v > southCap) {
            v = southCap;
        }
        return v;
    }

    /** Piecewise-linear lookup of knot column {@code column} at {@code blocks}; knots lacking it use {@code fallback}. */
    private double interpolate(double blocks, int column, double fallback) {
        List<Double> first = knots.get(0);
        if (blocks <= first.get(0)) {
            return column(first, column, fallback);
        }
        for (int i = 1; i < knots.size(); i++) {
            List<Double> hi = knots.get(i);
            if (blocks <= hi.get(0)) {
                List<Double> lo = knots.get(i - 1);
                double f = (blocks - lo.get(0)) / (hi.get(0) - lo.get(0));
                double a = column(lo, column, fallback);
                return a + (column(hi, column, fallback) - a) * f;
            }
        }
        return column(knots.get(knots.size() - 1), column, fallback);
    }

    private static double column(List<Double> knot, int column, double fallback) {
        return column < knot.size() ? knot.get(column) : fallback;
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ProgressionDensityFunction(
                northRange, southRange, xRange, xCap, southCap, startValue, endValue, zDirection, knots,
                jitterNoise.map(visitor::visitNoise), jitterXzScale, jitterBlocks));
    }

    @Override
    public double minValue() {
        if (knots.isEmpty()) {
            return Math.min(startValue, endValue);
        }
        return knots.stream().mapToDouble(k -> k.get(1)).min().orElse(startValue);
    }

    @Override
    public double maxValue() {
        if (knots.isEmpty()) {
            return Math.max(startValue, endValue);
        }
        return knots.stream().mapToDouble(k -> k.get(1)).max().orElse(endValue);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
