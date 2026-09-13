package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.datapack.TriggerContext;
import com.yamikhal.frostline.compat.railways.ClimateTrigger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EventTrigger is sealed, so frostline:climate triggers ride inside GameTime records with
 * a reserved negative key (see ClimateTrigger). This routes those keys to the climate
 * test; ordinary game-time triggers are untouched.
 */
@Mixin(targets = "com.vodmordia.railwaysuntold.datapack.EventTrigger$GameTime", remap = false)
public abstract class GameTimeTriggerMixin {

    @Shadow
    @Final
    private long minTicks;

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void frostline$climate(TriggerContext ctx, CallbackInfoReturnable<Boolean> cir) {
        if (ClimateTrigger.isCarrierKey(minTicks)) {
            cir.setReturnValue(ClimateTrigger.testCarrier(minTicks, ctx));
        }
    }
}
