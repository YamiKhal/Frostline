package com.yamikhal.frostline.weather.client;

import com.yamikhal.frostline.weather.FrostlineWeather;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The wind: one looping, non-positional sound in the Weather category whose volume and
 * pitch glide toward what WeatherClient asks for. Stops itself after being silent for two
 * seconds; WeatherClient starts a new one when wind returns.
 */
final class WindSound extends AbstractTickableSoundInstance {

    private static final int SILENT_TICKS_TO_STOP = 40;

    private float targetVolume;
    private float targetPitch = 1;
    private int silentTicks;

    WindSound() {
        super(FrostlineWeather.WIND.get(), SoundSource.WEATHER, RandomSource.create());
        looping = true;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = 0;
    }

    void setTarget(float volume, float pitch) {
        targetVolume = volume;
        targetPitch = pitch;
    }

    @Override
    public void tick() {
        volume += (targetVolume - volume) * 0.05f;
        pitch += (targetPitch - pitch) * 0.05f;
        if (targetVolume < 0.005f && volume < 0.005f) {
            if (++silentTicks > SILENT_TICKS_TO_STOP) {
                stop();
            }
        } else {
            silentTicks = 0;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
