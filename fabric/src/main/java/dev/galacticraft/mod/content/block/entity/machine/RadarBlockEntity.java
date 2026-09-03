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
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.screen.RadarMenu;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class RadarBlockEntity extends MachineBlockEntity {
    public static final int DETECTION_RANGE = 1024;
    public static final int LINK_RANGE = 64;
    public static final int MAX_TRACKS = 4;
    public static final long ENERGY_PER_TICK = 10;
    private static final int SCAN_INTERVAL = 10;
    private static final StorageSpec SPEC = StorageSpec.of(MachineEnergyStorage.spec(
            Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0));
    private static final Map<ServerLevel, Set<BlockPos>> LOADED_RADARS = new WeakHashMap<>();

    private List<MeteorTrack> tracks = List.of();
    private int linkedRadarCount = 1;
    private int linkedCannonCount;
    private boolean operational;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.RADAR, pos, state, SPEC);
    }

    @Override
    protected MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        LOADED_RADARS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
        if (!this.energyStorage().extractExact(ENERGY_PER_TICK)) {
            this.operational = false;
            this.tracks = List.of();
            this.linkedRadarCount = 0;
            this.linkedCannonCount = 0;
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }

        this.operational = true;
        if (level.getGameTime() % SCAN_INTERVAL == Math.floorMod(pos.asLong(), SCAN_INTERVAL)) {
            this.scan(level);
        }
        return MachineStatuses.ACTIVE;
    }

    @Override
    protected void tickDisabled(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        this.operational = false;
        this.tracks = List.of();
        this.linkedRadarCount = 0;
        this.linkedCannonCount = 0;
    }

    private void scan(ServerLevel level) {
        this.linkedRadarCount = (int) LOADED_RADARS.getOrDefault(level, Set.of()).stream()
                .filter(pos -> pos.distSqr(this.worldPosition) <= LINK_RANGE * LINK_RANGE)
                .map(level::getBlockEntity)
                .filter(RadarBlockEntity.class::isInstance)
                .map(RadarBlockEntity.class::cast)
                .filter(RadarBlockEntity::isOperational)
                .count();
        this.linkedCannonCount = CannonBlockEntity.countLinked(level, this.worldPosition);
        int uncertainty = RadarTrackingLogic.uncertaintyRadius(this.linkedRadarCount);
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        this.tracks = level.getEntities(GCEntityTypes.FALLING_METEOR,
                        meteor -> meteor.distanceToSqr(center) <= (double) DETECTION_RANGE * DETECTION_RANGE)
                .stream()
                .map(meteor -> predict(level, meteor, uncertainty))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(MeteorTrack::ticksToImpact))
                .limit(MAX_TRACKS)
                .toList();
    }

    private static @Nullable MeteorTrack predict(ServerLevel level, FallingMeteorEntity meteor, int uncertainty) {
        Vec3 position = meteor.position();
        Vec3 velocity = meteor.getDeltaMovement();
        if (velocity.y >= -0.001 || velocity.lengthSqr() < 0.001) return null;

        double ticksToBottom = (level.getMinBuildHeight() - 32.0 - position.y) / velocity.y;
        Vec3 end = position.add(velocity.scale(ticksToBottom));
        BlockHitResult hit = level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, meteor));
        if (hit.getType() == HitResult.Type.MISS) return null;

        int ticks = Math.max(1, (int) Math.ceil(hit.getLocation().distanceTo(position) / velocity.length()));
        BlockPos impact = new BlockPos(
                RadarTrackingLogic.estimatedCoordinate(hit.getLocation().x, uncertainty),
                hit.getBlockPos().getY(),
                RadarTrackingLogic.estimatedCoordinate(hit.getLocation().z, uncertainty));
        return new MeteorTrack(meteor.getUUID(), position, velocity, impact, ticks, uncertainty,
                meteor.getMeteoroidClass(), meteor.getSize());
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            Set<BlockPos> radars = LOADED_RADARS.get(serverLevel);
            if (radars != null) radars.remove(this.worldPosition);
        }
        super.setRemoved();
    }

    public List<MeteorTrack> getTracks() {
        return this.tracks;
    }

    public static @Nullable MeteorTrack findTarget(ServerLevel level, BlockPos origin, int targetRange) {
        Vec3 center = Vec3.atCenterOf(origin);
        return LOADED_RADARS.getOrDefault(level, Set.of()).stream()
                .filter(pos -> pos.distSqr(origin) <= LINK_RANGE * LINK_RANGE)
                .map(level::getBlockEntity)
                .filter(RadarBlockEntity.class::isInstance)
                .map(RadarBlockEntity.class::cast)
                .filter(RadarBlockEntity::isOperational)
                .flatMap(radar -> radar.tracks.stream())
                .filter(track -> track.position().distanceToSqr(center) <= (double) targetRange * targetRange)
                .min(Comparator.comparingInt(MeteorTrack::ticksToImpact))
                .orElse(null);
    }

    public int getLinkedRadarCount() {
        return this.linkedRadarCount;
    }

    public int getLinkedCannonCount() {
        return this.linkedCannonCount;
    }

    public boolean isOperational() {
        return this.operational;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory,
                                                                          Player player) {
        return new RadarMenu(syncId, inventory, this);
    }

    public record MeteorTrack(UUID meteorId, Vec3 position, Vec3 velocity, BlockPos estimatedImpact,
                              int ticksToImpact, int uncertaintyRadius, MeteoroidClass type, int size) {
    }
}
