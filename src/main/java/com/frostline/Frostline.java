package com.frostline;

import com.frostline.worldgen.ProgressionDensityFunction;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Frostline is a datapack wearing a mod as a hat.
 *
 * The ONLY thing registered here is a density function that can read X/Z.
 * Vanilla density functions see Y, noise and splines but never horizontal
 * position, so "region N begins M blocks from spawn" is impossible to express
 * in a pure datapack. That single gap is what this class fills.
 *
 * Everything else - terrain shape, surfaces, biomes, snow depth, decoration -
 * is JSON under src/main/resources/data/, emitted by gen3.py. If you find
 * yourself wanting to add Java here, check first whether a density function,
 * a surface rule, or a placed feature with a block_predicate_filter can say it.
 */
@Mod(Frostline.MODID)
public class Frostline {

    public static final String MODID = "frostline";

    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);

    public static final RegistryObject<Codec<? extends DensityFunction>> PROGRESSION =
            DENSITY_FUNCTIONS.register("progression", () -> ProgressionDensityFunction.CODEC.codec());

    public Frostline() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        DENSITY_FUNCTIONS.register(bus);
    }
}
