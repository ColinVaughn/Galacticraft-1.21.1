package dev.galacticraft.mod.content.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Queen brood that tunnels to a player when ordinary pathfinding fails. */
public class SpiderlingEntity extends Monster {
    private static final EntityDataAccessor<Boolean> BURROWING = SynchedEntityData.defineId(SpiderlingEntity.class, EntityDataSerializers.BOOLEAN);
    private int blockedTicks;
    private int burrowTicks;
    private double lastTargetDistance = Double.MAX_VALUE;

    public SpiderlingEntity(EntityType<? extends SpiderlingEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURROWING, false);
    }

    public boolean isBurrowing() {
        return this.entityData.get(BURROWING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, false));
    }

    @Override
    public void aiStep() {
        this.noPhysics = this.isBurrowing();
        super.aiStep();
        if (this.level().isClientSide || this.isRemoved() || this.isDeadOrDying()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || target.isSpectator()
                || target instanceof Player player && player.isCreative()) {
            this.setTarget(null);
            this.blockedTicks = 0;
            if (this.isBurrowing() && this.level().noCollision(this)) {
                this.setBurrowing(false);
            } else if (this.isBurrowing() && ++this.burrowTicks > 40) {
                this.discard();
            }
            return;
        }
        double distance = this.distanceToSqr(target);
        if (distance > 1024.0D) {
            this.setTarget(null);
            if (this.isBurrowing() && this.level().noCollision(this)) {
                this.setBurrowing(false);
            } else if (this.isBurrowing() && ++this.burrowTicks > 40) {
                this.discard();
            }
            return;
        }
        if (!this.isBurrowing()) {
            boolean stalled = !this.getSensing().hasLineOfSight(target)
                    && distance > 1.0D && distance >= this.lastTargetDistance - 0.02D;
            this.blockedTicks = stalled ? this.blockedTicks + 1 : 0;
            this.lastTargetDistance = distance;
            if (this.blockedTicks >= 25) {
                this.setBurrowing(true);
                this.getNavigation().stop();
            }
            return;
        }

        this.burrowTicks++;
        this.getNavigation().stop();
        Vec3 toward = target.position().add(0.0D, 0.35D, 0.0D).subtract(this.position());
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.position().add(toward.normalize().scale(0.22D)));
        if (this.burrowTicks % 4 == 0 && this.level() instanceof ServerLevel server) {
            BlockState state = this.level().getBlockState(BlockPos.containing(this.position()));
            if (!state.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        this.getX(), this.getY() + 0.2D, this.getZ(), 8, 0.25D, 0.2D, 0.25D, 0.03D);
            }
        }
        if (this.level().noCollision(this) && (distance < 3.0D || this.getSensing().hasLineOfSight(target))) {
            this.setBurrowing(false);
            this.playSound(SoundEvents.GRAVEL_BREAK, 0.6F, 1.4F);
        } else if (this.burrowTicks > 180) {
            // Remove brood that remains trapped underground.
            this.discard();
        }
    }

    private void setBurrowing(boolean burrowing) {
        this.entityData.set(BURROWING, burrowing);
        this.noPhysics = burrowing;
        this.burrowTicks = 0;
        this.blockedTicks = 0;
        this.lastTargetDistance = Double.MAX_VALUE;
        if (burrowing) {
            this.playSound(SoundEvents.GRAVEL_BREAK, 0.6F, 0.7F);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !this.isBurrowing() && super.doHurtTarget(target);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }
}
