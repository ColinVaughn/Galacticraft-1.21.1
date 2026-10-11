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

import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.GCEntityTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The Venus dungeon boss: an evolved spider queen with a pounce and venom burst. */
public class SpiderBoss extends AbstractBossEntity {
    public static final int POUNCE_WINDUP = 1;
    public static final int POUNCE_AIR = 2;
    public static final int POUNCE_RECOVER = 3;
    public static final int VENOM_WINDUP = 4;
    public static final int VENOM_RECOVER = 5;
    public static final int NEST_BUILD = 6;
    public static final int NEST_HEAL = 7;
    public static final int EGG_WINDUP = 8;
    private static final EntityDataAccessor<Integer> ATTACK_STATE = SynchedEntityData.defineId(SpiderBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICKS = SynchedEntityData.defineId(SpiderBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(SpiderBoss.class, EntityDataSerializers.BOOLEAN);
    private int specialCooldown = 80;
    private boolean pounceNext = true;
    private Vec3 venomTarget = Vec3.ZERO;
    private boolean nestCompleted;
    private int unreachableTicks;
    private int eggCooldown;
    private double lastTargetDistance = Double.MAX_VALUE;
    private float climbBlend;
    private float previousClimbBlend;

    public SpiderBoss(EntityType<? extends SpiderBoss> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_STATE, 0);
        builder.define(ATTACK_TICKS, 0);
        builder.define(CLIMBING, false);
    }

    public int getSpecialAttack() {
        return this.entityData.get(ATTACK_STATE);
    }

    public int getSpecialAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    public boolean isClimbing() {
        return this.entityData.get(CLIMBING);
    }

    public float getClimbBlend(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), this.previousClimbBlend, this.climbBlend);
    }

    @Override
    public void tick() {
        super.tick();
        this.previousClimbBlend = this.climbBlend;
        this.climbBlend = Mth.clamp(this.climbBlend + (this.isClimbing() ? 0.16F : -0.12F), 0.0F, 1.0F);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("NestCompleted", this.nestCompleted);
        tag.putInt("EggCooldown", this.eggCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.nestCompleted = tag.getBoolean("NestCompleted");
        this.eggCooldown = tag.getInt("EggCooldown");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, false));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || this.isRemoved() || this.isDeadOrDying() || this.isNoAi()) {
            return;
        }

        this.entityData.set(CLIMBING, this.horizontalCollision);

        if (this.specialCooldown > 0) {
            this.specialCooldown--;
        }
        if (this.eggCooldown > 0) {
            this.eggCooldown--;
        }
        if (this.getSpecialAttack() != 0) {
            this.tickSpecialAttack();
            return;
        }

        LivingEntity target = this.getTarget();
        double targetDistance = target == null ? Double.MAX_VALUE : this.distanceToSqr(target);
        if (target != null && target.isAlive() && targetDistance > 16.0D
                && targetDistance < 900.0D && !this.getSensing().hasLineOfSight(target)
                && targetDistance >= this.lastTargetDistance - 0.04D) {
            this.unreachableTicks++;
        } else {
            this.unreachableTicks = Math.max(0, this.unreachableTicks - 4);
        }
        this.lastTargetDistance = targetDistance;
        if (!this.nestCompleted && this.onGround()
                && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.beginSpecial(NEST_BUILD);
            this.playSound(SoundEvents.COBWEB_PLACE, 1.5F, 0.6F);
            return;
        }
        if (this.unreachableTicks >= 80 && this.eggCooldown == 0 && this.onGround()
                && this.level().getEntitiesOfClass(SpiderEggEntity.class, this.getBoundingBox().inflate(24.0D)).size()
                + this.level().getEntitiesOfClass(SpiderlingEntity.class, this.getBoundingBox().inflate(24.0D)).size() < 6) {
            this.unreachableTicks = 0;
            this.eggCooldown = 240;
            this.beginSpecial(EGG_WINDUP);
            return;
        }
        if (this.specialCooldown == 0 && target != null && target.isAlive()
                && this.getSensing().hasLineOfSight(target)) {
            double distance = this.distanceToSqr(target);
            if (this.pounceNext && this.onGround() && distance > 16.0D && distance < 121.0D) {
                this.beginSpecial(POUNCE_WINDUP);
            } else if (distance > 9.0D && distance < 196.0D) {
                this.venomTarget = target.position();
                this.beginSpecial(VENOM_WINDUP);
            }
        }
    }

    private void beginSpecial(int state) {
        this.entityData.set(ATTACK_STATE, state);
        this.entityData.set(ATTACK_TICKS, 0);
        this.specialCooldown = 110;
        if (state != NEST_BUILD && state != EGG_WINDUP) {
            this.pounceNext = !this.pounceNext;
        }
        this.getNavigation().stop();
        this.playSound(SoundEvents.SPIDER_AMBIENT, 1.4F, state == POUNCE_WINDUP || state == NEST_BUILD || state == EGG_WINDUP ? 0.65F : 1.3F);
    }

    private void endSpecial() {
        this.entityData.set(ATTACK_STATE, 0);
        this.entityData.set(ATTACK_TICKS, 0);
    }

    private void tickSpecialAttack() {
        int state = this.getSpecialAttack();
        int ticks = this.getSpecialAttackTicks() + 1;
        this.entityData.set(ATTACK_TICKS, ticks);
        ServerLevel server = (ServerLevel) this.level();
        LivingEntity target = this.getTarget();

        if (state == POUNCE_WINDUP) {
            this.getNavigation().stop();
            if (target == null || !target.isAlive()) {
                this.entityData.set(ATTACK_STATE, 0);
                this.entityData.set(ATTACK_TICKS, 0);
                return;
            }
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (ticks % 4 == 0) {
                server.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 12, 1.2D, 0.2D, 1.2D, 0.02D);
            }
            if (ticks >= 20) {
                Vec3 direction = target.position().subtract(this.position());
                Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z).normalize();
                double speed = Math.min(0.8D, Math.max(0.35D, direction.horizontalDistance() / 14.0D));
                this.setDeltaMovement(horizontal.scale(speed).add(0.0D, 0.62D, 0.0D));
                this.hasImpulse = true;
                this.entityData.set(ATTACK_STATE, POUNCE_AIR);
                this.entityData.set(ATTACK_TICKS, 0);
                this.playSound(SoundEvents.SPIDER_STEP, 1.5F, 0.55F);
            }
        } else if (state == POUNCE_AIR) {
            if (ticks > 5 && this.onGround()) {
                this.slam(server);
                this.entityData.set(ATTACK_STATE, POUNCE_RECOVER);
                this.entityData.set(ATTACK_TICKS, 0);
            } else if (ticks > 40) {
                this.entityData.set(ATTACK_STATE, 0);
                this.entityData.set(ATTACK_TICKS, 0);
            }
        } else if (state == VENOM_WINDUP) {
            this.getNavigation().stop();
            if (ticks % 4 == 0) {
                server.sendParticles(ParticleTypes.WITCH, this.venomTarget.x, this.venomTarget.y + 0.1D, this.venomTarget.z,
                        16, 1.5D, 0.05D, 1.5D, 0.01D);
                server.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 1.5D, this.getZ(),
                        8, 0.8D, 0.5D, 0.8D, 0.01D);
            }
            if (ticks >= 30) {
                this.releaseVenom(server);
                this.entityData.set(ATTACK_STATE, VENOM_RECOVER);
                this.entityData.set(ATTACK_TICKS, 0);
            }
        } else if (state == NEST_BUILD || state == NEST_HEAL) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            if (ticks % 3 == 0) {
                this.spawnNestSilk(server, state == NEST_BUILD ? 10 : 5);
            }
            if (state == NEST_BUILD && ticks >= 40) {
                this.entityData.set(ATTACK_STATE, NEST_HEAL);
                this.entityData.set(ATTACK_TICKS, 0);
                this.playSound(SoundEvents.SPIDER_AMBIENT, 1.2F, 1.65F);
            } else if (state == NEST_HEAL) {
                if (ticks % 10 == 0) {
                    this.heal(3.0F);
                    server.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.2D, this.getZ(),
                            4, 0.8D, 0.3D, 0.8D, 0.0D);
                }
                if (ticks >= 60) {
                    this.nestCompleted = true;
                    this.endSpecial();
                }
            }
        } else if (state == EGG_WINDUP) {
            this.getNavigation().stop();
            if (ticks >= 30) {
                SpiderEggEntity egg = new SpiderEggEntity(GCEntityTypes.SPIDER_EGG, server);
                Vec3 behind = this.getLookAngle().scale(-1.2D);
                egg.setPos(this.getX() + behind.x, this.getY() + 0.1D, this.getZ() + behind.z);
                server.addFreshEntity(egg);
                this.playSound(SoundEvents.SLIME_SQUISH, 1.4F, 0.6F);
                this.endSpecial();
            }
        } else if (ticks >= 12) {
            this.endSpecial();
        }
    }

    private void spawnNestSilk(ServerLevel server, int count) {
        double radius = 1.6D + (this.getSpecialAttack() == NEST_BUILD ?
                Math.min(this.getSpecialAttackTicks() / 40.0D, 1.0D) : 1.0D);
        for (int i = 0; i < count; i++) {
            double angle = 2.0D * Math.PI * (i + this.tickCount * 0.12D) / count;
            server.sendParticles(ParticleTypes.CLOUD,
                    this.getX() + Math.cos(angle) * radius, this.getY() + 0.1D,
                    this.getZ() + Math.sin(angle) * radius, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
                    this.getX() + Math.cos(angle) * radius, this.getY() + 0.15D,
                    this.getZ() + Math.sin(angle) * radius, 1, 0.08D, 0.03D, 0.08D, 0.0D);
        }
        server.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0D, this.getZ(),
                4, 1.2D, 0.45D, 1.2D, 0.01D);
    }

    private void slam(ServerLevel server) {
        server.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2D, this.getZ(),
                48, 2.0D, 0.2D, 2.0D, 0.18D);
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.7F);
        for (Player player : server.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(2.5D, 1.0D, 2.5D),
                player -> player.isAlive() && !player.isSpectator() && player.distanceToSqr(this) <= 16.0D
                        && this.getSensing().hasLineOfSight(player))) {
            player.hurt(this.damageSources().mobAttack(this), 8.0F);
            player.knockback(1.4D, this.getX() - player.getX(), this.getZ() - player.getZ());
        }
    }

    private void releaseVenom(ServerLevel server) {
        server.addFreshEntity(new SpiderVenomEntity(this, this.venomTarget));
        this.playSound(SoundEvents.WITCH_THROW, 1.5F, 0.65F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return this.getSpecialAttack() == 0 && super.doHurtTarget(target);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public List<ItemStack> getGuaranteedLoot(RandomSource rand) {
        // Venus dungeon boss: grants the Cargo Rocket and Astro Miner schematics.
        return List.of(
                new ItemStack(GCItems.CARGO_ROCKET_SCHEMATIC),
                new ItemStack(GCItems.ASTRO_MINER_SCHEMATIC)
        );
    }

    @Override
    public int getChestTier() {
        return 3;
    }


    @Override
    public BossEvent.BossBarColor getHealthBarColor() {
        return BossEvent.BossBarColor.PURPLE;
    }
}
