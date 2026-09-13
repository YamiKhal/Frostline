package com.yamikhal.frostline.weather;

import com.yamikhal.Frostline;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Snowstorms and snow ambience. Server side: StormScheduler and StormCommands. Client side:
 * weather.client, loaded only on the physical client.
 */
public final class FrostlineWeather {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Frostline.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Frostline.MODID);

    public static final RegistryObject<SoundEvent> WIND = SOUND_EVENTS.register("weather.wind",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Frostline.MODID, "weather.wind")));
    public static final RegistryObject<SimpleParticleType> SNOW_DRIFT =
            PARTICLE_TYPES.register("snow_drift", () -> new SimpleParticleType(false));

    /** Biomes that are never fully calm (datapack tag). */
    public static final TagKey<Biome> WINDSWEPT =
            TagKey.create(Registries.BIOME, new ResourceLocation(Frostline.MODID, "windswept"));
    /** Blocks that snow sifts down from when there is air below. */
    public static final TagKey<Block> SIFTING_SNOW =
            TagKey.create(Registries.BLOCK, new ResourceLocation(Frostline.MODID, "sifting_snow"));

    private FrostlineWeather() {
    }

    public static void init(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
        PARTICLE_TYPES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WeatherConfig.COMMON_SPEC, "frostline-weather.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WeatherConfig.CLIENT_SPEC, "frostline-weather-client.toml");
        MinecraftForge.EVENT_BUS.addListener(StormScheduler::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(StormCommands::onRegisterCommands);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.yamikhal.frostline.weather.client.WeatherClient.init(modBus);
        }
    }
}
