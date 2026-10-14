/* Copyright (c) 2026 Team Galacticraft. GC4 processing attribution: art/gc4-gas-machines.md. */
package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.*;
import dev.galacticraft.machinelib.api.storage.slot.*;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.GasProcessingMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class MethaneSynthesizerBlockEntity extends GasProcessingBlockEntity {
    public static final int CHARGE_SLOT = 0, HYDROGEN_INPUT_SLOT = 1, CO2_INPUT_SLOT = 2, CARBON_SLOT = 3, METHANE_OUTPUT_SLOT = 4;
    public static final int HYDROGEN_TANK = 0, CO2_TANK = 1, METHANE_TANK = 2;
    public static final long ENERGY_PER_TICK = 45;
    private int coalPartial;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(53, 53).capacity(1).filter(ResourceFilters.CAN_EXTRACT_ENERGY),
                    ItemResourceSlot.builder(TransferType.PROCESSING).pos(7, 7).capacity(1).filter(ResourceFilters.canExtractFluid(Gases.HYDROGEN)),
                    ItemResourceSlot.builder(TransferType.PROCESSING).pos(28, 7).capacity(1).filter(ResourceFilters.ofResource(GCItems.ATMOSPHERIC_VALVE)),
                    ItemResourceSlot.builder(TransferType.INPUT).pos(28, 53).filter(ResourceFilters.ofResource(GCItems.CARBON_FRAGMENTS)),
                    ItemResourceSlot.builder(TransferType.PROCESSING).pos(153, 7).capacity(1).filter((item, components) -> item == GCItems.ATMOSPHERIC_VALVE || ResourceFilters.canInsertFluid(Gases.METHANE).test(item, components))
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT).pos(7, 28).height(38).unmarked().capacity(FluidUtil.bucketsToDroplets(4)).filter(ResourceFilters.ofResource(Gases.HYDROGEN)),
                    FluidResourceSlot.builder(TransferType.INTERNAL).pos(28, 28).height(20).unmarked().capacity(FluidUtil.bucketsToDroplets(2)).filter(ResourceFilters.ofResource(Gases.CARBON_DIOXIDE)),
                    FluidResourceSlot.builder(TransferType.OUTPUT).pos(153, 28).height(38).unmarked().capacity(FluidUtil.bucketsToDroplets(2)).filter(ResourceFilters.ofResource(Gases.METHANE))
            )
    );

    public MethaneSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.METHANE_SYNTHESIZER, pos, state, SPEC, ENERGY_PER_TICK, 8);
    }

    @Override
    protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        takeFluidFromSlot(HYDROGEN_INPUT_SLOT, HYDROGEN_TANK, Gases.HYDROGEN);
        boolean hasCO2 = level.dimension() != Level.OVERWORLD && atmosphere(level).contains(Gases.CARBON_DIOXIDE_ID);
        var co2 = fluidStorage().slot(CO2_TANK);
        if (!hasCO2) co2.extract(co2.getAmount());
        if (hasCO2 && itemStorage().slot(CO2_INPUT_SLOT).getResource() == GCItems.ATMOSPHERIC_VALVE
                && level.isEmptyBlock(pos.above()) && !level.isBreathable(pos.above())) {
            co2.insert(Gases.CARBON_DIOXIDE, 4 * MILLIBUCKET);
        }
        drainOrVent(METHANE_OUTPUT_SLOT, METHANE_TANK);
    }

    @Override
    public boolean canProcess() {
        return !isManuallyDisabled() && !fluidStorage().slot(HYDROGEN_TANK).isEmpty()
                && (coalPartial > 0 || itemStorage().slot(CARBON_SLOT).getResource() == GCItems.CARBON_FRAGMENTS
                    || !fluidStorage().slot(CO2_TANK).isEmpty())
                && !fluidStorage().slot(METHANE_TANK).isFull();
    }

    @Override
    public void doLiquefaction() {
        if (!canProcess()) return;
        if (coalPartial == 0 && itemStorage().slot(CARBON_SLOT).getResource() != GCItems.CARBON_FRAGMENTS) {
            if (fluidStorage().slot(CO2_TANK).extract(MILLIBUCKET) < MILLIBUCKET) return;
        } else {
            if (coalPartial == 0) itemStorage().slot(CARBON_SLOT).extractOne();
            coalPartial = (coalPartial + 1) % 40;
        }
        // GC4 makes up to 2 mB even from the final partial hydrogen input.
        long methane = fluidStorage().slot(METHANE_TANK).insert(Gases.METHANE, 2 * MILLIBUCKET);
        fluidStorage().slot(HYDROGEN_TANK).extract(methane * 8);
        setChanged();
    }

    public int getCoalPartial() { return coalPartial; }

    @Override protected MachineStatus workingStatus() { return GCMachineStatuses.SYNTHESIZING; }
    @Override protected MachineStatus blockedStatus() {
        if (fluidStorage().slot(HYDROGEN_TANK).isEmpty()) return GCMachineStatuses.MISSING_GAS;
        if (fluidStorage().slot(METHANE_TANK).isFull()) return MachineStatuses.OUTPUT_FULL;
        return GCMachineStatuses.MISSING_CARBON;
    }

    @Override
    public MachineMenu<?> createMenu(int syncId, Inventory inv, Player player) {
        return new GasProcessingMenu<>(GCMenuTypes.METHANE_SYNTHESIZER, syncId, player, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("coalPartial", coalPartial);
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        coalPartial = Math.max(0, Math.min(39, tag.getInt("coalPartial")));
    }
}
