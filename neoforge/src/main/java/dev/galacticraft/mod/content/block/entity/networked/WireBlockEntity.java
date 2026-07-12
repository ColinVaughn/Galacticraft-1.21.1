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

package dev.galacticraft.mod.content.block.entity.networked;

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.wire.Wire;
import dev.galacticraft.mod.api.wire.WireNetwork;
import dev.galacticraft.mod.api.wire.impl.WireNetworkImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** NeoForge wire block entity and native energy capability endpoint. */
public class WireBlockEntity extends BlockEntity implements Wire, IEnergyStorage {
    @Nullable private WireNetwork network;
    private final int maxTransferRate;
    private final boolean[] connections = new boolean[6];

    public WireBlockEntity(BlockEntityType<? extends WireBlockEntity> type, BlockPos pos, BlockState state, int maxTransferRate) {
        super(type, pos, state);
        this.maxTransferRate = maxTransferRate;
    }

    public static WireBlockEntity createT1(BlockEntityType<? extends WireBlockEntity> type, BlockPos pos, BlockState state) {
        return new WireBlockEntity(type, pos, state, (int) Galacticraft.CONFIG.wireTransferLimit());
    }

    public static WireBlockEntity createT2(BlockEntityType<? extends WireBlockEntity> type, BlockPos pos, BlockState state) {
        return new WireBlockEntity(type, pos, state, (int) Galacticraft.CONFIG.heavyWireTransferLimit());
    }

    @Override public void forceCreateNetwork() { this.createNetwork(); }

    public void createNetwork() {
        if (this.level instanceof ServerLevel serverLevel) {
            this.network = new WireNetworkImpl(serverLevel, this.maxTransferRate, this.getBlockPos());
        }
    }

    @Override
    public void setNetwork(@Nullable WireNetwork network) {
        if ((this.network == null || this.network.markedForRemoval()) && network != null && !network.markedForRemoval()) {
            this.network = network;
            if (this.level != null) this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        } else {
            this.network = network;
        }
    }

    @Override @Nullable public WireNetwork getNetwork() { return this.network; }

    @Override
    public IEnergyStorage getInsertable() {
        if (this.network == null || this.network.markedForRemoval()) this.createNetwork();
        return this;
    }

    @Override public int getMaxTransferRate() { return this.maxTransferRate; }
    @Override public boolean[] getConnections() { return this.connections; }

    @Override
    public void updateConnection(BlockState state, BlockPos pos, BlockPos neighborPos, Direction direction) {
        if (this.network == null || this.network.markedForRemoval()) this.createNetwork();
        if (this.network != null) this.network.updateConnection(pos, neighborPos, direction);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        this.writeConnectionNbt(nbt);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.readConnectionNbt(nbt);
        if (this.level != null && this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) { return this.saveWithoutMetadata(registryLookup); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        if (this.network == null || this.network.markedForRemoval()) this.createNetwork();
        return this.network == null ? 0 : (int) this.network.insert(Math.min(this.maxTransferRate, maxReceive), simulate);
    }

    @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return 0; }
    @Override public int getMaxEnergyStored() { return this.maxTransferRate; }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return this.maxTransferRate > 0; }
}
