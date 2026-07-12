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

package dev.galacticraft.mod.api.wire.impl;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.wire.Wire;
import dev.galacticraft.mod.api.wire.WireNetwork;
import dev.galacticraft.mod.content.block.entity.networked.WireBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native NeoForge implementation of Galacticraft's wire network. */
public class WireNetworkImpl implements WireNetwork {
    private final @NotNull ServerLevel level;
    private final @NotNull Object2ObjectOpenHashMap<BlockPos, IEnergyStorage @Nullable []> wires = new Object2ObjectOpenHashMap<>(1);
    private final long maxTransferRate;
    private boolean markedForRemoval;
    private boolean activeTransfer;
    private long tickId;
    private long transferred;

    public WireNetworkImpl(@NotNull ServerLevel level, long maxTransferRate, @NotNull BlockPos pos) {
        this.level = level;
        this.maxTransferRate = maxTransferRate;
        this.tickId = level.getServer().getTickCount();
        this.addWire(pos, null);
    }

    private static int saturate(long value) {
        return (int) Math.min(Math.max(value, 0), Integer.MAX_VALUE);
    }

    private @Nullable IEnergyStorage findStorage(BlockPos pos, Direction side) {
        return Capabilities.EnergyStorage.BLOCK.getCapability(this.level, pos, null, null, side);
    }

    private void addWire(@NotNull BlockPos pos, @Nullable Wire wire) {
        if (wire == null) wire = (Wire) this.level.getBlockEntity(pos);
        if (wire == null) throw new IllegalStateException("Attempted to add a wire that does not exist at " + pos);

        if (wire.getNetwork() != null && wire.getNetwork() != this && !wire.getNetwork().markedForRemoval()) {
            wire.getNetwork().markForRemoval();
            this.wires.putAll(((WireNetworkImpl) wire.getNetwork()).wires);
        }
        wire.setNetwork(this);
        this.wires.put(pos, null);

        for (Direction direction : Constant.Misc.DIRECTIONS) {
            if (!wire.canConnect(direction)) continue;
            BlockPos adjacentPos = pos.relative(direction);
            BlockEntity blockEntity = this.level.getBlockEntity(adjacentPos);
            if (blockEntity instanceof Wire adjacent && !blockEntity.isRemoved() && this.isCompatibleWith(adjacent)) {
                if (adjacent.getNetwork() != this && adjacent.canConnect(direction.getOpposite())) this.addWire(adjacentPos, adjacent);
                continue;
            }

            IEnergyStorage storage = this.findStorage(adjacentPos, direction.getOpposite());
            if (storage != null && storage.canReceive()) {
                if (this.wires.get(pos) == null) this.wires.put(pos, new IEnergyStorage[6]);
                Objects.requireNonNull(this.wires.get(pos))[direction.get3DDataValue()] = storage;
            }
        }
    }

    public void removeWire(@NotNull BlockPos removedPos) {
        if (!this.level.isLoaded(removedPos)) {
            Constant.LOGGER.debug("Removing wire from unloaded chunk, removing entire network");
            this.markForRemoval();
            return;
        }
        this.wires.remove(removedPos);
        if (this.wires.isEmpty()) {
            this.markForRemoval();
            return;
        }

        List<Wire> adjacent = new ArrayList<>(6);
        for (Direction direction : Constant.Misc.DIRECTIONS) {
            BlockPos adjacentWirePos = removedPos.relative(direction);
            if (this.wires.containsKey(adjacentWirePos)) {
                Wire wire = (Wire) Objects.requireNonNull(this.level.getBlockEntity(adjacentWirePos));
                if (wire.canConnect(direction.getOpposite())) adjacent.add(wire);
            }
        }
        if (adjacent.size() <= 1) return;

        this.markForRemoval();
        for (Wire wire : adjacent) {
            if (wire.getNetwork() == this) {
                wire.setNetwork(null);
                ((WireBlockEntity) wire).createNetwork();
            }
        }
    }

    @Override
    public void updateConnection(@NotNull BlockPos wirePos, @NotNull BlockPos adjacentPos, @NotNull Direction direction) {
        if (this.level.getBlockEntity(adjacentPos) instanceof Wire wire && this.isCompatibleWith(wire)) {
            if (!this.wires.containsKey(adjacentPos)) this.addWire(adjacentPos, wire);
            return;
        }

        if (this.wires.containsKey(adjacentPos)) this.removeWire(adjacentPos);
        IEnergyStorage storage = this.findStorage(adjacentPos, direction.getOpposite());
        if (storage != null && storage.canReceive()) {
            if (this.wires.get(wirePos) == null) this.wires.put(wirePos, new IEnergyStorage[6]);
            Objects.requireNonNull(this.wires.get(wirePos))[direction.get3DDataValue()] = storage;
        } else if (this.wires.get(wirePos) != null) {
            Objects.requireNonNull(this.wires.get(wirePos))[direction.get3DDataValue()] = null;
        }
    }

    @Override
    public long insert(long amount, boolean simulate) {
        if (amount <= 0 || this.activeTransfer) return 0;
        this.activeTransfer = true;
        try {
            if (this.tickId != this.level.getServer().getTickCount()) {
                this.tickId = this.level.getServer().getTickCount();
                this.transferred = 0;
            }

            long permitted = Math.min(amount, this.maxTransferRate - this.transferred);
            if (permitted <= 0) return 0;
            long totalRequested = 0;
            Object2LongMap<IEnergyStorage> requests = new Object2LongOpenHashMap<>();
            for (IEnergyStorage[] storages : this.wires.values()) {
                if (storages == null) continue;
                for (IEnergyStorage storage : storages) {
                    if (storage == null || !storage.canReceive()) continue;
                    long accepted = storage.receiveEnergy(saturate(permitted), true);
                    if (accepted > 0) {
                        totalRequested += accepted;
                        requests.put(storage, accepted);
                    }
                }
            }
            if (totalRequested == 0) return 0;

            double ratio = Math.min(1.0, (double) permitted / (double) totalRequested);
            long acceptedTotal = 0;
            for (Object2LongMap.Entry<IEnergyStorage> request : requests.object2LongEntrySet()) {
                int share = saturate((long) (request.getLongValue() * ratio));
                if (share > 0) acceptedTotal += simulate ? share : request.getKey().receiveEnergy(share, false);
            }
            if (!simulate) this.transferred += acceptedTotal;
            return acceptedTotal;
        } finally {
            this.activeTransfer = false;
        }
    }

    @Override public long getMaxTransferRate() { return this.maxTransferRate; }
    @Override public boolean markedForRemoval() { return this.markedForRemoval; }
    @Override public void markForRemoval() { this.markedForRemoval = true; }
    @Override public boolean isCompatibleWith(@NotNull Wire wire) { return this.maxTransferRate == wire.getMaxTransferRate(); }

    @VisibleForTesting
    @ApiStatus.Internal
    public @NotNull Object2ObjectOpenHashMap<BlockPos, IEnergyStorage[]> getWires() {
        return this.wires;
    }

    @Override
    public String toString() {
        return "WireNetworkImpl{" + "level=" + this.level.dimension().location() + ", wires=" + this.wires
                + ", markedForRemoval=" + this.markedForRemoval + ", maxTransferRate=" + this.maxTransferRate
                + ", tickId=" + this.tickId + ", transferred=" + this.transferred + '}';
    }
}
