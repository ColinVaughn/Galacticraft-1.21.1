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

package dev.galacticraft.mod.content.item;

import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.util.TooltipUtil;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.galacticraft.api.component.GCDataComponents.FLUID_DATA;

public class FluidCanisterItem extends Item {
    public static final ResourceLocation FILL_LEVEL = Constant.id("fill_level");
    public final long capacity;
    public FluidCanisterItem(Properties properties, long capacity) { super(properties); this.capacity = capacity; }
    public static CanisterStorageView getStorage(ItemStack stack) {
        FluidData data = stack.get(FLUID_DATA);
        return new CanisterStorageView(data == null ? 0 : data.amount(), ((FluidCanisterItem) stack.getItem()).capacity);
    }
    public static ItemStack getFilledCanister(Item item, Fluid fluid) {
        ItemStack stack = new ItemStack(item);
        stack.set(FLUID_DATA, new FluidData(new MLFluidStack(fluid), ((FluidCanisterItem) item).capacity));
        return stack;
    }
    @Override public @NotNull Component getName(ItemStack stack) {
        FluidData data = stack.get(FLUID_DATA);
        if (data == null || data.variant().fluid() == Fluids.EMPTY) return super.getName(stack);
        return Component.translatable(Translations.Items.FLUID_CANISTER_FILLED, fluidName(data.variant().fluid()));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidData data = stack.get(FLUID_DATA);
        if (data == null || data.variant().fluid() == Fluids.EMPTY) {
            tooltip.add(Component.translatable(Translations.Tooltip.FLUID_CANISTER_EMPTY).setStyle(Constant.Text.DARK_GRAY_STYLE));
        } else {
            TooltipUtil.appendLabeledTooltip(Translations.Tooltip.FLUID_CANISTER_FLUID_INFO,
                    fluidName(data.variant().fluid()), TooltipUtil.formatFluidRemaining(data.amount(), capacity), tooltip);
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
    private static Component fluidName(Fluid fluid) {
        if (fluid.isSame(GCFluids.LIQUID_OXYGEN)) return Component.translatable(Translations.Tooltip.FLUID_CANISTER_LOX);
        return Component.translatable(BuiltInRegistries.FLUID.getKey(fluid).toLanguageKey("fluid"));
    }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) {
        CanisterStorageView storage = getStorage(stack);
        return Math.round(13.0F * (float) storage.amount / storage.capacity);
    }
    @Override public int getBarColor(ItemStack stack) {
        CanisterStorageView storage = getStorage(stack);
        return Constant.Text.getStorageLevelColor(1.0F - (float) storage.amount / storage.capacity);
    }
    public record CanisterStorageView(long amount, long capacity) {
        public long getAmount() { return amount; }
        public long getCapacity() { return capacity; }
    }
}
