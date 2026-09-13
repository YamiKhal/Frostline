package com.yamikhal.frostline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;

/**
 * Terrain shaping shared by PondFeature and FrozenLakeFeature.
 *
 * Neither feature cuts terrain down to a water plane any more: that left flat walls on
 * slopes. Instead each column inside the basin sinks by depth * profile(t), carrying its
 * surface (snow block, grass, snow layer) down with it. Water then fills only the columns
 * whose sunken ground ends up below the rim level, so the shoreline is a contour of the
 * real terrain and the uphill side stays a dry, sloping bank.
 */
final class Hollow {

    /** Blocks of soil carried down under the surface, so a dent keeps its layering. */
    private static final int SOIL = 5;

    private Hollow() {
    }

    /** 1 at the centre, easing to 0 with zero slope at t = 1, so the dent has no rim step. */
    static double profile(double t) {
        if (t >= 1.0D) {
            return 0.0D;
        }
        double a = 1.0D - t * t;
        return a * a;
    }

    /**
     * Moves the top of column (x, z) down by `lower`, keeping block order; vacated blocks
     * become air. The column is read in full before anything is emitted, so `out` may write
     * straight into the level. Emits nothing and returns false if a block that must not move
     * (logs, block entities, bedrock) is in the way.
     */
    static boolean sink(BlockGetter level, BiConsumer<BlockPos, BlockState> out, int x, int z, int ground, int lower) {
        if (lower <= 0) {
            return true;
        }
        int from = ground - SOIL - lower;
        int top = ground + 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState[] column = new BlockState[top - from + 1];
        for (int y = from; y <= top; y++) {
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (!PondFeature.canReplace(state)) {
                return false;
            }
            column[y - from] = state;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = from; y <= top; y++) {
            int source = y + lower;
            out.accept(new BlockPos(x, y, z), source <= top ? column[source - from] : air);
        }
        return true;
    }

    /** A plug that blends in: the block under `pos` when that is solid terrain, else `fallback`. */
    static BlockState terrainLike(BlockGetter level, BlockPos pos, BlockState below, BlockState fallback) {
        BlockPos belowPos = pos.below();
        if (below.getFluidState().isEmpty() && below.isFaceSturdy(level, belowPos, Direction.UP)
                && PondFeature.canReplace(below)) {
            return below;
        }
        return fallback;
    }
}
