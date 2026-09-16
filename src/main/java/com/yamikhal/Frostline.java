package com.yamikhal;

import com.yamikhal.frostline.FrozenLakeConfiguration;
import com.yamikhal.frostline.FrozenLakeFeature;
import com.yamikhal.frostline.LakeFieldDensityFunction;
import com.yamikhal.frostline.PondConfiguration;
import com.yamikhal.frostline.PondFeature;
import com.yamikhal.frostline.ProgressionDensityFunction;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Java shim for the Frostline worldgen datapack.
 *
 * Everything registered here fills a gap datapacks cannot:
 *
 *   frostline:progression  a density function that can read X/Z. Vanilla density
 *                          functions see Y, noise and splines but never horizontal
 *                          position, so "region N begins M blocks from spawn" is
 *                          impossible to express in a pure datapack.
 *
 *   frostline:pond         a feature that picks one level water plane and carves the
 *                          terrain around it. minecraft:lake is fixed at 4 deep and
 *                          fails on slopes; vegetation patches step with the terrain
 *                          and leak. Every pond parameter is still datapack JSON.
 *
 *   (rivers are NOT here. They are terrain cut below sea_level by the noise router, and
 *    the engine floods them: the vanilla mechanism. Four Java versions of a stream feature
 *    were deleted when the world's vertical layout was re-based onto sea level 63.)
 *
 *   frostline:lake_field   a density function placing sparse lake sites by X/Z, fed to
 *   frostline:frozen_lake  the router's continents slot so a biome lands exactly on
 *                          each site, and the feature that carves that lake across
 *                          chunks at one agreed water level.
 *
 * weather: scheduled snowstorms on top of vanilla rain, and client-side snowfall, fog,
 * wind and snow particles in place of the biomes' old ambient particles. Datapacks
 * cannot schedule weather or draw it.
 *
 * Railways are not here. Everything rail related (the corridor pass, Railways Untold
 * compat, the worldgen railway) lives in the separate FrostLineRailways mod
 * (modid frostline_railways), which depends on this one.
 *
 * All worldgen data lives in the separate datapack. If you find yourself
 * wanting to add Java here, check first whether a density function, a surface
 * rule, or a placed feature with a block_predicate_filter can say it.
 */
@Mod(Frostline.MODID)
public class Frostline {

    public static final String MODID = "frostline";

    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);

    public static final RegistryObject<Codec<? extends DensityFunction>> PROGRESSION =
            DENSITY_FUNCTIONS.register("progression", () -> ProgressionDensityFunction.CODEC.codec());

    public static final RegistryObject<Codec<? extends DensityFunction>> LAKE_FIELD =
            DENSITY_FUNCTIONS.register("lake_field", () -> LakeFieldDensityFunction.CODEC.codec());

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MODID);

    public static final RegistryObject<PondFeature> POND =
            FEATURES.register("pond", () -> new PondFeature(PondConfiguration.CODEC));

    public static final RegistryObject<FrozenLakeFeature> FROZEN_LAKE =
            FEATURES.register("frozen_lake", () -> new FrozenLakeFeature(FrozenLakeConfiguration.CODEC));

    public Frostline() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        DENSITY_FUNCTIONS.register(bus);
        FEATURES.register(bus);
        com.yamikhal.frostline.weather.FrostlineWeather.init(bus);
    }
}
