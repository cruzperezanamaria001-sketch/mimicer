package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.VoidEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class VoidRenderer extends MobRenderer<VoidEntity, EndermanModel<VoidEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/the_void.png");
    private static final RenderType EYES =
            RenderType.eyes(new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/the_void_eyes.png"));

    public VoidRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.5F);
        this.addLayer(new EyesLayer<VoidEntity, EndermanModel<VoidEntity>>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public void render(VoidEntity entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        EndermanModel<VoidEntity> m = this.getModel();
        m.carrying = entity.isGrabbing();
        m.creepy = entity.isAggressive();
        super.render(entity, yaw, partialTicks, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidEntity entity) {
        return TEXTURE;
    }
}
