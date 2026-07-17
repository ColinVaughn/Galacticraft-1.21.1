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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.machinelib.client.impl.platform.MachineLibClientPlatform;
import dev.galacticraft.mod.content.block.entity.networked.GlassFluidPipeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.material.Fluids;

public class FluidPipeBlockEntityRenderer implements BlockEntityRenderer<GlassFluidPipeBlockEntity> {
    private static final float INNER_MIN = 0.40625F;
    private static final float INNER_MAX = 0.59375F;

    public FluidPipeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GlassFluidPipeBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        MLFluidStack fluid = entity.getDisplayedFluid();
        if (fluid == null || fluid.fluid() == Fluids.EMPTY) return;

        TextureAtlasSprite sprite = MachineLibClientPlatform.fluidSprite(fluid.fluid(), fluid.components());
        if (sprite == null) {
            sprite = MachineLibClientPlatform.fluidSprite(Fluids.WATER, DataComponentPatch.EMPTY);
            if (sprite == null) return;
        }

        int tint = MachineLibClientPlatform.fluidColor(fluid.fluid(), fluid.components());
        int alpha = FastColor.ARGB32.alpha(tint);
        if (alpha == 0) alpha = 255;
        alpha = Math.min(alpha, 224);

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(sprite.atlasLocation()));
        PoseStack.Pose pose = matrices.last();
        int red = FastColor.ARGB32.red(tint);
        int green = FastColor.ARGB32.green(tint);
        int blue = FastColor.ARGB32.blue(tint);

        renderCuboid(consumer, pose, sprite, INNER_MIN, INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX, INNER_MAX,
                red, green, blue, alpha, light, overlay);

        for (Direction direction : Direction.values()) {
            if (!entity.isConnected(direction)) continue;
            switch (direction) {
                case DOWN -> renderCuboid(consumer, pose, sprite, INNER_MIN, 0.0F, INNER_MIN, INNER_MAX, INNER_MIN, INNER_MAX,
                        red, green, blue, alpha, light, overlay);
                case UP -> renderCuboid(consumer, pose, sprite, INNER_MIN, INNER_MAX, INNER_MIN, INNER_MAX, 1.0F, INNER_MAX,
                        red, green, blue, alpha, light, overlay);
                case NORTH -> renderCuboid(consumer, pose, sprite, INNER_MIN, INNER_MIN, 0.0F, INNER_MAX, INNER_MAX, INNER_MIN,
                        red, green, blue, alpha, light, overlay);
                case SOUTH -> renderCuboid(consumer, pose, sprite, INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX, INNER_MAX, 1.0F,
                        red, green, blue, alpha, light, overlay);
                case WEST -> renderCuboid(consumer, pose, sprite, 0.0F, INNER_MIN, INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX,
                        red, green, blue, alpha, light, overlay);
                case EAST -> renderCuboid(consumer, pose, sprite, INNER_MAX, INNER_MIN, INNER_MIN, 1.0F, INNER_MAX, INNER_MAX,
                        red, green, blue, alpha, light, overlay);
            }
        }
    }

    private static void renderCuboid(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite,
                                     float x0, float y0, float z0, float x1, float y1, float z1,
                                     int red, int green, int blue, int alpha, int light, int overlay) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        vertex(consumer, pose, x1, y0, z0, u0, v1, red, green, blue, alpha, light, overlay, 0, 0, -1);
        vertex(consumer, pose, x0, y0, z0, u1, v1, red, green, blue, alpha, light, overlay, 0, 0, -1);
        vertex(consumer, pose, x0, y1, z0, u1, v0, red, green, blue, alpha, light, overlay, 0, 0, -1);
        vertex(consumer, pose, x1, y1, z0, u0, v0, red, green, blue, alpha, light, overlay, 0, 0, -1);

        vertex(consumer, pose, x0, y0, z1, u0, v1, red, green, blue, alpha, light, overlay, 0, 0, 1);
        vertex(consumer, pose, x1, y0, z1, u1, v1, red, green, blue, alpha, light, overlay, 0, 0, 1);
        vertex(consumer, pose, x1, y1, z1, u1, v0, red, green, blue, alpha, light, overlay, 0, 0, 1);
        vertex(consumer, pose, x0, y1, z1, u0, v0, red, green, blue, alpha, light, overlay, 0, 0, 1);

        vertex(consumer, pose, x0, y0, z0, u0, v1, red, green, blue, alpha, light, overlay, -1, 0, 0);
        vertex(consumer, pose, x0, y0, z1, u1, v1, red, green, blue, alpha, light, overlay, -1, 0, 0);
        vertex(consumer, pose, x0, y1, z1, u1, v0, red, green, blue, alpha, light, overlay, -1, 0, 0);
        vertex(consumer, pose, x0, y1, z0, u0, v0, red, green, blue, alpha, light, overlay, -1, 0, 0);

        vertex(consumer, pose, x1, y0, z1, u0, v1, red, green, blue, alpha, light, overlay, 1, 0, 0);
        vertex(consumer, pose, x1, y0, z0, u1, v1, red, green, blue, alpha, light, overlay, 1, 0, 0);
        vertex(consumer, pose, x1, y1, z0, u1, v0, red, green, blue, alpha, light, overlay, 1, 0, 0);
        vertex(consumer, pose, x1, y1, z1, u0, v0, red, green, blue, alpha, light, overlay, 1, 0, 0);

        vertex(consumer, pose, x0, y0, z1, u0, v1, red, green, blue, alpha, light, overlay, 0, -1, 0);
        vertex(consumer, pose, x0, y0, z0, u0, v0, red, green, blue, alpha, light, overlay, 0, -1, 0);
        vertex(consumer, pose, x1, y0, z0, u1, v0, red, green, blue, alpha, light, overlay, 0, -1, 0);
        vertex(consumer, pose, x1, y0, z1, u1, v1, red, green, blue, alpha, light, overlay, 0, -1, 0);

        vertex(consumer, pose, x0, y1, z0, u0, v1, red, green, blue, alpha, light, overlay, 0, 1, 0);
        vertex(consumer, pose, x0, y1, z1, u0, v0, red, green, blue, alpha, light, overlay, 0, 1, 0);
        vertex(consumer, pose, x1, y1, z1, u1, v0, red, green, blue, alpha, light, overlay, 0, 1, 0);
        vertex(consumer, pose, x1, y1, z0, u1, v1, red, green, blue, alpha, light, overlay, 0, 1, 0);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int red, int green, int blue, int alpha,
                               int light, int overlay, float normalX, float normalY, float normalZ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
