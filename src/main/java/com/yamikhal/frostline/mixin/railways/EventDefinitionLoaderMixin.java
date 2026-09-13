package com.yamikhal.frostline.mixin.railways;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vodmordia.railwaysuntold.datapack.EventDefinitionLoader;
import com.vodmordia.railwaysuntold.datapack.EventTrigger;
import com.vodmordia.railwaysuntold.datapack.TriggerContext;
import com.yamikhal.frostline.compat.railways.ClimateTrigger;
import com.yamikhal.frostline.compat.railways.TerminusBackstop;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Three hooks on Railways Untold's event loader:
 *
 *   parseTriggers           adds the frostline:climate trigger type (their parser is a closed switch)
 *   apply (RETURN)          indexes terminus events for TerminusBackstop after each reload
 *   getWeightedRandomEvent  hands out the event TerminusBackstop forced, ignoring roll and filters
 */
@Mixin(value = EventDefinitionLoader.class, remap = false)
public abstract class EventDefinitionLoaderMixin {

    @Shadow
    private volatile List<EventDefinitionLoader.ValidatedEventEntry> entries;

    @Inject(method = "parseTriggers", at = @At("HEAD"), cancellable = true)
    private static void frostline$climateTriggers(JsonObject json, CallbackInfoReturnable<List<EventTrigger>> cir) {
        if (!json.has("triggers") || !json.get("triggers").isJsonArray()) {
            return;
        }
        boolean ours = false;
        for (JsonElement element : json.getAsJsonArray("triggers")) {
            if (element.isJsonObject()
                    && ClimateTrigger.TYPE.equals(GsonHelper.getAsString(element.getAsJsonObject(), "type", ""))) {
                ours = true;
                break;
            }
        }
        if (!ours) {
            return;
        }
        List<EventTrigger> triggers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("triggers")) {
            JsonObject object = element.getAsJsonObject();
            triggers.add(ClimateTrigger.TYPE.equals(GsonHelper.getAsString(object, "type", ""))
                    ? ClimateTrigger.parseCarrier(object)
                    : EventTrigger.parse(object));
        }
        cir.setReturnValue(Collections.unmodifiableList(triggers));
    }

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"))
    private void frostline$indexTermini(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager,
                                        ProfilerFiller profiler, CallbackInfo ci) {
        TerminusBackstop.rebuild(entries);
    }

    @Inject(method = "getWeightedRandomEvent", at = @At("HEAD"), cancellable = true)
    private void frostline$forcedTerminus(Random random, Holder<Biome> biome, TriggerContext context,
                                          CallbackInfoReturnable<EventDefinitionLoader.ValidatedEventEntry> cir) {
        ResourceLocation id = TerminusBackstop.consumeForced();
        if (id == null) {
            return;
        }
        for (EventDefinitionLoader.ValidatedEventEntry entry : entries) {
            if (entry.definition().id().equals(id)) {
                cir.setReturnValue(entry);
                return;
            }
        }
    }
}
