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
import dev.galacticraft.machinelib.impl.platform.MachineLibPlatform;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.GasProcessingMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

public class GasLiquefierBlockEntity extends GasProcessingBlockEntity {
    public static final int CHARGE_SLOT = 0, GAS_INPUT_SLOT = 1, LIQUID_OUTPUT_SLOT = 2, SECOND_OUTPUT_SLOT = 3;
    public static final int GAS_TANK = 0, LIQUID_TANK = 1, SECOND_TANK = 2;
    public static final long ENERGY_PER_TICK = 60;
    private static final Fluid[] GASES = { Gases.METHANE, Gases.OXYGEN, Gases.NITROGEN, GCFluids.ARGON };
    private static final Fluid[] LIQUIDS = { GCFluids.FUEL, GCFluids.LIQUID_OXYGEN, GCFluids.LIQUID_NITROGEN, GCFluids.LIQUID_ARGON };
    private int airProducts;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(34, 50).capacity(1).filter(ResourceFilters.CAN_EXTRACT_ENERGY),
                    ItemResourceSlot.builder(TransferType.PROCESSING).pos(7, 7).capacity(1).filter((item, components) -> {
                        if (item == GCItems.ATMOSPHERIC_VALVE) return true;
                        for (Fluid gas : GASES) if (ResourceFilters.canExtractFluid(gas).test(item, components)) return true;
                        return ResourceFilters.canExtractFluid(GCFluids.LIQUID_OXYGEN).test(item, components)
                                || ResourceFilters.canExtractFluid(GCFluids.LIQUID_NITROGEN).test(item, components);
                    }),
                    outputSlot(132), outputSlot(153)
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT).pos(7, 28).height(38).unmarked().capacity(FluidUtil.bucketsToDroplets(4)).filter((fluid, components) -> fluid == GCFluids.ATMOSPHERIC_GASES || gasId(fluid) >= 0),
                    liquidTank(132), liquidTank(153)
            )
    );

    private static ItemResourceSlot.Spec outputSlot(int x) {
        return ItemResourceSlot.builder(TransferType.PROCESSING).pos(x, 7).capacity(1).filter((item, components) -> {
            if (item == GCItems.ATMOSPHERIC_VALVE) return true;
            for (Fluid fluid : LIQUIDS) if (ResourceFilters.canInsertFluid(fluid).test(item, components)) return true;
            return false;
        });
    }

    private static FluidResourceSlot.Spec liquidTank(int x) {
        return FluidResourceSlot.builder(TransferType.OUTPUT).pos(x, 28).height(38).unmarked().capacity(FluidUtil.bucketsToDroplets(2)).filter((fluid, components) -> {
            for (Fluid liquid : LIQUIDS) if (fluid == liquid) return true;
            return false;
        });
    }

    public GasLiquefierBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.GAS_LIQUEFIER, pos, state, SPEC, ENERGY_PER_TICK, 10);
    }

    public static int gasId(Fluid fluid) {
        for (int i = 0; i < GASES.length; i++) if (fluid == GASES[i]) return i;
        return -1;
    }

    @Override
    protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        airProducts = 0;
        int index = 0;
        for (var gas : atmosphere(level)) {
            airProducts |= (gasId(BuiltInRegistries.FLUID.get(gas)) + 1) << (index++ * 4);
        }
        var tank = fluidStorage().slot(GAS_TANK);
        if (airProducts == 0 && tank.getResource() == GCFluids.ATMOSPHERIC_GASES) tank.extract(tank.getAmount());
        if (itemStorage().slot(GAS_INPUT_SLOT).getResource() == GCItems.ATMOSPHERIC_VALVE) {
            if (airProducts > 0 && level.isEmptyBlock(pos.above())) tank.insert(GCFluids.ATMOSPHERIC_GASES, 4 * MILLIBUCKET);
        } else {
            takeFluidFromSlot(GAS_INPUT_SLOT, GAS_TANK);
            takeCryogenicContainer(GCFluids.LIQUID_OXYGEN, Gases.OXYGEN);
            takeCryogenicContainer(GCFluids.LIQUID_NITROGEN, Gases.NITROGEN);
        }
        drainOrVent(LIQUID_OUTPUT_SLOT, LIQUID_TANK);
        drainOrVent(SECOND_OUTPUT_SLOT, SECOND_TANK);
    }

    private void takeCryogenicContainer(Fluid liquid, Fluid gas) {
        var tank = fluidStorage().slot(GAS_TANK);
        if (!tank.isEmpty() && tank.getResource() != gas) return;
        long space = (tank.getCapacity() - tank.getAmount()) / 2;
        if (space == 0) return;
        var buffer = FluidResourceSlot.create(TransferType.INTERNAL, null, space, ResourceFilters.ofResource(liquid));
        MachineLibPlatform.takeFluidFromItem(itemStorage(), GAS_INPUT_SLOT, buffer, (int) space, liquid);
        tank.insert(gas, buffer.getAmount() * 2);
    }

    private long productSpace(int product) {
        if (product < 0 || product >= LIQUIDS.length) return 0;
        Fluid liquid = LIQUIDS[product];
        return fluidStorage().slot(SECOND_TANK).tryInsert(liquid, Long.MAX_VALUE)
                + fluidStorage().slot(LIQUID_TANK).tryInsert(liquid, Long.MAX_VALUE);
    }

    @Override
    public boolean canProcess() {
        if (isManuallyDisabled() || fluidStorage().slot(GAS_TANK).isEmpty()) return false;
        if (fluidStorage().slot(GAS_TANK).getResource() == GCFluids.ATMOSPHERIC_GASES) {
            for (int products = airProducts; products > 0; products >>= 4)
                if (productSpace((products & 15) - 1) > 0) return true;
            return false;
        }
        return productSpace(gasId(fluidStorage().slot(GAS_TANK).getResource())) > 0;
    }

    private long placeIntoFluidTanks(int product, long amount) {
        if (product < 0 || product >= LIQUIDS.length) return 0;
        var second = fluidStorage().slot(SECOND_TANK);
        // GC4 always fills its second output first, without splitting a batch across tanks.
        if (second.tryInsert(LIQUIDS[product], amount) > 0) return second.insert(LIQUIDS[product], amount);
        return fluidStorage().slot(LIQUID_TANK).insert(LIQUIDS[product], amount);
    }

    @Override
    public void doLiquefaction() {
        if (!canProcess()) return;
        var gas = fluidStorage().slot(GAS_TANK);
        long amount = gas.getAmount();
        if (gas.getResource() == GCFluids.ATMOSPHERIC_GASES) {
            long batch = Math.min(amount / (2 * MILLIBUCKET), airProducts > 15 ? 2 : 3);
            batch = Math.max(1, batch);
            for (int products = airProducts; products > 0; products >>= 4) {
                int product = (products & 15) - 1;
                if (product >= 0) gas.extract(placeIntoFluidTanks(product, batch * MILLIBUCKET) * 2);
                batch = Math.max(1, batch >> 1);
            }
        } else if (amount <= MILLIBUCKET) {
            gas.extract(placeIntoFluidTanks(gasId(gas.getResource()), amount));
        } else {
            gas.extract(placeIntoFluidTanks(gasId(gas.getResource()), Math.min(amount / (2 * MILLIBUCKET), 3) * MILLIBUCKET) * 2);
        }
        setChanged();
    }

    @Override protected MachineStatus workingStatus() { return GCMachineStatuses.LIQUEFYING; }
    @Override protected MachineStatus blockedStatus() {
        return fluidStorage().slot(GAS_TANK).isEmpty() ? GCMachineStatuses.MISSING_GAS : MachineStatuses.OUTPUT_FULL;
    }

    @Override
    public MachineMenu<?> createMenu(int syncId, Inventory inv, Player player) {
        return new GasProcessingMenu<>(GCMenuTypes.GAS_LIQUEFIER, syncId, player, this);
    }
}
