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

import dev.galacticraft.machinelib.api.compat.transfer.ExposedEnergyStorage;
import dev.galacticraft.mod.api.wire.Wire;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

/** Team Reborn Energy providers used only by the Fabric loader. */
public final class FabricEnergyLookupProviders {
    private static final BlockEntityType<?>[] WIRE_TYPES = {
            GCBlockEntityTypes.WIRE_T1,
            GCBlockEntityTypes.WIRE_T2
    };

    private FabricEnergyLookupProviders() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register() {
        EnergyStorage.SIDED.registerForBlockEntities((blockEntity, direction) -> {
            if (direction == null || !((Wire) blockEntity).canConnect(direction)) return null;
            return ((Wire) blockEntity).getInsertable();
        }, (BlockEntityType[]) WIRE_TYPES);

        EnergyStorage.SIDED.registerForBlockEntity((be, direction) ->
                        ExposedEnergyStorage.create(be.getEnergyStorage(), be.getEnergyStorage().externalInsertionRate(), 0),
                GCBlockEntityTypes.ASTRO_MINER_BASE);
    }
}
