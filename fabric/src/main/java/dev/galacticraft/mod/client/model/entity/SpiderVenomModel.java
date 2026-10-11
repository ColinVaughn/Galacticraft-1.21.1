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

package dev.galacticraft.mod.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class SpiderVenomModel {
    private final ModelPart root;
    private final ModelPart orb;
    private final ModelPart pool;

    public SpiderVenomModel(ModelPart root) {
        this.root = root;
        this.orb = root.getChild("orb");
        this.pool = root.getChild("pool");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("orb", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 10.0F, 10.0F)
                .texOffs(55, 28).addBox(-6.0F, -7.0F, -6.0F, 12.0F, 4.0F, 12.0F)
                .texOffs(0, 85).addBox(-3.0F, -2.0F, -7.0F, 6.0F, 5.0F, 2.0F)
                .texOffs(92, 68).addBox(-5.0F, -3.0F, -6.0F, 3.0F, 3.0F, 1.0F)
                .texOffs(92, 73).addBox(2.0F, -3.0F, -6.0F, 3.0F, 3.0F, 1.0F)
                .texOffs(43, 88).addBox(-6.0F, -11.0F, -3.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(52, 88).addBox(4.0F, -11.0F, -3.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(34, 88).addBox(-1.0F, -12.0F, 4.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(61, 88).addBox(-5.0F, 2.0F, -2.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(91, 86).addBox(3.0F, 2.0F, 1.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(74, 86).addBox(-3.0F, 1.0F, 4.0F, 6.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        root.addOrReplaceChild("pool", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-13.0F, -1.0F, -13.0F, 26.0F, 1.0F, 26.0F)
                .texOffs(55, 56).addBox(-8.0F, -1.0F, -20.0F, 16.0F, 1.0F, 10.0F)
                .texOffs(55, 45).addBox(-9.0F, -1.0F, 10.0F, 18.0F, 1.0F, 9.0F)
                .texOffs(0, 28).addBox(10.0F, -1.0F, -8.0F, 11.0F, 1.0F, 16.0F)
                .texOffs(0, 46).addBox(-21.0F, -1.0F, -8.0F, 11.0F, 1.0F, 16.0F)
                .texOffs(41, 68).addBox(-11.0F, -2.0F, -12.0F, 22.0F, 1.0F, 3.0F)
                .texOffs(41, 73).addBox(-11.0F, -2.0F, 9.0F, 22.0F, 1.0F, 3.0F)
                .texOffs(17, 85).addBox(-7.0F, -4.0F, -5.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(74, 78).addBox(4.0F, -3.0F, 2.0F, 5.0F, 2.0F, 5.0F)
                .texOffs(41, 78).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 1.0F, 8.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    public void render(boolean pooled, PoseStack poseStack, VertexConsumer vertices, int light) {
        this.orb.visible = !pooled;
        this.pool.visible = pooled;
        this.root.render(poseStack, vertices, light, OverlayTexture.NO_OVERLAY);
    }
}
