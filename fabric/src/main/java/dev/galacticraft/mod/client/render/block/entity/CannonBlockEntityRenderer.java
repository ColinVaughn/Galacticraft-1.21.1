/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.content.block.entity.machine.CannonBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class CannonBlockEntityRenderer implements BlockEntityRenderer<CannonBlockEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/model/cannon.png");
    private final ModelPart model;
    private final ModelPart yaw;
    private final ModelPart pitch;

    public CannonBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.model = context.bakeLayer(GCEntityModelLayer.CANNON);
        this.yaw = this.model.getChild("yaw");
        this.pitch = this.yaw.getChild("pitch");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition yaw = root.addOrReplaceChild("yaw", CubeListBuilder.create()
                        .texOffs(52, 36).addBox(-6, 16, -6, 12, 1, 12)
                        .texOffs(64, 0).addBox(-2, 17, -2, 4, 7, 4)
                        .texOffs(0, 52).addBox(-5, 24, -7, 10, 1, 14)
                        .texOffs(48, 63).addBox(-5, 25, -7, 10, 10, 1)
                        .texOffs(48, 63).addBox(-5, 25, 6, 10, 10, 1)
                        .texOffs(0, 67).addBox(0, 32, -6, 1, 1, 3)
                        .texOffs(0, 67).addBox(0, 32, 3, 1, 1, 3),
                PartPose.ZERO);
        yaw.addOrReplaceChild("pitch", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-14, -3, -3, 20, 10, 6)
                        .texOffs(48, 52).addBox(-13, 5, -2, 8, 7, 4)
                        .texOffs(64, 16).addBox(6, -2, -2, 1, 4, 4)
                        .texOffs(0, 32).addBox(7, -1, -1, 35, 2, 2)
                        .texOffs(64, 16).addBox(42, -2, -2, 1, 4, 4)
                        .texOffs(64, 11).addBox(43, -2, -2, 5, 1, 4)
                        .texOffs(64, 11).addBox(43, 1, -2, 5, 1, 4)
                        .texOffs(64, 16).addBox(48, -2, -2, 1, 4, 4)
                        .texOffs(64, 24).addBox(44, -1, -2, 1, 2, 4)
                        .texOffs(64, 24).addBox(46, -1, -2, 1, 2, 4),
                PartPose.offset(0, 32, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(CannonBlockEntity cannon, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay) {
        Vec3 target = cannon.getTargetPosition();
        if (cannon.getLevel() instanceof ClientLevel clientLevel) {
            Entity entity = clientLevel.getEntity(cannon.getTargetEntityId());
            if (entity != null) target = entity.getPosition(partialTick);
        }

        this.yaw.yRot = 0.0f;
        this.pitch.zRot = radians(15.0f);
        if (target != null) {
            double dx = target.x - (cannon.getBlockPos().getX() + 0.5);
            double dy = target.y - (cannon.getBlockPos().getY() + 2.0);
            double dz = target.z - (cannon.getBlockPos().getZ() + 0.5);
            this.yaw.yRot = (float) -Math.atan2(dz, dx);
            this.pitch.zRot = Mth.clamp((float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)),
                    radians(-10.0f), radians(80.0f));
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        this.model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        poseStack.popPose();
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    @Override
    public boolean shouldRenderOffScreen(CannonBlockEntity cannon) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
