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
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.content.block.entity.machine.RadarBlockEntity;
import dev.galacticraft.mod.content.block.machine.RadarBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

@Environment(EnvType.CLIENT)
public class RadarBlockEntityRenderer implements BlockEntityRenderer<RadarBlockEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/model/radar.png");
    private final ModelPart model;
    private final ModelPart scanner;

    public RadarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.model = context.bakeLayer(GCEntityModelLayer.RADAR);
        this.scanner = this.model.getChild("scanner");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "small_base_n", 27, 82, -7, 14.59808f, -5.5f, 7, 20.59808f, -0.5f,
                0, 17.59808f, -4.5f, 30, 0, 0);
        part(root, "small_base_s", 37, 107, -7, 14.59808f, 0.5f, 7, 20.59808f, 5.5f,
                0, 17.59808f, 4.5f, -30, 0, 0);
        part(root, "small_base", 65, 52, -7, 16, -4, 7, 21, 4,
                -6, 16, -3, 0, 0, 0);
        part(root, "neck", 85, 85, -2, 21, -2, 2, 47, 2,
                0, 31, 0, 0, 0, 0);
        part(root, "neck_cross", 85, 85, -2, 21, -2, 2, 47, 2,
                0, 31, 0, 0, 45, 0);
        PartDefinition scanner = root.addOrReplaceChild("scanner", CubeListBuilder.create(), PartPose.ZERO);
        part(scanner, "neck_arm", 69, 2, -9, 43, -3, -3, 63, 3,
                -8, 43, -2, 0, 0, -50);
        part(scanner, "mount", 70, 69, 3.7f, 53.57861f, -5, 13.7f, 55.57861f, 5,
                8.33719f, 54.57861f, 0, 0, 0, -50);
        part(scanner, "dish_1", 0, 58, 3.7f, 56.57861f, -7.5f, 18.7f, 58.57861f, 7.5f,
                8.33719f, 57.57861f, -2, 0, 0, -50);
        part(scanner, "dish_2", 0, 35, -1.3f, 69.57861f, -7.5f, 13.7f, 72.57861f, 7.5f,
                3.33719f, 70.57861f, -2, 0, 0, -75);
        part(scanner, "dish_3", 0, 35, 13.43178f, 46.36874f, -7.5f, 28.43178f, 49.36874f, 7.5f,
                20.93178f, 47.36874f, 0, 0, 0, -25);
        part(scanner, "dish_4", 0, 35, 5.3f, 56, 5.31262f, 20.3f, 59, 20.31262f,
                12.82486f, 56.92888f, 12.81262f, -25, 0, -50);
        part(scanner, "dish_5", 0, 35, 5.21963f, 56.41527f, -20.29799f, 20.21963f, 59.41527f, -5.29799f,
                12.71963f, 57.91527f, -12.79799f, 25, 0, -50);
        part(scanner, "dish_angle_1", 71, 32, 14.3f, 51, -17.34242f, 24.3f, 52, -1.34242f,
                19.1632f, 51.35247f, -9.34242f, 28.16232f, 37.95241f, -11.2093f);
        part(scanner, "dish_angle_2", 71, 32, 14.1632f, 50.85247f, 1.65758f, 24.1632f, 51.85247f, 17.65758f,
                19.1632f, 51.35247f, 9.65758f, -26.05116f, -38.74682f, -15.47114f);
        part(scanner, "dish_angle_3", 71, 32, 2.1632f, 65.85247f, -17.34242f, 12.1632f, 66.85247f, -1.34242f,
                7.1632f, 66.35247f, -9.34242f, 24.94618f, -40.7774f, -86.25279f);
        part(scanner, "dish_angle_4", 71, 32, 2.1632f, 65.85247f, 1.65758f, 12.1632f, 66.85247f, 17.65758f,
                7.3f, 66.35247f, 9.65758f, -26.99297f, 42.30176f, -89.53775f);
        part(scanner, "arm_1", 8, 84, 7.07373f, 65.8f, -10.36887f, 8.07373f, 83.8f, -9.36887f,
                7.57373f, 66.17361f, -9.86887f, 51.50428f, 45.54665f, -51.02347f);
        part(scanner, "arm_2", 8, 84, 20.07373f, 50.67361f, -9.36887f, 21.07373f, 68.67361f, -8.36887f,
                20.57373f, 51.17361f, -8.86887f, 35.34672f, 22.13248f, 8.06314f);
        part(scanner, "arm_3", 8, 84, 20.07373f, 50.67361f, 8.63113f, 21.07373f, 68.67361f, 9.63113f,
                20.57373f, 51.17361f, 9.13113f, -35.5f, -22, 8.06314f);
        part(scanner, "arm_4", 8, 84, 7.07373f, 65.67361f, 8.63113f, 8.07373f, 83.67361f, 9.63113f,
                7.57373f, 66.17361f, 9.13113f, -55, -50, -46.02347f);
        part(scanner, "reflector", 62, 37, 20.58273f, 65.04905f, -2, 24.58273f, 66.04905f, 2,
                22.58273f, 65.54905f, 0, 0, 0, -50);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void part(PartDefinition root, String name, int u, int v,
                             float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                             float originX, float originY, float originZ,
                             float rotationX, float rotationY, float rotationZ) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                        .addBox(fromX - originX, fromY - originY, fromZ - originZ,
                                toX - fromX, toY - fromY, toZ - fromZ),
                PartPose.offsetAndRotation(originX, originY, originZ,
                        radians(rotationX), radians(rotationY), radians(rotationZ)));
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    @Override
    public void render(RadarBlockEntity radar, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-radar.getBlockState().getValue(RadarBlock.FACING).toYRot()));
        this.scanner.yRot = radar.getState().isActive()
                ? radians((float) Math.sin((radar.getLevel().getGameTime() + partialTick) / 100.0) * 75.0f)
                : 0.0f;
        this.model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RadarBlockEntity radar) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
