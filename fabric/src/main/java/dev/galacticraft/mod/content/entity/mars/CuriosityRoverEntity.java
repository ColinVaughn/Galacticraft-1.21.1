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

package dev.galacticraft.mod.content.entity.mars;

import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Passive Mars rover that surveys and drills samples. Retaliates against its attacker and
 * returns to passive behavior when that target dies or disappears.
 */
public class CuriosityRoverEntity extends PathfinderMob {
    public static final int SURVEY_TICKS = 80, ATTACK_TICKS = 10;

    public enum Action { NONE, SURVEY, ATTACK }

    private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(CuriosityRoverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(CuriosityRoverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_LEFT = SynchedEntityData.defineId(CuriosityRoverEntity.class, EntityDataSerializers.INT);

    /** Client-side odometer: blocks rolled forward (negative when reversing), so the wheels turn at ground speed. */
    public float wheelTravel, wheelTravelO;

    public CuriosityRoverEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.wheelTravelO = this.wheelTravel;
            float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
            this.wheelTravel += (float) (-(this.getX() - this.xo) * Mth.sin(yaw) + (this.getZ() - this.zo) * Mth.cos(yaw));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.ARMOR, 6.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 24.0).add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D);
    }

    /** Rovers are placed when Mars generates, one in about a hundred chunks, and never topped up afterwards. */
    public static boolean checkSpawnRules(EntityType<CuriosityRoverEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                          BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.NATURAL) return false;
        return level.canSeeSky(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.8, true));
        this.goalSelector.addGoal(2, new SurveyGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANGRY, false);
        builder.define(ACTION, Action.NONE.ordinal());
        builder.define(ACTION_LEFT, 0);
    }

    /** True while it is chasing someone who attacked it. */
    public boolean isAngry() {
        return this.entityData.get(ANGRY);
    }

    public Action action() {
        return Action.values()[Math.floorMod(this.entityData.get(ACTION), Action.values().length)];
    }

    /** Ticks since the current action began, for the renderer. */
    public int actionElapsed() {
        Action action = this.action();
        int length = action == Action.SURVEY ? SURVEY_TICKS : action == Action.ATTACK ? ATTACK_TICKS : 0;
        return length - this.entityData.get(ACTION_LEFT);
    }

    /** Holds the rover in an action's pose {@code elapsed} ticks in, for showcase captures of a rover without AI. */
    public void showPose(Action action, int elapsed, boolean angry) {
        int length = action == Action.SURVEY ? SURVEY_TICKS : action == Action.ATTACK ? ATTACK_TICKS : 0;
        this.begin(action, length - elapsed);
        this.entityData.set(ANGRY, angry);
    }

    private void begin(Action action, int ticks) {
        this.entityData.set(ACTION, action.ordinal());
        this.entityData.set(ACTION_LEFT, ticks);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean angry = this.getTarget() != null && this.getTarget().isAlive();
        if (angry != this.isAngry()) {
            this.entityData.set(ANGRY, angry);
            if (angry) this.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.0F, 0.5F);
        }
        int left = this.entityData.get(ACTION_LEFT);
        if (left > 0) {
            this.entityData.set(ACTION_LEFT, left - 1);
            if (left == 1) this.entityData.set(ACTION, Action.NONE.ordinal());
        }
        if (angry && this.tickCount % 8 == 0) this.playSound(SoundEvents.GRINDSTONE_USE, 0.25F, 1.8F);
    }

    /** The drill strike: sparks and grinding as the bit bites. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.begin(Action.ATTACK, ATTACK_TICKS);
            if (this.level() instanceof ServerLevel level) {
                Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.02);
            }
            this.playSound(SoundEvents.GRINDSTONE_USE, 1.0F, 1.4F);
        }
        return hit;
    }

    /** Where the drill bit meets the ground while surveying: just ahead of the chassis. */
    public Vec3 drillPoint() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return this.position().add(-Mth.sin(yaw) * 1.25, 0, Mth.cos(yaw) * 1.25);
    }

    /** Now and then, while calm, the rover stops and drills a sample from the ground in front of it. */
    static final class SurveyGoal extends Goal {
        private final CuriosityRoverEntity rover;
        private int ticks;

        SurveyGoal(CuriosityRoverEntity rover) {
            this.rover = rover;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !this.rover.isAngry() && this.rover.onGround() && this.rover.getRandom().nextInt(600) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks > 0 && !this.rover.isAngry();
        }

        @Override
        public void start() {
            this.ticks = SURVEY_TICKS;
            this.rover.getNavigation().stop();
            this.rover.begin(Action.SURVEY, SURVEY_TICKS);
        }

        @Override
        public void stop() {
            this.ticks = 0;
            if (this.rover.action() == Action.SURVEY) this.rover.begin(Action.NONE, 0);
        }

        @Override
        public void tick() {
            this.ticks--;
            int elapsed = SURVEY_TICKS - this.ticks;
            // the bit bores in between 1 s and 3 s into the clip
            if (elapsed >= 20 && elapsed <= 60 && this.rover.level() instanceof ServerLevel level) {
                Vec3 at = this.rover.drillPoint();
                BlockPos ground = BlockPos.containing(at.x, this.rover.getY() - 0.5, at.z);
                BlockState state = level.getBlockState(ground);
                if (elapsed % 3 == 0 && !state.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, this.rover.getY() + 0.1, at.z, 6, 0.15, 0.05, 0.15, 0.1);
                }
                if (elapsed % 10 == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.5F, 0.8F);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isAngry() ? null : SoundEvents.NOTE_BLOCK_BIT.value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + this.random.nextFloat() * 0.3F;
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.12F, 1.8F);
    }
}
