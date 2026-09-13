package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.worldgen.village.VillagePredictor;
import com.yamikhal.frostline.compat.railways.RailwaysCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Structure targets must lie within TARGET_CONE_DEGREES of the heading (Railways Untold:
 * 120). The line still visits settlements, but only ones it can reach while heading
 * north or south, so it never swings sideways across the map for one.
 */
@Mixin(value = VillagePredictor.class, remap = false)
public abstract class VillagePredictorMixin {

    @ModifyConstant(method = "findBestScoredPrediction", constant = @Constant(doubleValue = 120.0D))
    private static double frostline$narrowCone(double original) {
        return RailwaysCompat.TARGET_CONE_DEGREES;
    }
}
