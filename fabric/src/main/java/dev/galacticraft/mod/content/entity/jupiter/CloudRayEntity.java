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

import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cloud Ray: a passive glider of the upper banks. It wanders between waypoints in the bank layer (Y
 * {@value #MIN_Y}-{@value #MAX_Y} on Jupiter, around where it appeared elsewhere) and bolts upward and away when hurt.
 */
public class CloudRayEntity extends JupiterFlyer {
    public static final int MIN_Y = 200, MAX_Y = 255;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PANICKING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CloudRayEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private @Nullable Vec3 waypoint;
    private double homeY = Double.NaN;
    private int retarget, panic;

    public CloudRayEntity(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.xpReward = 2;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PANICKING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return flyerAttributes(14.0);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (Double.isNaN(this.homeY)) this.homeY = this.getY();
        if (this.panic > 0) this.panic--;
        if (this.entityData.get(PANICKING) != this.panic > 0) this.entityData.set(PANICKING, this.panic > 0);
        if (this.waypoint == null || --this.retarget <= 0 || this.distanceToSqr(this.waypoint) < 9.0) this.pickWaypoint();
        if (this.waypoint != null) this.steer(this.waypoint, this.panic > 0 ? 0.34 : 0.13, this.panic > 0 ? 0.12 : 0.05);
    }

    private void pickWaypoint() {
        this.retarget = 80 + this.random.nextInt(80);
        boolean jupiter = this.level().dimension().equals(GCDimensions.JUPITER);
        double low = jupiter ? MIN_Y : this.homeY - 8, high = jupiter ? MAX_Y : this.homeY + 8;
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 candidate = new Vec3(this.getX() + this.random.nextInt(49) - 24,
                    Mth.clamp(this.getY() + this.random.nextInt(13) - 6, low, high), this.getZ() + this.random.nextInt(49) - 24);
            if (this.fitsAt(candidate)) {
                this.waypoint = candidate;
                return;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            this.panic = 60;
            Vec3 away = source.getEntity() == null ? Vec3.ZERO : this.position().subtract(source.getEntity().position()).normalize().scale(16);
            this.waypoint = this.position().add(away.x, 10, away.z);
            this.retarget = 60;
        }
        return hurt;
    }

    /** Synced, so the client can play the flee clip. */
    public boolean isPanicking() {
        return this.level().isClientSide ? this.entityData.get(PANICKING) : this.panic > 0;
    }

    public @Nullable Vec3 waypoint() {
        return this.waypoint;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }
}
