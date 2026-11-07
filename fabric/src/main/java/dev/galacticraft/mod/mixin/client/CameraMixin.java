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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.galacticraft.mod.content.block.entity.CryogenicChamberBlockEntity;
import dev.galacticraft.mod.content.block.entity.CryogenicChamberPartBlockEntity;
import dev.galacticraft.mod.content.block.special.CryogenicChamberBlock;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.tag.GCFluidTags;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 vec3);

    @Shadow
    protected abstract void move(float x, float y, float z);

    @Shadow
    protected abstract float getXRot();

    @Shadow
    protected abstract float getYRot();

    @Unique
    private static float sleepDirectionToRotationCryo(Direction direction) {
        return switch (direction) {
            default -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
        };
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void gc$rotateCamera(BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {
        if (entity instanceof Player player && player.isInCryoSleep()) {
            var x = Mth.floor(entity.getX());
            var y = Mth.floor(entity.getY());
            var z = Mth.floor(entity.getZ());
            var blockEntity = entity.level().getBlockEntity(new BlockPos(x, y, z));

            if (blockEntity instanceof CryogenicChamberPartBlockEntity partBlockEntity) {
                blockEntity = partBlockEntity.getLevel().getBlockEntity(partBlockEntity.basePos);
            }
            if (blockEntity instanceof CryogenicChamberBlockEntity) {
                var sleepTimer = player.getSleepTimer();
                var yRot = (sleepTimer - 50) + sleepDirectionToRotationCryo(blockEntity.getBlockState().getValue(CryogenicChamberBlock.FACING));

                if (sleepTimer < 100) {
                    yRot += partialTicks;
                }
                entity.xRotO = 45.0F;
                this.setRotation(yRot, 0.0F);
                this.move(-4.1F, 0.3F, 0.0F);
            }
        } else if (!detached && entity.isPassenger() && entity.getVehicle() instanceof RocketEntity rocket) {
            float pitch = rocket.getViewXRot(partialTicks);
            float yaw = rocket.getViewYRot(partialTicks);
            if (rocket.isInSpaceflight()) this.setRotation(yaw, pitch - 90);
            else if (!rocket.isLanding()) this.setRotation(this.getYRot() + yaw - rocket.getInitialYRot(), this.getXRot() + pitch);

            Quaternionf rotation = new Quaternionf();
            rotation.rotateYXZ(-yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, 0);
            var geometry = dev.galacticraft.api.rocket.RocketGeometry.of(rocket);
            float offset = rocket.isInSpaceflight()
                    ? geometry.top() / 16F - 1.6F + .3F
                    : rocket.isLanding() ? -2.2F : 1.12F;
            float landingOffset = rocket.isLanding() ? Math.max(4, geometry.width() / 32F + 2) : 0;
            Vector3f vector = new Vector3f(0.0F, offset, landingOffset);
            vector.rotate(rotation);
            vector.y += 1.6F;
            this.setPosition(rocket.getPosition(partialTicks).add(new Vec3(vector)));
        }
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float gc$landerCameraDistance(float distance, @Local(argsOnly = true) Entity entity) {
        // Keep the lander's legs and the automatic vehicles' canopy in view; vanilla still clips against terrain.
        return entity.getVehicle() instanceof dev.galacticraft.mod.content.entity.vehicle.LanderEntity lander
                ? distance * (lander.hasManualControls() ? 2.5F : 2F) : distance;
    }

    @ModifyExpressionValue(method = "getFluidInCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private boolean gc$getFluidInCamera(boolean original, @Local FluidState fluidState) {
        return original || fluidState.is(GCFluidTags.OIL) || fluidState.is(GCFluidTags.FUEL) || fluidState.is(GCFluidTags.SULFURIC_ACID);
    }
}