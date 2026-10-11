/*
 * Copyright (c) 2019-2026 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.content.entity.boss.SpiderBoss;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class SpiderQueenModel extends EntityModel<SpiderBoss> {
    private static final float DEG = Mth.PI / 180.0F;
    private static final float[] LEG_YAW = {32.0F, 12.0F, -12.0F, -32.0F};
    private static final float[] SHIN_PITCH = {18.0F, 7.0F, -7.0F, -18.0F};

    private final ModelPart root;
    private final ModelPart thorax;
    private final ModelPart head;
    private final ModelPart abdomen;
    private final ModelPart leftMandible;
    private final ModelPart rightMandible;
    private final ModelPart[] leftLegs = new ModelPart[4];
    private final ModelPart[] rightLegs = new ModelPart[4];
    private final ModelPart[] leftShins = new ModelPart[4];
    private final ModelPart[] rightShins = new ModelPart[4];

    public SpiderQueenModel(ModelPart root) {
        this.root = root;
        this.thorax = root.getChild("thorax");
        this.head = thorax.getChild("head");
        this.abdomen = thorax.getChild("abdomen");
        this.leftMandible = head.getChild("left_mandible");
        this.rightMandible = head.getChild("right_mandible");
        for (int i = 0; i < 4; i++) {
            this.leftLegs[i] = thorax.getChild("left_leg_" + (i + 1));
            this.rightLegs[i] = thorax.getChild("right_leg_" + (i + 1));
            this.leftShins[i] = leftLegs[i].getChild("left_shin_" + (i + 1));
            this.rightShins[i] = rightLegs[i].getChild("right_shin_" + (i + 1));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootPart = mesh.getRoot();
        PartDefinition thorax = rootPart.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(47, 0).addBox(-5.000F, -4.000F, -5.000F, 10.000F, 8.000F, 10.000F).texOffs(0, 20).addBox(-6.000F, -6.000F, -6.000F, 12.000F, 4.000F, 12.000F).texOffs(49, 45).addBox(-3.000F, 3.000F, -4.000F, 6.000F, 2.000F, 8.000F).texOffs(56, 61).addBox(-4.000F, -5.000F, -8.000F, 8.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(0.000F, 11.000F, 0.000F, 0.000F, 0.000F, 0.000F));
        PartDefinition head = thorax.addOrReplaceChild("head", CubeListBuilder.create().texOffs(49, 19).addBox(-4.000F, -3.000F, -7.000F, 8.000F, 7.000F, 6.000F).texOffs(0, 52).addBox(-5.000F, -4.000F, -8.000F, 10.000F, 3.000F, 5.000F).texOffs(0, 89).addBox(-3.000F, -1.000F, -8.000F, 6.000F, 4.000F, 1.000F).texOffs(89, 16).addBox(-1.000F, -7.000F, -6.000F, 2.000F, 3.000F, 4.000F).texOffs(101, 98).addBox(-5.000F, -6.000F, -5.000F, 2.000F, 3.000F, 3.000F).texOffs(0, 102).addBox(3.000F, -6.000F, -5.000F, 2.000F, 3.000F, 3.000F).texOffs(42, 52).addBox(-4.000F, -1.000F, -8.500F, 2.000F, 2.000F, 1.000F).texOffs(78, 52).addBox(2.000F, -1.000F, -8.500F, 2.000F, 2.000F, 1.000F).texOffs(22, 71).addBox(-2.000F, 0.000F, -8.600F, 1.000F, 2.000F, 1.000F).texOffs(22, 75).addBox(1.000F, 0.000F, -8.600F, 1.000F, 2.000F, 1.000F).texOffs(29, 105).addBox(-5.000F, -11.000F, -5.000F, 1.000F, 5.000F, 2.000F).texOffs(36, 105).addBox(4.000F, -11.000F, -5.000F, 1.000F, 5.000F, 2.000F).texOffs(103, 0).addBox(-1.000F, -13.000F, -4.000F, 2.000F, 6.000F, 2.000F).texOffs(89, 58).addBox(-5.000F, -3.000F, -7.800F, 2.000F, 1.000F, 0.800F).texOffs(103, 88).addBox(3.000F, -3.000F, -7.800F, 2.000F, 1.000F, 0.800F).texOffs(103, 80).addBox(-5.000F, -1.000F, -8.000F, 2.000F, 5.000F, 2.000F).texOffs(104, 18).addBox(3.000F, -1.000F, -8.000F, 2.000F, 5.000F, 2.000F), PartPose.offsetAndRotation(0.000F, -1.000F, -5.000F, 0.000F, 0.000F, 0.000F));
        PartDefinition left_mandible = head.addOrReplaceChild("left_mandible", CubeListBuilder.create().texOffs(99, 43).addBox(-1.000F, -1.000F, -3.000F, 2.000F, 4.000F, 3.000F).texOffs(104, 26).addBox(-0.500F, 2.000F, -5.000F, 1.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(-2.000F, 3.000F, -6.000F, 0.000F, 0.000F, 0.000F));
        PartDefinition right_mandible = head.addOrReplaceChild("right_mandible", CubeListBuilder.create().texOffs(52, 100).addBox(-1.000F, -1.000F, -3.000F, 2.000F, 4.000F, 3.000F).texOffs(104, 51).addBox(-0.500F, 2.000F, -5.000F, 1.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(2.000F, 3.000F, -6.000F, 0.000F, 0.000F, 0.000F));
        PartDefinition abdomen = thorax.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-6.000F, -3.000F, -1.000F, 12.000F, 8.000F, 11.000F).texOffs(0, 37).addBox(-7.000F, -5.000F, 0.000F, 14.000F, 4.000F, 10.000F).texOffs(49, 33).addBox(-5.000F, -3.000F, 9.000F, 10.000F, 7.000F, 4.000F).texOffs(27, 61).addBox(-6.000F, -6.000F, 1.000F, 12.000F, 2.000F, 2.000F).texOffs(31, 56).addBox(-7.000F, -6.000F, 5.000F, 14.000F, 2.000F, 2.000F).texOffs(64, 56).addBox(-5.000F, -5.000F, 10.000F, 10.000F, 2.000F, 2.000F).texOffs(100, 58).addBox(-7.500F, -1.000F, 2.000F, 1.000F, 3.000F, 4.000F).texOffs(63, 100).addBox(6.500F, -1.000F, 2.000F, 1.000F, 3.000F, 4.000F).texOffs(74, 104).addBox(-2.000F, 3.000F, 12.000F, 1.000F, 3.000F, 3.000F).texOffs(20, 105).addBox(1.000F, 3.000F, 12.000F, 1.000F, 3.000F, 3.000F).texOffs(0, 61).addBox(-4.000F, -6.000F, 9.000F, 8.000F, 4.000F, 5.000F).texOffs(31, 52).addBox(-2.000F, -5.000F, 14.000F, 4.000F, 2.000F, 1.000F).texOffs(103, 9).addBox(-7.000F, -10.000F, 5.000F, 2.000F, 6.000F, 2.000F).texOffs(11, 103).addBox(5.000F, -10.000F, 5.000F, 2.000F, 6.000F, 2.000F).texOffs(43, 105).addBox(-5.000F, -8.000F, 10.000F, 1.000F, 5.000F, 2.000F).texOffs(83, 105).addBox(4.000F, -8.000F, 10.000F, 1.000F, 5.000F, 2.000F).texOffs(78, 37).addBox(-2.000F, 2.000F, 13.000F, 4.000F, 3.000F, 5.000F).texOffs(64, 86).addBox(-1.000F, 4.000F, 17.000F, 2.000F, 2.000F, 5.000F), PartPose.offsetAndRotation(0.000F, -1.000F, 5.000F, 0.000F, 0.000F, 0.000F));
        PartDefinition left_leg_1 = thorax.addOrReplaceChild("left_leg_1", CubeListBuilder.create().texOffs(78, 46).addBox(-8.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(94, 91).addBox(-9.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F).texOffs(88, 0).addBox(-10.000F, -7.000F, -2.000F, 4.000F, 4.000F, 3.000F).texOffs(103, 66).addBox(-11.000F, -10.000F, -2.000F, 3.000F, 4.000F, 2.000F), PartPose.offsetAndRotation(-5.000F, -1.000F, -4.000F, 0.000F, -0.559F, 0.000F));
        PartDefinition left_shin_1 = left_leg_1.addOrReplaceChild("left_shin_1", CubeListBuilder.create().texOffs(27, 66).addBox(-2.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(89, 24).addBox(-3.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(-7.000F, -2.000F, 0.000F, -0.314F, 0.000F, 0.209F));
        PartDefinition left_leg_2 = thorax.addOrReplaceChild("left_leg_2", CubeListBuilder.create().texOffs(79, 61).addBox(-8.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(0, 95).addBox(-9.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(-5.000F, -1.000F, -1.000F, 0.000F, -0.209F, 0.000F));
        PartDefinition left_shin_2 = left_leg_2.addOrReplaceChild("left_shin_2", CubeListBuilder.create().texOffs(38, 66).addBox(-2.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(89, 30).addBox(-3.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(-7.000F, -2.000F, 0.000F, -0.122F, 0.000F, 0.209F));
        PartDefinition left_leg_3 = thorax.addOrReplaceChild("left_leg_3", CubeListBuilder.create().texOffs(82, 67).addBox(-8.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(13, 96).addBox(-9.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(-5.000F, -1.000F, 3.000F, 0.000F, 0.209F, 0.000F));
        PartDefinition left_shin_3 = left_leg_3.addOrReplaceChild("left_shin_3", CubeListBuilder.create().texOffs(49, 68).addBox(-2.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(89, 52).addBox(-3.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(-7.000F, -2.000F, 0.000F, 0.122F, 0.000F, 0.209F));
        PartDefinition left_leg_4 = thorax.addOrReplaceChild("left_leg_4", CubeListBuilder.create().texOffs(82, 73).addBox(-8.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(97, 36).addBox(-9.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(-5.000F, -1.000F, 6.000F, 0.000F, 0.559F, 0.000F));
        PartDefinition left_shin_4 = left_leg_4.addOrReplaceChild("left_shin_4", CubeListBuilder.create().texOffs(60, 68).addBox(-2.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(15, 90).addBox(-3.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(-7.000F, -2.000F, 0.000F, 0.314F, 0.000F, 0.209F));
        PartDefinition right_leg_1 = thorax.addOrReplaceChild("right_leg_1", CubeListBuilder.create().texOffs(82, 79).addBox(0.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(75, 97).addBox(6.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F).texOffs(88, 8).addBox(6.000F, -7.000F, -2.000F, 4.000F, 4.000F, 3.000F).texOffs(103, 73).addBox(8.000F, -10.000F, -2.000F, 3.000F, 4.000F, 2.000F), PartPose.offsetAndRotation(5.000F, -1.000F, -4.000F, 0.000F, 0.559F, 0.000F));
        PartDefinition right_shin_1 = right_leg_1.addOrReplaceChild("right_shin_1", CubeListBuilder.create().texOffs(0, 71).addBox(-1.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(79, 91).addBox(-1.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(7.000F, -2.000F, 0.000F, -0.314F, 0.000F, -0.209F));
        PartDefinition right_leg_2 = thorax.addOrReplaceChild("right_leg_2", CubeListBuilder.create().texOffs(22, 84).addBox(0.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(26, 98).addBox(6.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(5.000F, -1.000F, -1.000F, 0.000F, 0.209F, 0.000F));
        PartDefinition right_shin_2 = right_leg_2.addOrReplaceChild("right_shin_2", CubeListBuilder.create().texOffs(11, 71).addBox(-1.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(30, 92).addBox(-1.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(7.000F, -2.000F, 0.000F, -0.122F, 0.000F, -0.209F));
        PartDefinition right_leg_3 = thorax.addOrReplaceChild("right_leg_3", CubeListBuilder.create().texOffs(82, 85).addBox(0.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(39, 98).addBox(6.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(5.000F, -1.000F, 3.000F, 0.000F, -0.209F, 0.000F));
        PartDefinition right_shin_3 = right_leg_3.addOrReplaceChild("right_shin_3", CubeListBuilder.create().texOffs(71, 68).addBox(-1.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(45, 92).addBox(-1.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(7.000F, -2.000F, 0.000F, 0.122F, 0.000F, -0.209F));
        PartDefinition right_leg_4 = thorax.addOrReplaceChild("right_leg_4", CubeListBuilder.create().texOffs(43, 86).addBox(0.000F, -3.000F, -1.000F, 8.000F, 3.000F, 2.000F).texOffs(88, 98).addBox(6.000F, -5.000F, -1.500F, 3.000F, 3.000F, 3.000F), PartPose.offsetAndRotation(5.000F, -1.000F, 6.000F, 0.000F, -0.559F, 0.000F));
        PartDefinition right_shin_4 = right_leg_4.addOrReplaceChild("right_shin_4", CubeListBuilder.create().texOffs(78, 19).addBox(-1.000F, 0.000F, -1.000F, 3.000F, 15.000F, 2.000F).texOffs(60, 94).addBox(-1.000F, 14.000F, -2.000F, 4.000F, 2.000F, 3.000F), PartPose.offsetAndRotation(7.000F, -2.000F, 0.000F, 0.314F, 0.000F, -0.209F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(SpiderBoss entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float stride = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float cycle = limbSwing * 1.3324F;
        float breath = Mth.sin(ageInTicks * 0.1F);
        float attack = Mth.sin(entity.getAttackAnim(0.0F) * Mth.PI);

        thorax.y = 11.0F - 0.2F * breath - 0.25F * stride * Mth.abs(Mth.sin(cycle));
        thorax.xRot = attack * 8.0F * DEG;
        head.xRot = Mth.clamp(headPitch, -25.0F, 25.0F) * DEG + attack * 12.0F * DEG - breath * 2.0F * DEG;
        head.yRot = Mth.clamp(netHeadYaw, -30.0F, 30.0F) * DEG;
        abdomen.xRot = -attack * 6.0F * DEG + breath * 2.0F * DEG;
        abdomen.yRot = breath * 2.0F * DEG;
        leftMandible.yRot = (-5.0F - attack * 20.0F) * DEG;
        rightMandible.yRot = (5.0F + attack * 20.0F) * DEG;

        for (int i = 0; i < 4; i++) {
            float phase = (i & 1) == 0 ? 0.0F : Mth.PI;
            float step = Mth.cos(cycle + phase) * stride;
            float opposite = Mth.cos(cycle + phase + Mth.PI) * stride;
            leftLegs[i].yRot = (-LEG_YAW[i] + step * 10.0F) * DEG;
            rightLegs[i].yRot = (LEG_YAW[i] + opposite * 10.0F) * DEG;
            leftLegs[i].xRot = i == 0 ? attack * 15.0F * DEG : 0.0F;
            rightLegs[i].xRot = i == 0 ? attack * 15.0F * DEG : 0.0F;
            leftShins[i].xRot = (-SHIN_PITCH[i] + Mth.sin(cycle + phase) * stride * 12.0F) * DEG;
            rightShins[i].xRot = (-SHIN_PITCH[i] + Mth.sin(cycle + phase + Mth.PI) * stride * 12.0F) * DEG;
            leftShins[i].zRot = 12.0F * DEG;
            rightShins[i].zRot = -12.0F * DEG;
            leftLegs[i].zRot = 0.0F;
            rightLegs[i].zRot = 0.0F;
        }

        int special = entity.getSpecialAttack();
        float specialTicks = entity.getSpecialAttackTicks();
        float climbBlend = entity.getClimbBlend(ageInTicks - entity.tickCount);
        if (climbBlend > 0.0F) {
            float climbCycle = ageInTicks * 0.55F;
            thorax.y -= climbBlend;
            thorax.xRot -= 18.0F * DEG * climbBlend;
            head.xRot -= 15.0F * DEG * climbBlend;
            abdomen.xRot += 10.0F * DEG * climbBlend;
            for (int i = 0; i < 4; i++) {
                float grip = Mth.sin(climbCycle + i * Mth.PI * 0.5F);
                leftLegs[i].xRot += (15.0F + grip * 18.0F) * DEG * climbBlend;
                rightLegs[i].xRot += (15.0F - grip * 18.0F) * DEG * climbBlend;
                leftLegs[i].zRot = -15.0F * DEG * climbBlend;
                rightLegs[i].zRot = 15.0F * DEG * climbBlend;
                leftShins[i].xRot -= (12.0F + grip * 12.0F) * DEG * climbBlend;
                rightShins[i].xRot -= (12.0F - grip * 12.0F) * DEG * climbBlend;
            }
        }
        if (special == SpiderBoss.POUNCE_WINDUP) {
            float crouch = Mth.clamp(specialTicks / 20.0F, 0.0F, 1.0F);
            thorax.y += 2.0F * crouch;
            head.xRot += 12.0F * DEG * crouch;
            abdomen.xRot -= 12.0F * DEG * crouch;
            leftMandible.yRot -= 20.0F * DEG * crouch;
            rightMandible.yRot += 20.0F * DEG * crouch;
            for (int i = 0; i < 4; i++) {
                leftLegs[i].zRot = 18.0F * DEG * crouch;
                rightLegs[i].zRot = -18.0F * DEG * crouch;
            }
        } else if (special == SpiderBoss.POUNCE_AIR) {
            thorax.y -= 1.5F;
            head.xRot -= 18.0F * DEG;
            abdomen.xRot += 15.0F * DEG;
            for (int i = 0; i < 4; i++) {
                leftLegs[i].xRot = -25.0F * DEG;
                rightLegs[i].xRot = -25.0F * DEG;
            }
        } else if (special == SpiderBoss.POUNCE_RECOVER) {
            thorax.y += (1.0F - specialTicks / 12.0F);
        } else if (special == SpiderBoss.VENOM_WINDUP) {
            float charge = Mth.clamp(specialTicks / 30.0F, 0.0F, 1.0F);
            abdomen.xRot += 22.0F * DEG * charge;
            head.xRot -= 12.0F * DEG * charge;
            leftMandible.yRot -= 25.0F * DEG * charge;
            rightMandible.yRot += 25.0F * DEG * charge;
            thorax.y += charge;
        } else if (special == SpiderBoss.VENOM_RECOVER) {
            float recoil = 1.0F - Mth.clamp(specialTicks / 12.0F, 0.0F, 1.0F);
            abdomen.xRot -= 18.0F * DEG * recoil;
            head.xRot += 15.0F * DEG * recoil;
        } else if (special == SpiderBoss.NEST_BUILD || special == SpiderBoss.NEST_HEAL) {
            float weave = Mth.sin(ageInTicks * (special == SpiderBoss.NEST_BUILD ? 0.7F : 0.2F));
            float crouch = special == SpiderBoss.NEST_BUILD
                    ? Mth.clamp(specialTicks / 40.0F, 0.0F, 1.0F) : 1.0F;
            thorax.y += 2.0F * crouch + weave * 0.25F;
            head.xRot += 18.0F * DEG * crouch;
            abdomen.xRot += (25.0F + weave * 12.0F) * DEG * crouch;
            abdomen.yRot += weave * 8.0F * DEG;
            leftMandible.yRot -= 12.0F * DEG * crouch;
            rightMandible.yRot += 12.0F * DEG * crouch;
            for (int i = 0; i < 4; i++) {
                float legWeave = Mth.sin(ageInTicks * 0.65F + i * Mth.PI * 0.5F);
                leftLegs[i].zRot = (15.0F + legWeave * 6.0F) * DEG * crouch;
                rightLegs[i].zRot = (-15.0F - legWeave * 6.0F) * DEG * crouch;
                leftShins[i].xRot -= 10.0F * DEG * crouch;
                rightShins[i].xRot -= 10.0F * DEG * crouch;
            }
        } else if (special == SpiderBoss.EGG_WINDUP) {
            float lay = Mth.clamp(specialTicks / 30.0F, 0.0F, 1.0F);
            float pulse = Mth.sin(ageInTicks * 0.8F) * lay;
            thorax.y += 2.0F * lay + pulse * 0.2F;
            abdomen.xRot -= (20.0F + pulse * 8.0F) * DEG * lay;
            head.xRot += 15.0F * DEG * lay;
            for (int i = 0; i < 4; i++) {
                leftLegs[i].zRot = 15.0F * DEG * lay;
                rightLegs[i].zRot = -15.0F * DEG * lay;
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int light, int overlay, int color) {
        this.root.render(poseStack, vertices, light, overlay);
    }
}
