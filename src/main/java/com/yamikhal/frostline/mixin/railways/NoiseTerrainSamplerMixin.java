package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.worldgen.terrain.NoiseTerrainSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A column is only "ocean" if its ground is actually below sea level.
 *
 * Railways Untold decides ocean from continentalness alone (below -0.19, vanilla's ocean
 * band). Frostline uses continentalness as The Quiet's axis and pins it at -1 across the
 * whole journey, so every column read as ocean. Each route plan then flood-filled 3 000
 * blocks looking for land, island-hopped, priced every cell as sea, and the water checks
 * and bridge detection built on top of it misfired. That was the main planning cost.
 *
 * Keeping the original verdict and also requiring ground below sea level leaves real oceans
 * in normal worlds unchanged. getBaseHeight is cached per column and the planner samples it
 * anyway.
 */
@Mixin(value = NoiseTerrainSampler.class, remap = false)
public abstract class NoiseTerrainSamplerMixin {

    @Shadow
    public abstract int getBaseHeight(int x, int z);

    @Shadow
    public abstract int getSeaLevel();

    @Inject(method = "isLikelyOcean", at = @At("RETURN"), cancellable = true)
    private void frostline$groundBelowSea(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && getBaseHeight(x, z) >= getSeaLevel()) {
            cir.setReturnValue(false);
        }
    }
}
