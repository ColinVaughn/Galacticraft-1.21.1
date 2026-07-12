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

import dev.galacticraft.mod.api.pipe.FluidPipe;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import dev.galacticraft.mod.storage.CanisterFluidStorage;
import dev.galacticraft.mod.storage.OxygenTankFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fabric Transfer API and Team Reborn Energy providers. */
public final class FabricApiLookupProviders {
    private static final BlockEntityType<?>[] PIPE_TYPES = {
            GCBlockEntityTypes.GLASS_FLUID_PIPE
    };

    private FabricApiLookupProviders() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register() {
        FluidStorage.SIDED.registerForBlockEntities((blockEntity, direction) -> {
            if (direction == null || !((FluidPipe) blockEntity).canConnect(direction)) return null;
            return ((FluidPipe) blockEntity).getInsertable();
        }, (BlockEntityType[]) PIPE_TYPES);

        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> InventoryStorage.of(be.getHold(), direction), GCBlockEntityTypes.ASTRO_MINER_BASE);

        FluidStorage.ITEM.registerForItems((itemStack, context) -> {
            long capacity = ((OxygenTankItem) itemStack.getItem()).capacity;
            return new OxygenTankFluidStorage(context, capacity);
        }, GCItems.SMALL_OXYGEN_TANK, GCItems.MEDIUM_OXYGEN_TANK, GCItems.LARGE_OXYGEN_TANK);
        FluidStorage.ITEM.registerSelf(GCItems.INFINITE_OXYGEN_TANK);
        FluidStorage.ITEM.registerForItems(
                (itemStack, context) -> new CanisterFluidStorage(itemStack, context), GCItems.FLUID_CANISTER);
    }
}
