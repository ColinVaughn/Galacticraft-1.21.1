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

package dev.galacticraft.mod.content.block.special.fluidpipe;

import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.mod.api.pipe.FluidPipe;
import dev.galacticraft.mod.api.pipe.PipeNetwork;
import dev.galacticraft.mod.api.pipe.impl.PipeNetworkImpl;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

public abstract class PipeBlockEntity extends BlockEntity implements FluidPipe, Storage<FluidVariant> {
    public static final int DISPLAY_TIMEOUT_TICKS = 4;
    private static final String DISPLAYED_FLUID_KEY = "displayed_fluid";
    private @Nullable PipeNetwork network = null;
    private @Nullable MLFluidStack displayedFluid;
    private long displayedFluidExpiresAt;
    private final long maxTransferRate; // 1 bucket per second
    private final boolean[] connections = new boolean[6];

    public PipeBlockEntity(BlockEntityType<? extends PipeBlockEntity> type, BlockPos pos, BlockState state, long maxTransferRate) {
        super(type, pos, state);
        this.maxTransferRate = maxTransferRate;
    }

    @Override
    public void forceCreateNetwork() {
        this.createNetwork();
    }

    private void createNetwork() {
        assert this.network == null || this.network.markedForRemoval();
        if (!this.level.isClientSide) {
            this.network = new PipeNetworkImpl((ServerLevel) this.level, this.maxTransferRate, this.getBlockPos());
        }
    }

    @Override
    public void setNetwork(@Nullable PipeNetwork network) {
        if ((this.network == null || this.network.markedForRemoval()) && (network != null && !network.markedForRemoval())) {
            this.network = network;
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        } else {
            this.network = network;
        }
    }

    @Override
    public @Nullable PipeNetwork getNetwork() {
        return this.network;
    }

    @Override
    public Storage<FluidVariant> getInsertable() {
        if (this.network == null || this.network.markedForRemoval()) {
            this.createNetwork();
        }
        return this;
    }

    @Override
    public long getMaxTransferRate() {
        return this.maxTransferRate;
    }

    @Override
    public boolean[] getConnections() {
        return this.connections;
    }

    public @Nullable MLFluidStack getDisplayedFluid() {
        return this.displayedFluid;
    }

    public void setDisplayedFluid(@NotNull MLFluidStack fluid) {
        this.refreshDisplayedFluidTimeout();
        if (Objects.equals(this.displayedFluid, fluid)) return;
        this.displayedFluid = fluid;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void refreshDisplayedFluidTimeout() {
        if (this.level instanceof ServerLevel serverLevel) {
            this.displayedFluidExpiresAt = serverLevel.getGameTime() + DISPLAY_TIMEOUT_TICKS;
            serverLevel.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), DISPLAY_TIMEOUT_TICKS);
        }
    }

    public void clearDisplayedFluidIfExpired(@NotNull ServerLevel serverLevel) {
        if (this.displayedFluid == null) return;

        long remainingTicks = this.displayedFluidExpiresAt - serverLevel.getGameTime();
        if (remainingTicks > 0) {
            serverLevel.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), (int) remainingTicks);
            return;
        }

        this.displayedFluid = null;
        this.displayedFluidExpiresAt = 0;
        this.setChanged();
        serverLevel.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void updateConnection(BlockState state, BlockPos pos, BlockPos neighborPos, Direction direction) {
        if (this.network == null || this.network.markedForRemoval()) {
            this.createNetwork();
        }
        if (this.network != null) {
            this.network.updateConnection(pos, neighborPos, direction);
        }
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        if (this.network != null) {
            return this.network.insert(resource, Math.min(this.maxTransferRate, maxAmount), transaction);
        }

        return 0;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public boolean supportsInsertion() {
        return this.maxTransferRate > 0;
    }

    @Override
    public @NotNull Iterator<StorageView<FluidVariant>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.readConnectionNbt(nbt);
        this.displayedFluid = nbt.contains(DISPLAYED_FLUID_KEY)
                ? MLFluidStack.CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt.get(DISPLAYED_FLUID_KEY)).result().orElse(null)
                : null;

        if (this.level != null && this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        this.writeConnectionNbt(nbt);
    }

    private void writeDisplayedFluid(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        if (this.displayedFluid != null) {
            nbt.put(DISPLAYED_FLUID_KEY, MLFluidStack.CODEC.encodeStart(
                    registryLookup.createSerializationContext(NbtOps.INSTANCE), this.displayedFluid).getOrThrow());
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag nbt = this.saveWithoutMetadata(registryLookup);
        this.writeDisplayedFluid(nbt, registryLookup);
        return nbt;
    }
}
