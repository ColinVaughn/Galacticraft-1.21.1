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

package dev.galacticraft.mod.content.entity;

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.world.dimension.meteor.AtmosphereProfile;
import dev.galacticraft.mod.world.dimension.meteor.MeteorImpact;
import dev.galacticraft.mod.world.dimension.meteor.MeteorImpactRules;
import dev.galacticraft.mod.world.dimension.meteor.MeteorPhysics;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static dev.galacticraft.mod.content.entity.damage.GCDamageTypes.METEOR_STRIKE;

/**
 * A meteoroid in atmospheric flight: a body of real blocks generated from a seed, integrated with
 * the drag/ablation model in {@link MeteorPhysics}, which either burns away in the sky, breaks up
 * into fragments, or reaches the ground and digs a crater.
 *
 * The block body is never sent over the wire. The seed, size class, material class and current
 * mass fraction ride on synched entity data, and both sides rebuild the identical voxel list from
 * {@link MeteoroidShape} - so ablation is visible as the body eroding from the outside in for
 * about eleven bytes of traffic.
 */
public class FallingMeteorEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(FallingMeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SIZE = SynchedEntityData.defineId(FallingMeteorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_CLASS = SynchedEntityData.defineId(FallingMeteorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_MASS_FRACTION = SynchedEntityData.defineId(FallingMeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GLOW = SynchedEntityData.defineId(FallingMeteorEntity.class, EntityDataSerializers.FLOAT);

    /** Fragments may not fragment again, so a breakup cannot cascade. */
    private static final byte MAX_GENERATION = 1;
    /** Safety valve: nothing stays in flight longer than this, whatever the physics says. */
    private static final int MAX_LIFETIME_TICKS = 1200;
    /** Mass a body must still have for a breakup to be worth spawning fragments for, kg. */
    private static final double FRAGMENT_MASS_THRESHOLD = 2000.0;
    /** Fraction of remaining mass shed per tick when a body is too small to fragment properly. */
    private static final double PANCAKE_MASS_LOSS = 0.08;
    /** Ablation rate, as a fraction of entry mass per tick, at which the body glows white hot. */
    private static final double GLOW_SATURATION = 0.002;

    private double entryMass = 1.0;
    private MeteoroidState physics = new MeteoroidState(1.0, 0.0, 0.0, 0.0, 0.0);
    private AtmosphereProfile profile;
    private byte generation;
    private float spinYaw;
    private float spinPitch;

    private List<MeteoroidShape.Voxel> cachedVoxels;
    private int cachedVoxelKey;

    public FallingMeteorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * Configures a freshly constructed meteoroid. The body's mass follows from its generated
     * volume and the density of its material class, so a big iron body really is heavier than a
     * small stony one.
     *
     * @param velocity entry velocity in m/s (SI, not blocks per tick)
     */
    public void initialise(MeteoroidClass type, int size, int seed, Vec3 velocity) {
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_SIZE, (byte) Mth.clamp(size, MeteoroidShape.MIN_SIZE, MeteoroidShape.MAX_SIZE));
        this.entityData.set(DATA_CLASS, type.id());
        this.entityData.set(DATA_MASS_FRACTION, 1.0f);

        this.entryMass = Math.max(1.0, voxels().size() * type.bulkDensity());
        this.physics = new MeteoroidState(this.entryMass, velocity.x, velocity.y, velocity.z, 0.0);
        this.spinYaw = (this.random.nextFloat() - 0.5f) * 14.0f;
        this.spinPitch = (this.random.nextFloat() - 0.5f) * 14.0f;
        this.refreshDimensions();
    }

    /** Overrides the mass of a body whose volume should not dictate its weight, such as a fragment. */
    public void setMass(double mass) {
        this.entryMass = Math.max(1.0, mass);
        this.physics = this.physics.withMass(this.entryMass);
    }

    public void setGeneration(byte generation) {
        this.generation = generation;
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public int getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    public MeteoroidClass getMeteoroidClass() {
        return MeteoroidClass.byId(this.entityData.get(DATA_CLASS));
    }

    /** Remaining fraction of the body's entry mass, in {@code [0, 1]}. */
    public float getMassFraction() {
        return this.entityData.get(DATA_MASS_FRACTION);
    }

    /** How fiercely the body is ablating right now, in {@code [0, 1]}; drives the glow and trail. */
    public float getGlow() {
        return this.entityData.get(DATA_GLOW);
    }

    /**
     * The body's voxels, rebuilt from the synched seed and size and cached. Identical on the
     * client and the server, which is what makes seed-only syncing work.
     */
    public List<MeteoroidShape.Voxel> voxels() {
        int key = this.getSeed() * 31 + this.getSize();
        if (this.cachedVoxels == null || this.cachedVoxelKey != key) {
            this.cachedVoxels = MeteoroidShape.build(this.getSeed(), this.getSize());
            this.cachedVoxelKey = key;
        }
        return this.cachedVoxels;
    }

    /** How many voxels are still attached at the current mass fraction. */
    public int survivingVoxelCount() {
        return MeteoroidShape.survivingVoxels(voxels().size(), getMassFraction());
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        this.setRot(this.getYRot() + this.spinYaw, this.getXRot() + this.spinPitch);

        if (this.level() instanceof ServerLevel level) {
            serverTick(level);
        } else {
            // Dead-reckon between the per-tick position updates. Collision is resolved server-side
            // by an explicit sweep, so running vanilla's resolution here would only risk snagging a
            // body that is covering several blocks a tick.
            advance();
            spawnTrail();
        }
    }

    /**
     * Advances the body along its velocity without vanilla collision resolution. The server has
     * already swept the path with {@code clip} and handled any hit, and a meteoroid should never
     * slide along or step up a surface - it either misses or it lands.
     */
    private void advance() {
        Vec3 movement = this.getDeltaMovement();
        this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
    }

    private void serverTick(ServerLevel level) {
        if (this.profile == null) this.profile = AtmosphereProfile.of(level);
        MeteoroidClass type = getMeteoroidClass();

        MeteorPhysics.Step step = MeteorPhysics.step(
                this.physics.withAltitude(this.profile.altitudeOf(this.getY())), type, this.profile, this.entryMass);
        this.physics = step.state();

        // A body too small (or too fragmented) to break into pieces flattens and sheds mass
        // instead - real pancaking, and it keeps a breakup from cascading into an entity storm.
        if (step.breakup() && !canFragment()) {
            this.physics = this.physics.withMass(this.physics.mass() * (1.0 - PANCAKE_MASS_LOSS));
        }

        this.entityData.set(DATA_MASS_FRACTION, (float) Mth.clamp(this.physics.mass() / this.entryMass, 0.0, 1.0));
        this.entityData.set(DATA_GLOW, (float) Mth.clamp(step.ablatedMass() / (this.entryMass * GLOW_SATURATION), 0.0, 1.0));

        if (step.burnedOut()) {
            burnUp(level);
            return;
        }
        if (step.breakup() && canFragment()) {
            fragment(level, type);
            return;
        }

        this.setDeltaMovement(
                MeteorPhysics.toBlocksPerTick(this.physics.vx()),
                MeteorPhysics.toBlocksPerTick(this.physics.vy()),
                MeteorPhysics.toBlocksPerTick(this.physics.vz()));

        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

        hurtEntitiesAlong(level, from, end);

        if (blockHit.getType() != HitResult.Type.MISS) {
            MeteorImpact.strike(level, blockHit.getLocation(), this.physics, type, this.getSeed(), voxels().size(), survivingVoxelCount());
            this.discard();
            return;
        }

        advance();

        if (this.getY() < level.getMinBuildHeight() - 32) {
            this.discard();
        }
    }

    private boolean canFragment() {
        return this.generation < MAX_GENERATION
                && this.physics.mass() > FRAGMENT_MASS_THRESHOLD
                && Galacticraft.CONFIG.meteorFragmentation();
    }

    /**
     * Splits the body into a handful of fragments flying slightly apart. Each carries a share of
     * the mass, and their much worse mass-to-area ratio is what turns a breakup into an airburst.
     */
    private void fragment(ServerLevel level, MeteoroidClass type) {
        int count = 2 + this.random.nextInt(3);
        double childMass = this.physics.mass() / count;
        int childSize = Math.max(MeteoroidShape.MIN_SIZE, this.getSize() - 2);

        for (int i = 0; i < count; i++) {
            FallingMeteorEntity child = new FallingMeteorEntity(GCEntityTypes.FALLING_METEOR, level);
            child.setPos(this.getX(), this.getY(), this.getZ());
            child.initialise(type, childSize, this.random.nextInt(), new Vec3(
                    this.physics.vx() + (this.random.nextDouble() - 0.5) * 200.0,
                    this.physics.vy() + (this.random.nextDouble() - 0.5) * 100.0,
                    this.physics.vz() + (this.random.nextDouble() - 0.5) * 200.0));
            child.setMass(childMass);
            child.setGeneration((byte) (this.generation + 1));
            level.addFreshEntity(child);
        }

        airburst(level);
        this.discard();
    }

    /** The flash and thump of a body coming apart under its own ram pressure. */
    private void airburst(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 40, 3.0, 3.0, 3.0, 0.02);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 6.0f, 0.5f);
    }

    /** The last of the body ablating away, high in the sky. */
    private void burnUp(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 30, 1.5, 1.5, 1.5, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 20, 2.0, 2.0, 2.0, 0.01);
        this.discard();
    }

    /** Anything caught in the swept path takes a direct hit. */
    private void hurtEntitiesAlong(ServerLevel level, Vec3 from, Vec3 to) {
        if (!MeteorImpactRules.blockDamageEnabled(level)) return;
        double radius = MeteoroidShape.radiusFor(getSize());
        AABB sweep = new AABB(from, to).inflate(radius);
        List<Entity> hit = level.getEntities(this, sweep, EntitySelector.NO_SPECTATORS.and(Entity::isAlive));
        if (hit.isEmpty()) return;

        float damage = (float) (10.0 + getSize() * 5.0);
        for (Entity entity : hit) {
            if (entity instanceof FallingMeteorEntity) continue;
            entity.hurt(level.damageSources().source(METEOR_STRIKE, this), damage);
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 60));
        }
    }

    private void spawnTrail() {
        float glow = getGlow();
        if (glow <= 0.02f) return;

        int count = 1 + Mth.floor(glow * 4.0f);
        double radius = MeteoroidShape.radiusFor(getSize());
        for (int i = 0; i < count; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * radius * 2.0;
            double offsetY = (this.random.nextDouble() - 0.5) * radius * 2.0;
            double offsetZ = (this.random.nextDouble() - 0.5) * radius * 2.0;
            this.level().addParticle(glow > 0.5f ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ, 0.0, 0.0, 0.0);
        }
        this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float diameter = (float) (MeteoroidShape.radiusFor(getSize()) * 2.0);
        return EntityDimensions.scalable(diameter, diameter);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_SIZE.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    protected double getDefaultGravity() {
        // Gravity is integrated by MeteorPhysics in SI units; vanilla must not add its own.
        return 0.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SEED, 0);
        builder.define(DATA_SIZE, (byte) 3);
        builder.define(DATA_CLASS, MeteoroidClass.STONY.id());
        builder.define(DATA_MASS_FRACTION, 1.0f);
        builder.define(DATA_GLOW, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.entityData.set(DATA_SEED, compound.getInt("seed"));
        this.entityData.set(DATA_SIZE, (byte) Mth.clamp(compound.getByte("size"), MeteoroidShape.MIN_SIZE, MeteoroidShape.MAX_SIZE));
        this.entityData.set(DATA_CLASS, compound.getByte("meteoroid_class"));
        this.entryMass = Math.max(1.0, compound.getDouble("entry_mass"));
        this.generation = compound.getByte("generation");
        this.spinYaw = compound.getFloat("spin_yaw");
        this.spinPitch = compound.getFloat("spin_pitch");
        this.physics = new MeteoroidState(
                Math.max(0.0, compound.getDouble("mass")),
                compound.getDouble("vx"), compound.getDouble("vy"), compound.getDouble("vz"),
                0.0);
        this.entityData.set(DATA_MASS_FRACTION, (float) Mth.clamp(this.physics.mass() / this.entryMass, 0.0, 1.0));
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("seed", getSeed());
        compound.putByte("size", (byte) getSize());
        compound.putByte("meteoroid_class", getMeteoroidClass().id());
        compound.putDouble("entry_mass", this.entryMass);
        compound.putDouble("mass", this.physics.mass());
        compound.putDouble("vx", this.physics.vx());
        compound.putDouble("vy", this.physics.vy());
        compound.putDouble("vz", this.physics.vz());
        compound.putByte("generation", this.generation);
        compound.putFloat("spin_yaw", this.spinYaw);
        compound.putFloat("spin_pitch", this.spinPitch);
    }
}
