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

package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineFluidStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.network.s2c.BubbleSizePayload;
import dev.galacticraft.mod.network.s2c.BubbleUpdatePayload;
import dev.galacticraft.mod.screen.OxygenBubbleDistributorMenu;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.util.SphericalVolumeDiff;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OxygenBubbleDistributorBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int OXYGEN_INPUT_SLOT = 1;
    public static final int OXYGEN_TANK = 0;
    public static final long MAX_OXYGEN = FluidUtil.bucketsToDroplets(50);

    private boolean bubbleVisible = true;
    private double size = 0;
    private int targetSize = 1;
    private double lastSyncedSize = Double.NaN;
    private long ticksSinceSync = 0;
    private int appliedBubbleRadiusSquared = -1;
    private boolean oxygenWorld = true;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 72)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.PROCESSING)
                            .pos(31, 72)
                            .capacity(1)
                            .filter(ResourceFilters.canExtractFluid(Gases.OXYGEN))
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.OXYGEN_TANK))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate() * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_INPUT)
                            .pos(31, 17)
                            .capacity(OxygenBubbleDistributorBlockEntity.MAX_OXYGEN)
                            .filter(ResourceFilters.ofResource(Gases.OXYGEN))
            )
    );

    public OxygenBubbleDistributorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.OXYGEN_BUBBLE_DISTRIBUTOR, pos, state, SPEC);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.oxygenWorld = level.getDefaultBreathable();
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        profiler.push("extract_resources");
        this.chargeFromSlot(CHARGE_SLOT);
        this.takeFluidFromSlot(OXYGEN_INPUT_SLOT, OXYGEN_TANK, Gases.OXYGEN);
        profiler.pop();
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("transaction");
        MachineStatus status;
        ticksSinceSync++;
        try {
            if (this.energyStorage().canExtract(Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate())) { //todo: config
                profiler.push("bubble");
                if (this.size > this.targetSize)
                    this.setSize(Math.max(this.size - 0.1, this.targetSize));

                profiler.pop();

                profiler.push("bubbler_distributor_transfer");
                long oxygenRequired = Math.max((long) ((4.0 / 3.0) * Math.PI * this.size * this.size * this.size), 1L);
                FluidResourceSlot slot = this.fluidStorage().slot(OXYGEN_TANK);

                if (slot.canExtract(oxygenRequired)) {
                    slot.extract(oxygenRequired);
                    this.energyStorage().extract(Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate());
                    if (this.size < this.targetSize)
                        this.setSize(this.size + 0.1);

                    this.updateBreathableArea(this.size);
                    this.trySyncSize(level, pos, profiler);
                    profiler.pop();
                    return GCMachineStatuses.DISTRIBUTING;
                }
                else {
                    status = GCMachineStatuses.NOT_ENOUGH_OXYGEN;
                    profiler.pop();
                }
            }
            else
                status = MachineStatuses.NOT_ENOUGH_ENERGY;
        }
        finally {
            profiler.pop();
        }
        profiler.push("size");

        if (this.size > 0.0)
            this.setSize(this.size - 0.2);

        this.updateBreathableArea(this.size);
        this.trySyncSize(level, pos, profiler);
        profiler.pop();
        return status;
    }

    @Override
    protected void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        if (this.size > 0.0) {
            this.setSize(0.0);
            this.updateBreathableArea(this.size);
        }
        this.trySyncSize(level, pos, profiler);

        super.tickDisabled(level, pos, state, profiler);
    }

    @Override
    public void onRemove() { this.updateBreathableArea(0.0); }

    private void trySyncSize(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull ProfilerFiller profiler) {
        if (this.ticksSinceSync >= 10 && this.lastSyncedSize != this.size) {
            this.ticksSinceSync = 0;
            this.lastSyncedSize = this.size;
            profiler.push("network");
            for (ServerPlayer player : level.players())
                NetworkManager.sendToPlayer(player, new BubbleSizePayload(pos, this.size));
            profiler.pop();
        }
    }

    public void updateBreathableArea(double size) {
        if(this.oxygenWorld) return;

        int bubbleRadiusSquared = size == 0 ? -1 : (int) (size * size);
        if (bubbleRadiusSquared == this.appliedBubbleRadiusSquared) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos origin = this.getBlockPos().above();
        SphericalVolumeDiff.forEach(this.appliedBubbleRadiusSquared, bubbleRadiusSquared, (x, y, z, added) ->
                this.level.setBreathable(pos.setWithOffset(origin, x, y, z), added));
        this.appliedBubbleRadiusSquared = bubbleRadiusSquared;
    }

    public void setTargetSize(int targetSize) { this.targetSize = targetSize; }

    public int getTargetSize() {
        return this.targetSize;
    }

    public double getSize() {
        return this.size;
    }

    public void setSize(double size) { this.size = Math.max(0.0, size); }

    public boolean isBubbleVisible() { return this.bubbleVisible; }

    public void setBubbleVisible(boolean bubbleVisible) {
        this.bubbleVisible = bubbleVisible;
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new OxygenBubbleDistributorMenu(syncId, player, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt(Constant.Nbt.MAX_SIZE, this.targetSize);
        tag.putDouble(Constant.Nbt.SIZE, this.size);
        tag.putBoolean(Constant.Nbt.VISIBLE, this.bubbleVisible);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.setSize(tag.getDouble(Constant.Nbt.SIZE));
        this.targetSize = tag.getInt(Constant.Nbt.MAX_SIZE);
        this.bubbleVisible = tag.getBoolean(Constant.Nbt.VISIBLE);
    }

    @Override
    public @NotNull CustomPacketPayload createUpdatePayload() {
        return new BubbleUpdatePayload(this.getBlockPos(), this.targetSize, this.size, this.bubbleVisible);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registryLookup);
        this.populateUpdateTag(tag);
        return tag;
    }
}
