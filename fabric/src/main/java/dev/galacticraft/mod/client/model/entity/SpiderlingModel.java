package dev.galacticraft.mod.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.content.entity.boss.SpiderlingEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class SpiderlingModel extends EntityModel<SpiderlingEntity> {
    private static final float DEG = Mth.PI / 180.0F;
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart abdomen;
    private final ModelPart[] leftLegs = new ModelPart[4];
    private final ModelPart[] rightLegs = new ModelPart[4];

    public SpiderlingModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.abdomen = body.getChild("abdomen");
        for (int i = 0; i < 4; i++) {
            this.leftLegs[i] = body.getChild("left_leg_" + (i + 1));
            this.rightLegs[i] = body.getChild("right_leg_" + (i + 1));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 13).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 4.0F, 5.0F)
                .texOffs(29, 13).addBox(-3.0F, -2.0F, -5.5F, 6.0F, 2.0F, 1.5F)
                .texOffs(7, 40).addBox(-2.5F, -1.5F, -5.8F, 2.0F, 1.5F, 0.4F)
                .texOffs(24, 45).addBox(0.5F, -1.5F, -5.8F, 2.0F, 1.5F, 0.4F)
                .texOffs(14, 43).addBox(-2.0F, 1.0F, -6.0F, 1.0F, 3.0F, 1.0F)
                .texOffs(44, 3).addBox(1.0F, 1.0F, -6.0F, 1.0F, 3.0F, 1.0F),
                PartPose.offset(0.0F, -1.0F, -3.0F));
        body.addOrReplaceChild("abdomen", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -2.0F, 8.0F, 5.0F, 7.0F)
                .texOffs(23, 26).addBox(-3.0F, -4.0F, -1.0F, 6.0F, 2.0F, 5.0F)
                .texOffs(44, 0).addBox(-1.0F, -4.1F, 1.0F, 2.0F, 0.4F, 2.0F)
                .texOffs(44, 8).addBox(-3.0F, -6.0F, 2.0F, 1.0F, 3.0F, 1.0F)
                .texOffs(19, 45).addBox(2.0F, -6.0F, 2.0F, 1.0F, 3.0F, 1.0F),
                PartPose.offset(0.0F, -1.0F, 3.0F));
        int[][] leftUpper = {{29, 18}, {0, 36}, {31, 0}, {31, 8}};
        int[][] leftTip = {{13, 36}, {27, 38}, {0, 40}, {42, 18}};
        int[][] rightUpper = {{29, 22}, {36, 34}, {31, 4}, {23, 34}};
        int[][] rightTip = {{20, 38}, {34, 38}, {41, 38}, {7, 43}};
        for (int i = 0; i < 4; i++) {
            body.addOrReplaceChild("left_leg_" + (i + 1), CubeListBuilder.create()
                    .texOffs(leftUpper[i][0], leftUpper[i][1]).addBox(-5.0F, -1.0F, -0.7F, 5.0F, 2.0F, 1.4F)
                    .texOffs(leftTip[i][0], leftTip[i][1]).addBox(-6.0F, 0.0F, -0.5F, 2.0F, 5.0F, 1.0F),
                    PartPose.offset(-3.0F, -1.0F, -5.0F + 3.0F * (i + 1)));
            body.addOrReplaceChild("right_leg_" + (i + 1), CubeListBuilder.create()
                    .texOffs(rightUpper[i][0], rightUpper[i][1]).addBox(0.0F, -1.0F, -0.7F, 5.0F, 2.0F, 1.4F)
                    .texOffs(rightTip[i][0], rightTip[i][1]).addBox(4.0F, 0.0F, -0.5F, 2.0F, 5.0F, 1.0F),
                    PartPose.offset(3.0F, -1.0F, -5.0F + 3.0F * (i + 1)));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SpiderlingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float stride = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        this.body.y = entity.isBurrowing() ? 23.0F + Mth.sin(ageInTicks * 0.5F) : 20.0F;
        this.body.xRot = entity.isBurrowing() ? 8.0F * DEG : 0.0F;
        this.head.yRot = Mth.clamp(netHeadYaw, -30.0F, 30.0F) * DEG;
        this.head.xRot = Mth.clamp(headPitch, -20.0F, 20.0F) * DEG;
        this.abdomen.xRot = Mth.sin(ageInTicks * 0.18F) * 3.0F * DEG;
        for (int i = 0; i < 4; i++) {
            float step = Mth.cos(limbSwing * 1.5F + (i % 2) * Mth.PI) * stride * 16.0F * DEG;
            this.leftLegs[i].yRot = entity.isBurrowing() ? 0.0F : step;
            this.rightLegs[i].yRot = entity.isBurrowing() ? 0.0F : -step;
            float fold = entity.isBurrowing() ? 35.0F + Mth.sin(ageInTicks * 0.5F + i) * 6.0F : 8.0F;
            this.leftLegs[i].zRot = -fold * DEG;
            this.rightLegs[i].zRot = fold * DEG;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int light, int overlay, int color) {
        this.root.render(poseStack, vertices, light, overlay);
    }
}
