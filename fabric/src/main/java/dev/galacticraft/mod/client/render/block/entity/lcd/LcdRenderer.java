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

package dev.galacticraft.mod.client.render.block.entity.lcd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Draws {@link LcdImage}s as glowing flat color just off a block face, turned with the block. */
public final class LcdRenderer {
    public enum Face { TOP, FRONT }

    private static final ResourceLocation WHITE = Constant.id("textures/obj/white.png");
    // Lifts the display off the face so it does not z-fight with the block.
    private static final float OFFSET = 0.001F;
    private static final int MIN_LIGHT = 12;
    private static final int PULSE_LIT_MS = 1600;
    private static final int PULSE_PERIOD_MS = PULSE_LIT_MS * 3;

    private LcdRenderer() {
    }

    /** {@code rgb} fading in and out over 1.6 s once every 4.8 s, running {@code delayMs} behind. */
    public static int pulse(int rgb, int delayMs) {
        long time = Math.floorMod(Util.getMillis() - delayMs, PULSE_PERIOD_MS);
        double phase = Math.min((double) time / PULSE_LIT_MS, 1.0);
        int alpha = (int) ((1.0 - Math.cos(phase * 2.0 * Math.PI)) * 127.5);
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /** {@code facing} is the block's horizontal facing; {@link Face#FRONT} is the face it points out of. */
    public static void draw(Face face, LcdImage image, int argb, Direction facing, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (image.rects().isEmpty()) return;
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.translate(-0.5, -0.5, -0.5);

        Quad quad = new Quad(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), poses.last(), argb, overlay,
                LightTexture.pack(Math.max(MIN_LIGHT, LightTexture.block(light)), Math.max(MIN_LIGHT, LightTexture.sky(light))),
                face == Face.TOP ? Direction.UP : Direction.SOUTH);
        float scale = 1.0F / image.pixelsPerBlock();
        for (LcdImage.Rect rect : image.rects()) {
            float x1 = rect.x1() * scale, x2 = rect.x2() * scale, y1 = rect.y1() * scale, y2 = rect.y2() * scale;
            if (face == Face.TOP) {
                quad.emit(x1, 1 + OFFSET, y2, x2, 1 + OFFSET, y2, x2, 1 + OFFSET, y1, x1, 1 + OFFSET, y1);
            } else {
                quad.emit(x1, 1 - y1, 1 + OFFSET, x2, 1 - y1, 1 + OFFSET, x2, 1 - y2, 1 + OFFSET, x1, 1 - y2, 1 + OFFSET);
            }
        }
        poses.popPose();
    }

    private record Quad(VertexConsumer consumer, PoseStack.Pose pose, int argb, int overlay, int light, Direction normal) {
        void emit(float... corners) {
            for (int i = 0; i < corners.length; i += 3) {
                this.consumer.addVertex(this.pose.pose(), corners[i], corners[i + 1], corners[i + 2])
                        .setColor(this.argb >> 16 & 0xFF, this.argb >> 8 & 0xFF, this.argb & 0xFF, this.argb >>> 24)
                        .setLight(this.light)
                        .setOverlay(this.overlay)
                        .setUv(0.5F, 0.5F)
                        .setNormal(this.pose, this.normal.getStepX(), this.normal.getStepY(), this.normal.getStepZ());
            }
        }
    }
}
