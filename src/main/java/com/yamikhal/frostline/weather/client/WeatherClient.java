package com.yamikhal.frostline.weather.client;

import com.yamikhal.frostline.weather.FrostlineWeather;
import com.yamikhal.frostline.weather.WeatherConfig;
import com.yamikhal.frostline.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client weather state, updated once per tick and read by the renderer, fog, sound and
 * particles.
 *
 *   storm     storm intensity 0..1: the vanilla rain level (synced by the server), eased over
 *             stormFadeSeconds, never below windsweptIntensity in #frostline:windswept biomes.
 *   local     storm felt at the camera: storm, but only where snow actually falls.
 *   exposure  0 sheltered .. 1 under open sky, from sky light at the eye.
 *
 * No vanilla class is patched: rendering goes through Forge's DimensionSpecialEffects hook on
 * the overworld effects (FrostlineOverworldEffects), the rest through Forge events.
 */
public final class WeatherClient {

    static final ColumnCache COLUMNS = new ColumnCache();

    private static float storm;
    private static float stormO;
    private static float local;
    private static float localO;
    private static float exposure;
    /** Storm at the camera from actual weather only (no windswept floor), for ducking biome ambience. */
    private static float stormOnly;
    private static float gust = 1;
    private static ClientLevel lastLevel;
    private static int tickCount;
    private static WindSound wind;

    private WeatherClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(WeatherClient::onRegisterEffects);
        modBus.addListener(WeatherClient::onRegisterParticles);
        MinecraftForge.EVENT_BUS.addListener(WeatherClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(WeatherClient::onRenderFog);
        MinecraftForge.EVENT_BUS.addListener(WeatherClient::onFogColor);
        MinecraftForge.EVENT_BUS.addListener(StormAmbience::onPlaySound);
    }

    private static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new FrostlineOverworldEffects());
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FrostlineWeather.SNOW_DRIFT.get(), SnowDriftParticle.Provider::new);
    }

    /** Frostline weather applies to this level (overworld-style effects, not excluded, enabled). */
    static boolean active(ClientLevel level) {
        return level != null
                && WeatherConfig.CLIENT.enabled.get()
                && level.effects() instanceof FrostlineOverworldEffects
                && !WeatherConfig.CLIENT.excludedDimensions.get().contains(level.dimension().location().toString());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.player == null || !active(level)) {
            lastLevel = null;
            stormOnly = 0;
            COLUMNS.invalidate();
            if (wind != null) {
                wind.setTarget(0, 1);
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        BlockPos eye = camera.isInitialized() ? camera.getBlockPosition() : mc.player.blockPosition();
        boolean reset = level != lastLevel;
        lastLevel = level;
        tickCount++;
        COLUMNS.update(level, eye, tickCount);

        WeatherConfig.Client cfg = WeatherConfig.CLIENT;
        float floor = level.getBiome(eye).is(FrostlineWeather.WINDSWEPT) ? cfg.windsweptIntensity.get().floatValue() : 0;
        float stormTarget = Math.max(level.getRainLevel(1), floor);
        float snowHere = COLUMNS.snowAt(eye.getX(), eye.getZ()) ? 1 : 0;
        boolean inFluid = camera.isInitialized() && camera.getFluidInCamera() != FogType.NONE;
        float exposureTarget = inFluid ? 0 : Mth.clamp((level.getBrightness(LightLayer.SKY, eye) - 6) / 9f, 0, 1);

        stormO = storm;
        localO = local;
        float fadeStep = 1f / (cfg.stormFadeSeconds.get() * 20f);
        float stormOnlyTarget = level.getRainLevel(1) * snowHere;
        if (reset) {
            storm = stormTarget;
            local = stormTarget * snowHere;
            exposure = exposureTarget;
            stormOnly = stormOnlyTarget;
            stormO = storm;
            localO = local;
        } else {
            storm = approach(storm, stormTarget, fadeStep);
            local = approach(local, storm * snowHere, 0.02f);
            exposure = approach(exposure, exposureTarget, 0.05f);
            stormOnly = approach(stormOnly, stormOnlyTarget, Math.max(fadeStep, 0.02f * (1 - snowHere)));
        }
        gust = (float) Wind.gust(level.getGameTime());

        AmbientSnow.tick(mc, level, eye, storm, windX(level, 1), windZ(level, 1));
        tickWind(mc, level, snowHere, inFluid);
    }

    private static void tickWind(Minecraft mc, ClientLevel level, float snowHere, boolean inFluid) {
        WeatherConfig.Client cfg = WeatherConfig.CLIENT;
        float shelter = Mth.lerp(exposure, cfg.shelteredVolume.get().floatValue(), 1f);
        float volume = (cfg.stormWindVolume.get().floatValue() * local
                + cfg.calmWindVolume.get().floatValue() * snowHere * (1 - local)) * shelter;
        volume *= Mth.clamp(0.7f + 0.3f * gust, 0.5f, 1.2f);
        float pitch = inFluid ? 0.5f : Mth.clamp(0.85f + 0.12f * (gust - 1) + 0.1f * local, 0.7f, 1.1f);
        if (inFluid) {
            volume *= 0.35f;
        }
        if (wind == null || wind.isStopped() || !mc.getSoundManager().isActive(wind)) {
            if (volume < 0.01f) {
                return;
            }
            wind = new WindSound();
            mc.getSoundManager().play(wind);
        }
        wind.setTarget(volume, pitch);
    }

    private static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!WeatherConfig.CLIENT.fog.get() || event.getType() != FogType.NONE) {
            return;
        }
        float f = fogFactor(event.getPartialTick());
        if (f <= 0.001f) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        float stormFar = Math.min(far, WeatherConfig.CLIENT.stormFogDistance.get());
        event.setFarPlaneDistance(Mth.lerp(f, far, stormFar));
        event.setNearPlaneDistance(Mth.lerp(f, near, Math.min(near, stormFar * 0.05f)));
        event.setCanceled(true);
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!WeatherConfig.CLIENT.fog.get() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float f = fogFactor(event.getPartialTick()) * 0.85f;
        ClientLevel level = Minecraft.getInstance().level;
        if (f <= 0.001f || level == null) {
            return;
        }
        float daylight = Mth.clamp((level.getSkyDarken((float) event.getPartialTick()) - 0.2f) / 0.8f, 0, 1);
        int day = WeatherConfig.parseColor(WeatherConfig.CLIENT.fogColorDay.get(), 0xC3CBD3);
        int night = WeatherConfig.parseColor(WeatherConfig.CLIENT.fogColorNight.get(), 0x1C2229);
        event.setRed(Mth.lerp(f, event.getRed(), mix(night >> 16, day >> 16, daylight)));
        event.setGreen(Mth.lerp(f, event.getGreen(), mix(night >> 8, day >> 8, daylight)));
        event.setBlue(Mth.lerp(f, event.getBlue(), mix(night, day, daylight)));
    }

    private static float fogFactor(double partialTick) {
        if (lastLevel == null || lastLevel != Minecraft.getInstance().level) {
            return 0;
        }
        float f = Mth.lerp((float) partialTick, localO, local) * exposure;
        return 1 - (1 - f) * (1 - f);
    }

    private static float mix(int a, int b, float t) {
        return Mth.lerp(t, (a & 0xFF) / 255f, (b & 0xFF) / 255f);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    /** Storm intensity for rendering, interpolated. */
    static float stormAt(float partialTick) {
        return Mth.lerp(partialTick, stormO, storm);
    }

    /** Wind velocity in blocks per tick: a light breeze when calm, strong in a storm. */
    static double windX(ClientLevel level, float partialTick) {
        return Math.cos(Wind.direction(level.getGameTime() + partialTick)) * windSpeed(partialTick);
    }

    static double windZ(ClientLevel level, float partialTick) {
        return Math.sin(Wind.direction(level.getGameTime() + partialTick)) * windSpeed(partialTick);
    }

    private static double windSpeed(float partialTick) {
        return (0.03 + 0.37 * stormAt(partialTick)) * gust;
    }

    static float stormOnly() {
        return stormOnly;
    }

    static float gust() {
        return gust;
    }
}
