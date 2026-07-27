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
import dev.galacticraft.mod.screen.OxygenSealerMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OxygenSealerBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int OXYGEN_INPUT_SLOT = 1;
    public static final int OXYGEN_TANK = 0;

    public static final long MAX_OXYGEN = FluidUtil.bucketsToDroplets(20);
    public static final int SEAL_CHECK_TIME = 20;

    private boolean isSealed = false;
    /**
     * Whether this sealer is in a position to hold a space: powered, gassed, its vent clear and not
     * switched off. One flag rather than one per reason, so that no path out of the tick can leave a
     * stale answer behind - a sealer that is not running must not go on holding a room.
     */
    private boolean canSeal = false;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                    ItemResourceSlot.builder(TransferType.PROCESSING) // todo: drop for decompressor?
                            .pos(31, 62)
                            .capacity(1)
                            .filter(ResourceFilters.canExtractFluid(Gases.OXYGEN))
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.OXYGEN_TANK))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    Galacticraft.CONFIG.oxygenSealerEnergyConsumptionRate() * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_INPUT)
                            .pos(31, 8)
                            .capacity(OxygenSealerBlockEntity.MAX_OXYGEN)
                            .filter(ResourceFilters.ofResource(Gases.OXYGEN))
            )
    );


    public OxygenSealerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.OXYGEN_SEALER, pos, state, SPEC);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            level.galacticraft$getSealerManager().addSealer(this);
        }
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
        // Check if the machine has enough energy
        if (!this.energyStorage().canExtract(Galacticraft.CONFIG.oxygenSealerEnergyConsumptionRate())) {
            this.canSeal = false;
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }

        // Check if the oxygen tank is empty
        if (this.fluidStorage().slot(OXYGEN_TANK).isEmpty()) {
            this.canSeal = false;
            return GCMachineStatuses.NOT_ENOUGH_OXYGEN;
        }

        if (!level.getBlockState(pos.offset(0, 1, 0)).isAir()) {
            this.canSeal = false;
            return GCMachineStatuses.BLOCKED;
        }
        this.canSeal = true;

        this.consumeEnergy();

        if (this.isSealed) {
            // A sealed room only needs topping up.
            this.consumeOxygen(Galacticraft.CONFIG.oxygenSealerOxygenConsumptionRate());
            return GCMachineStatuses.SEALED;
        }
        // Running without a room to hold means venting into the open, which as in Galacticraft Legacy
        // costs several times more than maintaining a seal - a leak should be worth finding.
        this.consumeOxygen(Galacticraft.CONFIG.oxygenSealerUnsealedOxygenConsumptionRate());
        return GCMachineStatuses.AREA_TOO_LARGE;
    }

    @Override
    protected void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickDisabled(level, pos, state, profiler);
        // Switched off, so it spends nothing - and must not go on holding a room for free either.
        this.canSeal = false;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            this.level.galacticraft$getSealerManager().removeSealer(this);
        }
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new OxygenSealerMenu(syncId, player, this);
    }

    private void consumeOxygen(long amount) {
        this.fluidStorage().slot(OXYGEN_TANK).extract(amount);
    }

    private void consumeEnergy() {
        this.energyStorage().extract(Galacticraft.CONFIG.oxygenSealerEnergyConsumptionRate());
    }

    public boolean isSealed() {
        return this.isSealed;
    }

    public void setSealed(boolean sealed) {
        this.isSealed = sealed;
    }

    /** {@return whether this sealer is running and so able to hold part of a space} */
    public boolean canSeal() {
        return this.canSeal;
    }

    public int getSealTickTime() {
        if (this.level == null) return 0;
        return SEAL_CHECK_TIME - (int) (this.level.getGameTime() % SEAL_CHECK_TIME);
    }
}
