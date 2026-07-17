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
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineFluidStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machinelib port of legacy Galacticraft's bulk Fluid Tank. Stores a large amount of any single
 * fluid and exposes it to adjacent fluid pipes (via {@code FluidStorage.SIDED}). The two item slots
 * let players fill the tank from a full fluid container and drain it into an empty one.
 */
public class FluidTankBlockEntity extends MachineBlockEntity {
    public static final int FILL_SLOT = 0;
    public static final int DRAIN_SLOT = 1;
    public static final int FLUID_TANK = 0;
    public static final long CAPACITY = FluidUtil.bucketsToDroplets(50);

    public static final StorageSpec SPEC = new StorageSpec(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(43, 33)
                            .capacity(1)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.BUCKET)),
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(115, 33)
                            .capacity(1)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.BUCKET))
            ),
            null,
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STORAGE)
                            .hidden()
                            .capacity(FluidTankBlockEntity.CAPACITY)
            )
    );
    private final FluidSource fluidSource = new FluidSource(this);

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.FLUID_TANK, pos, state, SPEC);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        // Fill the tank from a full fluid container placed in the fill slot.
        this.takeFluidFromSlot(FILL_SLOT, FLUID_TANK);

        // Drain the tank into an empty fluid container placed in the drain slot.
        this.drainFluidToSlot(DRAIN_SLOT, FLUID_TANK);

        // Push stored fluid through every side configured as a fluid output.
        this.fluidSource.trySpreadFluids(level, pos, state);

        return MachineStatuses.ACTIVE;
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new MachineMenu<>(GCMenuTypes.FLUID_TANK, syncId, player, this);
    }
}
