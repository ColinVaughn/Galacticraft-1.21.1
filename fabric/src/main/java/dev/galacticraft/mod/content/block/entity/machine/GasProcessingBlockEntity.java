/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

/** GC4's countdown, valve venting and tank transfer adapted to MachineLib. */
public abstract class GasProcessingBlockEntity extends MachineBlockEntity {
    public static final long MILLIBUCKET = FluidUtil.bucketsToDroplets(1) / 1000;
    public static final int PROCESS_TIME = 3;
    private final long energyPerTick;
    private final int idleTicks;
    private final FluidSource fluidSource = new FluidSource(this);
    private List<ResourceLocation> atmosphere;
    protected int processTicks;
    private boolean manuallyDisabled;
    private int disableCooldown;

    protected GasProcessingBlockEntity(BlockEntityType<? extends MachineBlockEntity> type, BlockPos pos, BlockState state,
                                       StorageSpec spec, long energyPerTick, int idleTicks) {
        super(type, pos, state, spec);
        this.energyPerTick = energyPerTick;
        this.idleTicks = idleTicks;
        this.processTicks = -idleTicks;
    }

    protected final List<ResourceLocation> atmosphere(ServerLevel level) {
        if (atmosphere == null) {
            var body = level.galacticraft$getCelestialBody();
            if (level.dimension() == Level.OVERWORLD || body == null) {
                atmosphere = List.of(Gases.NITROGEN_ID, Gases.OXYGEN_ID);
            } else {
                atmosphere = body.value().atmosphere().composition().object2DoubleEntrySet().stream()
                        .filter(entry -> entry.getDoubleValue() > 0)
                        .sorted(Comparator.comparingDouble((it.unimi.dsi.fastutil.objects.Object2DoubleMap.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.level.material.Fluid>> entry) -> entry.getDoubleValue()).reversed())
                        .limit(3).map(entry -> entry.getKey().location()).toList();
            }
        }
        return atmosphere;
    }

    protected final void drainOrVent(int slot, int tank) {
        if (itemStorage().slot(slot).getResource() == GCItems.ATMOSPHERIC_VALVE) {
            fluidStorage().slot(tank).extract(4 * MILLIBUCKET);
        } else {
            drainFluidToSlot(slot, tank);
        }
    }

    @Override
    protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        chargeFromSlot(0);
        fluidSource.trySpreadFluids(level, pos, state);
        if (disableCooldown > 0) disableCooldown--;
    }

    @Override
    protected final MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        if (manuallyDisabled) { coolDown(); return MachineStatuses.IDLE; }
        if (!canProcess()) { coolDown(); return blockedStatus(); }
        if (energyStorage().getAmount() <= energyPerTick) { coolDown(); return MachineStatuses.NOT_ENOUGH_ENERGY; }
        energyStorage().extract(energyPerTick);
        if (processTicks <= 0) processTicks = PROCESS_TIME;
        else if (--processTicks <= 0) {
            doLiquefaction();
            processTicks = canProcess() ? PROCESS_TIME : 0;
        }
        setChanged();
        return workingStatus();
    }

    private void coolDown() {
        processTicks = processTicks > 0 ? 0 : Math.max(-idleTicks, processTicks - 1);
    }

    @Override
    protected void tickDisabled(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        coolDown();
    }

    public abstract boolean canProcess();
    public abstract void doLiquefaction();
    protected abstract MachineStatus blockedStatus();
    protected abstract MachineStatus workingStatus();
    public final int getProcessTicks() { return processTicks; }
    public final boolean isManuallyDisabled() { return manuallyDisabled; }
    public final int getDisableCooldown() { return disableCooldown; }
    public final void toggleDisabled() {
        if (disableCooldown == 0) {
            manuallyDisabled = !manuallyDisabled;
            disableCooldown = 10;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("smeltingTicks", processTicks);
        tag.putBoolean("disabled", manuallyDisabled);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        processTicks = tag.contains("smeltingTicks") ? Math.max(-idleTicks, Math.min(PROCESS_TIME, tag.getInt("smeltingTicks"))) : -idleTicks;
        manuallyDisabled = tag.getBoolean("disabled");
        atmosphere = null;
    }
}
