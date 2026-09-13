package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Datapack-facing knobs for {@link FrozenLakeFeature}. Where lakes are and how big the
 * biome is comes from frostline:lake_field in the noise router, not from here.
 *
 *   basin_fraction   basin radius as a share of the biome radius; keep it so basin plus
 *                    warp stays inside the biome
 *   min/max_depth    how far the basin centre sinks, rolled once per lake; on flat ground
 *                    the deepest water is about one less (the water sits a block below
 *                    the rim)
 *   max_berm_height  most a low rim point is lifted to hold the water; also caps how far
 *                    above the lowest rim point the water level may sit
 *   shore_height     blocks above the water level over which the sink fades out; ground
 *                    higher than this is never reshaped
 *   warp             domain warp strength as a share of the biome radius
 *   warp_scale       warp noise frequency; lower = broader lobes
 *   surface          the open-lake cap (thin ice)
 *   floe             cap for floes, the shore ring and 1-deep water
 *   floe_scale       multiplier on the field noise coordinates; higher = smaller floes
 *   floe_threshold   field noise above this becomes floe
 *   shoreline        basin t beyond which the cap is always floe
 *   floor            laid on the sunken ground under every water column
 *   wall             last-resort plug next to the water; terrain-matching blocks first
 *   ground           what counts as terrain when finding the surface
 */
public record FrozenLakeConfiguration(
        double basinFraction,
        int minDepth,
        int maxDepth,
        int maxBermHeight,
        int shoreHeight,
        double warp,
        double warpScale,
        BlockState surface,
        BlockStateProvider floe,
        double floeScale,
        double floeThreshold,
        double shoreline,
        BlockStateProvider floor,
        BlockStateProvider wall,
        TagKey<Block> ground
) implements FeatureConfiguration {

    public static final Codec<FrozenLakeConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.1D, 1.0D).fieldOf("basin_fraction")
                    .forGetter(FrozenLakeConfiguration::basinFraction),
            Codec.intRange(2, 16).fieldOf("min_depth")
                    .forGetter(FrozenLakeConfiguration::minDepth),
            Codec.intRange(2, 16).fieldOf("max_depth")
                    .forGetter(FrozenLakeConfiguration::maxDepth),
            Codec.intRange(1, 16).optionalFieldOf("max_berm_height", 4)
                    .forGetter(FrozenLakeConfiguration::maxBermHeight),
            Codec.intRange(1, 16).optionalFieldOf("shore_height", 4)
                    .forGetter(FrozenLakeConfiguration::shoreHeight),
            Codec.doubleRange(0.0D, 0.5D).optionalFieldOf("warp", 0.25D)
                    .forGetter(FrozenLakeConfiguration::warp),
            Codec.doubleRange(0.01D, 4.0D).optionalFieldOf("warp_scale", 0.33D)
                    .forGetter(FrozenLakeConfiguration::warpScale),
            BlockState.CODEC.fieldOf("surface")
                    .forGetter(FrozenLakeConfiguration::surface),
            BlockStateProvider.CODEC.fieldOf("floe")
                    .forGetter(FrozenLakeConfiguration::floe),
            Codec.doubleRange(0.01D, 64.0D).optionalFieldOf("floe_scale", 1.0D)
                    .forGetter(FrozenLakeConfiguration::floeScale),
            Codec.doubleRange(-2.0D, 2.0D).optionalFieldOf("floe_threshold", 0.35D)
                    .forGetter(FrozenLakeConfiguration::floeThreshold),
            Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("shoreline", 0.6D)
                    .forGetter(FrozenLakeConfiguration::shoreline),
            BlockStateProvider.CODEC.fieldOf("floor")
                    .forGetter(FrozenLakeConfiguration::floor),
            BlockStateProvider.CODEC.fieldOf("wall")
                    .forGetter(FrozenLakeConfiguration::wall),
            TagKey.hashedCodec(Registries.BLOCK).fieldOf("ground")
                    .forGetter(FrozenLakeConfiguration::ground)
    ).apply(instance, FrozenLakeConfiguration::new));
}
