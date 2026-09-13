package com.yamikhal.frostline;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reads a climate value of a level at a column, the same way its terrain does.
 *
 *   parameter = temperature | humidity | continentalness | erosion | depth | weirdness
 *       the router slot as generated, jitter included
 *   parameter = progression, axis = <slot>
 *       the raw frostline:progression inside that slot, without the slot's own jitter,
 *       optionally plus a jitter of our choosing:
 *       jitter = { noise, xz_scale, amount }  ->  + amount * shifted_noise(noise, xz_scale)
 *
 * The second form exists because set-pieces are not drawn on the biome value. The
 * escarpment wall is gate(progression + 0.012 * cliff_jitter), while temperature carries
 * a 0.075 progress_jitter (about ±1 900 blocks), so only the rebuilt expression lands on
 * the wall. The jitter term is built from vanilla DensityFunctions and wired with the
 * level's own noises, so it matches the router's shifted_noise exactly.
 *
 * Compiled once per RandomState; sampling is a plain 2D compute.
 */
public final class ClimateProbe {

    private final String parameter;
    private final String axis;
    private final ResourceLocation jitterNoise;
    private final double jitterScale;
    private final double jitterAmount;
    private final Map<RandomState, DensityFunction> compiled = Collections.synchronizedMap(new WeakHashMap<>());

    public ClimateProbe(String parameter, String axis, ResourceLocation jitterNoise,
                        double jitterScale, double jitterAmount) {
        this.parameter = parameter.toLowerCase(Locale.ROOT);
        this.axis = axis.toLowerCase(Locale.ROOT);
        this.jitterNoise = jitterNoise;
        this.jitterScale = jitterScale;
        this.jitterAmount = jitterAmount;
    }

    public static ClimateProbe fromJson(JsonObject json) {
        String parameter = GsonHelper.getAsString(json, "parameter");
        String axis = GsonHelper.getAsString(json, "axis", "temperature");
        ResourceLocation noise = null;
        double scale = 0.0D;
        double amount = 0.0D;
        if (json.has("jitter")) {
            JsonObject jitter = GsonHelper.getAsJsonObject(json, "jitter");
            noise = new ResourceLocation(GsonHelper.getAsString(jitter, "noise"));
            scale = GsonHelper.getAsDouble(jitter, "xz_scale");
            amount = GsonHelper.getAsDouble(jitter, "amount");
        }
        ClimateProbe probe = new ClimateProbe(parameter, axis, noise, scale, amount);
        slotName(probe.parameter.equals("progression") ? probe.axis : probe.parameter);
        return probe;
    }

    public double sample(ServerLevel level, int x, int z) {
        RandomState state = level.getChunkSource().randomState();
        DensityFunction function = compiled.computeIfAbsent(state, s -> compile(level, s));
        return function.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    private DensityFunction compile(ServerLevel level, RandomState state) {
        NoiseRouter router = state.router();
        DensityFunction base = parameter.equals("progression")
                ? findProgression(slot(router, axis))
                : slot(router, parameter);
        if (jitterNoise == null || jitterAmount == 0.0D) {
            return base;
        }
        Registry<NormalNoise.NoiseParameters> noises = level.registryAccess().registryOrThrow(Registries.NOISE);
        Holder<NormalNoise.NoiseParameters> offset = noises.getHolderOrThrow(Noises.SHIFT);
        Holder<NormalNoise.NoiseParameters> jitter =
                noises.getHolderOrThrow(ResourceKey.create(Registries.NOISE, jitterNoise));
        DensityFunction shifted = DensityFunctions.shiftedNoise2d(
                DensityFunctions.shiftA(offset), DensityFunctions.shiftB(offset), jitterScale, jitter);
        DensityFunction wired = shifted.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                return function;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                return new DensityFunction.NoiseHolder(holder.noiseData(),
                        state.getOrCreateNoise(holder.noiseData().unwrapKey().orElseThrow()));
            }
        });
        return DensityFunctions.add(base, DensityFunctions.mul(DensityFunctions.constant(jitterAmount), wired));
    }

    private static String slotName(String name) {
        return switch (name) {
            case "temperature", "humidity", "vegetation", "continentalness", "continents",
                 "erosion", "depth", "weirdness", "ridges" -> name;
            default -> throw new IllegalArgumentException("Unknown climate parameter: " + name);
        };
    }

    private static DensityFunction slot(NoiseRouter router, String name) {
        return switch (slotName(name)) {
            case "temperature" -> router.temperature();
            case "humidity", "vegetation" -> router.vegetation();
            case "continentalness", "continents" -> router.continents();
            case "erosion" -> router.erosion();
            case "depth" -> router.depth();
            default -> router.ridges();
        };
    }

    private static DensityFunction findProgression(DensityFunction slot) {
        DensityFunction[] found = new DensityFunction[1];
        slot.mapAll(function -> {
            if (found[0] == null && function instanceof ProgressionDensityFunction) {
                found[0] = function;
            }
            return function;
        });
        if (found[0] == null) {
            throw new IllegalStateException("No frostline:progression in that router slot");
        }
        return found[0];
    }
}
