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

import dev.galacticraft.mod.api.pipe.FluidPipe;
import dev.galacticraft.mod.api.pipe.PipeNetwork;
import dev.galacticraft.mod.api.pipe.impl.PipeNetworkImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PipeBlockEntity extends BlockEntity implements FluidPipe, IFluidHandler {
    private @Nullable PipeNetwork network;
    private final int maxTransferRate;
    private final boolean[] connections = new boolean[6];

    protected PipeBlockEntity(BlockEntityType<? extends PipeBlockEntity> type, BlockPos pos,
                              BlockState state, int maxTransferRate) {
        super(type, pos, state);
        this.maxTransferRate = maxTransferRate;
    }

    @Override public void forceCreateNetwork() { createNetwork(); }
    private void createNetwork() {
        if (level instanceof ServerLevel server) network = new PipeNetworkImpl(server, maxTransferRate, getBlockPos());
    }
    @Override public void setNetwork(@Nullable PipeNetwork value) { network = value; }
    @Override public @Nullable PipeNetwork getNetwork() { return network; }
    @Override public IFluidHandler getInsertable() {
        if (network == null || network.markedForRemoval()) createNetwork();
        return this;
    }
    @Override public int getMaxTransferRate() { return maxTransferRate; }
    @Override public boolean[] getConnections() { return connections; }
    @Override public void updateConnection(BlockState state, BlockPos pos, BlockPos neighbor, Direction direction) {
        if (network == null || network.markedForRemoval()) createNetwork();
        if (network != null) network.updateConnection(pos, neighbor, direction);
    }

    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) { return maxTransferRate; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && !stack.isEmpty(); }
    @Override public int fill(FluidStack stack, FluidAction action) {
        if (stack.isEmpty()) return 0;
        PipeNetwork current = getInsertableNetwork();
        return current == null ? 0 : current.insert(stack.copyWithAmount(Math.min(stack.getAmount(), maxTransferRate)), action);
    }
    private @Nullable PipeNetwork getInsertableNetwork() {
        getInsertable();
        return network;
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
    @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        readConnectionNbt(tag);
        if (level != null && level.isClientSide) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        writeConnectionNbt(tag);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
}
