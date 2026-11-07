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
import dev.galacticraft.api.gas.Gases;
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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Services a Cloud Diver parked beside it: tops up the diver's battery and cabin oxygen from the dock's own
 * stores, and patches the hull with Aerostat Salvage once it has taken real damage.
 */
public class VehicleDockBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int CHARGE_SLOT = 0;
    public static final int SALVAGE_SLOT = 1;
    public static final int OXYGEN_TANK = 0;
    public static final int SERVICE_INTERVAL = 10;
    public static final int ENERGY_PER_SERVICE = 2000;
    public static final int REPAIR_PER_SALVAGE = 30;
    /** A hull this damaged or worse is worth spending salvage on. */
    public static final int REPAIR_THRESHOLD = 270;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(8, 62).capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.INPUT).pos(31, 62)
                            .filter((item, components) -> item == JupiterItems.AEROSTAT_SALVAGE)
            ),
            MachineEnergyStorage.spec(100_000, 4_000, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.INPUT).pos(31, 17).height(42).capacity(FluidUtil.bucketsToDroplets(16))
                            .filter(ResourceFilters.ofResource(Gases.OXYGEN))
            )
    );
    private @Nullable CloudDiverEntity docked;
    private boolean servicing;

    public VehicleDockBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.VEHICLE_DOCK, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        if ((level.getGameTime() + pos.asLong()) % SERVICE_INTERVAL == 0) {
            this.docked = level.getEntitiesOfClass(CloudDiverEntity.class, new AABB(pos).inflate(3, 4, 3), diver -> diver.isAlive() && diver.hull() > 0)
                    .stream().min(Comparator.comparingDouble(diver -> diver.distanceToSqr(pos.getCenter()))).orElse(null);
            if (this.docked != null) this.docked.dockWith(pos);
            this.servicing = this.docked != null && this.service(this.docked);
        }
        if (this.docked == null || this.docked.isRemoved()) {
            this.docked = null;
            return JupiterMachineStatuses.NO_VEHICLE;
        }
        if (this.energyStorage().isEmpty() && this.fluidStorage().slot(OXYGEN_TANK).isEmpty()) return MachineStatuses.NOT_ENOUGH_ENERGY;
        return this.servicing ? JupiterMachineStatuses.SERVICING : JupiterMachineStatuses.VEHICLE_READY;
    }

    /** One service pass; returns whether anything was transferred. */
    private boolean service(CloudDiverEntity diver) {
        boolean worked = false;
        int energy = (int) Math.min(ENERGY_PER_SERVICE, this.energyStorage().getAmount());
        int accepted = diver.acceptEnergy(energy);
        if (accepted > 0) {
            this.energyStorage().extract(accepted);
            worked = true;
        }
        var tank = this.fluidStorage().slot(OXYGEN_TANK);
        int oxygen = (int) Math.min(FluidUtil.bucketsToDroplets(1) / 8, tank.getAmount());
        int taken = diver.acceptOxygen(oxygen);
        if (taken > 0) {
            tank.extract(taken);
            worked = true;
        }
        var salvage = this.itemStorage().slot(SALVAGE_SLOT);
        if (diver.hull() <= REPAIR_THRESHOLD && !salvage.isEmpty()) {
            salvage.consume(1);
            diver.repair(REPAIR_PER_SALVAGE);
            worked = true;
        }
        return worked;
    }

    @Override
    public List<Integer> readout() {
        CloudDiverEntity diver = this.docked;
        if (diver == null || diver.isRemoved()) return List.of(0, 0, 0, 0);
        return List.of(1, diver.hull(), Math.round(diver.energy() * 100F / CloudDiverEntity.ENERGY_CAPACITY), Math.round(diver.oxygen() * 100F / CloudDiverEntity.OXYGEN_CAPACITY));
    }

    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.VEHICLE_DOCK, syncId, player, this);
    }
}
