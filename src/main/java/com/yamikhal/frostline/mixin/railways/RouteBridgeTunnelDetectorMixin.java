package com.yamikhal.frostline.mixin.railways;

import com.yamikhal.frostline.compat.railways.RailwaysCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Raises the peak rise Railways Untold bores a tunnel for (25 blocks) to
 * TUNNEL_RISE_THRESHOLD. Smaller humps are followed or cut through, which the corridor
 * valley makes the common case. The class is package-private, hence targets by name.
 */
@Mixin(targets = "com.vodmordia.railwaysuntold.worldgen.planner.noise.RouteBridgeTunnelDetector", remap = false)
public abstract class RouteBridgeTunnelDetectorMixin {

    @ModifyConstant(method = "applyTunnelDetection", constant = @Constant(intValue = 25))
    private static int frostline$tunnelRise(int original) {
        return RailwaysCompat.TUNNEL_RISE_THRESHOLD;
    }
}
