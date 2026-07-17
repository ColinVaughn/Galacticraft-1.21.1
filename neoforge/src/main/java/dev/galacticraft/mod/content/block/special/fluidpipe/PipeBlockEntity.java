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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class PipeBlockEntity extends BlockEntity implements FluidPipe, IFluidHandler {
    public static final int DISPLAY_TIMEOUT_TICKS = 4;
    private static final String DISPLAYED_FLUID_KEY = "displayed_fluid";
    private @Nullable PipeNetwork network;
    private @Nullable MLFluidStack displayedFluid;
    private long displayedFluidExpiresAt;
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
    public @Nullable MLFluidStack getDisplayedFluid() { return displayedFluid; }
    public void setDisplayedFluid(@NotNull MLFluidStack fluid) {
        refreshDisplayedFluidTimeout();
        if (Objects.equals(displayedFluid, fluid)) return;
        displayedFluid = fluid;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    private void refreshDisplayedFluidTimeout() {
        if (level instanceof ServerLevel serverLevel) {
            displayedFluidExpiresAt = serverLevel.getGameTime() + DISPLAY_TIMEOUT_TICKS;
            serverLevel.scheduleTick(getBlockPos(), getBlockState().getBlock(), DISPLAY_TIMEOUT_TICKS);
        }
    }
    public void clearDisplayedFluidIfExpired(@NotNull ServerLevel serverLevel) {
        if (displayedFluid == null) return;
        long remainingTicks = displayedFluidExpiresAt - serverLevel.getGameTime();
        if (remainingTicks > 0) {
            serverLevel.scheduleTick(getBlockPos(), getBlockState().getBlock(), (int) remainingTicks);
            return;
        }
        displayedFluid = null;
        displayedFluidExpiresAt = 0;
        setChanged();
        serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
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
        displayedFluid = tag.contains(DISPLAYED_FLUID_KEY)
                ? MLFluidStack.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag.get(DISPLAYED_FLUID_KEY)).result().orElse(null)
                : null;
        if (level != null && level.isClientSide) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        writeConnectionNbt(tag);
    }
    private void writeDisplayedFluid(CompoundTag tag, HolderLookup.Provider lookup) {
        if (displayedFluid != null) {
            tag.put(DISPLAYED_FLUID_KEY, MLFluidStack.CODEC.encodeStart(
                    lookup.createSerializationContext(NbtOps.INSTANCE), displayedFluid).getOrThrow());
        }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = saveWithoutMetadata(lookup);
        writeDisplayedFluid(tag, lookup);
        return tag;
    }
}
