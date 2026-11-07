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
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.model.LanderAnimations;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Geometry, texture and clips exported from art/blockbench/lander.bbmodel. */
public class LanderEntityRenderer extends EntityRenderer<LanderEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/lander.json");
    /** Same OBJ with the light layer (console screens, cabin lamps, landing and navigation lights). */
    public static final ResourceLocation EMISSIVE_MODEL = Constant.id("models/misc/lander_emissive.json");
    public static final GCModelState BODY = new GCModelState("Body");
    public static final GCModelState CABIN = new GCModelState("Cabin");
    public static final GCModelState FEET = new GCModelState("Feet");
    public static final GCModelState ENGINE = new GCModelState("Engine");
    public static final GCModelState GLOW = new GCModelState("Glow");
    public static final GCModelState DOOR = new GCModelState("Door");
    public static final GCModelState DOOR_GLASS = new GCModelState("DoorGlass");
    public static final GCModelState DISH = new GCModelState("Dish");
    public static final GCModelState WHIP = new GCModelState("Whip");
    /** Tilt pivot, in blocks above the feet: where the legacy lander model swung from. */
    private static final float PIVOT_Y = 1.5F;

    private final LanderAnimations animations;

    public LanderEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2F;
        this.animations = new LanderAnimations(Minecraft.getInstance().getResourceManager());
    }

    @Override
    public void render(LanderEntity lander, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int light) {
        poseStack.pushPose();
        final float pitch = lander.getViewXRot(partialTicks);

        float hurtTime = lander.getHurtTime() - partialTicks;
        float damage = lander.getDamage() - partialTicks;

        if (damage < 0.0F) {
            damage = 0.0F;
        }

        poseStack.translate(0, PIVOT_Y, 0);
        if (hurtTime > 0.0F) {
            poseStack.mulPose(Axis.XN.rotationDegrees(Mth.sin(hurtTime) * 0.2F * hurtTime * damage / 25.0F));
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.ZN.rotationDegrees(pitch));
        poseStack.translate(0, -PIVOT_Y, 0);
        // The cockpit (Blockbench north) faces the way forward thrust carries the lander.
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        GCModel model = GCModelLoader.INSTANCE.getModel(MODEL);
        VertexConsumer consumer = multiBufferSource.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        var motion = lander.getAnimationState();
        double seconds = (lander.tickCount + (double) partialTicks) / 20;
        float touchdown = motion.touchdownTime(partialTicks);
        float thrust = motion.thrust(partialTicks);

        // The feet stay planted while the rest of the lander rides its shock struts.
        model.render(poseStack, FEET, consumer, light, OverlayTexture.NO_OVERLAY);
        LanderAnimations.apply(poseStack, 0, 0, 0, animations.sample("touchdown", "Body", touchdown), 1);
        model.render(poseStack, BODY, consumer, light, OverlayTexture.NO_OVERLAY);

        poseStack.pushPose();
        LanderAnimations.apply(poseStack, 0, 16, 0, animations.sample("gimbal", "Engine", seconds), thrust);
        LanderAnimations.apply(poseStack, 0, 16, 0, animations.sample("touchdown", "Engine", touchdown), 1);
        model.render(poseStack, ENGINE, consumer, light, OverlayTexture.NO_OVERLAY);
        boolean burning = thrust > 0;
        if (burning) LanderAnimations.apply(poseStack, 0, 7.9F, 0, animations.sample("glow", "Glow", seconds), 1);
        model.render(poseStack, GLOW, consumer, burning ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
                burning ? 0xFFFFFFFF : 0xFF30343C);
        poseStack.popPose();

        // The pilot sits inside the cabin, so first person sees the cockpit and looks out through the hatch window.
        var door = animations.sample("door", "Door", motion.door(partialTicks));
        var whip = animations.sample("touchdown", "Whip", touchdown);
        model.render(poseStack, CABIN, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.pushPose();
        LanderAnimations.apply(poseStack, 6, 29, -16.5F, door, 1);
        model.render(poseStack, DOOR, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.pushPose();
        LanderAnimations.apply(poseStack, 8, 57.5F, 15, animations.sample("antenna", "Dish", seconds + lander.getId() % 8), 1);
        LanderAnimations.apply(poseStack, 8, 57.5F, 15, animations.sample("touchdown", "Dish", touchdown), 1);
        model.render(poseStack, DISH, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.pushPose();
        LanderAnimations.apply(poseStack, -9, 49, 15, whip, 1);
        model.render(poseStack, WHIP, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // Light layer after the opaque draws: switching layers can flush the previous builder.
        GCModel lights = GCModelLoader.INSTANCE.getModel(EMISSIVE_MODEL);
        VertexConsumer glow = multiBufferSource.getBuffer(RenderType.eyes(GCRenderTypes.OBJ_ATLAS));
        lights.render(poseStack, BODY, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        lights.render(poseStack, CABIN, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        // Anti-collision beacon on the whip antenna.
        if ((lander.tickCount + lander.getId()) % 30 < 4) {
            poseStack.pushPose();
            LanderAnimations.apply(poseStack, -9, 49, 15, whip, 1);
            lights.render(poseStack, WHIP, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        // Glass goes last, in the translucent pass, so it blends over the lit interior.
        poseStack.pushPose();
        LanderAnimations.apply(poseStack, 6, 29, -16.5F, door, 1);
        model.render(poseStack, DOOR_GLASS, multiBufferSource.getBuffer(GCRenderTypes.objTranslucent(GCRenderTypes.OBJ_ATLAS)),
                light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LanderEntity entity) {
        return GCRenderTypes.OBJ_ATLAS;
    }
}
