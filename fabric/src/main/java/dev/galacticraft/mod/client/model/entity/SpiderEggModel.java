package dev.galacticraft.mod.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.content.entity.boss.SpiderEggEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

public class SpiderEggModel extends EntityModel<SpiderEggEntity> {
    private final ModelPart root;
    private final ModelPart egg;

    public SpiderEggModel(ModelPart root) {
        this.root = root;
        this.egg = root.getChild("egg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("egg", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 5.0F, 10.0F)
                .texOffs(0, 0).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 6.0F, 12.0F)
                .texOffs(0, 35).addBox(-4.0F, -13.0F, -4.0F, 8.0F, 4.0F, 8.0F)
                .texOffs(33, 35).addBox(-6.0F, -7.0F, -6.5F, 12.0F, 2.0F, 0.5F)
                .texOffs(33, 39).addBox(-6.0F, -7.0F, 6.0F, 12.0F, 2.0F, 0.5F)
                .texOffs(33, 43).addBox(-2.0F, -10.0F, -6.7F, 4.0F, 2.0F, 0.4F)
                .texOffs(42, 43).addBox(-2.0F, -10.0F, 6.3F, 4.0F, 2.0F, 0.4F)
                .texOffs(41, 19).addBox(-6.0F, -13.0F, -2.0F, 1.0F, 4.0F, 2.0F)
                .texOffs(41, 26).addBox(5.0F, -13.0F, -2.0F, 1.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SpiderEggEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float ready = Mth.clamp(entity.getHatchAge() / (float) SpiderEggEntity.HATCH_TIME, 0.0F, 1.0F);
        float pulse = Mth.sin(ageInTicks * (0.18F + ready * 0.32F));
        this.egg.y = 24.0F - pulse * ready * 0.5F;
        this.egg.xScale = this.egg.zScale = 1.0F + pulse * ready * 0.04F;
        this.egg.yScale = 1.0F + pulse * ready * 0.08F;
        this.egg.zRot = Mth.sin(ageInTicks * 0.2F) * ready * 0.04F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int light, int overlay, int color) {
        this.root.render(poseStack, vertices, light, overlay);
    }
}
