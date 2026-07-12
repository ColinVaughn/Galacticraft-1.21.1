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

package dev.galacticraft.impl.internal.mixin.gear;

import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import dev.galacticraft.mod.tag.GCFluidTags;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge replaces LivingEntity's vanilla air checks with CommonHooks, so the
 * Fabric call sites used by the shared mixin are not present here.
 */
@Mixin(LivingEntity.class)
abstract class NeoLivingEntityMixin {
    @Unique
    private int galacticraft$lastHurtBySuffocationTimestamp;

    @Shadow
    protected abstract int decreaseAirSupply(int air);

    @Inject(
            method = "baseTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;baseTick()V", shift = At.Shift.AFTER)
    )
    private void galacticraft$oxygenCheckAfterNeoForgeBreathing(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.galacticraft$oxygenConsumptionRate() == 0) {
            return;
        }

        AttributeInstance attribute = entity.getAttribute(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE);
        boolean canBreathe = attribute != null && attribute.getValue() >= 0.99D;
        boolean breathable = entity.level().isBreathable(entity.blockPosition().relative(
                Direction.UP, (int) Math.floor(entity.getEyeHeight(entity.getPose()))
        ));
        if (breathable || canBreathe || entity.isEyeInFluid(GCFluidTags.NON_BREATHABLE)
                || entity instanceof Player player && player.getAbilities().invulnerable) {
            return;
        }

        entity.setAirSupply(this.decreaseAirSupply(entity.getAirSupply()));
        if (entity.getAirSupply() == -20) {
            entity.setAirSupply(0);
            this.galacticraft$lastHurtBySuffocationTimestamp = entity.tickCount;
            entity.hurt(entity.damageSources().source(GCDamageTypes.SUFFOCATION), 2.0F);
        } else if (entity.tickCount - this.galacticraft$lastHurtBySuffocationTimestamp > 20) {
            this.galacticraft$lastHurtBySuffocationTimestamp = entity.tickCount;
            entity.hurt(entity.damageSources().source(GCDamageTypes.SUFFOCATION), 1.0F);
        }
    }
}
