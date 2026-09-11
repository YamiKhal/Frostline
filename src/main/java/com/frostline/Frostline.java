package com.frostline;

import com.frostline.worldgen.ProgressionDensityFunction;
import com.frostline.worldgen.SnowDriftFeature;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(Frostline.MODID)
public class Frostline {

    public static final String MODID = "frostline";

    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);

    public static final RegistryObject<Codec<? extends DensityFunction>> PROGRESSION =
            DENSITY_FUNCTIONS.register("progression", () -> ProgressionDensityFunction.CODEC.codec());

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, MODID);

    public static final RegistryObject<SnowDriftFeature> SNOW_DRIFT =
            FEATURES.register("snow_drift", SnowDriftFeature::new);

    public Frostline() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        DENSITY_FUNCTIONS.register(bus);
        FEATURES.register(bus);
    }
}
