package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        event.registerEntityRenderer(ModEntities.CORPSE.get(), CorpseRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID.get(), VoidRenderer::new);
        event.registerEntityRenderer(ModEntities.GLITCH.get(), GlitchRenderer::new);
        event.registerEntityRenderer(ModEntities.REAPER.get(), ReaperRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.mimicer.registry.ModBlockEntities.WARDROBE.get(), WardrobeRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(WardrobeRenderer.DOOR_W);
        event.register(WardrobeRenderer.DOOR_E);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MimicModel.LAYER, MimicModel::createBodyLayer);
        event.registerLayerDefinition(VoidModel.LAYER, VoidModel::createBodyLayer);
    }
}
