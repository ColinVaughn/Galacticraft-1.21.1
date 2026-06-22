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

package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.entity.vehicle.AstroMinerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class AstroMinerRenderer extends EntityRenderer<AstroMinerEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/astro_miner.json");

    private GCModel astroMinerModel;

    public AstroMinerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AstroMinerEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (this.astroMinerModel == null)
            this.astroMinerModel = GCModelLoader.INSTANCE.getModel(MODEL);
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();
        // The OBJ is authored in a ~1/20-block unit space (~65 units long), so it must be
        // scaled down to entity size. Transform mirrors the legacy RenderAstroMiner.doRender:
        // lift onto the entity, rotate to face, offset, then scale by 0.0495.
        matrices.translate(0.0F, 1.4F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        matrices.translate(0.0F, -0.42F, 0.28F);
        matrices.scale(0.0495F, 0.0495F, 0.0495F);
        // null state = render the whole model at once.
        this.astroMinerModel.render(matrices, null, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        renderLasers(entity, tickDelta, matrices, vertexConsumers);
    }

    /** Draws the mining lasers from the drill to each block currently being cut. */
    private void renderLasers(AstroMinerEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource buffers) {
        List<BlockPos> targets = entity.getLaserTargets();
        if (targets.isEmpty()) {
            return;
        }
        double ex = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double ey = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double ez = Mth.lerp(tickDelta, entity.zOld, entity.getZ());

        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = matrices.last().pose();
        Vec3 origin = new Vec3(0.0D, 1.0D, 0.0D); // drill, in entity-local space
        for (BlockPos target : targets) {
            Vec3 to = new Vec3(target.getX() + 0.5D - ex, target.getY() + 0.5D - ey, target.getZ() + 0.5D - ez);
            renderBeam(matrix, consumer, origin, to);
        }
    }

    private static void renderBeam(Matrix4f matrix, VertexConsumer c, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0E-4D) {
            return;
        }
        Vec3 dir = delta.scale(1.0D / len);
        Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        double w = 0.05D;
        Vec3 right = dir.cross(up).normalize().scale(w);
        Vec3 fwd = dir.cross(right).normalize().scale(w);
        // Bright red, additive (RenderType.lightning ignores the texture and glows).
        float r = 1.0F, g = 0.12F, b = 0.08F, a = 0.85F;
        Vec3[] near = {from.add(right).add(fwd), from.subtract(right).add(fwd), from.subtract(right).subtract(fwd), from.add(right).subtract(fwd)};
        Vec3[] far = {to.add(right).add(fwd), to.subtract(right).add(fwd), to.subtract(right).subtract(fwd), to.add(right).subtract(fwd)};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            vert(matrix, c, near[i], r, g, b, a);
            vert(matrix, c, near[j], r, g, b, a);
            vert(matrix, c, far[j], r, g, b, a);
            vert(matrix, c, far[i], r, g, b, a);
        }
    }

    private static void vert(Matrix4f m, VertexConsumer c, Vec3 p, float r, float g, float b, float a) {
        c.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(AstroMinerEntity entity) {
        return null;
    }
}
