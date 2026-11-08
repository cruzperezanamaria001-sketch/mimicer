package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.ReaperEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class ReaperRenderer extends GeoEntityRenderer<ReaperEntity> {
    public ReaperRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(MimicerMod.MOD_ID, "reaper")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6F;
    }
}
