/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.content.entity.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** GC4's automatic arrivals, with server-owned physics and clearance for taller terrain. */
public final class AutomaticLanderEntity extends LanderEntity {
    public enum Kind { BALLOONS, ASTEROID_POD, VENUS_POD }
    private static final EntityDataAccessor<Boolean> PARACHUTE = SynchedEntityData.defineId(AutomaticLanderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENTRY_FLAMES = SynchedEntityData.defineId(AutomaticLanderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BOUNCES = SynchedEntityData.defineId(AutomaticLanderEntity.class, EntityDataSerializers.INT);
    private final Kind kind;

    public AutomaticLanderEntity(EntityType<?> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public boolean hasParachute() { return entityData.get(PARACHUTE); }
    public boolean hasEntryFlames() { return entityData.get(ENTRY_FLAMES); }
    public int bounces() { return entityData.get(BOUNCES); }
    @Override public boolean hasManualControls() { return false; }
    @Override public LivingEntity getControllingPassenger() { return null; }
    @Override public boolean shouldSpawnParticles() { return false; }

    // The pods and balloons keep the legacy seat height; LanderEntity's seat fits the moon lander cabin.
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vec3(0, 1.5F, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARACHUTE, false);
        builder.define(ENTRY_FLAMES, false);
        builder.define(BOUNCES, 0);
    }

    @Override
    public void startDescent() {
        super.startDescent();
        if (kind == Kind.VENUS_POD) setDeltaMovement(0, -2.5, 0);
    }

    @Override
    public void tickInAir() {
        double altitude = getY() - level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
        boolean venusChuteHeight = getY() <= 242 || altitude <= 30;
        boolean chute = bounces() == 0 && (kind == Kind.BALLOONS ? getY() >= 500 && altitude > 20
                : kind == Kind.VENUS_POD && altitude > 5 && venusChuteHeight);
        entityData.set(PARACHUTE, chute);
        entityData.set(ENTRY_FLAMES, kind == Kind.VENUS_POD && !venusChuteHeight);
        Vec3 velocity = getDeltaMovement();
        double y = switch (kind) {
            // GC4's height-dependent fall avoids a ninety-second parachute crawl from Y=900.
            case BALLOONS -> bounces() == 0 ? -Math.max(1.2, getY() / 50) : velocity.y * .95 - .08;
            case ASTEROID_POD -> Math.max(-.7, velocity.y - .002);
            case VENUS_POD -> {
                double descent = velocity.y - .002;
                if (descent < -.7) descent *= .994;
                if (venusChuteHeight) descent *= altitude > 5 ? .995 : .9995;
                yield descent;
            }
        };
        setDeltaMovement(velocity.x * .95, y, velocity.z * .95);
    }

    @Override
    public void onGroundHit() {
        entityData.set(PARACHUTE, false);
        entityData.set(ENTRY_FLAMES, false);
        if (kind == Kind.BALLOONS && bounces() < 14) {
            entityData.set(BOUNCES, bounces() + 1);
            // Keep the restored high-altitude entry's touchdown bounces contained near the cargo.
            double bounce = 1.2 / bounces();
            double direction = random.nextDouble() * Math.PI * 2;
            setOnGround(false);
            setDeltaMovement(Math.cos(direction) * bounce / 3, bounce, Math.sin(direction) * bounce / 3);
            hasImpulse = true;
        } else {
            awardSafeLanding();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Bounces", bounces());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(BOUNCES, Math.clamp(tag.getInt("Bounces"), 0, 14));
    }
}