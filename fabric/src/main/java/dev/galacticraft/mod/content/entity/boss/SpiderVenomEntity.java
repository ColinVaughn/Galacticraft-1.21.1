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

package dev.galacticraft.mod.content.entity.boss;

import dev.galacticraft.mod.content.GCEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A modeled venom glob that becomes a visible poison pool on impact. */
public class SpiderVenomEntity extends Entity {
    private static final EntityDataAccessor<Boolean> POOLED = SynchedEntityData.defineId(SpiderVenomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int POOL_DURATION = 120;
    private Vec3 target = Vec3.ZERO;
    private SpiderBoss owner;
    private int poolTicks;
    private int settlingTicks;
    private boolean settling;

    public SpiderVenomEntity(EntityType<? extends SpiderVenomEntity> type, Level level) {
        super(type, level);
    }

    public SpiderVenomEntity(SpiderBoss owner, Vec3 target) {
        this(GCEntityTypes.SPIDER_VENOM, owner.level());
        this.owner = owner;
        this.target = target;
        this.setPos(owner.getX(), owner.getY() + 1.45D, owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POOLED, false);
    }

    public boolean isPooled() {
        return this.entityData.get(POOLED);
    }

    public float getPoolAge(float partialTick) {
        return this.poolTicks + partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isPooled()) {
            if (++this.poolTicks >= POOL_DURATION) {
                if (!this.level().isClientSide) {
                    this.discard();
                }
            } else if (!this.level().isClientSide && this.poolTicks % 10 == 0) {
                this.poisonNearbyPlayers();
            }
            return;
        }
        if (this.level().isClientSide) {
            return;
        }

        if (this.settling) {
            this.setDeltaMovement(0.0D, -0.6D, 0.0D);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.verticalCollision || this.onGround()) {
                this.land();
            } else if (++this.settlingTicks >= 32) {
                this.discard();
            }
            return;
        }

        Vec3 remaining = this.target.subtract(this.position());
        if (remaining.lengthSqr() <= 0.36D || this.tickCount >= 32) {
            this.settling = true;
            return;
        }
        Vec3 step = remaining.normalize().scale(Math.min(0.7D, remaining.length()));
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        if (this.horizontalCollision || this.verticalCollision) {
            this.settling = true;
        }
    }

    private void land() {
        this.entityData.set(POOLED, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SLIME_SQUISH, 1.6F, 0.55F);
        this.poisonNearbyPlayers();
    }

    private void poisonNearbyPlayers() {
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(3.0D, 1.5D, 3.0D),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative())) {
            double dx = player.getX() - this.getX();
            double dz = player.getZ() - this.getZ();
            if (dx * dx + dz * dz <= 9.0D && Math.abs(player.getY() - this.getY()) <= 1.5D) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0), this.owner);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this.owner);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
