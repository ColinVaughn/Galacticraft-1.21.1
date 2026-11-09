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

package dev.galacticraft.mod.client.render.entity.feature.spacegear;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Renders stock gear using {@link SpaceGearFit}. The input pose uses vanilla model-space blocks
 * (Y down, front toward -Z); fit dimensions are in pixels.
 */
public final class SpaceGearDrawer {
    private static final ResourceLocation MASK_TEXTURE = Constant.id(Constant.GearTexture.OXYGEN_GEAR);
    private static final ResourceLocation TANK_TEXTURE = Constant.id(Constant.GearTexture.OXYGEN_TANKS);
    private static final float PIXEL = 1.0F / 16.0F;

    /** The stock mask from {@code OxygenMaskRenderLayer}, centred on the origin so it can be scaled about its middle. */
    private static final ModelPart MASK;
    /** The stock tanks from {@code OxygenTanksRenderLayer}, one per tank size, with their corner at the origin. */
    private static final ModelPart TANKS;

    static {
        MeshDefinition mask = new MeshDefinition();
        float half = SpaceGearFit.HELMET_SIZE / 2;
        mask.getRoot().addOrReplaceChild(Constant.ModelPartName.OXYGEN_MASK, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-half, -half, -half, SpaceGearFit.HELMET_SIZE, SpaceGearFit.HELMET_SIZE, SpaceGearFit.HELMET_SIZE,
                        new CubeDeformation(-0.1F)), PartPose.ZERO);
        MASK = mask.getRoot().bake(64, 32).getChild(Constant.ModelPartName.OXYGEN_MASK);

        MeshDefinition tanks = new MeshDefinition();
        PartDefinition root = tanks.getRoot();
        tank(root, Constant.Item.SMALL_OXYGEN_TANK, 0, 0);
        tank(root, Constant.Item.MEDIUM_OXYGEN_TANK, 16, 0);
        tank(root, Constant.Item.LARGE_OXYGEN_TANK, 0, 16);
        tank(root, Constant.Item.INFINITE_OXYGEN_TANK, 16, 16);
        TANKS = root.bake(32, 32);
    }

    private SpaceGearDrawer() {}

    private static void tank(PartDefinition root, String size, int u, int v) {
        root.addOrReplaceChild(size, CubeListBuilder.create().texOffs(u, v)
                .addBox(0, 0, 0, SpaceGearFit.TANK_WIDTH, SpaceGearFit.TANK_LENGTH, SpaceGearFit.TANK_WIDTH), PartPose.ZERO);
    }

    public static void drawHelmet(PoseStack pose, MultiBufferSource buffers, int light, SpaceGearFit.Helmet helmet) {
        pose.pushPose();
        pose.translate(helmet.centerX() * PIXEL, helmet.centerY() * PIXEL, helmet.centerZ() * PIXEL);
        pose.scale(helmet.scaleX(), helmet.scaleY(), helmet.scaleZ());
        MASK.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MASK_TEXTURE, true)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * @param firstSize the first tank's item id path, as synced in {@code galacticraft$tankSize(0)};
     *                  an empty or unknown size draws no tank there
     */
    public static void drawTanks(PoseStack pose, MultiBufferSource buffers, int light, SpaceGearFit.Tanks tanks,
                                 String firstSize, String secondSize) {
        drawTank(pose, buffers, light, tanks.transform(0), firstSize);
        drawTank(pose, buffers, light, tanks.transform(1), secondSize);
    }

    private static void drawTank(PoseStack pose, MultiBufferSource buffers, int light, Matrix4f pixelTransform, String size) {
        if (size == null || size.isEmpty() || !TANKS.hasChild(size)) return;
        // The fit maps tank pixels to part pixels, and the cube divides its own pixels by 16, so
        // only the translation needs converting to blocks.
        Matrix4f blocks = new Matrix4f(pixelTransform);
        blocks.m30(blocks.m30() * PIXEL).m31(blocks.m31() * PIXEL).m32(blocks.m32() * PIXEL);
        pose.pushPose();
        pose.mulPose(blocks);
        TANKS.getChild(size).render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TANK_TEXTURE, true)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
