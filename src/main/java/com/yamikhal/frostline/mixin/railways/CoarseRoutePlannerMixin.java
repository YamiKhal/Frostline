package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.worldgen.planner.noise.CoarseRoute;
import com.vodmordia.railwaysuntold.worldgen.planner.noise.CoarseRoutePlanner;
import com.vodmordia.railwaysuntold.worldgen.terrain.NoiseTerrainProfile;
import com.vodmordia.railwaysuntold.worldgen.terrain.NoiseTerrainSampler;
import com.yamikhal.frostline.compat.railways.ElevationLookahead;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Runs ElevationLookahead on each coarse route right after Railways Untold marks its own
 * bridges and tunnels, and before slope validation, smoothing and compilation, so every later
 * pass builds on the held profile.
 */
@Mixin(value = CoarseRoutePlanner.class, remap = false)
public abstract class CoarseRoutePlannerMixin {

    @Inject(method = "step4_DetectBridgesAndTunnels", at = @At("RETURN"))
    private static void frostline$holdBetweenHills(List<CoarseRoute.CoarseWaypoint> waypoints,
                                                   NoiseTerrainSampler sampler, int currentTrackY,
                                                   CallbackInfoReturnable<NoiseTerrainProfile> cir) {
        ElevationLookahead.apply(waypoints);
    }
}
