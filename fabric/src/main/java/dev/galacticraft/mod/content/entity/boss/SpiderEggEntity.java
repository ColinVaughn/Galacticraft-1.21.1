package dev.galacticraft.mod.content.entity.boss;

import dev.galacticraft.mod.content.GCEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** A fragile brood egg with a visible countdown before it hatches. */
public class SpiderEggEntity extends Monster {
    public static final int HATCH_TIME = 100;
    private static final EntityDataAccessor<Integer> HATCH_AGE = SynchedEntityData.defineId(SpiderEggEntity.class, EntityDataSerializers.INT);

    public SpiderEggEntity(EntityType<? extends SpiderEggEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HATCH_AGE, 0);
    }

    public int getHatchAge() {
        return this.entityData.get(HATCH_AGE);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || this.isRemoved() || this.isDeadOrDying()) {
            return;
        }
        int age = this.getHatchAge() + 1;
        this.entityData.set(HATCH_AGE, age);
        if (age >= HATCH_TIME) {
            for (int i = 0; i < 2; i++) {
                SpiderlingEntity baby = new SpiderlingEntity(GCEntityTypes.SPIDERLING, this.level());
                baby.setPos(this.getX() + (i == 0 ? -0.3D : 0.3D), this.getY() + 0.1D, this.getZ());
                this.level().addFreshEntity(baby);
            }
            this.playSound(SoundEvents.TURTLE_EGG_HATCH, 1.2F, 0.65F);
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            this.playSound(SoundEvents.TURTLE_EGG_BREAK, 1.0F, 0.7F);
            this.discard();
        }
        return hit;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HatchAge", this.getHatchAge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(HATCH_AGE, tag.getInt("HatchAge"));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }
}
