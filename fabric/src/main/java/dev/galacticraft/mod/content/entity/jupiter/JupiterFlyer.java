/*
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

package dev.galacticraft.mod.content.entity.jupiter;

import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A creature of Jupiter's open atmosphere: it never falls, breathes the planet's air, and moves by steering, i.e.
 * accelerating toward a point and banking its body into the motion, so every flier drifts with some inertia.
 */
public abstract class JupiterFlyer extends FlyingMob {
    protected JupiterFlyer(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    protected static AttributeSupplier.Builder flyerAttributes(double health) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.FOLLOW_RANGE, 24.0)
                .add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D);
    }

    /** Accelerates toward {@code target} at up to {@code speed} blocks per tick, easing in as it arrives. */
    protected void steer(Vec3 target, double speed, double acceleration) {
        Vec3 offset = target.subtract(this.position());
        double distance = offset.length();
        Vec3 desired = distance < 1.0E-3 ? Vec3.ZERO : offset.scale(Math.min(speed, distance * 0.15) / distance);
        Vec3 velocity = this.getDeltaMovement();
        Vec3 next = velocity.add(desired.subtract(velocity).scale(acceleration));
        this.setDeltaMovement(next);
        this.face(next, 0.25F);
    }

    /** Turns the body toward the direction of travel. */
    protected void face(Vec3 motion, float rate) {
        if (motion.horizontalDistanceSqr() < 1.0E-5) return;
        float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(Mth.rotLerp(rate, this.getYRot(), yaw));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    /** The nearest player this creature may attack within {@code range} blocks. */
    protected @Nullable Player prey(double range) {
        Player nearest = null;
        double best = range * range;
        for (Player player : this.level().players()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            double distance = player.distanceToSqr(this);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /** True when this creature would fit at {@code position}. */
    protected boolean fitsAt(Vec3 position) {
        return this.level().noCollision(this, this.getBoundingBox().move(position.subtract(this.position())));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }
}
