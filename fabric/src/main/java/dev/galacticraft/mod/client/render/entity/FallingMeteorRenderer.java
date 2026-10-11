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

package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.MeteorDefenseModel;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Blockbench rock, ember seams and animated flame cards driven by the existing ablation signal. */
@Environment(EnvType.CLIENT)
public class FallingMeteorRenderer extends EntityRenderer<FallingMeteorEntity> {
    private static final ResourceLocation STONY = texture("stony");
    private static final ResourceLocation IRON = texture("iron");
    private static final ResourceLocation PALLASITE = texture("pallasite");
    private static final ResourceLocation GLOW = texture("glow");
    private static final ResourceLocation FLAMES = texture("flames");
    private final MeteorDefenseModel model;

    public FallingMeteorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new MeteorDefenseModel(Minecraft.getInstance().getResourceManager(), "entity", "falling_meteor");
    }

    @Override
    public void render(FallingMeteorEntity entity, float yaw, float partialTick, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        float scale = bodyScale(entity.getSize(), entity.getMassFraction());
        if (scale <= 0) return;
        this.shadowRadius = scale;
        double seconds = (entity.tickCount + (double) partialTick) / 20 + Math.floorMod(entity.getSeed(), 120) / 20.0;
        model.reset();
        model.animate("animation.falling_meteor.tumble", seconds);
        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        model.renderBone("body", matrices, vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),
                light, OverlayTexture.NO_OVERLAY, -1);
        float heat = Mth.clamp(entity.getGlow(), 0, 1);
        if (heat > 0.02f) {
            int color = (Math.round(heat * 255) << 24) | 0xFFFFFF;
            var embers = vertexConsumers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
            model.renderBone("body", matrices, embers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            model.animate("animation.falling_meteor.burn", seconds);
            alignFlames(matrices, entity.getDeltaMovement());
            model.bone("burning").yScale = 0.4f + 0.6f * heat;
            model.renderBone("burning", matrices, vertexConsumers.getBuffer(RenderType.entityTranslucentEmissive(FLAMES)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        }
        matrices.popPose();
        super.render(entity, yaw, partialTick, matrices, vertexConsumers, light);
    }

    static float bodyScale(int size, float massFraction) {
        return (float) (MeteoroidShape.radiusFor(size) * Math.cbrt(Mth.clamp(massFraction, 0, 1)));
    }

    static void alignFlames(PoseStack matrices, Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0e-8) return;
        Vec3 tail = velocity.normalize().scale(-1);
        matrices.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) tail.x, (float) tail.y, (float) tail.z));
    }

    private static ResourceLocation texture(String material) {
        return Constant.id("textures/entity/meteor/falling_meteor_" + material + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(FallingMeteorEntity entity) {
        return switch (entity.getMeteoroidClass()) {
            case STONY -> STONY;
            case IRON -> IRON;
            case PALLASITE -> PALLASITE;
        };
    }
}
