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

package dev.galacticraft.mod.api.pipe.impl;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.block.FluidPipeBlock;
import dev.galacticraft.mod.api.pipe.FluidPipe;
import dev.galacticraft.mod.api.pipe.PipeNetwork;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.mod.content.block.special.fluidpipe.PipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native NeoForge pipe graph with the same per-tick, single-fluid throughput rules as Fabric. */
public final class PipeNetworkImpl implements PipeNetwork {
    private final ServerLevel level;
    private final Map<BlockPos, IFluidHandler @Nullable []> pipes = new LinkedHashMap<>();
    private final int maxTransferRate;
    private boolean activeTransaction;
    private boolean markedForRemoval;
    private int tickId;
    private int transferred;
    private FluidStack currentFluid = FluidStack.EMPTY;

    public PipeNetworkImpl(ServerLevel level, int maxTransferRate, BlockPos pos) {
        this.level = level;
        this.maxTransferRate = maxTransferRate;
        this.tickId = level.getServer().getTickCount();
        addPipe(pos, null);
    }

    private void addPipe(BlockPos pos, @Nullable FluidPipe supplied) {
        if (markedForRemoval || pipes.containsKey(pos)) return;
        FluidPipe pipe = supplied;
        if (pipe == null && level.getBlockEntity(pos) instanceof FluidPipe found) pipe = found;
        if (pipe == null || !isCompatibleWith(pipe)) return;

        PipeNetwork old = pipe.getNetwork();
        if (old != null && old != this && !old.markedForRemoval()) old.markForRemoval();
        pipe.setNetwork(this);
        pipes.put(pos.immutable(), null);

        FluidPipeBlock pipeBlock = (FluidPipeBlock) level.getBlockState(pos).getBlock();
        for (Direction direction : Constant.Misc.DIRECTIONS) {
            if (!pipe.canConnect(direction)) continue;
            BlockPos adjacentPos = pos.relative(direction);
            BlockEntity adjacentEntity = level.getBlockEntity(adjacentPos);
            if (adjacentEntity instanceof FluidPipe adjacent) {
                if (level.getBlockState(adjacentPos).getBlock() instanceof FluidPipeBlock adjacentBlock
                        && pipeBlock.color.canConnectTo(adjacentBlock.color)
                        && adjacent.canConnect(direction.getOpposite()) && isCompatibleWith(adjacent)) {
                    addPipe(adjacentPos, adjacent);
                }
                continue;
            }
            setExternal(pos, direction, findExternal(adjacentPos, direction.getOpposite()));
        }
    }

    private @Nullable IFluidHandler findExternal(BlockPos pos, Direction side) {
        return Capabilities.FluidHandler.BLOCK.getCapability(level, pos, null, null, side);
    }

    private void setExternal(BlockPos pipePos, Direction direction, @Nullable IFluidHandler handler) {
        IFluidHandler[] handlers = pipes.get(pipePos);
        if (handler != null && handlers == null) {
            handlers = new IFluidHandler[6];
            pipes.put(pipePos, handlers);
        }
        if (handlers != null) handlers[direction.get3DDataValue()] = handler;
    }

    @Override
    public void updateConnection(BlockPos pipePos, BlockPos adjacentPos, Direction direction) {
        if (markedForRemoval || !pipes.containsKey(pipePos)) return;
        BlockEntity adjacentEntity = level.getBlockEntity(adjacentPos);
        if (adjacentEntity instanceof FluidPipe adjacent && isCompatibleWith(adjacent)) {
            addPipe(adjacentPos, adjacent);
        } else {
            setExternal(pipePos, direction, findExternal(adjacentPos, direction.getOpposite()));
        }
    }

    @Override
    public int insert(FluidStack resource, IFluidHandler.FluidAction action) {
        if (activeTransaction || resource.isEmpty()) return 0;
        int now = level.getServer().getTickCount();
        if (tickId != now) {
            tickId = now;
            transferred = 0;
            currentFluid = FluidStack.EMPTY;
        }
        if (!currentFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(currentFluid, resource)) return 0;
        int available = Math.min(resource.getAmount(), maxTransferRate - transferred);
        if (available <= 0) return 0;

        activeTransaction = true;
        try {
            Map<IFluidHandler, Integer> requests = new LinkedHashMap<>();
            for (IFluidHandler[] handlers : pipes.values()) {
                if (handlers == null) continue;
                for (IFluidHandler handler : handlers) {
                    if (handler == null) continue;
                    int accepted = handler.fill(resource.copyWithAmount(available), IFluidHandler.FluidAction.SIMULATE);
                    if (accepted > 0) {
                        requests.merge(handler, accepted, Math::max);
                    }
                }
            }
            int totalRequested = requests.values().stream().mapToInt(Integer::intValue).sum();
            if (totalRequested == 0) return 0;
            if (action.simulate()) return Math.min(available, totalRequested);

            int inserted = 0;
            for (Map.Entry<IFluidHandler, Integer> entry : requests.entrySet()) {
                int share = Math.max(1, (int) ((long) entry.getValue() * available / totalRequested));
                share = Math.min(share, available - inserted);
                if (share <= 0) break;
                inserted += entry.getKey().fill(resource.copyWithAmount(share), IFluidHandler.FluidAction.EXECUTE);
            }
            if (inserted > 0) {
                currentFluid = resource.copyWithAmount(1);
                transferred += inserted;
                updateDisplayedFluid(new MLFluidStack(resource.getFluid(), resource.getComponentsPatch()));
            }
            return inserted;
        } finally {
            activeTransaction = false;
        }
    }

    private void updateDisplayedFluid(MLFluidStack fluid) {
        for (BlockPos pos : pipes.keySet()) {
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.setDisplayedFluid(fluid);
            }
        }
    }

    @Override public int getMaxTransferRate() { return maxTransferRate; }
    @Override public boolean markedForRemoval() { return markedForRemoval; }
    @Override public void markForRemoval() { markedForRemoval = true; }
    @Override public boolean isCompatibleWith(FluidPipe pipe) { return maxTransferRate == pipe.getMaxTransferRate(); }

    @Override
    public String toString() {
        return "PipeNetworkImpl{" + level.dimension().location() + ", pipes=" + pipes.size()
                + ", removed=" + markedForRemoval + ", transferred=" + transferred + '}';
    }
}
