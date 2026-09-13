package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

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
 *   v       = lerp(start_value, end_value, t)
 *   if z < 0: v = min(v, south_cap)                          southern hemisphere
 *                                                            never reaches the
 *                                                            payoff region
 *
 * Output is deliberately smooth and monotonic in |z|, so region ordering is a
 * guarantee, not a tuning artifact. Organic border wobble is added on the
 * datapack side by summing a low-frequency noise onto this value — keep that
 * amplitude below (band_start - south_cap) or the cap leaks.
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
        double zDirection
) implements DensityFunction.SimpleFunction {

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
                            .forGetter(ProgressionDensityFunction::zDirection)
            ).apply(instance, ProgressionDensityFunction::new)));

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

        double v = startValue + (endValue - startValue) * t;

        if (z < 0.0D && v > southCap) {
            v = southCap;
        }
        return v;
    }

    @Override
    public double minValue() {
        return Math.min(startValue, endValue);
    }

    @Override
    public double maxValue() {
        return Math.max(startValue, endValue);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
