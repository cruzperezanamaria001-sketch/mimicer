package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.VoidEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo propio de The Void (no es el del Enderman): encorvado, cuello largo, cráneo alargado con mandíbula
 * abierta, brazos de dos tramos que casi tocan el suelo con garras y piernas de rodilla invertida.
 */
public class VoidModel extends EntityModel<VoidEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MimicerMod.MOD_ID, "the_void"), "main");

    private final ModelPart root;
    private final ModelPart body, head, jaw, armL, armR, foreL, foreR, legL, legR, shinL, shinR;

    public VoidModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armL = body.getChild("arm_l");
        this.armR = body.getChild("arm_r");
        this.foreL = armL.getChild("fore_l");
        this.foreR = armR.getChild("fore_r");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.shinL = legL.getChild("shin_l");
        this.shinR = legR.getChild("shin_r");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 23).addBox(-3.0F, -14.0F, -1.5F, 6.0F, 14.0F, 3.0F),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(22, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F)
                        .texOffs(0, 0).addBox(-2.5F, -14.0F, -3.0F, 5.0F, 9.0F, 5.0F),
                PartPose.offset(0.0F, -14.0F, 0.0F));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, 0.0F, -5.0F, 4.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, -6.0F, 2.0F));

        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            float sx = left ? 4.5F : -4.5F;
            String s = left ? "l" : "r";
            PartDefinition arm = body.addOrReplaceChild("arm_" + s,
                    CubeListBuilder.create().texOffs(20, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                    PartPose.offset(sx, -13.0F, 0.0F));
            arm.addOrReplaceChild("fore_" + s,
                    CubeListBuilder.create().texOffs(30, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F)
                            .texOffs(40, 14).addBox(-1.5F, 14.0F, -1.0F, 1.0F, 5.0F, 1.0F)
                            .texOffs(40, 14).addBox(-0.5F, 14.0F, -1.0F, 1.0F, 6.0F, 1.0F)
                            .texOffs(40, 14).addBox(0.5F, 14.0F, -1.0F, 1.0F, 5.0F, 1.0F),
                    PartPose.offset(0.0F, 12.0F, 0.0F));

            float lx = left ? 2.5F : -2.5F;
            PartDefinition leg = root.addOrReplaceChild("leg_" + s,
                    CubeListBuilder.create().texOffs(46, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                    PartPose.offset(lx, 6.0F, 0.0F));
            leg.addOrReplaceChild("shin_" + s,
                    CubeListBuilder.create().texOffs(46, 12).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                    PartPose.offset(0.0F, 9.0F, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(VoidEntity e, float limbSwing, float amt, float age, float netHeadYaw, float headPitch) {
        float sw = limbSwing * 0.45F;
        boolean chase = e.isAggressive();
        boolean grab = e.isGrabbing();

        // Postura base: encorvado y balanceándose muy despacio.
        float lean = grab ? 0.15F : chase ? 0.55F : 0.3F;
        body.xRot = lean + Mth.sin(age * 0.04F) * 0.03F;
        body.zRot = Mth.sin(age * 0.03F) * 0.04F;
        body.yRot = chase ? Mth.sin(sw) * 0.12F * amt : 0.0F;

        // Cabeza: sigue la mirada, con tics nerviosos y ladeada.
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.9F;
        head.xRot = headPitch * Mth.DEG_TO_RAD - lean * 0.6F + 0.35F;
        head.zRot = 0.25F + Mth.sin(age * 0.07F) * 0.08F;
        if (chase) {
            head.zRot += Mth.sin(age * 2.7F) * 0.06F;
            head.xRot += Mth.sin(age * 3.1F) * 0.05F;
        }
        float open = grab ? 0.55F : chase ? 0.95F + Mth.sin(age * 1.6F) * 0.2F : 0.18F + Mth.sin(age * 0.1F) * 0.05F;
        jaw.xRot = open;

        // Brazos
        if (grab) {
            armL.xRot = -2.3F;
            armR.xRot = -2.3F;
            armL.zRot = -0.25F;
            armR.zRot = 0.25F;
            foreL.xRot = -0.6F + Mth.sin(age * 0.8F) * 0.1F;
            foreR.xRot = -0.6F + Mth.sin(age * 0.8F + 1.0F) * 0.1F;
        } else if (chase) {
            armL.xRot = -1.35F + Mth.sin(sw) * 0.25F * amt;
            armR.xRot = -1.35F - Mth.sin(sw) * 0.25F * amt;
            armL.zRot = -0.35F;
            armR.zRot = 0.35F;
            foreL.xRot = -0.3F + Mth.sin(age * 0.9F) * 0.25F;
            foreR.xRot = -0.3F + Mth.sin(age * 0.9F + 2.0F) * 0.25F;
        } else {
            armL.xRot = 0.1F + Mth.sin(age * 0.05F) * 0.06F - Mth.cos(sw) * 0.35F * amt;
            armR.xRot = 0.1F + Mth.sin(age * 0.05F + 1.5F) * 0.06F + Mth.cos(sw) * 0.35F * amt;
            armL.zRot = -0.1F - Mth.cos(age * 0.07F) * 0.03F;
            armR.zRot = 0.1F + Mth.cos(age * 0.07F + 1.0F) * 0.03F;
            foreL.xRot = -0.15F;
            foreR.xRot = -0.15F;
        }

        boolean duck = e.isDucking();
        if (duck) {
            body.xRot += 0.6F;
            head.xRot -= 0.35F;
            if (!grab) {
                armL.xRot += 0.5F;
                armR.xRot += 0.5F;
            }
        }
        float drop = duck ? 6.0F : 0.0F;
        body.y = 6.0F + drop;
        legL.y = 6.0F + drop;
        legR.y = 6.0F + drop;

        // Piernas: zancadas largas con rodilla hacia atrás.
        float stride = chase ? 1.3F : 0.9F;
        legL.xRot = Mth.cos(sw) * stride * amt - 0.1F;
        legR.xRot = Mth.cos(sw + Mth.PI) * stride * amt - 0.1F;
        shinL.xRot = Math.max(0.0F, Mth.sin(sw)) * 1.2F * amt + 0.2F;
        shinR.xRot = Math.max(0.0F, Mth.sin(sw + Mth.PI)) * 1.2F * amt + 0.2F;
        if (duck) {
            legL.xRot = -0.9F + legL.xRot * 0.4F;
            legR.xRot = -0.9F + legR.xRot * 0.4F;
            shinL.xRot = 1.7F;
            shinR.xRot = 1.7F;
        }
        legL.zRot = -0.04F;
        legR.zRot = 0.04F;
        applyScenes(e, age - (float) Math.floor(age), age);
    }

    /** Alimentación (inclinado, mandíbula desencajada) y extracción de camas (brazo larguísimo y arrastre). */
    private void applyScenes(VoidEntity e, float partial, float age) {
        float ft = e.feedTime(partial);
        if (ft >= 0.0F) {
            ScenePose.Feed f = ScenePose.feed(ft);
            body.xRot += f.down() * 0.55F;
            head.xRot += f.down() * 0.7F - f.pull() * 0.3F;
            head.zRot += f.pull() * Mth.sin(age * 2.1F) * 0.25F + f.chew() * 0.1F;
            jaw.xRot = 0.3F + f.jaw() * 1.0F + Math.max(0.0F, f.chew()) * 0.3F;
            float dig = f.reach() * 1.0F - f.pull() * 0.8F;
            armL.xRot = -1.2F - dig;
            armR.xRot = -1.2F - dig * 0.8F;
            foreL.xRot = -0.4F + f.pull() * 0.5F + f.chew() * 0.1F;
            foreR.xRot = -0.4F + f.pull() * 0.4F - f.chew() * 0.1F;
        }
        float et = e.extractTime(partial);
        if (et >= 0.0F) {
            ScenePose.Extract x = ScenePose.extract(et, com.mimicer.entity.scene.SceneTiming.Kind.VOID);
            head.xRot += 0.5F * x.search();
            head.zRot += 0.3F * x.search();
            jaw.xRot = 0.5F + 0.25F * Mth.sin(age * 0.2F);
            if (x.grab() < 0.5F) {
                // Barrido lento y largo bajo la cama.
                armR.xRot = -1.7F * x.search() - 0.2F;
                armR.zRot = 0.25F + Mth.sin(et * 0.12F) * 0.55F * x.search();
                foreR.xRot = -0.1F - 0.4F * Mth.sin(et * 0.2F) * x.search();
                armL.xRot = -0.4F * x.search();
            } else {
                // Cierra la mano y la saca arrastrando en un solo movimiento largo.
                armR.xRot = -1.7F + 1.3F * x.drag();
                armR.zRot = 0.25F;
                foreR.xRot = -0.9F - 0.2F * Mth.sin(age * 0.8F);
                armL.xRot = -1.0F + 0.5F * x.drag();
                body.xRot -= 0.35F * x.drag();
                head.zRot += 0.06F * x.rock();
                jaw.xRot = 1.0F + 0.2F * Mth.sin(age * 1.2F);
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay,
                               float r, float g, float b, float a) {
        root.render(pose, vc, light, overlay, r, g, b, a);
    }
}
