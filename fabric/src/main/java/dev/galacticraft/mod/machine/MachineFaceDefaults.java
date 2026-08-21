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
 * Galacticraft Legacy used fixed ports: inputs on the right and outputs on the left. The default here
 * follows that layout while deriving the resource types from each machine's storage spec, so a machine
 * added later is covered without a table to update.
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
     * storage's transfer rates. A machine whose only tank is a hidden internal buffer offers nothing.
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
        ResourceType type = config.get(BlockFace.FRONT).getType();
        if (type != ResourceType.ENERGY && type != ResourceType.ANY) return false;

        for (BlockFace face : BlockFace.values()) {
            if (config.get(face).getType() != type
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

    /** {@return whether this is the every-side profile assigned by versions 5.4.10 through 5.4.13} */
    static boolean isOldDefault(IOConfig config, @Nullable ResourceFlow energyFlow, @Nullable ResourceFlow fluidFlow) {
        if (energyFlow == null && fluidFlow == null) return false;

        ResourceType type = energyFlow != null && fluidFlow != null
                ? ResourceType.ANY
                : energyFlow != null ? ResourceType.ENERGY : ResourceType.FLUID;
        ResourceFlow flow = energyFlow != null && fluidFlow != null && energyFlow != fluidFlow
                ? ResourceFlow.BOTH
                : energyFlow != null ? energyFlow : fluidFlow;

        for (BlockFace face : BlockFace.values()) {
            if (config.get(face).getType() != type || config.get(face).getFlow() != flow) return false;
        }
        return true;
    }

    private static void applyFace(IOConfig config, BlockFace face, ResourceFlow flow,
                                  @Nullable ResourceFlow energyFlow, @Nullable ResourceFlow fluidFlow) {
        boolean energy = energyFlow == flow || energyFlow == ResourceFlow.BOTH;
        boolean fluid = fluidFlow == flow || fluidFlow == ResourceFlow.BOTH;
        if (!energy && !fluid) return;

        config.get(face).setOption(energy && fluid ? ResourceType.ANY
                : energy ? ResourceType.ENERGY : ResourceType.FLUID, flow);
    }

    static void applyDefaultFaces(IOConfig config, @Nullable ResourceFlow energyFlow, @Nullable ResourceFlow fluidFlow) {
        if (!isUnconfigured(config) && !isOldDefault(config, energyFlow, fluidFlow)) return;

        for (BlockFace face : BlockFace.values()) {
            config.get(face).setOption(ResourceType.NONE, ResourceFlow.BOTH);
        }
        applyFace(config, BlockFace.RIGHT, ResourceFlow.INPUT, energyFlow, fluidFlow);
        applyFace(config, BlockFace.LEFT, ResourceFlow.OUTPUT, energyFlow, fluidFlow);
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

        MachineEnergyStorage energy = machine.energyStorage();
        ResourceFlow energyFlow = defaultEnergyFlow(energy.externalInsertionRate(), energy.externalExtractionRate());
        ResourceFlow fluidFlow = defaultFluidFlow(machine.fluidStorage());
        applyDefaultFaces(config, energyFlow, fluidFlow);
    }
}
