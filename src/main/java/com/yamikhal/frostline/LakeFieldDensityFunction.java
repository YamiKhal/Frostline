package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sparse field of lake sites, exposed as a climate value so a biome can sit exactly on each lake.
 *
 * Lives in the noise router's `continents` slot. Zone dimensions leave that slot at a
 * constant -1 and no terrain function reads it, so it only steers biome selection. The
 * lake biome claims continentalness >= BIOME_EDGE while every other zone biome stops
 * there, which makes the biome border the lake's outer shore.
 *
 * Model:
 *   cells     the world is cut into cell_size squares; each rolls once for a site
 *   present   roll < chance AND gate(centre) >= gate_min
 *             (gate is erosion, so lakes only settle on flat ground)
 *   centre    jittered inside its cell, kept max_radius + 16 from the edges so a site
 *             never reaches past the 3x3 neighbouring cells
 *   radius    uniform in [min_radius, max_radius], wobbled per angle by two harmonics
 *   value     d = distance / wobbled radius
 *             d <  1   BIOME_EDGE + (1 - BIOME_EDGE) * (1 - d)
 *             d >= 1   BIOME_EDGE - (d - 1) * radius / FALLOFF_BLOCKS
 *
 * Rolls are `noise` sampled at cell coordinates, so sites follow the world seed without
 * this function ever seeing the seed. FrozenLakeFeature reads the same sites back through
 * {@link #find} and {@link #siteAt}, keeping the carved lake and the biome in lockstep.
 *
 * Not a record on purpose: the site cache is mutable, and the router wiring hashes
 * density functions.
 */
public final class LakeFieldDensityFunction implements DensityFunction.SimpleFunction {

    /** Must match the lake biome's lower continentalness bound in the dimension JSON. */
    public static final double BIOME_EDGE = 0.6D;
    private static final double FALLOFF_BLOCKS = 12.0D;
    private static final int CACHE_LIMIT = 8192;

    public static final KeyDispatchDataCodec<LakeFieldDensityFunction> CODEC =
            KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise")
                            .forGetter(LakeFieldDensityFunction::noise),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("gate")
                            .forGetter(LakeFieldDensityFunction::gate),
                    Codec.DOUBLE.optionalFieldOf("gate_min", 0.0D)
                            .forGetter(LakeFieldDensityFunction::gateMin),
                    Codec.intRange(64, 8192).fieldOf("cell_size")
                            .forGetter(LakeFieldDensityFunction::cellSize),
                    Codec.doubleRange(0.0D, 1.0D).fieldOf("chance")
                            .forGetter(LakeFieldDensityFunction::chance),
                    Codec.doubleRange(4.0D, 256.0D).fieldOf("min_radius")
                            .forGetter(LakeFieldDensityFunction::minRadius),
                    Codec.doubleRange(4.0D, 256.0D).fieldOf("max_radius")
                            .forGetter(LakeFieldDensityFunction::maxRadius)
            ).apply(instance, LakeFieldDensityFunction::new)));

    private static final Map<RandomState, Optional<LakeFieldDensityFunction>> BY_STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final DensityFunction.NoiseHolder noise;
    private final DensityFunction gate;
    private final double gateMin;
    private final int cellSize;
    private final double chance;
    private final double minRadius;
    private final double maxRadius;
    private final Map<Long, Optional<Site>> sites = new ConcurrentHashMap<>();

    public LakeFieldDensityFunction(DensityFunction.NoiseHolder noise, DensityFunction gate, double gateMin,
                                    int cellSize, double chance, double minRadius, double maxRadius) {
        this.noise = noise;
        this.gate = gate;
        this.gateMin = gateMin;
        this.cellSize = cellSize;
        this.chance = chance;
        this.minRadius = Math.min(minRadius, maxRadius);
        this.maxRadius = Math.max(minRadius, maxRadius);
    }

    /**
     * One lake: centre, biome radius, edge wobble, and a spare roll for per-lake choices
     * (depth) that every chunk of the lake must agree on.
     */
    public record Site(double x, double z, double radius,
                       double wobble2, double wobble3, double phase2, double phase3, double roll) {

        /** Biome radius at angle theta. */
        public double edge(double theta) {
            return radius * (1.0D
                    + wobble2 * Math.sin(2.0D * theta + phase2)
                    + wobble3 * Math.sin(3.0D * theta + phase3));
        }

        /** Distance from centre over the edge at that angle; below 1 is inside the biome. */
        public double normalized(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return Math.sqrt(dx * dx + dz * dz) / edge(Math.atan2(dz, dx));
        }
    }

    /** The wired, seeded field behind a dimension's `continents`, if that dimension uses one. */
    public static Optional<LakeFieldDensityFunction> find(RandomState state) {
        return BY_STATE.computeIfAbsent(state, s -> {
            LakeFieldDensityFunction[] found = new LakeFieldDensityFunction[1];
            s.router().continents().mapAll(function -> {
                if (function instanceof LakeFieldDensityFunction lake) {
                    found[0] = lake;
                }
                return function;
            });
            return Optional.ofNullable(found[0]);
        });
    }

    public Optional<Site> siteAt(int cellX, int cellZ) {
        if (sites.size() > CACHE_LIMIT) {
            sites.clear();
        }
        return sites.computeIfAbsent(ChunkPos.asLong(cellX, cellZ), key -> rollSite(cellX, cellZ));
    }

    private Optional<Site> rollSite(int cellX, int cellZ) {
        if (roll(cellX, cellZ, 0) >= chance) {
            return Optional.empty();
        }
        double margin = maxRadius + 16.0D;
        double span = Math.max(0.0D, cellSize - 2.0D * margin);
        double x = (double) cellX * cellSize + margin + roll(cellX, cellZ, 1) * span;
        double z = (double) cellZ * cellSize + margin + roll(cellX, cellZ, 2) * span;
        if (gate.compute(new DensityFunction.SinglePointContext(Mth.floor(x), 0, Mth.floor(z))) < gateMin) {
            return Optional.empty();
        }
        return Optional.of(new Site(
                x, z,
                minRadius + roll(cellX, cellZ, 3) * (maxRadius - minRadius),
                0.05D + roll(cellX, cellZ, 4) * 0.10D,
                0.03D + roll(cellX, cellZ, 5) * 0.07D,
                roll(cellX, cellZ, 6) * Math.PI * 2.0D,
                roll(cellX, cellZ, 7) * Math.PI * 2.0D,
                roll(cellX, cellZ, 8)));
    }

    /** Uniform-ish [0, 1) from the seeded noise: fractional part of an amplified sample. */
    private double roll(int cellX, int cellZ, int salt) {
        double sample = noise.getValue(cellX * 17.31D + salt * 101.7D, salt * 13.9D, cellZ * 17.31D - salt * 57.3D);
        double scaled = Math.abs(sample) * 7919.0D;
        return scaled - Math.floor(scaled);
    }

    @Override
    public double compute(FunctionContext ctx) {
        int x = ctx.blockX();
        int z = ctx.blockZ();
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);

        double best = -1.0D;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<Site> site = siteAt(cellX + dx, cellZ + dz);
                if (site.isEmpty()) {
                    continue;
                }
                double d = site.get().normalized(x, z);
                double value = d < 1.0D
                        ? BIOME_EDGE + (1.0D - BIOME_EDGE) * (1.0D - d)
                        : BIOME_EDGE - (d - 1.0D) * site.get().radius() / FALLOFF_BLOCKS;
                best = Math.max(best, value);
            }
        }
        return Mth.clamp(best, -1.0D, 1.0D);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new LakeFieldDensityFunction(
                visitor.visitNoise(noise), gate.mapAll(visitor), gateMin, cellSize, chance, minRadius, maxRadius));
    }

    @Override
    public double minValue() {
        return -1.0D;
    }

    @Override
    public double maxValue() {
        return 1.0D;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    public DensityFunction.NoiseHolder noise() {
        return noise;
    }

    public DensityFunction gate() {
        return gate;
    }

    public double gateMin() {
        return gateMin;
    }

    public int cellSize() {
        return cellSize;
    }

    public double chance() {
        return chance;
    }

    public double minRadius() {
        return minRadius;
    }

    public double maxRadius() {
        return maxRadius;
    }
}
