package com.yamikhal.frostline.weather.client;

import com.yamikhal.frostline.weather.WeatherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Quiets the biome's own ambience while a storm blows, so the wind carries the storm.
 *
 *   loop       the biome's ambient_sound. Wrapped when it starts, so its volume follows
 *              stormAmbienceVolume as a storm builds and returns as it passes. Vanilla keeps
 *              fading and stopping the original instance; the wrapper just scales it.
 *   additions  the biome's additions_sound one-shots. Skipped in proportion to the storm.
 *
 * Mood (cave) sounds are left alone. Nothing changes in the biome files.
 */
final class StormAmbience {

    private StormAmbience() {
    }

    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.AMBIENT || !WeatherClient.active(Minecraft.getInstance().level)) {
            return;
        }
        if (sound instanceof BiomeAmbientSoundsHandler.LoopSoundInstance loop) {
            event.setSound(new Ducked(loop));
        } else if (sound.isRelative() && !(sound instanceof TickableSoundInstance)) {
            // Additions are relative one-shots; mood sounds are positioned in the world.
            float duck = 1 - factor();
            if (duck > 0 && Minecraft.getInstance().level.random.nextFloat() < duck) {
                event.setSound(null);
            }
        }
    }

    /** Volume multiplier for biome ambience right now: 1 when calm, stormAmbienceVolume in a full storm. */
    static float factor() {
        float target = WeatherConfig.CLIENT.stormAmbienceVolume.get().floatValue();
        return 1 + (target - 1) * WeatherClient.stormOnly();
    }

    /** The biome loop with its volume scaled by the storm. */
    private record Ducked(BiomeAmbientSoundsHandler.LoopSoundInstance loop) implements TickableSoundInstance {

        @Override
        public boolean isStopped() {
            return loop.isStopped();
        }

        @Override
        public void tick() {
            loop.tick();
        }

        @Override
        public float getVolume() {
            return loop.getVolume() * factor();
        }

        @Override
        public ResourceLocation getLocation() {
            return loop.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return loop.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return loop.getSound();
        }

        @Override
        public SoundSource getSource() {
            return loop.getSource();
        }

        @Override
        public boolean isLooping() {
            return loop.isLooping();
        }

        @Override
        public boolean isRelative() {
            return loop.isRelative();
        }

        @Override
        public int getDelay() {
            return loop.getDelay();
        }

        @Override
        public float getPitch() {
            return loop.getPitch();
        }

        @Override
        public double getX() {
            return loop.getX();
        }

        @Override
        public double getY() {
            return loop.getY();
        }

        @Override
        public double getZ() {
            return loop.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return loop.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return loop.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return loop.getStream(buffers, sound, looping);
        }
    }
}
