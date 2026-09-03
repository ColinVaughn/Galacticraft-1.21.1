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

package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.PowerPortMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class CannonBlockEntity extends MachineBlockEntity {
    public static final int RANGE = 256;
    public static final long TRACKING_ENERGY_PER_TICK = 5;
    public static final long SHOT_ENERGY = 1_000;
    private static final int ACQUIRE_INTERVAL = 5;
    private static final int COOLDOWN_TICKS = 60;
    private static final int BASE_LOCK_TICKS = 40;
    private static final int MIN_LOCK_TICKS = 10;
    private static final int IMPACT_SAFETY_TICKS = 15;
    private static final double SHOT_SPEED = 24.0;
    private static final double DETECTION_RANGE_SQUARED = (double) RadarBlockEntity.DETECTION_RANGE
            * RadarBlockEntity.DETECTION_RANGE;
    private static final StorageSpec SPEC = StorageSpec.of(MachineEnergyStorage.spec(
            Galacticraft.CONFIG.machineEnergyStorageSize(), 100, 0));
    private static final Map<ServerLevel, Set<BlockPos>> LOADED_CANNONS = new WeakHashMap<>();

    private @Nullable UUID targetId;
    private @Nullable Vec3 targetPosition;
    private int targetEntityId = -1;
    private int lockTicks;
    private int requiredLockTicks = MIN_LOCK_TICKS;
    private int cooldown;
    private @Nullable Vec3 shotPosition;
    private int shotTicks;
    private boolean operational;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.CANNON, pos, state, SPEC);
    }

    @Override
    protected MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        LOADED_CANNONS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
        if (this.cooldown > 0) this.cooldown--;
        if (this.shotPosition != null) {
            this.operational = true;
            this.tickShot(level);
            return MachineStatuses.ACTIVE;
        }

        if (!this.energyStorage().canExtract(TRACKING_ENERGY_PER_TICK)) {
            this.operational = false;
            this.clearTarget(level);
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }
        this.operational = true;

        FallingMeteorEntity target = this.targetId == null ? null
                : level.getEntity(this.targetId) instanceof FallingMeteorEntity meteor ? meteor : null;
        if (target == null || !target.isAlive()
                || target.distanceToSqr(Vec3.atCenterOf(pos)) > DETECTION_RANGE_SQUARED) {
            this.clearTarget(level);
            if (level.getGameTime() % ACQUIRE_INTERVAL != Math.floorMod(pos.asLong(), ACQUIRE_INTERVAL)) {
                return MachineStatuses.IDLE;
            }
            RadarBlockEntity.MeteorTrack track = RadarBlockEntity.findTarget(level, pos,
                    RadarBlockEntity.DETECTION_RANGE);
            if (track == null) return MachineStatuses.IDLE;
            target = level.getEntity(track.meteorId()) instanceof FallingMeteorEntity meteor ? meteor : null;
            if (target == null || target.distanceToSqr(Vec3.atCenterOf(pos)) > DETECTION_RANGE_SQUARED) {
                return MachineStatuses.IDLE;
            }
            this.energyStorage().extractExact(TRACKING_ENERGY_PER_TICK);
            this.acquire(level, target, track.uncertaintyRadius(), track.ticksToImpact());
            return MachineStatuses.ACTIVE;
        }

        this.energyStorage().extractExact(TRACKING_ENERGY_PER_TICK);
        this.targetPosition = target.position();
        this.lockTicks++;
        if (this.lockTicks % 20 == 0) this.sync(level);
        if (this.lockTicks % 5 == 0) this.charge(level, target);
        boolean clearShot = this.hasClearShot(level, target);
        if (readyToFire(target.distanceToSqr(Vec3.atCenterOf(pos)), clearShot, this.lockTicks,
                this.requiredLockTicks, this.cooldown, this.energyStorage().canExtract(SHOT_ENERGY))) {
            this.energyStorage().extractExact(SHOT_ENERGY);
            this.beginShot(level, target);
        }
        return MachineStatuses.ACTIVE;
    }

    @Override
    protected void tickDisabled(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        this.operational = false;
        this.clearTarget(level);
    }

    static boolean readyToFire(double distanceSqr, boolean clearShot, int lockTicks, int requiredLockTicks,
                               int cooldown, boolean hasShotEnergy) {
        return distanceSqr <= RANGE * RANGE && clearShot && lockTicks >= requiredLockTicks && cooldown == 0
                && hasShotEnergy;
    }

    static int requiredLockTicks(int uncertaintyRadius, int ticksToImpact) {
        int desired = BASE_LOCK_TICKS + uncertaintyRadius / 2;
        return Math.min(desired, Math.max(MIN_LOCK_TICKS, ticksToImpact - IMPACT_SAFETY_TICKS));
    }

    static Vec3 advanceShot(Vec3 from, Vec3 target) {
        Vec3 path = target.subtract(from);
        double distance = path.length();
        return distance <= SHOT_SPEED ? target : from.add(path.scale(SHOT_SPEED / distance));
    }

    private void acquire(ServerLevel level, FallingMeteorEntity target, int uncertaintyRadius, int ticksToImpact) {
        this.targetId = target.getUUID();
        this.targetEntityId = target.getId();
        this.targetPosition = target.position();
        this.lockTicks = 0;
        this.requiredLockTicks = requiredLockTicks(uncertaintyRadius, ticksToImpact);
        sync(level);
    }

    private boolean hasClearShot(ServerLevel level, FallingMeteorEntity target) {
        Vec3 muzzle = muzzle(target.position());
        return level.clip(new ClipContext(muzzle, target.position(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
    }

    private Vec3 muzzle(Vec3 target) {
        Vec3 pivot = Vec3.atBottomCenterOf(this.worldPosition).add(0.0, 2.0, 0.0);
        return pivot.add(target.subtract(pivot).normalize().scale(3.0));
    }

    private void charge(ServerLevel level, FallingMeteorEntity target) {
        Vec3 muzzle = muzzle(target.position());
        particles(level, ParticleTypes.ELECTRIC_SPARK, muzzle, 4, 0.18, 0.03);
        particles(level, ParticleTypes.SMOKE, muzzle, 1, 0.08, 0.01);
    }

    private void beginShot(ServerLevel level, FallingMeteorEntity target) {
        this.shotPosition = muzzle(target.position());
        this.shotTicks = 0;
        particles(level, ParticleTypes.FLASH, this.shotPosition, 1, 0.0, 0.0);
        particles(level, ParticleTypes.LARGE_SMOKE, this.shotPosition, 16, 0.35, 0.04);
        particles(level, ParticleTypes.FLAME, this.shotPosition, 8, 0.2, 0.06);
        level.playSound(null, this.shotPosition.x, this.shotPosition.y, this.shotPosition.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0f, 1.5f);
    }

    private void tickShot(ServerLevel level) {
        FallingMeteorEntity target = this.targetId == null ? null
                : level.getEntity(this.targetId) instanceof FallingMeteorEntity meteor ? meteor : null;
        if (target == null || !target.isAlive()) {
            clearTarget(level);
            return;
        }

        Vec3 from = this.shotPosition;
        Vec3 destination = target.position();
        Vec3 next = advanceShot(from, destination);
        for (int i = 1; i <= 4; i++) {
            particles(level, ParticleTypes.END_ROD, from.lerp(next, i / 4.0), 1, 0.0, 0.0);
        }
        particles(level, ParticleTypes.ELECTRIC_SPARK, next, 3, 0.1, 0.02);
        particles(level, ParticleTypes.SMOKE, from, 1, 0.04, 0.0);
        this.shotPosition = next;
        this.shotTicks++;
        if (next.distanceToSqr(destination) < 0.01 || this.shotTicks >= 40) impact(level, target);
    }

    private void impact(ServerLevel level, FallingMeteorEntity target) {
        Vec3 impact = target.position();
        particles(level, ParticleTypes.FLASH, impact, 1, 0.0, 0.0);
        particles(level, ParticleTypes.EXPLOSION_EMITTER, impact, 1, 0.0, 0.0);
        particles(level, ParticleTypes.LARGE_SMOKE, impact, 40, 1.5, 0.08);
        particles(level, ParticleTypes.FLAME, impact, 30, 1.0, 0.12);
        particles(level, ParticleTypes.ELECTRIC_SPARK, impact, 24, 1.25, 0.15);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.WEATHER, 8.0f, 0.75f);
        target.discard();
        this.cooldown = COOLDOWN_TICKS;
        this.shotPosition = null;
        this.shotTicks = 0;
        clearTarget(level);
    }

    private static void particles(ServerLevel level, ParticleOptions particle, Vec3 pos, int count,
                                  double spread, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, count,
                    spread, spread, spread, speed);
        }
    }

    private void clearTarget(ServerLevel level) {
        if (this.targetId == null && this.targetPosition == null) return;
        this.targetId = null;
        this.targetPosition = null;
        this.targetEntityId = -1;
        this.lockTicks = 0;
        this.shotPosition = null;
        this.shotTicks = 0;
        sync(level);
    }

    private void sync(ServerLevel level) {
        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
    }

    public int getTargetEntityId() {
        return this.targetEntityId;
    }

    public @Nullable Vec3 getTargetPosition() {
        return this.targetPosition;
    }

    public static int countLinked(ServerLevel level, BlockPos radarPos) {
        return (int) LOADED_CANNONS.getOrDefault(level, Set.of()).stream()
                .filter(pos -> pos.distSqr(radarPos) <= RadarBlockEntity.LINK_RANGE * RadarBlockEntity.LINK_RANGE)
                .map(level::getBlockEntity)
                .filter(CannonBlockEntity.class::isInstance)
                .map(CannonBlockEntity.class::cast)
                .filter(cannon -> cannon.operational)
                .count();
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            Set<BlockPos> cannons = LOADED_CANNONS.get(serverLevel);
            if (cannons != null) cannons.remove(this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("target_entity", this.targetEntityId);
        if (this.targetPosition != null) {
            tag.putDouble("target_x", this.targetPosition.x);
            tag.putDouble("target_y", this.targetPosition.y);
            tag.putDouble("target_z", this.targetPosition.z);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.targetEntityId = tag.getInt("target_entity");
        this.targetPosition = tag.contains("target_x")
                ? new Vec3(tag.getDouble("target_x"), tag.getDouble("target_y"), tag.getDouble("target_z")) : null;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory,
                                                                          Player player) {
        return new PowerPortMenu<>(GCMenuTypes.CANNON, syncId, player, this);
    }

    @Override
    public void populateUpdateTag(CompoundTag tag) {
        super.populateUpdateTag(tag);
        tag.putInt("target_entity", this.targetEntityId);
        if (this.targetPosition != null) {
            tag.putDouble("target_x", this.targetPosition.x);
            tag.putDouble("target_y", this.targetPosition.y);
            tag.putDouble("target_z", this.targetPosition.z);
        }
    }
}
