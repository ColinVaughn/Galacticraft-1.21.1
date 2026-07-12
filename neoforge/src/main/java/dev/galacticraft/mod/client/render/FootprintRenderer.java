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

package dev.galacticraft.mod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.misc.footprint.Footprint;
import dev.galacticraft.mod.misc.footprint.FootprintManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class FootprintRenderer {
    private static final ResourceLocation FOOTPRINT_TEXTURE = Constant.id("textures/misc/footprint.png");

    public static void renderFootprints(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ResourceLocation dimension = minecraft.level.dimensionTypeRegistration().unwrapKey().orElseThrow().location();
        List<Footprint> visible = new ArrayList<>();
        minecraft.level.galacticraft$getFootprintManager().getFootprints().values().forEach(list ->
                list.stream().filter(footprint -> footprint.dimension.equals(dimension)).forEach(visible::add));
        if (visible.isEmpty()) return;

        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        RenderSystem.setShaderTexture(0, FOOTPRINT_TEXTURE);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Vec3 camera = event.getCamera().getPosition();
        for (Footprint footprint : visible) {
            poses.pushPose();
            float age = 1.0F - footprint.age / (float) Footprint.MAX_AGE;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(age, age, age, age);
            poses.translate(footprint.position.x - camera.x, footprint.position.y - camera.y + 0.01F * age, footprint.position.z - camera.z);
            Matrix4f matrix = poses.last().pose();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float rotation = 45.0F * Mth.DEG_TO_RAD - footprint.rotation;
            for (int i = 3; i >= 0; i--) {
                buffer.addVertex(matrix, Mth.sin(rotation) * 0.5F, 0, Mth.cos(rotation) * 0.5F)
                        .setUv(i / 2, (i == 0 || i == 3) ? 1 : 0);
                rotation += Mth.HALF_PI;
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            poses.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        poses.popPose();
    }

    public static void setFootprints(long chunk, List<Footprint> prints) {
        if (Minecraft.getInstance().level == null) return;
        FootprintManager manager = Minecraft.getInstance().level.galacticraft$getFootprintManager();
        List<Footprint> footprintList = manager.getFootprints().computeIfAbsent(chunk, ignored -> new ArrayList<>());
        footprintList.addAll(prints);
    }

    private FootprintRenderer() {}
}
