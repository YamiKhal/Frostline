package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.Optional;

/**
 * Datapack-facing knobs for {@link PondFeature}. See that class for the model.
 *
 *   radius           basin radius before stretch and warp
 *   depth            how far the basin centre sinks; on flat ground that is the water
 *                    depth in the middle, cap layer included
 *   max_bank_height  height spread allowed along the rim; steeper sites are skipped
 *   surface_offset   sink the water plane this many blocks below the lowest rim column
 *   cap              top layer, rolled per column with cap_chance
 *   shallow_cap      top layer where the water is only 1 deep (thin ice needs water under
 *                    it); defaults to cap
 *   floor            laid on the sunken ground under every water column; falling blocks
 *                    are swapped for wall where they would have nothing to rest on
 *   wall             last-resort plug for caves next to the water; terrain-matching
 *                    blocks are tried first
 *   ground           what counts as terrain when finding the surface; any other solid
 *                    block on the way down (logs, structures) cancels the pond
 *   spacing          region size in chunks. Each region gets at most one pond, in one
 *                    seed-chosen chunk, so ponds never cluster. 1 disables this.
 *   region_chance    chance a region gets a pond attempt at all
 *   salt             keeps pond types on independent region grids
 */
public record PondConfiguration(
        IntProvider radius,
        IntProvider depth,
        int maxBankHeight,
        int surfaceOffset,
        Optional<BlockStateProvider> cap,
        Optional<BlockStateProvider> shallowCap,
        float capChance,
        Optional<BlockStateProvider> floor,
        BlockStateProvider wall,
        TagKey<Block> ground,
        int spacing,
        float regionChance,
        int salt
) implements FeatureConfiguration {

    public static final Codec<PondConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // 12 * stretch * wobble + warp stays inside PondFeature.REACH.
            IntProvider.codec(1, 12).fieldOf("radius")
                    .forGetter(PondConfiguration::radius),
            IntProvider.codec(1, 8).fieldOf("depth")
                    .forGetter(PondConfiguration::depth),
            Codec.intRange(0, 16).optionalFieldOf("max_bank_height", 3)
                    .forGetter(PondConfiguration::maxBankHeight),
            Codec.intRange(0, 4).optionalFieldOf("surface_offset", 0)
                    .forGetter(PondConfiguration::surfaceOffset),
            BlockStateProvider.CODEC.optionalFieldOf("cap")
                    .forGetter(PondConfiguration::cap),
            BlockStateProvider.CODEC.optionalFieldOf("shallow_cap")
                    .forGetter(PondConfiguration::shallowCap),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("cap_chance", 1.0F)
                    .forGetter(PondConfiguration::capChance),
            BlockStateProvider.CODEC.optionalFieldOf("floor")
                    .forGetter(PondConfiguration::floor),
            BlockStateProvider.CODEC.fieldOf("wall")
                    .forGetter(PondConfiguration::wall),
            TagKey.hashedCodec(Registries.BLOCK).fieldOf("ground")
                    .forGetter(PondConfiguration::ground),
            Codec.intRange(1, 256).optionalFieldOf("spacing", 1)
                    .forGetter(PondConfiguration::spacing),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("region_chance", 1.0F)
                    .forGetter(PondConfiguration::regionChance),
            Codec.INT.optionalFieldOf("salt", 0)
                    .forGetter(PondConfiguration::salt)
    ).apply(instance, PondConfiguration::new));
}
