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
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.storage.ContainerTransfer;
import dev.galacticraft.machinelib.impl.platform.MachineLibPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machinelib port of legacy Galacticraft's Cargo Loader. Pushes items from its internal
 * buffer into any adjacent container, prioritizing a cargo rocket docked on an adjacent pad.
 */
public class CargoLoaderBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int BUFFER_START = 1;
    public static final int BUFFER_SIZE = 14;
    public static final long TRANSFER_RATE = 16;
    public static final long ENERGY_USAGE = 30;

    public static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.builder()
                    .add(ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)))
                    .addGrid(TransferType.STORAGE, 34, 18, 7, 2),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    Galacticraft.CONFIG.machineEnergyStorageSize() / 120
            )
    );

    public CargoLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.CARGO_LOADER, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(world, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        if (!this.energyStorage().canExtract(ENERGY_USAGE)) return MachineStatuses.NOT_ENOUGH_ENERGY;

        CargoRocketEntity rocket = CargoRocketEntity.findDockedRocket(level, pos);
        if (rocket != null) {
            int remaining = (int) TRANSFER_RATE;
            int moved = 0;
            Container rocketInventory = rocket.getVehicleInventory();
            for (int slot = BUFFER_START; slot < BUFFER_START + BUFFER_SIZE && remaining > 0; slot++) {
                ItemStack stack = this.itemStorage().getItem(slot);
                if (stack.isEmpty()) continue;
                int inserted = ContainerTransfer.insert(rocketInventory, 0, rocketInventory.getContainerSize(),
                        stack, remaining);
                if (inserted > 0) {
                    this.itemStorage().slot(slot).extract(inserted);
                    remaining -= inserted;
                    moved += inserted;
                }
            }
            if (moved > 0) {
                this.energyStorage().extract(ENERGY_USAGE);
                return MachineStatuses.ACTIVE;
            }
        }

        long before = this.itemStorage().getModifications();
        for (Direction direction : Direction.values()) {
            MachineLibPlatform.spreadItems(level, pos, direction, this.itemStorage());
        }

        if (this.itemStorage().getModifications() != before) {
            this.energyStorage().extract(ENERGY_USAGE);
            return MachineStatuses.ACTIVE;
        }
        return MachineStatuses.IDLE;
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new MachineMenu<>(GCMenuTypes.CARGO_LOADER, syncId, player, this);
    }
}
