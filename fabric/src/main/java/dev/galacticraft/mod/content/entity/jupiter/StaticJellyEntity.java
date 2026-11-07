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

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Storm Belt hostile that approaches, charges for {@value #CHARGE_TICKS} ticks, then discharges
 * within {@value #RANGE} blocks. The charge gives players time to leave its attack radius.
 */
public class StaticJellyEntity extends JupiterFlyer implements Enemy {
    public static final int CHARGE_TICKS = 30, COOLDOWN_TICKS = 60;
    public static final double RANGE = 5.0, SIGHT = 16.0;
    public static final float DISCHARGE_DAMAGE = 6.0F;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(StaticJellyEntity.class, EntityDataSerializers.INT);
    /** Ticks left of the visible burst after a discharge. */
    private static final EntityDataAccessor<Integer> BURST = SynchedEntityData.defineId(StaticJellyEntity.class, EntityDataSerializers.INT);
    public static final int BURST_TICKS = 10;
    private int cooldown;
    private @Nullable Vec3 drift;

    public StaticJellyEntity(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return flyerAttributes(16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0);
        builder.define(BURST, 0);
    }

    /** Ticks left of the post-discharge burst, or 0. */
    public int burst() {
        return this.entityData.get(BURST);
    }

    /** Ticks into the current charge, or 0 while not charging. */
    public int charge() {
        return this.entityData.get(CHARGE);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.burst() > 0) this.entityData.set(BURST, this.burst() - 1);
        int charge = this.charge();
        if (charge > 0) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6));
            if (charge % 6 == 0) this.playSound(SoundEvents.BEEHIVE_WORK, 0.8F, 1.8F);
            if (charge + 1 >= CHARGE_TICKS) {
                this.discharge();
                this.entityData.set(CHARGE, 0);
                this.entityData.set(BURST, BURST_TICKS);
                this.cooldown = COOLDOWN_TICKS;
            } else {
                this.entityData.set(CHARGE, charge + 1);
            }
            return;
        }
        if (this.cooldown > 0) this.cooldown--;
        Player target = this.prey(SIGHT);
        if (target != null) {
            this.steer(target.position().add(0, 1.6, 0), 0.07, 0.04);
            if (this.cooldown == 0 && this.distanceTo(target) < RANGE - 0.75) {
                this.entityData.set(CHARGE, 1);
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.9F);
            }
        } else {
            if (this.drift == null || this.random.nextInt(120) == 0 || this.distanceToSqr(this.drift) < 4) {
                this.drift = this.position().add(this.random.nextInt(17) - 8, this.random.nextInt(7) - 3, this.random.nextInt(17) - 8);
            }
            this.steer(this.drift, 0.03, 0.02);
        }
    }

    /** Strikes everyone in range; returns how many were hit. */
    public int discharge() {
        if (!(this.level() instanceof ServerLevel level)) return 0;
        int hit = 0;
        Vec3 centre = this.position().add(0, this.getBbHeight() * 0.5, 0);
        for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(RANGE))) {
            if (player.isCreative() || player.isSpectator() || player.distanceTo(this) > RANGE) continue;
            if (player.hurt(this.damageSources().mobAttack(this), DISCHARGE_DAMAGE)) {
                hit++;
                Vec3 push = player.position().subtract(this.position()).normalize().scale(0.6);
                player.push(push.x, 0.25, push.z);
                player.hurtMarked = true;
            }
            Vec3 to = player.position().add(0, 1, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 at = centre.lerp(to, i / 8.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.02);
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 30, RANGE * 0.3, 0.4, RANGE * 0.3, 0.2);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.7F, 1.7F);
        return hit;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }
}
