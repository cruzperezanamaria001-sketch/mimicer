package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.block.WardrobeBlock;
import com.mimicer.block.WardrobeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Dibuja las dos puertas del armario (modelos de bloque normales) girando sobre sus bisagras según la apertura
 * interpolada. El cuerpo del armario es el modelo de bloque estático.
 */
public class WardrobeRenderer implements BlockEntityRenderer<WardrobeBlockEntity> {
    public static final ResourceLocation DOOR_W = new ResourceLocation(MimicerMod.MOD_ID, "block/wardrobe_door_w");
    public static final ResourceLocation DOOR_E = new ResourceLocation(MimicerMod.MOD_ID, "block/wardrobe_door_e");
    private static final float MAX_ANGLE = 112.0F;

    public WardrobeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WardrobeBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof WardrobeBlock)) {
            return;
        }
        float time = be.getLevel().getGameTime() + partial;
        float open = be.openAmount(time);
        Direction facing = be.getBlockState().getValue(WardrobeBlock.FACING);
        BakedModel west = Minecraft.getInstance().getModelManager().getModel(DOOR_W);
        BakedModel east = Minecraft.getInstance().getModelManager().getModel(DOOR_E);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5, 0.0, -0.5);
        // Con un pequeño bamboleo al final para que las puertas no se paren en seco.
        float wobble = open > 0.0F && open < 1.0F ? 0.0F : 0.0F;
        float angle = (open + wobble) * MAX_ANGLE;
        door(pose, consumer, west, 0.0F, 1.0F / 32.0F, angle, light, overlay);
        door(pose, consumer, east, 1.0F, 1.0F / 32.0F, -angle, light, overlay);
        pose.popPose();
    }

    private void door(PoseStack pose, VertexConsumer consumer, BakedModel model, float pivotX, float pivotZ,
                      float angle, int light, int overlay) {
        pose.pushPose();
        pose.translate(pivotX, 0.0, pivotZ);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-pivotX, 0.0, -pivotZ);
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i <= 6; i++) {
            Direction d = i == 6 ? null : Direction.from3DDataValue(i);
            for (var quad : model.getQuads(null, d, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, light, overlay);
            }
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WardrobeBlockEntity be) {
        return false;
    }
}
