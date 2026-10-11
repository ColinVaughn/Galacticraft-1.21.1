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
import dev.galacticraft.mod.client.model.BuggyAnimations;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BuggyRenderer extends EntityRenderer<Buggy> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/buggy.json");
    public static final GCModelState MAIN_MODEL = new GCModelState("MainBody");
    public static final GCModelState RADAR_DISH = new GCModelState("RadarDish");
    public static final GCModelState WHEEL_LEFT_COVER = new GCModelState("LeftFender");
    public static final GCModelState WHEEL_RIGHT = new GCModelState("WheelRight");
    public static final GCModelState WHEEL_LEFT = new GCModelState("WheelLeft");
    public static final GCModelState WHEEL_RIGHT_COVER = new GCModelState("RightFender");
    public static final GCModelState CARGO_LEFT = new GCModelState("CargoLeft");
    public static final GCModelState CARGO_MID = new GCModelState("CargoMid");
    public static final GCModelState CARGO_RIGHT = new GCModelState("CargoRight");
    public static final GCModelState STEERING_WHEEL = new GCModelState("SteeringWheel");

    public static final float FRONT_AXLE_X = 1.9F;
    public static final float REAR_AXLE_X = 1.9F;
    public static final float AXLE_Y = 0.976F;
    public static final float AXLE_Z = 2.727F;
    public static final float RADAR_X = -0.720683F;
    public static final float RADAR_Y = 4.1F;
    public static final float RADAR_Z = -2.397F;
    public static final float STEERING_Y = 12.9F / BuggyAnimations.UNITS_PER_OBJ;
    public static final float STEERING_Z = -7.45F / BuggyAnimations.UNITS_PER_OBJ;

    private final BuggyAnimations animations;

    public BuggyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.animations = new BuggyAnimations(Minecraft.getInstance().getResourceManager());
    }

    @Override
    public void render(Buggy buggy, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        GCModel model = GCModelLoader.INSTANCE.getModel(MODEL);
        var motion = buggy.getAnimationState();
        double turns = motion.wheelTurns(tickDelta);
        float steering = motion.steering(tickDelta);
        double seconds = (buggy.tickCount + (double) tickDelta) / 20;
        float pitch = Mth.lerp(tickDelta, buggy.xRotO, buggy.getXRot());
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();
        matrices.scale(1.0F, 1.0F, 1.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        matrices.mulPose(Axis.ZP.rotationDegrees(-pitch));
        matrices.scale(0.41F, 0.41F, 0.41F);

        // Pitch the complete vehicle about its trailing axle so mounted parts stay connected.
        float climbPivot = AXLE_Z * motion.climbDirection();
        matrices.translate(0, AXLE_Y, climbPivot);
        BuggyAnimations.apply(matrices, animations.sample("climb", "Climb", motion.climbTime(tickDelta)), motion.climbDirection());
        matrices.translate(0, -AXLE_Y, -climbPivot);

        wheel(model, matrices, consumer, light, FRONT_AXLE_X, -AXLE_Z, WHEEL_RIGHT_COVER, WHEEL_RIGHT, turns, steering);
        wheel(model, matrices, consumer, light, -FRONT_AXLE_X, -AXLE_Z, WHEEL_LEFT_COVER, WHEEL_LEFT, turns, steering);
        wheel(model, matrices, consumer, light, REAR_AXLE_X, AXLE_Z, null, WHEEL_RIGHT, turns, 0);
        wheel(model, matrices, consumer, light, -REAR_AXLE_X, AXLE_Z, null, WHEEL_LEFT, turns, 0);

        matrices.pushPose();
        matrices.translate(0, AXLE_Y, 0);
        BuggyAnimations.apply(matrices, animations.sample("suspension", "Chassis", seconds), motion.drive(tickDelta));
        matrices.translate(0, -AXLE_Y, 0);
        model.render(matrices, MAIN_MODEL, consumer, light, OverlayTexture.NO_OVERLAY);
        renderCargo(model, buggy.getVariant(), matrices, consumer, light, OverlayTexture.NO_OVERLAY);

        matrices.pushPose();
        matrices.translate(0, STEERING_Y, STEERING_Z);
        BuggyAnimations.apply(matrices, animations.steering("SteeringWheel", steering), 1);
        model.render(matrices, STEERING_WHEEL, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // Radar Dish
        matrices.pushPose();
        matrices.translate(RADAR_X, RADAR_Y, RADAR_Z);
        BuggyAnimations.apply(matrices, animations.sample("radar", "RadarDish", seconds + buggy.getId() % 8), 1);
        model.render(matrices, RADAR_DISH, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        matrices.popPose();
        matrices.popPose();
    }

    private void wheel(GCModel model, PoseStack matrices, VertexConsumer consumer, int light, float x, float z,
                       GCModelState cover, GCModelState wheel, double turns, float steering) {
        matrices.pushPose();
        matrices.translate(x, AXLE_Y, z);
        if (cover != null) {
            BuggyAnimations.apply(matrices, animations.steering(wheel.getName(), steering), 1);
            model.render(matrices, cover, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        BuggyAnimations.apply(matrices, animations.sample("roll", wheel.getName(), turns), 1);
        model.render(matrices, wheel, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }

    public static void renderCargo(GCModel model, Buggy.BuggyType type, PoseStack matrices,
                                   VertexConsumer consumer, int light, int overlay) {
        switch (type) {
            case NORMAL -> { }
            case STORAGE_18 -> model.render(matrices, CARGO_MID, consumer, light, overlay);
            case STORAGE_36 -> {
                model.render(matrices, CARGO_LEFT, consumer, light, overlay);
                model.render(matrices, CARGO_RIGHT, consumer, light, overlay);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Buggy entity) {
        return null;
    }
}
