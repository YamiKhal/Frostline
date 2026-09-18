package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.yamikhal.Frostline;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter: rejects a position where a structure template placed minecraft:snow.
 *
 * Why this is Java: snow passes are features in top_layer_modification, the last step, and
 * every structure places before them (even one in that step: structures go first within a
 * step). A feature's placement can test blocks, biome and height, never "is this a block a
 * structure wrote". So a template's hand-shaped snow layers were flattened, unstacked and
 * holed like terrain. With this modifier on a snow pass, snow a template put there stays as
 * built; everything else - terrain snow, and snow that lands ON a structure later - is still
 * handled as before.
 *
 * Positions: StructureSnow. A piece its processors vetoed still protects those positions; the
 * snow there is terrain snow, and the only effect is that a pass skips a handful of blocks.
 *
 * freeze_top_layer is not a placed pass (one placement per chunk, every column inside the
 * feature), so it cannot carry this; FreezeTopLayerFeature covers it.
 */
public class NotStructureSnowFilter extends PlacementFilter {

    public static final NotStructureSnowFilter INSTANCE = new NotStructureSnowFilter();
    public static final Codec<NotStructureSnowFilter> CODEC = Codec.unit(() -> INSTANCE);

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        if (!(context.getLevel() instanceof WorldGenRegion region)) {
            return true;
        }
        return !StructureSnow.in(region, new ChunkPos(pos)).contains(pos.asLong());
    }

    @Override
    public PlacementModifierType<?> type() {
        return Frostline.NOT_STRUCTURE_SNOW.get();
    }
}
