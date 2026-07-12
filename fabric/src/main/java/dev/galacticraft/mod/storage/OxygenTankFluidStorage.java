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

package dev.galacticraft.mod.storage;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.component.MLDataComponents;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantItemStorage;
import net.minecraft.world.item.ItemStack;

/** Loader-neutral Fabric Transfer view over an oxygen tank's amount component. */
public final class OxygenTankFluidStorage extends SingleVariantItemStorage<FluidVariant> {
    private final long capacity;
    private final FluidVariant oxygen = FluidVariant.of(Gases.OXYGEN);

    public OxygenTankFluidStorage(ContainerItemContext context, long capacity) {
        super(context);
        this.capacity = capacity;
    }

    @Override
    protected FluidVariant getBlankResource() {
        return FluidVariant.blank();
    }

    @Override
    protected FluidVariant getResource(ItemVariant currentVariant) {
        return getAmount(currentVariant) > 0 ? this.oxygen : FluidVariant.blank();
    }

    @Override
    protected long getAmount(ItemVariant currentVariant) {
        return currentVariant.toStack().getOrDefault(MLDataComponents.AMOUNT.get(), 0L);
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return this.capacity;
    }

    @Override
    protected ItemVariant getUpdatedVariant(ItemVariant currentVariant, FluidVariant newResource, long newAmount) {
        ItemStack stack = currentVariant.toStack();
        if (newAmount <= 0) {
            stack.remove(MLDataComponents.AMOUNT.get());
        } else {
            stack.set(MLDataComponents.AMOUNT.get(), newAmount);
        }
        return ItemVariant.of(stack);
    }

    @Override
    protected boolean canInsert(FluidVariant resource) {
        return resource.equals(this.oxygen);
    }
}
