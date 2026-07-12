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

import dev.galacticraft.api.gas.Gases;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

public final class InfiniteOxygenFluidHandler implements IFluidHandlerItem {
    private final ItemStack stack;
    public InfiniteOxygenFluidHandler(ItemStack stack) { this.stack = stack; }
    @Override public int getTanks() { return 1; }
    @Override public @NotNull FluidStack getFluidInTank(int tank) { return tank == 0 ? new FluidStack(Gases.OXYGEN, Integer.MAX_VALUE) : FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? Integer.MAX_VALUE : 0; }
    @Override public boolean isFluidValid(int tank, @NotNull FluidStack fluid) { return false; }
    @Override public int fill(@NotNull FluidStack fluid, FluidAction action) { return 0; }
    @Override public @NotNull FluidStack drain(@NotNull FluidStack requested, FluidAction action) {
        return requested.is(Gases.OXYGEN) ? requested.copy() : FluidStack.EMPTY;
    }
    @Override public @NotNull FluidStack drain(int amount, FluidAction action) { return new FluidStack(Gases.OXYGEN, amount); }
    @Override public @NotNull ItemStack getContainer() { return stack; }
}
