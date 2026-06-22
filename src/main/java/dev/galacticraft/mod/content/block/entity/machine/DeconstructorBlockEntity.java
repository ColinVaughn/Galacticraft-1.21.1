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
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.GCMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A machinelib port of legacy Galacticraft's Deconstructor. Reverse-engineers a bounded set of
 * Galacticraft compressed metals back into their base ingots, consuming energy per operation.
 */
public class DeconstructorBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int INPUT_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final long ENERGY_USAGE = 75;

    /** Bounded reverse-engineering table: compressed metal -> the ingots it was pressed from. */
    private static final Map<Item, ItemStack> RESULTS = new HashMap<>();

    static {
        RESULTS.put(GCItems.COMPRESSED_TIN, new ItemStack(GCItems.TIN_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_ALUMINUM, new ItemStack(GCItems.ALUMINUM_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_METEORIC_IRON, new ItemStack(GCItems.METEORIC_IRON_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_DESH, new ItemStack(GCItems.DESH_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_TITANIUM, new ItemStack(GCItems.TITANIUM_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_IRON, new ItemStack(Items.IRON_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_STEEL, new ItemStack(Items.IRON_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_COPPER, new ItemStack(Items.COPPER_INGOT, 2));
        RESULTS.put(GCItems.COMPRESSED_BRONZE, new ItemStack(Items.COPPER_INGOT, 2));
    }

    public static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.INPUT)
                            .pos(52, 35),
                    ItemResourceSlot.builder(TransferType.OUTPUT)
                            .pos(113, 35)
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    Galacticraft.CONFIG.machineEnergyStorageSize() / 120,
                    0
            )
    );

    public DeconstructorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.DECONSTRUCTOR, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(world, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        ItemResourceSlot input = this.itemStorage().slot(INPUT_SLOT);
        ItemResourceSlot output = this.itemStorage().slot(OUTPUT_SLOT);

        if (input.isEmpty()) return MachineStatuses.IDLE;
        ItemStack result = RESULTS.get(input.getResource());
        if (result == null) return MachineStatuses.IDLE;

        if (!this.energyStorage().canExtract(ENERGY_USAGE)) return MachineStatuses.NOT_ENOUGH_ENERGY;
        if (!output.canInsert(result.getItem(), result.getCount())) return MachineStatuses.OUTPUT_FULL;

        this.energyStorage().extract(ENERGY_USAGE);
        input.extract(1);
        output.insert(result.getItem(), result.getCount());
        return MachineStatuses.ACTIVE;
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new MachineMenu<>(GCMenuTypes.DECONSTRUCTOR, syncId, player, this);
    }
}
