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
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A named navigation point. While powered it broadcasts on the dimension's beacon network, which Cloud Diver pilots
 * see as a bearing on their HUD; its screen lists the nearest other beacons. Name it in an anvil before placing.
 */
public class SurveyBeaconBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int CHARGE_SLOT = 0;
    public static final int ENERGY_PER_TICK = 5;
    public static final int LISTED = 5;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(8, 62).capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 4, 0)
    );
    private @Nullable String beaconName;

    public SurveyBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.SURVEY_BEACON, pos, state, SPEC);
    }

    public String beaconName() {
        return this.beaconName != null ? this.beaconName : "Beacon " + this.worldPosition.getX() + ", " + this.worldPosition.getZ();
    }

    public void setBeaconName(String name) {
        this.beaconName = name.isBlank() ? null : name.strip();
        this.setChanged();
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        boolean powered = this.energyStorage().canExtract(ENERGY_PER_TICK);
        if (powered) this.energyStorage().extract(ENERGY_PER_TICK);
        SurveyBeacons.get(level).update(pos, this.beaconName(), powered);
        return powered ? JupiterMachineStatuses.BROADCASTING : MachineStatuses.NOT_ENOUGH_ENERGY;
    }

    @Override
    protected void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickDisabled(level, pos, state, profiler);
        SurveyBeacons.get(level).update(pos, this.beaconName(), false);
    }

    @Override
    public List<Integer> readout() {
        return List.of(this.level instanceof ServerLevel server ? SurveyBeacons.get(server).nearest(this.worldPosition.getCenter(), this.worldPosition, Integer.MAX_VALUE).size() : 0);
    }

    /** The beacon's own name, then the nearest other broadcasting beacons as "name|distance|bearing". */
    @Override
    public List<String> readoutLines() {
        List<String> lines = new ArrayList<>();
        lines.add(this.beaconName());
        if (this.level instanceof ServerLevel server) {
            var from = this.worldPosition.getCenter();
            for (SurveyBeacons.Beacon beacon : SurveyBeacons.get(server).nearest(from, this.worldPosition, LISTED)) {
                lines.add(beacon.name() + "|" + SurveyBeacons.distance(from, beacon.pos()) + "|" + SurveyBeacons.bearing(from, beacon.pos()));
            }
        }
        return lines;
    }

    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.beaconName != null) tag.putString("BeaconName", this.beaconName);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.beaconName = tag.contains("BeaconName") ? tag.getString("BeaconName") : null;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.SURVEY_BEACON, syncId, player, this);
    }
}
