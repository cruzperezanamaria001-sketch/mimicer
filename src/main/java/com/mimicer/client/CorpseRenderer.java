package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.CorpseEntity;
import com.mimicer.limb.LimbSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dibuja el cuerpo tumbado en el suelo. Jugadores y aldeanos conservan su aspecto (skin incluida)
 * y se ocultan las partes que les arrancaron. El resto de criaturas se dibujan con su propio
 * renderizador, tumbadas.
 */
public class CorpseRenderer extends EntityRenderer<CorpseEntity> {
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("textures/entity/villager/villager.png");
    private static final ResourceLocation BLOOD_POOL = new ResourceLocation(MimicerMod.MOD_ID, "textures/entity/sangre.png");

    private final PlayerModel<AbstractClientPlayer> wideModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;
    private final VillagerModel<Villager> villagerModel;
    private final Map<String, Entity> dummies = new HashMap<>();

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.wideModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.villagerModel = new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER));
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return VILLAGER_TEXTURE;
    }

    @Override
    public void render(CorpseEntity corpse, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        drawBloodPool(corpse, poseStack, buffer, light);

        float bodyYaw = Float.isNaN(corpse.getYRot()) ? 0.0F : corpse.getYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

        boolean carried = corpse.isCarried();
        if (carried) {
            poseStack.translate(0.0F, 0.2F, 0.0F);
        } else {
            poseStack.translate(0.0F, 0.17F, 0.0F);
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(0.0F, -0.9F, 0.0F);

        int limbs = corpse.getLimbs();
        if (corpse.getPlayerUuid().isPresent()) {
            renderPlayer(corpse, limbs, poseStack, buffer, light);
        } else if ("minecraft:villager".equals(corpse.getVictimType())) {
            renderVillager(corpse.getClothes(), limbs, poseStack, buffer, light);
        } else {
            renderGeneric(corpse, partialTick, poseStack, buffer, light);
        }
        poseStack.popPose();
        super.render(corpse, yaw, partialTick, poseStack, buffer, light);
    }

    private void renderPlayer(CorpseEntity corpse, int limbs, PoseStack poseStack, MultiBufferSource buffer, int light) {
        UUID id = corpse.getPlayerUuid().get();
        ResourceLocation skin;
        boolean slim;
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null
                : Minecraft.getInstance().getConnection().getPlayerInfo(id);
        if (info != null) {
            skin = info.getSkinLocation();
            slim = "slim".equals(info.getModelName());
        } else {
            skin = DefaultPlayerSkin.getDefaultSkin(id);
            slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        }
        PlayerModel<AbstractClientPlayer> model = slim ? slimModel : wideModel;
        model.young = false;
        model.setAllVisible(true);
        resetPose(model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
        // Brazos ligeramente abiertos, piernas separadas.
        model.rightArm.zRot = 0.18F;
        model.leftArm.zRot = -0.18F;
        model.rightSleeve.zRot = 0.18F;
        model.leftSleeve.zRot = -0.18F;
        model.rightLeg.zRot = 0.06F;
        model.leftLeg.zRot = -0.06F;
        model.head.yRot = 0.4F;
        model.hat.yRot = 0.4F;

        if (LimbSystem.has(limbs, LimbSystem.HEAD)) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if (LimbSystem.has(limbs, LimbSystem.LEFT_ARM)) {
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        }
        if (LimbSystem.has(limbs, LimbSystem.RIGHT_ARM)) {
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
        if (LimbSystem.has(limbs, LimbSystem.LEFT_LEG)) {
            model.leftLeg.visible = false;
            model.leftPants.visible = false;
        }
        if (LimbSystem.has(limbs, LimbSystem.RIGHT_LEG)) {
            model.rightLeg.visible = false;
            model.rightPants.visible = false;
        }
        drawModel(model, skin, poseStack, buffer, light);
    }

    private static final String[] LEVEL_BADGES = {"stone", "stone", "iron", "gold", "emerald", "diamond"};

    private static ResourceLocation villagerTexture(String folder, String name) {
        return new ResourceLocation("textures/entity/villager/" + folder + "/" + name + ".png");
    }

    private void renderVillager(String clothes, int limbs, PoseStack poseStack, MultiBufferSource buffer, int light) {
        ModelPart root = villagerModel.root();
        ModelPart head = root.getChild("head");
        // El sombrero depende de metadatos del tipo de aldeano; el cuerpo se dibuja sin él.
        if (head.hasChild("hat")) {
            head.getChild("hat").visible = false;
        }
        ModelPart arms = root.getChild("arms");
        ModelPart rightLeg = root.getChild("right_leg");
        ModelPart leftLeg = root.getChild("left_leg");
        head.visible = !LimbSystem.has(limbs, LimbSystem.HEAD);
        // El aldeano tiene los brazos en una sola pieza: desaparece si perdió alguno.
        arms.visible = !(LimbSystem.has(limbs, LimbSystem.LEFT_ARM) || LimbSystem.has(limbs, LimbSystem.RIGHT_ARM));
        rightLeg.visible = !LimbSystem.has(limbs, LimbSystem.RIGHT_LEG);
        leftLeg.visible = !LimbSystem.has(limbs, LimbSystem.LEFT_LEG);
        resetPose(head, rightLeg, leftLeg);
        head.yRot = 0.4F;
        rightLeg.zRot = 0.06F;
        leftLeg.zRot = -0.06F;
        drawModel(villagerModel, VILLAGER_TEXTURE, poseStack, buffer, light);

        // Ropa de vainilla: bioma, profesión e insignia de nivel, como en el aldeano vivo.
        String[] parts = clothes.split("\\|");
        String type = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "plains";
        drawModel(villagerModel, villagerTexture("type", type), poseStack, buffer, light);
        if (parts.length > 1 && !"none".equals(parts[1])) {
            drawModel(villagerModel, villagerTexture("profession", parts[1]), poseStack, buffer, light);
            if (!"nitwit".equals(parts[1])) {
                int level = 1;
                if (parts.length > 2) {
                    try {
                        level = Math.max(1, Math.min(5, Integer.parseInt(parts[2])));
                    } catch (NumberFormatException ignored) {
                        // se queda en nivel 1
                    }
                }
                drawModel(villagerModel, villagerTexture("profession_level", LEVEL_BADGES[level]), poseStack, buffer, light);
            }
        }
    }

    private void drawModel(net.minecraft.client.model.EntityModel<?> model, ResourceLocation texture,
                           PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderGeneric(CorpseEntity corpse, float partialTick, PoseStack poseStack,
                               MultiBufferSource buffer, int light) {
        Entity dummy = dummies.computeIfAbsent(corpse.getVictimType(), key -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(key));
            return type.create(corpse.level());
        });
        if (dummy == null) {
            return;
        }
        poseStack.pushPose();
        // Del sistema ya rotado de longitud hacia el de la entidad: se vuelve a poner el centro.
        poseStack.translate(0.0F, 0.9F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        dummy.setYRot(0.0F);
        renderer.render(dummy, 0.0F, partialTick, poseStack, buffer, light);
        poseStack.popPose();
    }

    private void drawBloodPool(CorpseEntity corpse, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (corpse.isCarried()) {
            return;
        }
        float size = 0.9F + 0.1F * Math.min(Integer.bitCount(corpse.getLimbs()), 4);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(BLOOD_POOL));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float y = 0.02F;
        vertex(vc, matrix, normal, -size, y, -size, 0.0F, 0.0F, light);
        vertex(vc, matrix, normal, -size, y, size, 0.0F, 1.0F, light);
        vertex(vc, matrix, normal, size, y, size, 1.0F, 1.0F, light);
        vertex(vc, matrix, normal, size, y, -size, 1.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Matrix3f normal, float x, float y, float z,
                               float u, float v, int light) {
        vc.vertex(matrix, x, y, z).color(255, 255, 255, 230).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void resetPose(ModelPart... parts) {
        for (ModelPart part : parts) {
            part.xRot = 0.0F;
            part.yRot = 0.0F;
            part.zRot = 0.0F;
        }
    }
}
