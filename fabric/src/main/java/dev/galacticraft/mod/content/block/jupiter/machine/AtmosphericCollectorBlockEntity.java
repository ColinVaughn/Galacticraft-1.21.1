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
import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
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
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws hydrogen and helium out of a gas giant's atmosphere into two gas tanks. Denser air deeper down
 * gives more gas; the intake needs open sky above it. Gas leaves through the machine's configured faces.
 */
public class AtmosphericCollectorBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int CHARGE_SLOT = 0;
    public static final int HYDROGEN_TANK = 0;
    public static final int HELIUM_TANK = 1;
    public static final int ENERGY_PER_TICK = 30;
    /** Hydrogen droplets collected per tick at one bar; scales with the square root of the pressure. */
    private static final double HYDROGEN_BASE = 50;
    /** Jupiter's upper atmosphere is roughly 86% hydrogen and 14% helium. */
    private static final double HELIUM_SHARE = 0.16;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_OUTPUT).pos(31, 17).capacity(FluidUtil.bucketsToDroplets(16))
                            .filter(ResourceFilters.ofResource(Gases.HYDROGEN)),
                    FluidResourceSlot.builder(TransferType.STRICT_OUTPUT).pos(52, 17).capacity(FluidUtil.bucketsToDroplets(8))
                            .filter(ResourceFilters.ofResource(Gases.HELIUM))
            )
    );
    private final FluidSource fluidSource = new FluidSource(this);
    private long hydrogenPerTick, heliumPerTick;
    private float pressure;

    public AtmosphericCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.ATMOSPHERIC_COLLECTOR, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        this.fluidSource.trySpreadFluids(level, pos, state);
        this.hydrogenPerTick = this.heliumPerTick = 0;
        if (!CelestialBodyTags.is(level, CelestialBodyTags.GAS_GIANT)) return JupiterMachineStatuses.NOT_ON_GAS_GIANT;
        this.pressure = JupiterEnvironment.pressure(pos.getY());
        if (!level.canSeeSky(pos.above())) return JupiterMachineStatuses.INTAKE_BLOCKED;
        var hydrogen = this.fluidStorage().slot(HYDROGEN_TANK);
        var helium = this.fluidStorage().slot(HELIUM_TANK);
        if (hydrogen.isFull() && helium.isFull()) return JupiterMachineStatuses.GAS_TANKS_FULL;
        if (!this.energyStorage().canExtract(ENERGY_PER_TICK)) return MachineStatuses.NOT_ENOUGH_ENERGY;
        this.energyStorage().extract(ENERGY_PER_TICK);
        long[] yield = yieldPerTick(this.pressure);
        this.hydrogenPerTick = hydrogen.insert(Gases.HYDROGEN, yield[0]);
        this.heliumPerTick = helium.insert(Gases.HELIUM, yield[1]);
        return JupiterMachineStatuses.COLLECTING_GAS;
    }

    /** Hydrogen and helium droplets drawn per tick at {@code pressure}; denser air yields more. */
    public static long[] yieldPerTick(float pressure) {
        long total = Math.round(HYDROGEN_BASE * Math.sqrt(pressure));
        long helium = Math.round(total * HELIUM_SHARE);
        return new long[]{total - helium, helium};
    }

    @Override
    public List<Integer> readout() {
        long perSecond = FluidUtil.bucketsToDroplets(1) / 1000;
        return List.of((int) (this.hydrogenPerTick * 20 / Math.max(1, perSecond)), (int) (this.heliumPerTick * 20 / Math.max(1, perSecond)), Math.round(this.pressure * 10));
    }

    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.ATMOSPHERIC_COLLECTOR, syncId, player, this);
    }
}
