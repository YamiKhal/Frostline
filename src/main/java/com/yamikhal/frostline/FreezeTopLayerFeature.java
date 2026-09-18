package com.yamikhal.frostline;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * minecraft:freeze_top_layer that leaves structure snow as the template built it.
 *
 * Why this is Java: freeze_top_layer snows every column's top position, and Biome.shouldSnow
 * accepts a position that already holds snow, so it sets a template's 6-layer drift to 1 layer.
 * It is one placement per chunk that walks every column itself, so no placement modifier can
 * exclude single positions (NotStructureSnowFilter does that for the placed passes). Snow! Real
 * Magic replaces the vanilla feature's body with its own; it has the same effect.
 *
 * How: before running the real feature (vanilla, or whatever a mod made of it, so Snow! Real
 * Magic's snow-in-plants still happens), remember the state at every structure snow position
 * (StructureSnow) that holds minecraft:snow right now; after it, put those states back. Nothing
 * snows before this pass, so snow found there is snow a structure placed; a piece its
 * processors vetoed left none, and its positions are not touched.
 */
public class FreezeTopLayerFeature extends Feature<NoneFeatureConfiguration> {

    public FreezeTopLayerFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        Long2ObjectMap<BlockState> kept = new Long2ObjectOpenHashMap<>();
        if (level instanceof WorldGenRegion region) {
            LongIterator it = StructureSnow.in(region, new ChunkPos(context.origin())).iterator();
            while (it.hasNext()) {
                long p = it.nextLong();
                BlockState state = level.getBlockState(BlockPos.of(p));
                if (state.is(Blocks.SNOW)) {
                    kept.put(p, state);
                }
            }
        }

        boolean placed = Feature.FREEZE_TOP_LAYER.place(context.config(), level, context.chunkGenerator(),
                context.random(), context.origin());

        for (Long2ObjectMap.Entry<BlockState> entry : kept.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (level.getBlockState(pos) != entry.getValue()) {
                level.setBlock(pos, entry.getValue(), 2);
            }
        }
        return placed;
    }
}
