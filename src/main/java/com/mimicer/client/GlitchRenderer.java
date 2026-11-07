package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.GlitchEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GlitchRenderer extends GeoEntityRenderer<GlitchEntity> {
    public GlitchRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(MimicerMod.MOD_ID, "glitch")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.5F;
    }
}
