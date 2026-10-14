/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.EnergySource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.GeothermalGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/** GC4's sulfur-spout validation and fluctuating generation, using modern energy transfer. */
public class GeothermalGeneratorBlockEntity extends MachineBlockEntity {
    public static final int MIN_GENERATION = 30;
    public static final int MAX_GENERATION = 200;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(ItemResourceSlot.builder(TransferType.PROCESSING)
                    .pos(152, 83).capacity(1).filter(ResourceFilters.CAN_INSERT_ENERGY)
                    .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), 0, MAX_GENERATION));

    private final EnergySource energySource = new EnergySource(this);
    private long ticks;
    private boolean validSpout;
    private boolean manuallyDisabled;
    private int disableCooldown;
    private int generation;

    public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.GEOTHERMAL_GENERATOR, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        // GC4 stores the previous tick's calculated output before sampling the next one.
        energyStorage().insert(generation);
        ticks++;
        if (ticks % 20 == 0) validSpout = hasValidSpoutBelow(level, pos);
        drainPowerToSlot(0);
        if (disableCooldown > 0) disableCooldown--;
        generation = validSpout && !manuallyDisabled && !isDisabled() ? generationAt(ticks) : 0;
        // Stored power can still charge batteries and leave the generator while generation is stopped.
        energySource.trySpreadEnergy(level, pos, state);
    }

    static int generationAt(long ticks) {
        return (int) Math.floor((Math.sin(ticks / 50.0F) * 0.5F + 0.5F)
                * (MAX_GENERATION - MIN_GENERATION) + MIN_GENERATION);
    }

    static boolean hasValidSpoutBelow(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).is(GCBlocks.VAPOR_SPOUT)) return false;
        for (int depth = 2; depth < 20; depth++) {
            BlockPos sample = pos.below(depth);
            if (sample.getY() < level.getMinBuildHeight()) return false;
            BlockState state = level.getBlockState(sample);
            if (state.is(GCBlocks.SULFURIC_ACID)) return true;
            if (!state.isAir()) return false;
        }
        return false;
    }

    @Override
    protected MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        if (manuallyDisabled) return MachineStatuses.IDLE;
        return validSpout ? GCMachineStatuses.GENERATING : GCMachineStatuses.INVALID_SPOUT;
    }

    public int getGeneration() { return generation; }
    public boolean hasValidSpout() { return validSpout; }
    public boolean isManuallyDisabled() { return manuallyDisabled; }
    public int getDisableCooldown() { return disableCooldown; }

    public void toggleDisabled() {
        if (disableCooldown == 0) {
            manuallyDisabled = !manuallyDisabled;
            disableCooldown = 20;
            setChanged();
        }
    }

    @Override
    public GeothermalGeneratorMenu createMenu(int id, Inventory inventory, Player player) {
        return new GeothermalGeneratorMenu(id, player, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putBoolean("disabled", manuallyDisabled);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        manuallyDisabled = tag.getBoolean("disabled");
        generation = 0;
        validSpout = false;
        ticks = 0;
        disableCooldown = 0;
    }
}
