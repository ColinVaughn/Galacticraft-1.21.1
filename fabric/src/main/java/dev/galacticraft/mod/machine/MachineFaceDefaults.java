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

package dev.galacticraft.mod.machine;

import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.configuration.IOConfig;
import dev.galacticraft.machinelib.api.storage.MachineFluidStorage;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.transfer.ResourceFlow;
import dev.galacticraft.machinelib.api.transfer.ResourceType;
import dev.galacticraft.machinelib.api.util.BlockFace;
import dev.galacticraft.mod.content.block.entity.machine.TerraformerBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Gives a machine a face configuration that works before anyone has configured it.
 *
 * A machine's faces all start blank, and a blank face carries nothing: the energy lookup returns
 * nothing for it, so a wire will not connect to the machine, a neighbouring machine cannot push power
 * into it, and it cannot push power out. Left that way, a freshly placed machine is inert until every
 * face it needs has been set by hand in the configuration tab - which is easy to miss, and the machine
 * gives no sign that it is what is wrong.
 *
 * Galacticraft Legacy had no configuration tab at all; each machine simply had fixed sides for power
 * in and out. Rather than picking one face per machine, the default here follows what the machine's own
 * energy storage says it can do: something that can only be filled takes power on every side, something
 * that can only be drained gives it on every side, and a battery does both. That is derived from the
 * storage spec, so a machine added later is covered without a table to update.
 *
 * @see dev.galacticraft.mod.mixin.MachineBlockEntityMixin where this is applied
 */
public final class MachineFaceDefaults {
    private MachineFaceDefaults() {}

    /**
     * {@return the flow a machine with these external transfer rates should offer on its faces, or null
     * if it exchanges no energy with the world at all}
     */
    public static @Nullable ResourceFlow defaultEnergyFlow(long insertionRate, long extractionRate) {
        if (insertionRate > 0L && extractionRate > 0L) return ResourceFlow.BOTH;
        if (insertionRate > 0L) return ResourceFlow.INPUT;
        if (extractionRate > 0L) return ResourceFlow.OUTPUT;
        return null;
    }

    /**
     * {@return the flow a machine's tanks should offer on its faces, or null if none of them
     * exchanges fluid with the world}
     *
     * Derived from the tanks themselves, the same way the energy flow is derived from the energy
     * storage's transfer rates. A machine whose only tank is a hidden internal buffer offers
     * nothing; one holding a stock for others to draw on offers it on every side.
     */
    public static @Nullable ResourceFlow defaultFluidFlow(MachineFluidStorage storage) {
        boolean insert = false;
        boolean extract = false;
        for (FluidResourceSlot slot : storage) {
            TransferType mode = slot.transferMode();
            insert |= mode.externalInsertion();
            extract |= mode.externalExtraction();
        }

        if (insert && extract) return ResourceFlow.BOTH;
        if (insert) return ResourceFlow.INPUT;
        if (extract) return ResourceFlow.OUTPUT;
        return null;
    }

    /** {@return a flow that permits everything both of these do} */
    private static ResourceFlow combine(ResourceFlow a, ResourceFlow b) {
        return a == b ? a : ResourceFlow.BOTH;
    }

    /** {@return whether every face is still blank, meaning nobody has configured this machine} */
    public static boolean isUnconfigured(IOConfig config) {
        for (BlockFace face : BlockFace.values()) {
            if (config.get(face).getType() != ResourceType.NONE) return false;
        }
        return true;
    }

    /**
     * {@return whether this is the generic face profile previously assigned to a newly placed
     * terraformer}
     */
    static boolean isOldTerraformerDefault(IOConfig config) {
        for (BlockFace face : BlockFace.values()) {
            if (config.get(face).getType() != ResourceType.ANY
                    || config.get(face).getFlow() != ResourceFlow.INPUT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies the closest MachineLib representation of the fixed ports from Galacticraft Legacy.
     *
     * The front and back remain unconfigured so the two authored terraformer panel textures are
     * not replaced by generic I/O textures. The right is the fixed electrical input. The remaining
     * utility faces accept fluid and inventory automation; BOTH is safe here because the individual
     * storage slots still decide whether a resource may actually enter or leave.
     */
    static void applyTerraformer(IOConfig config) {
        for (BlockFace face : BlockFace.values()) {
            config.get(face).setOption(ResourceType.NONE, ResourceFlow.BOTH);
        }
        config.get(BlockFace.RIGHT).setOption(ResourceType.ENERGY, ResourceFlow.INPUT);
        config.get(BlockFace.LEFT).setOption(ResourceType.ANY, ResourceFlow.BOTH);
        config.get(BlockFace.TOP).setOption(ResourceType.ANY, ResourceFlow.BOTH);
        config.get(BlockFace.BOTTOM).setOption(ResourceType.ANY, ResourceFlow.BOTH);
    }

    /**
     * Applies the default face configuration to {@code machine}, unless it already has one.
     *
     * A machine whose every face is blank is treated as never configured, including one loaded from a
     * save written before this existed - that is what lets machines already placed in a world start
     * working. The cost is that blanking every face by hand is not a way to isolate a machine, since it
     * reads as unconfigured; redstone control is.
     */
    public static void apply(MachineBlockEntity machine) {
        IOConfig config = machine.getIOConfig();

        if (machine instanceof TerraformerBlockEntity) {
            if (isUnconfigured(config) || isOldTerraformerDefault(config)) {
                applyTerraformer(config);
            }
            return;
        }

        if (!isUnconfigured(config)) return;

        MachineEnergyStorage energy = machine.energyStorage();
        ResourceFlow energyFlow = defaultEnergyFlow(energy.externalInsertionRate(), energy.externalExtractionRate());
        ResourceFlow fluidFlow = defaultFluidFlow(machine.fluidStorage());

        ResourceType type;
        ResourceFlow flow;
        if (energyFlow != null && fluidFlow != null) {
            // A face carries one resource type, and there is no energy-and-fluid pairing, so a
            // machine that does both has to use ANY. Nothing is loosened by that: what may actually
            // cross a face is still decided by each storage's own transfer rates and slot types.
            type = ResourceType.ANY;
            flow = combine(energyFlow, fluidFlow);
        } else if (energyFlow != null) {
            type = ResourceType.ENERGY;
            flow = energyFlow;
        } else if (fluidFlow != null) {
            // Machines with no energy at all - the oxygen storage module, the fluid tank - used to
            // fall out here with every face left blank, which made them airtight in both
            // directions: a blank face exposes no tank, so nothing could fill or drain them.
            type = ResourceType.FLUID;
            flow = fluidFlow;
        } else {
            return;
        }

        for (BlockFace face : BlockFace.values()) {
            config.get(face).setOption(type, flow);
        }
    }
}
