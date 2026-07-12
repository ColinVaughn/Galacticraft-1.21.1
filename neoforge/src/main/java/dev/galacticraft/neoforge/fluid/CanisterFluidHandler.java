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

package dev.galacticraft.neoforge.fluid;

import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.mod.content.item.FluidCanisterItem;
import dev.galacticraft.mod.tag.GCFluidTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

import static dev.galacticraft.api.component.GCDataComponents.FLUID_DATA;

public final class CanisterFluidHandler implements IFluidHandlerItem {
    private static final long DROPLETS_PER_MB = 81;
    private final ItemStack stack;
    public CanisterFluidHandler(ItemStack stack) { this.stack = stack; }
    private long capacity() { return ((FluidCanisterItem) stack.getItem()).capacity; }
    private FluidData data() { return stack.get(FLUID_DATA); }
    @Override public int getTanks() { return 1; }
    @Override public @NotNull FluidStack getFluidInTank(int tank) {
        FluidData data = data();
        return tank == 0 && data != null ? stackOf(data.variant(), (int) (data.amount() / DROPLETS_PER_MB)) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? (int) (capacity() / DROPLETS_PER_MB) : 0; }
    @Override public boolean isFluidValid(int tank, @NotNull FluidStack fluid) { return tank == 0 && !fluid.isEmpty() && !fluid.is(GCFluidTags.FLUID_CANISTER_EXCLUSIONS); }
    @Override public int fill(@NotNull FluidStack fluid, FluidAction action) {
        if (!isFluidValid(0, fluid)) return 0;
        FluidStack stored = getFluidInTank(0);
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, fluid)) return 0;
        long current = data() == null ? 0 : data().amount();
        long accepted = Math.min((long) fluid.getAmount() * DROPLETS_PER_MB, capacity() - current);
        accepted = accepted / DROPLETS_PER_MB * DROPLETS_PER_MB;
        if (accepted > 0 && action.execute()) stack.set(FLUID_DATA,
                new FluidData(new MLFluidStack(fluid.getFluid(), fluid.getComponentsPatch()), current + accepted));
        return (int) (accepted / DROPLETS_PER_MB);
    }
    @Override public @NotNull FluidStack drain(@NotNull FluidStack fluid, FluidAction action) {
        FluidStack stored = getFluidInTank(0);
        return FluidStack.isSameFluidSameComponents(stored, fluid) ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
    }
    @Override public @NotNull FluidStack drain(int amount, FluidAction action) {
        FluidData data = data();
        if (data == null || amount <= 0) return FluidStack.EMPTY;
        long extracted = Math.min(data.amount(), (long) amount * DROPLETS_PER_MB);
        extracted = extracted / DROPLETS_PER_MB * DROPLETS_PER_MB;
        FluidStack result = stackOf(data.variant(), (int) (extracted / DROPLETS_PER_MB));
        if (action.execute()) {
            long remaining = data.amount() - extracted;
            if (remaining <= 0) stack.remove(FLUID_DATA);
            else stack.set(FLUID_DATA, new FluidData(data.variant(), remaining));
        }
        return result;
    }
    @Override public @NotNull ItemStack getContainer() { return stack; }
    private static FluidStack stackOf(MLFluidStack variant, int amount) {
        FluidStack result = new FluidStack(variant.fluid(), amount);
        result.applyComponents(variant.components());
        return result;
    }
}
