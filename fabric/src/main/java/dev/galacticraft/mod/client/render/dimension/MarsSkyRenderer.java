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

package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class MarsSkyRenderer {
    public static final MarsSkyRenderer INSTANCE = new MarsSkyRenderer();


    private final Minecraft minecraft = Minecraft.getInstance();
    public VertexBuffer starBuffer;
    public VertexBuffer skyBuffer;
    public VertexBuffer darkBuffer;
    private final float sunSize;

    public MarsSkyRenderer() {
        this.sunSize = 20.0F;

        this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        // Bind stars to display list
        this.starBuffer.bind();
        this.starBuffer.upload(this.renderStars());
        VertexBuffer.unbind();

        this.skyBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        final byte byte2 = 64;
        final int i = 256 / byte2 + 2;
        float f = 16F;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        RenderSystem.setShader(GameRenderer::getPositionShader);
        for (int j = -byte2 * i; j <= byte2 * i; j += byte2) {
            for (int l = -byte2 * i; l <= byte2 * i; l += byte2) {

                buffer.addVertex(j, f, l)
                        .addVertex(j + byte2, f, l)
                        .addVertex(j + byte2, f, l + byte2)
                        .addVertex(j, f, l + byte2);
            }
        }
        this.skyBuffer.bind();
        this.skyBuffer.upload(buffer.buildOrThrow());
        VertexBuffer.unbind();

        this.darkBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        f = -16F;
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int k = -byte2 * i; k <= byte2 * i; k += byte2) {
            for (int i1 = -byte2 * i; i1 <= byte2 * i; i1 += byte2) {
                buffer.addVertex(k + byte2, f, i1 + 0)
                        .addVertex(k + 0, f, i1 + 0)
                        .addVertex(k + 0, f, i1 + byte2)
                        .addVertex(k + byte2, f, i1 + byte2);
            }
        }

        this.darkBuffer.bind();
        this.darkBuffer.upload(buffer.buildOrThrow());
        VertexBuffer.unbind();
    }

    private MeshData renderStars() {
        RandomSource rand = RandomSource.create(10842L);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int starIndex = 0; starIndex < 35000; ++starIndex) {
            double var4 = rand.nextFloat() * 2.0F - 1.0F;
            double var6 = rand.nextFloat() * 2.0F - 1.0F;
            double var8 = rand.nextFloat() * 2.0F - 1.0F;
            final double var10 = 0.15F + rand.nextFloat() * 0.1F;
            double var12 = var4 * var4 + var6 * var6 + var8 * var8;

            if (var12 < 1.0D && var12 > 0.01D) {
                var12 = 1.0D / Math.sqrt(var12);
                var4 *= var12;
                var6 *= var12;
                var8 *= var12;
                final double var14 = var4 * (rand.nextDouble() * 150D + 130D);
                final double var16 = var6 * (rand.nextDouble() * 150D + 130D);
                final double var18 = var8 * (rand.nextDouble() * 150D + 130D);
                final double var20 = Math.atan2(var4, var8);
                final double var22 = Math.sin(var20);
                final double var24 = Math.cos(var20);
                final double var26 = Math.atan2(Math.sqrt(var4 * var4 + var8 * var8), var6);
                final double var28 = Math.sin(var26);
                final double var30 = Math.cos(var26);
                final double var32 = rand.nextDouble() * Math.PI * 2.0D;
                final double var34 = Math.sin(var32);
                final double var36 = Math.cos(var32);

                for (int var38 = 0; var38 < 4; ++var38) {
                    final double var39 = 0.0D;
                    final double var41 = ((var38 & 2) - 1) * var10;
                    final double var43 = ((var38 + 1 & 2) - 1) * var10;
                    final double var47 = var41 * var36 - var43 * var34;
                    final double var49 = var43 * var36 + var41 * var34;
                    final double var53 = var47 * var28 + var39 * var30;
                    final double var55 = var39 * var28 - var47 * var30;
                    final double var57 = var55 * var22 - var49 * var24;
                    final double var61 = var49 * var22 + var55 * var24;
                    buffer.addVertex((float) (var14 + var57), (float) (var16 + var53), (float) (var18 + var61));
                }
            }
        }

        return buffer.buildOrThrow();
    }

    public void render(GCWorldRenderContext context) {
        ClientLevel level = context.world();
        float partialTicks = context.tickCounter().getGameTimeDeltaPartialTick(true);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(context.positionMatrix());
        Vec3 vec3 = level.getSkyColor(context.camera().getPosition(), partialTicks);
        float f1 = (float) vec3.x;
        float f2 = (float) vec3.y;
        float f3 = (float) vec3.z;
        RenderSystem.depthMask(false);
        FogRenderer.levelFogColor();
        RenderSystem.setShaderColor(f1, f2, f3, 1.0F);
        ShaderInstance shader = RenderSystem.getShader();
        this.skyBuffer.bind();
        this.skyBuffer.drawWithShader(poseStack.last().pose(), context.projectionMatrix(), shader);
        VertexBuffer.unbind();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 0);
        float f7;
        float f8;
        float f9;
        float f10;

        float starBrightness = level.getStarBrightness(partialTicks);

        if (starBrightness > 0.0F) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.XN.rotationDegrees(level.getTimeOfDay(partialTicks) * 360.0F));
            poseStack.mulPose(Axis.YN.rotationDegrees(-19.0F));
            RenderSystem.setShaderColor(starBrightness, starBrightness, starBrightness, starBrightness);
            FogRenderer.setupNoFog();
            this.starBuffer.bind();
            this.starBuffer.drawWithShader(poseStack.last().pose(), context.projectionMatrix(), GameRenderer.getPositionShader());
            VertexBuffer.unbind();
            poseStack.popPose();
        }

        float timeOfDay = level.getTimeOfDay(partialTicks);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.XN.rotationDegrees(timeOfDay * 360.0F));

        // Martian dust scatters blue light forward, concentrating a cool halo
        // around the low Sun while the rest of the daylight sky remains rusty.
        float sunHeight = Mth.cos(timeOfDay * Mth.TWO_PI);
        float heightRange = Mth.clamp((Math.abs(sunHeight) - 0.12F) / 0.43F, 0.0F, 1.0F);
        heightRange = heightRange * heightRange * (3.0F - 2.0F * heightRange);
        float lowSun = 1.0F - heightRange;
        float sunVisibility = Mth.clamp((sunHeight + 0.18F) / 0.32F, 0.0F, 1.0F);
        float dustVisibility = 1.0F - ClientDustStorms.intensity(partialTicks) * 0.85F;
        float haloVisibility = sunVisibility * dustVisibility;

        float haloRed = Mth.lerp(lowSun, 0.90F, 0.38F);
        float haloGreen = Mth.lerp(lowSun, 0.63F, 0.58F);
        float haloBlue = Mth.lerp(lowSun, 0.43F, 0.95F);
        // Star rendering changes the global shader color. Leaving it in place
        // multiplies the halo toward black and can erase it entirely in daylight.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f last = poseStack.last().pose();
        renderSunHalo(last, Mth.lerp(lowSun, 20.0F, 29.0F), haloRed, haloGreen, haloBlue,
                Mth.lerp(lowSun, 0.18F, 0.50F) * haloVisibility);
        renderSunHalo(last, Mth.lerp(lowSun, 40.0F, 58.0F), haloRed, haloGreen, haloBlue,
                Mth.lerp(lowSun, 0.08F, 0.26F) * haloVisibility);
        renderSunHalo(last, Mth.lerp(lowSun, 62.0F, 88.0F), haloRed, haloGreen, haloBlue,
                lowSun * 0.10F * haloVisibility);
        poseStack.popPose();

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer;
        poseStack.pushPose();
        f7 = 0.0F;
        f8 = 0.0F;
        f9 = 0.0F;
        poseStack.translate(f7, f8, f9);
        poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-level.getTimeOfDay(partialTicks) * 360.0F));
        // Render sun
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
        // Some blanking to conceal the stars
        f10 = this.sunSize / 3.5F;
        Matrix4f last2 = poseStack.last().pose();
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(last2, -f10, 99.9F, -f10)
                .addVertex(last2, f10, 99.9F, -f10)
                .addVertex(last2, f10, 99.9F, f10)
                .addVertex(last2, -f10, 99.9F, f10);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.1F);
        f10 = this.sunSize;
        RenderSystem.setShaderTexture(0, Constant.Skybox.SUN_MARS);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(last2, -f10, 100.0F, -f10).setUv(0.0F, 0.0F)
                .addVertex(last2, f10, 100.0F, -f10).setUv(1.0F, 0.0F)
                .addVertex(last2, f10, 100.0F, f10).setUv(1.0F, 1.0F)
                .addVertex(last2, -f10, 100.0F, f10).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        // Render earth
        f10 = 0.5F;
        poseStack.scale(0.6F, 0.6F, 0.6F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200F));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1F);
        RenderSystem.setShaderTexture(0, Constant.CelestialBody.EARTH);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(last2, -f10, -100.0F, f10).setUv(0, 1.0F)
                .addVertex(last2, f10, -100.0F, f10).setUv(1.0F, 1.0F)
                .addVertex(last2, f10, -100.0F, -f10).setUv(1.0F, 0)
                .addVertex(last2, -f10, -100.0F, -f10).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
//        GL11.glEnable(GL11.GL_FOG);
        poseStack.popPose();
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
        double horizon = minecraft.player.getEyePosition().y - level.getLevelData().getHorizonHeight(level);

        if (horizon < 0.0D) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 12.0F, 0.0F);
            this.darkBuffer.bind();
            this.darkBuffer.drawWithShader(poseStack.last().pose(), context.projectionMatrix(), shader);
            VertexBuffer.unbind();
            poseStack.popPose();
            f8 = 1.0F;
            f9 = -((float) (horizon + 65.0D));
            f10 = -f8;
            Matrix4f last3 = poseStack.last().pose();
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.addVertex(last3, -f8, f9, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f9, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f9, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f9, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f9, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f9, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f9, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f9, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, -f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, -f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, f8).setColor(0, 0, 0, 1.0F)
                    .addVertex(last3, f8, f10, -f8).setColor(0, 0, 0, 1.0F);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, -((float) (horizon - 16.0D)), 0.0F);
        this.darkBuffer.bind();
        this.darkBuffer.drawWithShader(poseStack.last().pose(), context.projectionMatrix(), shader);
        VertexBuffer.unbind();
        poseStack.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableBlend();
    }

    private static void renderSunHalo(Matrix4f matrix, float radius, float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F) return;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, 0.0F, 100.0F, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, -radius, 100.0F, -radius).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, 0.0F, 100.0F, -radius * 1.5F).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, radius, 100.0F, -radius).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, radius * 1.5F, 100.0F, 0.0F).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, radius, 100.0F, radius).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, 0.0F, 100.0F, radius * 1.5F).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, -radius, 100.0F, radius).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, -radius * 1.5F, 100.0F, 0.0F).setColor(red, green, blue, 0.0F)
                .addVertex(matrix, -radius, 100.0F, -radius).setColor(red, green, blue, 0.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
