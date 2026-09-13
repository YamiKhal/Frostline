package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.util.spatial.DirectionUtil;
import com.yamikhal.frostline.compat.railways.RailwaysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Exploration targets land on the corridor line instead of a random ±200 blocks sideways.
 * Every exploration target in Railways Untold goes through this method
 * (generateExplorationTargetAvoiding calls it too), so the line follows the pass the
 * terrain carves and cannot drift or turn back.
 */
@Mixin(value = DirectionUtil.class, remap = false)
public abstract class DirectionUtilMixin {

    @Inject(method = "generateExplorationTarget", at = @At("RETURN"), cancellable = true)
    private static void frostline$followCorridor(BlockPos origin, Direction direction, int distance, Random random,
                                                 CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(RailwaysCompat.steerToCorridor(cir.getReturnValue(), direction));
    }
}
