package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.VoidEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class VoidRenderer extends MobRenderer<VoidEntity, VoidModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/the_void.png");
    private static final RenderType EYES =
            RenderType.eyes(new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/the_void_eyes.png"));

    public VoidRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidModel(context.bakeLayer(VoidModel.LAYER)), 0.6F);
        this.addLayer(new EyesLayer<VoidEntity, VoidModel>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(VoidEntity entity) {
        return TEXTURE;
    }
}
