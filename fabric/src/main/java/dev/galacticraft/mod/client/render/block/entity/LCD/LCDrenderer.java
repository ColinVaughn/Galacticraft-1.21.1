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

package dev.galacticraft.mod.client.render.block.entity.LCD;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;

public class LCDrenderer {
    private static final ResourceLocation WHITE_SQUARE = Constant.id("textures/obj/white.png");

    public void render(LCD LCDData, int region, Direction direction, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {

        matrices.pushPose();
        light = LightTexture.pack(Math.max(12, LightTexture.block(light)), Math.max(12, LightTexture.sky(light)));

        //Translate to match block direction
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()));
        matrices.translate(-0.5, -0.5, -0.5);

        //Fetch data
        final LCDStaticData staticData = LCDData.getStaticData(region);
        final LCDDynamicData dynamicData = LCDData.getDynamicData(region);

        //Base translation
        final Vertex BT = staticData.baseTranslation();
        matrices.translate(BT.x(), BT.y(), BT.z());

        //Render
        final PoseStack.Pose pose = matrices.last();
        final Matrix4f matrix = pose.pose();
        final VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(WHITE_SQUARE));

        //Static colors, Dynamic colors, Dynamic vertices, Normal
        final List<Integer> SC = staticData.staticColors();
        final List<Integer> DC = dynamicData.dynamicColors();
        final List<Vertex> DV = dynamicData.dynamicVertices();
        final Direction N = staticData.normal();

        int DVi = 0;

        if(staticData.uniColor()) {
            int C = SC.getFirst() == LCD.DC ? DC.getFirst() : SC.getFirst();

            for (Vertex SV : staticData.staticVertices()) {
                Vertex V = SV.x() == LCD.DV ? DV.get(DVi++) : SV;

                consumer.addVertex(matrix, V.x(), V.y(), V.z())
                        .setColor((C >> 16) &0xFF, (C >> 8) &0xFF, C &0xFF, C >>> 24)
                        .setLight(light)
                        .setOverlay(overlay)
                        .setUv(0.5f, 0.5f)
                        .setNormal(pose, N.getStepX(), N.getStepY(), N.getStepZ());
            }
        }
        else {
            int DCi = 0, SCi = 0;

            for (Vertex SV : staticData.staticVertices()) {
                Vertex V = SV.x() == LCD.DV ? DV.get(DVi++) : SV;
                int C = SC.get(SCi++);
                if(C == LCD.DC) C = DC.get(DCi++);

                consumer.addVertex(matrix, V.x(), V.y(), V.z())
                        .setColor((C >> 16) &0xFF, (C >> 8) &0xFF, C &0xFF, C >>> 24)
                        .setLight(light)
                        .setOverlay(overlay)
                        .setUv(0.5f, 0.5f)
                        .setNormal(pose, N.getStepX(), N.getStepY(), N.getStepZ());
            }
        }

        matrices.popPose();
    }
}
