package com.yamikhal.frostline.client;

import com.yamikhal.Frostline;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Render layers for the mod's blocks.
 *
 * Vanilla's ice-is-translucent mapping is keyed on Blocks.ICE itself, not on the model, so
 * frostline:cracked_ice borrows vanilla's ice model and still has to say this for itself -
 * without it the block renders on the solid layer and reads as opaque white.
 *
 * This is here rather than a render_type line in a model file on purpose: the block is meant to
 * ship no model of its own.
 */
public final class BlockRenderTypes {

    private BlockRenderTypes() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(BlockRenderTypes::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                ItemBlockRenderTypes.setRenderLayer(Frostline.CRACKED_ICE.get(), RenderType.translucent()));
    }
}
