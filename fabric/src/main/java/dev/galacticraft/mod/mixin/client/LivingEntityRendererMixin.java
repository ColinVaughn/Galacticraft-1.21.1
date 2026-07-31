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

package dev.galacticraft.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.client.gui.overlay.SensorGlassesOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique
    private boolean galacticraft$sensorPass;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", shift = At.Shift.AFTER)
    )
    private void galacticraft$scaleSensorMob(LivingEntity entity, float yaw, float partialTick,
                                              PoseStack pose, MultiBufferSource buffers, int packedLight,
                                              CallbackInfo ci) {
        if (this.galacticraft$sensorPass) {
            // Legacy's spider renderer applied an additional -0.03 Y translation before the
            // common +0.045 Sensor Glasses transform.
            pose.translate(0.0F, entity.getType() == dev.galacticraft.mod.content.GCEntityTypes.EVOLVED_SPIDER
                    ? 0.015F : 0.045F, 0.0F);
            pose.scale(1.07F, 1.035F, 1.07F);
        }
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true, index = 6
    )
    private int galacticraft$fullBrightSensorMob(int packedLight, LivingEntity entity) {
        return this.galacticraft$sensorPass ? LightTexture.FULL_BRIGHT : packedLight;
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void galacticraft$sensorMobTexture(LivingEntity entity, boolean bodyVisible, boolean translucent,
                                                boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (this.galacticraft$sensorPass) {
            cir.setReturnValue(RenderType.entityTranslucentEmissive(SensorGlassesOverlay.MOB_TEXTURE));
        }
    }

    /** Legacy rendered the normal mob first, then repeated the whole render with the sensor texture. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void galacticraft$renderSensorMobPass(LivingEntity entity, float yaw, float partialTick,
                                                   PoseStack pose, MultiBufferSource buffers, int packedLight,
                                                   CallbackInfo ci) {
        if (this.galacticraft$sensorPass || !SensorGlassesOverlay.shouldHighlight(entity)) return;
        this.galacticraft$sensorPass = true;
        try {
            ((LivingEntityRenderer) (Object) this).render(entity, yaw, partialTick, pose, buffers, packedLight);
        } finally {
            this.galacticraft$sensorPass = false;
        }
    }

    @Unique
    private static float sleepDirectionToRotationCryo(Direction direction) {
        return switch (direction) {
            default -> 0.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
        };
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasPose(Lnet/minecraft/world/entity/Pose;)Z"))
    private boolean gc$hasSleepPose(LivingEntity instance, Pose pose, Operation<Boolean> original) {
        return instance.isInCryoSleep() ? false : original.call(instance, pose);
    }

    @Inject(method = "setupRotations", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBedOrientation()Lnet/minecraft/core/Direction;"), cancellable = true)
    private void galacticraft$renderCryoChamberPos(LivingEntity entity, PoseStack pose, float animationProgress, float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (entity.isInCryoSleep()) {
            Direction direction = entity.getBedOrientation();
            float j = direction != null ? sleepDirectionToRotationCryo(direction) : bodyYaw;
            pose.translate(0, 0.82F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(j));
            ci.cancel();
        }
    }

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void rotateToMatchRocket(LivingEntity entity, PoseStack pose, float animationProgress, float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (entity.isPassenger() && entity.getVehicle() instanceof RocketEntity rocket) {
            double amplitude = switch(rocket.getLaunchStage()) {
                case IGNITED -> 0.1D;
                case LAUNCHED -> 0.04D;
                default -> 0.0D;
            };
            if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                amplitude *= 0.5D;
            }
            if (amplitude > 0.0D) {
                pose.translate((entity.level().random.nextDouble() - 0.5D) * amplitude, 0, (entity.level().random.nextDouble() - 0.5D) * amplitude);
            }

            Quaternionf rotation = new Quaternionf();
            rotation.rotateYXZ(-rocket.getViewYRot(tickDelta) * Mth.DEG_TO_RAD, rocket.getViewXRot(tickDelta) * Mth.DEG_TO_RAD, 0);
            rotation.mul(Axis.YP.rotationDegrees(rocket.getViewYRot(tickDelta)));
            pose.rotateAround(rotation, 0.0F, 0.5F, 0.0F);
        }
    }
}
