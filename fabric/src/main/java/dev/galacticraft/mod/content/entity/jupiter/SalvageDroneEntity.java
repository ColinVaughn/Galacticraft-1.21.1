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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Salvage Drone: guards the machinery of a Suspended Research Facility. It patrols a slow orbit around its post and
 * dives at players who come within {@value #GUARD_RADIUS} blocks of that post, but never chases far beyond it, so a
 * player can always break off by leaving the facility.
 */
public class SalvageDroneEntity extends JupiterFlyer implements Enemy {
    public static final int GUARD_RADIUS = 14, LEASH = 20;
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(SalvageDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TARGETING = SynchedEntityData.defineId(SalvageDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private @Nullable BlockPos post;
    private int attackCooldown;

    public SalvageDroneEntity(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return flyerAttributes(20.0).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, 0);
        builder.define(TARGETING, false);
    }

    /** Remaining ticks of the attack lunge, for the renderer. */
    public int attackTicks() {
        return this.entityData.get(ATTACK);
    }

    /** True while locked onto an intruder; drives the alert pose. */
    public boolean isTargeting() {
        return this.entityData.get(TARGETING);
    }

    public void guard(BlockPos post) {
        this.post = post.immutable();
        this.setPersistenceRequired();
    }

    public @Nullable BlockPos post() {
        return this.post;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.post == null) this.post = this.blockPosition();
        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.attackTicks() > 0) this.entityData.set(ATTACK, this.attackTicks() - 1);
        Vec3 post = Vec3.atCenterOf(this.post);
        Player target = this.prey(GUARD_RADIUS + 2);
        boolean engaging = target != null && this.canEngage(target);
        if (engaging != this.isTargeting()) {
            this.entityData.set(TARGETING, engaging);
            if (engaging) this.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.6F);
        }
        if (engaging) {
            this.steer(target.getEyePosition().subtract(0, 0.4, 0), 0.24, 0.12);
            if (this.attackCooldown == 0 && this.distanceTo(target) < 1.9) {
                this.doHurtTarget(target);
                this.entityData.set(ATTACK, 10);
                this.attackCooldown = 20;
            }
        } else {
            double angle = this.tickCount * 0.03 + (this.getId() % 7);
            this.steer(post.add(Math.cos(angle) * 3.5, Math.sin(this.tickCount * 0.05) * 0.6, Math.sin(angle) * 3.5), 0.1, 0.06);
        }
    }

    /** A drone only fights players inside its guarded area, and only while it is still near its post. */
    public boolean canEngage(Player player) {
        if (this.post == null) return false;
        Vec3 post = Vec3.atCenterOf(this.post);
        return player.position().distanceTo(post) <= GUARD_RADIUS && this.position().distanceTo(post) <= LEASH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.post != null) tag.put("Post", NbtUtils.writeBlockPos(this.post));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Post").ifPresent(pos -> this.post = pos);
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP_AGGRESSIVE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }
}
