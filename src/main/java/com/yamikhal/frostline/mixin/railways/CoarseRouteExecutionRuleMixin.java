package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.worldgen.placement.PlacementDecision;
import com.vodmordia.railwaysuntold.worldgen.placement.decision.CoarseRouteExecutionRule;
import com.vodmordia.railwaysuntold.worldgen.placement.decision.DeciderContext;
import com.yamikhal.frostline.compat.railways.TerminusBackstop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Gives TerminusBackstop the first say on every placement decision. */
@Mixin(value = CoarseRouteExecutionRule.class, remap = false)
public abstract class CoarseRouteExecutionRuleMixin {

    @Inject(method = "decide", at = @At("HEAD"), cancellable = true)
    private void frostline$terminus(DeciderContext context, CallbackInfoReturnable<Optional<PlacementDecision>> cir) {
        Optional<PlacementDecision> decision = TerminusBackstop.check(context);
        if (decision != null) {
            cir.setReturnValue(decision);
        }
    }
}
