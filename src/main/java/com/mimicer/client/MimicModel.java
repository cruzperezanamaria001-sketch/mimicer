package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.entity.MimicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo del Mímico. Con la transformación a 0 tiene exactamente la geometría y UV del aldeano
 * de vainilla (así puede usar su textura); al avanzar la transformación se deforma: cabeza y nariz
 * crecen, el torso se encorva, los brazos se descruzan y se alargan y las piernas se doblan.
 */
public class MimicModel extends EntityModel<MimicEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MimicerMod.MOD_ID, "mimico"), "main");

    private static final float DEG = (float) Math.PI / 180.0F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart nose;
    private final ModelPart jaw;
    private final ModelPart body;
    private final ModelPart jacket;
    private final ModelPart armsCross;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MimicModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.nose = head.getChild("nose");
        this.jaw = head.getChild("jaw");
        this.body = root.getChild("body");
        this.jacket = body.getChild("jacket");
        this.armsCross = root.getChild("arms_cross");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0F, -2.0F, -7.0F, 6.0F, 2.0F, 7.0F, new CubeDeformation(0.05F)),
                PartPose.offset(0.0F, 0.0F, 3.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(0, 38)
                        .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 18.0F, 6.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        PartPose armPose = PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F);
        root.addOrReplaceChild("arms_cross",
                CubeListBuilder.create().texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F), armPose);
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F), armPose);
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                armPose);

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MimicEntity mimic, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float partial = ageInTicks - mimic.tickCount;
        float m = Mth.clamp(mimic.getTransformProgress(partial), 0.0F, 1.0F);
        boolean monster = m >= 1.0F;
        boolean transforming = m > 0.0F && m < 1.0F;
        float swing = limbSwing * 0.6662F;
        float run = Mth.clamp((limbSwingAmount - 0.5F) * 2.0F, 0.0F, 1.0F) * m;

        // --- deformaciones que dependen de la transformación
        float shake = transforming ? Mth.sin(ageInTicks * 2.6F) * 0.12F * Mth.sin(m * (float) Math.PI) : 0.0F;

        head.xScale = 1.0F + 0.2F * m;
        head.yScale = 1.0F + 0.28F * m;
        head.zScale = 1.0F + 0.1F * m;
        // La nariz crece pero sigue pegada a la cara: se compensa el desplazamiento que provoca escalar
        // alrededor de su pivote (la caja ocupa z -6..-4 e y -1..3 respecto al pivote).
        float noseX = 1.0F + 0.5F * m;
        float noseY = 1.0F + 0.7F * m;
        float noseZ = 1.0F + 0.8F * m;
        nose.xScale = noseX;
        nose.yScale = noseY;
        nose.zScale = noseZ;
        nose.setPos(0.0F, noseY - 3.0F, 4.0F * (noseZ - 1.0F));

        body.xScale = 1.0F;
        body.y = 0.0F;
        body.yScale = 1.0F + 0.15F * m;
        jacket.visible = m < 0.9F;

        // --- cabeza
        head.setPos(0.0F, 1.5F * m, -2.0F * m);
        head.yRot = netHeadYaw * DEG * (1.0F - 0.4F * m) + shake;
        head.xRot = headPitch * DEG * (1.0F - 0.3F * m) + 0.4F * m + run * 0.2F;
        head.zRot = 0.3F * m + shake * 0.8F;
        if (mimic.isLurking() && monster) {
            head.zRot += 0.35F;
            head.xRot += 0.15F;
        }

        // --- torso encorvado
        body.xRot = 0.5F * m + run * 0.25F + shake * 0.5F;
        body.zRot = shake;

        // --- piernas
        float legSwing = Mth.cos(swing) * 1.4F * limbSwingAmount * (0.5F + 0.4F * m);
        rightLeg.setPos(-2.0F, 12.0F - 3.0F * m, 0.0F);
        leftLeg.setPos(2.0F, 12.0F - 3.0F * m, 0.0F);
        rightLeg.yScale = 1.0F + 0.25F * m;
        leftLeg.yScale = 1.0F + 0.25F * m;
        rightLeg.xRot = legSwing + 0.3F * m;
        leftLeg.xRot = -legSwing + 0.3F * m;
        rightLeg.zRot = -0.08F * m;
        leftLeg.zRot = 0.08F * m;

        // --- brazos
        boolean crossed = m < 0.5F;
        armsCross.visible = crossed;
        armsCross.xScale = 1.0F - m;
        float armBaseX = Mth.lerp(m, -0.75F, 0.0F);
        float armPivotZ = Mth.lerp(m, -1.0F, 0.0F);
        rightArm.setPos(0.0F, 3.0F, armPivotZ);
        leftArm.setPos(0.0F, 3.0F, armPivotZ);
        rightArm.yScale = 1.0F + 1.1F * m;
        leftArm.yScale = 1.0F + 1.1F * m;

        float rightX = armBaseX;
        float leftX = armBaseX;
        float armSwing = Mth.cos(swing + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F * m;
        float idle = Mth.sin(ageInTicks * 0.09F) * 0.05F * m;
        rightX += armSwing * (run > 0.0F ? 1.0F + run : 1.0F) + idle;
        leftX += -armSwing * (run > 0.0F ? 1.0F + run : 1.0F) - idle;
        if (run > 0.0F) {
            // Al correr echa los brazos hacia atrás.
            rightX += 0.9F * run;
            leftX += 0.9F * run;
        }

        if (monster) {
            // Ataque: zarpazo con los dos brazos.
            if (this.attackTime > 0.0F) {
                float a = Mth.sin(Mth.sqrt(this.attackTime) * (float) Math.PI);
                rightX = -2.0F * a - 0.2F;
                leftX = -1.6F * a - 0.2F;
            }
            // Levantar / cargar / lanzar cuerpos.
            int lift = mimic.getLiftTicks();
            int thr = mimic.getThrowTicks();
            if (mimic.isCarrying()) {
                float up = lift > 0 ? 1.0F - lift / 20.0F : 1.0F;
                float armsUp = -2.9F * up;
                if (thr > 0) {
                    armsUp = -3.5F;
                }
                rightX = armsUp + Mth.sin(ageInTicks * 0.2F) * 0.04F;
                leftX = armsUp - Mth.sin(ageInTicks * 0.2F) * 0.04F;
            } else if (thr > 0) {
                float p = 1.0F - thr / 10.0F;
                rightX = Mth.lerp(p, -2.9F, -0.4F);
                leftX = rightX;
            }
        }

        rightArm.xRot = rightX + shake;
        leftArm.xRot = leftX - shake;
        armsCross.xRot = armBaseX;
        rightArm.zRot = 0.12F * m;
        leftArm.zRot = -0.12F * m;

        jaw.xRot = 0.0F;
        applyCrawlPose(mimic, partial, swing, limbSwingAmount, headPitch);
        applyScenes(mimic, ageInTicks - (float) Math.floor(ageInTicks), ageInTicks);
    }

    /** Alimentación y extracción de camas: sobre la pose a cuatro patas, cabeza, mandíbula y brazos. */
    private void applyScenes(MimicEntity mimic, float partial, float age) {
        float ft = mimic.feedTime(partial);
        if (ft >= 0.0F) {
            ScenePose.Feed f = ScenePose.feed(ft);
            head.xRot += f.down() * 0.75F - f.pull() * 0.25F + f.chew() * 0.05F;
            head.y += f.down() * 1.5F;
            head.zRot = f.pull() * Mth.sin(age * 1.9F) * 0.3F + f.chew() * 0.08F;
            jaw.xRot = f.jaw() * 0.95F + Math.max(0.0F, f.chew()) * 0.35F;
            // Brazos: se clavan en el cuerpo y tiran de él, alternando.
            float dig = f.reach() * 0.9F - f.pull() * 0.6F;
            rightArm.xRot -= dig;
            leftArm.xRot -= dig * 0.6F;
            rightArm.zRot += f.pull() * 0.25F;
            leftArm.zRot -= f.pull() * 0.25F;
            body.xRot += f.down() * 0.06F + f.chew() * 0.02F;
        }
        float et = mimic.extractTime(partial);
        if (et >= 0.0F && mimic.getExtractMode() > 0) {
            applyCloset(mimic, et, age);
        } else if (et >= 0.0F) {
            ScenePose.Extract x = ScenePose.extract(et, com.mimicer.entity.scene.SceneTiming.Kind.MIMIC);
            // Asoma la cabeza ladeada para mirar bajo la cama y barre con el brazo derecho, muy estirado.
            head.xRot += 0.55F * x.search() * (1.0F - x.grab());
            head.zRot = 0.45F * x.search() * (1.0F - x.grab() * 0.6F);
            jaw.xRot = 0.25F + 0.2F * Mth.sin(age * 0.5F);
            if (x.grab() < 0.5F) {
                rightArm.xRot -= 1.1F * x.search();
                rightArm.zRot += Mth.sin(et * 0.45F) * 0.5F * x.search();
                rightArm.yScale = Math.max(rightArm.yScale, 2.1F);
            } else {
                // Agarra y tira a tirones, echando el cuerpo hacia atrás.
                float ratchet = (float) (x.drag() * 4.0F - Math.floor(x.drag() * 4.0F));
                rightArm.xRot -= 1.1F - 0.9F * ratchet;
                rightArm.yScale = Math.max(rightArm.yScale, 2.1F);
                leftArm.xRot -= 0.5F * x.rock();
                body.xRot -= 0.25F * x.drag() + 0.05F * x.rock();
                head.zRot += 0.05F * x.rock();
                jaw.xRot = 0.6F + 0.3F * Mth.sin(age * 1.4F);
            }
        }
    }

    /**
     * A cuatro patas, pegado al suelo, para colarse por ventanas de un bloque. Las posiciones salen de rotar
     * el torso 1,2 rad alrededor del cuello: hombros y caderas quedan adelantados/atrasados en consecuencia.
     */
    private void applyCrawlPose(MimicEntity mimic, float partial, float swing, float limbSwingAmount, float headPitch) {
        float c = mimic.getCrawlProgress(partial);
        if (c <= 0.0F) {
            return;
        }
        float stride = Mth.cos(swing) * Math.min(1.0F, limbSwingAmount * 1.6F);

        body.y = 12.0F * c;
        body.xRot = Mth.lerp(c, body.xRot, 1.2F);

        head.y = Mth.lerp(c, head.y, 12.0F);
        head.z = Mth.lerp(c, head.z, -1.0F);
        head.xRot = Mth.lerp(c, head.xRot, -0.8F + headPitch * DEG * 0.3F);

        rightArm.y = Mth.lerp(c, rightArm.y, 13.1F);
        leftArm.y = Mth.lerp(c, leftArm.y, 13.1F);
        rightArm.z = Mth.lerp(c, rightArm.z, 1.8F);
        leftArm.z = Mth.lerp(c, leftArm.z, 1.8F);
        rightArm.yScale = Mth.lerp(c, rightArm.yScale, 1.8F);
        leftArm.yScale = Mth.lerp(c, leftArm.yScale, 1.8F);
        rightArm.xRot = Mth.lerp(c, rightArm.xRot, -0.3F + stride * 0.8F);
        leftArm.xRot = Mth.lerp(c, leftArm.xRot, -0.3F - stride * 0.8F);

        rightLeg.y = Mth.lerp(c, rightLeg.y, 13.4F);
        leftLeg.y = Mth.lerp(c, leftLeg.y, 13.4F);
        rightLeg.z = Mth.lerp(c, rightLeg.z, 11.2F);
        leftLeg.z = Mth.lerp(c, leftLeg.z, 11.2F);
        rightLeg.yScale = Mth.lerp(c, rightLeg.yScale, 0.64F);
        leftLeg.yScale = Mth.lerp(c, leftLeg.yScale, 0.64F);
        rightLeg.xRot = Mth.lerp(c, rightLeg.xRot, 0.3F - stride * 0.7F);
        leftLeg.xRot = Mth.lerp(c, leftLeg.xRot, 0.3F + stride * 0.7F);
    }

    /**
     * Armario del Mímico: pega la oreja a la puerta (cabeza ladeada), la abre tirando con las dos manos, y si hay
     * alguien mete el brazo largo, agarra y tira a tirones echando el cuerpo atrás; si está vacío asoma y olfatea.
     */
    private void applyCloset(MimicEntity mimic, float et, float age) {
        ScenePose.Closet c = ScenePose.closet(et, com.mimicer.entity.scene.SceneTiming.Kind.MIMIC, mimic.getExtractMode());
        body.xRot += 0.22F * c.listen() + 0.3F * c.peek() + 0.12F * c.reach() - 0.28F * c.drag();
        head.zRot = 0.55F * c.listen() * (1.0F - 0.7F * c.open()) + Mth.sin(age * 0.22F) * 0.25F * c.peek()
                + 0.05F * c.rock();
        head.xRot += 0.15F * c.listen() + 0.35F * c.peek();
        float hands = c.open() * (1.0F - c.peek()) * (1.0F - c.reach() * 0.3F);
        rightArm.xRot -= 0.35F * c.listen() + 1.15F * hands;
        leftArm.xRot -= 0.35F * c.listen() + 1.15F * hands;
        rightArm.zRot += 0.7F * c.open() * (1.0F - c.reach());
        leftArm.zRot -= 0.7F * c.open() * (1.0F - c.reach());
        if (c.reach() > 0.0F) {
            // Mete el brazo derecho larguísimo (con la izquierda de apoyo) y agarra.
            rightArm.yScale = Math.max(rightArm.yScale, 1.0F + 1.1F * c.reach() + 0.4F);
            rightArm.xRot -= 0.9F * c.reach();
            leftArm.xRot -= 0.5F * c.reach();
            rightArm.zRot = 0.1F * (1.0F - c.reach());
            leftArm.zRot = -0.1F * (1.0F - c.reach());
        }
        if (c.grab() > 0.0F) {
            float ratchet = (float) (c.drag() * 4.0F - Math.floor(c.drag() * 4.0F));
            rightArm.xRot += 0.9F * ratchet;
            leftArm.xRot += 0.6F * ratchet;
            jaw.xRot = 0.65F + 0.3F * Mth.sin(age * 1.4F);
        } else {
            jaw.xRot = 0.15F + 0.2F * c.peek() + 0.1F * Mth.sin(age * 0.5F);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

}
