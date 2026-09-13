package com.yamikhal.frostline.weather.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;

/**
 * Vanilla overworld effects (sky, clouds, fog all unchanged) with Frostline's precipitation.
 * Registered for minecraft:overworld effects, which every Frostline dimension uses. Returns
 * false, handing back to vanilla, when weather visuals are off or the dimension is excluded.
 */
final class FrostlineOverworldEffects extends DimensionSpecialEffects.OverworldEffects {

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
                                     double camX, double camY, double camZ) {
        return WeatherClient.active(level)
                && SnowRenderer.render(level, ticks, partialTick, lightTexture, camX, camY, camZ);
    }
}
