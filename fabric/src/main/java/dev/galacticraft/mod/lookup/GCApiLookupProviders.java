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

package dev.galacticraft.mod.lookup;

import dev.architectury.platform.Platform;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Registers common MachineLib providers and dispatches external API bridges by loader. */
public final class GCApiLookupProviders {
    private GCApiLookupProviders() {
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        MachineBlockEntity.registerProviders(new BlockEntityType[]{
                GCBlockEntityTypes.COAL_GENERATOR,
                GCBlockEntityTypes.BASIC_SOLAR_PANEL,
                GCBlockEntityTypes.ADVANCED_SOLAR_PANEL,
                GCBlockEntityTypes.CIRCUIT_FABRICATOR,
                GCBlockEntityTypes.COMPRESSOR,
                GCBlockEntityTypes.ELECTRIC_COMPRESSOR,
                GCBlockEntityTypes.ELECTRIC_FURNACE,
                GCBlockEntityTypes.ELECTRIC_ARC_FURNACE,
                GCBlockEntityTypes.REFINERY,
                GCBlockEntityTypes.FUEL_LOADER,
                GCBlockEntityTypes.CARGO_LOADER,
                GCBlockEntityTypes.CARGO_UNLOADER,
                GCBlockEntityTypes.OXYGEN_COLLECTOR,
                GCBlockEntityTypes.OXYGEN_COMPRESSOR,
                GCBlockEntityTypes.OXYGEN_DECOMPRESSOR,
                GCBlockEntityTypes.OXYGEN_SEALER,
                GCBlockEntityTypes.OXYGEN_BUBBLE_DISTRIBUTOR,
                GCBlockEntityTypes.ENERGY_STORAGE_MODULE,
                GCBlockEntityTypes.ENERGY_STORAGE_CLUSTER,
                GCBlockEntityTypes.FOOD_CANNER,
                GCBlockEntityTypes.OXYGEN_STORAGE_MODULE,
                GCBlockEntityTypes.FLUID_TANK,
                GCBlockEntityTypes.PAINTER,
                GCBlockEntityTypes.DECONSTRUCTOR
        });

        if (Platform.isFabric()) {
            try {
                Class.forName("dev.galacticraft.mod.lookup.FabricApiLookupProviders")
                        .getMethod("register")
                        .invoke(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to register transfer capability providers", exception);
            }
        }

        if (Platform.isFabric()) {
            try {
                Class.forName("dev.galacticraft.mod.lookup.FabricEnergyLookupProviders")
                        .getMethod("register")
                        .invoke(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to register Fabric energy providers", exception);
            }
        }
    }
}
