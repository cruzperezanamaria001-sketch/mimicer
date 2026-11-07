package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.MimicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

public class MimicRenderer extends MobRenderer<MimicEntity, MimicModel> {
    /** De día usa la textura exacta del aldeano de vainilla. */
    private static final ResourceLocation DAY = new ResourceLocation("textures/entity/villager/villager.png");
    private static final ResourceLocation NIGHT = new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/mimico.png");
    private static final ResourceLocation EYES = new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/mimico_ojos.png");

    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context, new MimicModel(context.bakeLayer(MimicModel.LAYER)), 0.5F);
        this.addLayer(new ClothesLayer(this));
        this.addLayer(new EyesLayer(this));
    }

    private static final Map<String, ResourceLocation> CLOTHES_CACHE = new HashMap<>();

    private static ResourceLocation villagerTexture(String folder, String name) {
        return CLOTHES_CACHE.computeIfAbsent(folder + "/" + name,
                key -> new ResourceLocation("textures/entity/villager/" + key + ".png"));
    }

    /**
     * Ropa de aldeano de vainilla: la del bioma, la de la profesión y la insignia de nivel. Sin ella
     * el Mímico se vería con el cuerpo "desnudo" de la textura base. Solo mientras tiene aspecto de aldeano.
     */
    private static class ClothesLayer extends RenderLayer<MimicEntity, MimicModel> {
        ClothesLayer(RenderLayerParent<MimicEntity, MimicModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, MimicEntity mimic,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (mimic.isInvisible() || mimic.getTransformTicks() >= MimicEntity.TRANSFORM_TICKS / 2) {
                return;
            }
            renderColoredCutoutModel(getParentModel(), villagerTexture("type", mimic.getVillagerTypeName()),
                    poseStack, buffer, packedLight, mimic, 1.0F, 1.0F, 1.0F);
            String profession = mimic.getProfessionName();
            if (!"none".equals(profession)) {
                renderColoredCutoutModel(getParentModel(), villagerTexture("profession", profession),
                        poseStack, buffer, packedLight, mimic, 1.0F, 1.0F, 1.0F);
                if (!"nitwit".equals(profession)) {
                    renderColoredCutoutModel(getParentModel(), villagerTexture("profession_level", "stone"),
                            poseStack, buffer, packedLight, mimic, 1.0F, 1.0F, 1.0F);
                }
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MimicEntity mimic) {
        // El cambio de textura ocurre a mitad de la transformación, escondido por el temblor.
        return mimic.getTransformTicks() >= MimicEntity.TRANSFORM_TICKS / 2 ? NIGHT : DAY;
    }

    @Override
    protected void scale(MimicEntity mimic, PoseStack poseStack, float partialTick) {
        float m = mimic.getTransformProgress(partialTick);
        float s = 0.9375F * Mth.lerp(m, 1.0F, 1.15F);
        poseStack.scale(s, s, s);
    }

    /** Ojos rojos brillantes que solo aparecen en la forma monstruosa. */
    private static class EyesLayer extends RenderLayer<MimicEntity, MimicModel> {
        EyesLayer(RenderLayerParent<MimicEntity, MimicModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, MimicEntity mimic,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (mimic.getTransformProgress(partialTick) < 0.5F || mimic.isInvisible()) {
                return;
            }
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(EYES));
            getParentModel().renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
