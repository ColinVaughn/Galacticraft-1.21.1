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

package dev.galacticraft.mod.content.block.jupiter.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.MachineBlock;
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
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Spins condensate apart into water and hydrosulfide crystals. Cloud condensate gives 250 mB of water and a 25%
 * chance of a crystal; charged condensate gives 500 mB, a 50% chance, and discharges {@link #CHARGE_REFUND} energy
 * back into the machine as it breaks down.
 */
public class CondensateSeparatorBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int CHARGE_SLOT = 0;
    public static final int INPUT_SLOT = 1;
    public static final int OUTPUT_SLOTS = 2;
    public static final int WATER_TANK = 0;
    public static final int PROCESS_TICKS = 100;
    public static final int ENERGY_PER_TICK = 25;
    public static final int CHARGE_REFUND = 2000;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(8, 62).capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.INPUT).pos(40, 26)
                            .filter((item, components) -> item == JupiterItems.CLOUD_CONDENSATE || item == JupiterItems.CHARGED_CONDENSATE),
                    ItemResourceSlot.builder(TransferType.OUTPUT).pos(96, 17),
                    ItemResourceSlot.builder(TransferType.OUTPUT).pos(96, 39)
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_OUTPUT).pos(152, 17).capacity(FluidUtil.bucketsToDroplets(8))
                            .filter(ResourceFilters.ofResource(Fluids.WATER))
            )
    );
    private final FluidSource fluidSource = new FluidSource(this);
    private int progress;

    public CondensateSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.CONDENSATE_SEPARATOR, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        this.fluidSource.trySpreadFluids(level, pos, state);
        var input = this.itemStorage().slot(INPUT_SLOT);
        if (input.isEmpty()) {
            this.progress = 0;
            return JupiterMachineStatuses.NO_CONDENSATE;
        }
        boolean charged = input.getResource() == JupiterItems.CHARGED_CONDENSATE;
        long water = FluidUtil.bucketsToDroplets(1) / (charged ? 2 : 4);
        var tank = this.fluidStorage().slot(WATER_TANK);
        if (!tank.canInsert(Fluids.WATER, water) || this.byproductSlot(JupiterItems.HYDROSULFIDE_CRYSTAL) < 0) return JupiterMachineStatuses.OUTPUT_FULL;
        if (!this.energyStorage().canExtract(ENERGY_PER_TICK)) return MachineStatuses.NOT_ENOUGH_ENERGY;
        this.energyStorage().extract(ENERGY_PER_TICK);
        if (++this.progress >= PROCESS_TICKS) {
            this.progress = 0;
            input.consume(1);
            tank.insert(Fluids.WATER, water);
            if (level.random.nextFloat() < (charged ? 0.5F : 0.25F)) {
                this.itemStorage().slot(this.byproductSlot(JupiterItems.HYDROSULFIDE_CRYSTAL)).insert(JupiterItems.HYDROSULFIDE_CRYSTAL, 1);
            }
            if (charged) this.energyStorage().insert(CHARGE_REFUND);
        }
        return JupiterMachineStatuses.SEPARATING;
    }

    /** The first output slot that can take one more of {@code item}, or -1. */
    private int byproductSlot(Item item) {
        for (int i = OUTPUT_SLOTS; i < OUTPUT_SLOTS + 2; i++) if (this.itemStorage().slot(i).canInsert(item, 1)) return i;
        return -1;
    }

    @Override
    public List<Integer> readout() {
        return List.of(this.progress, PROCESS_TICKS);
    }

    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", this.progress);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.progress = tag.getInt("Progress");
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.CONDENSATE_SEPARATOR, syncId, player, this);
    }
}
