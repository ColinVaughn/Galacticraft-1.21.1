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

import dev.galacticraft.machinelib.api.component.MLDataComponents;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.TooltipUtil;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class OxygenTankItem extends AccessoryItem {
    public final long capacity;
    public OxygenTankItem(Properties properties, long capacity) { super(properties); this.capacity = capacity; }

    public static OxygenStorageView getStorage(ItemStack stack) {
        if (stack.getItem() instanceof InfiniteOxygenTankItem) {
            return new OxygenStorageView(Long.MAX_VALUE, Long.MAX_VALUE);
        }
        OxygenTankItem item = (OxygenTankItem) stack.getItem();
        return new OxygenStorageView(stack.getOrDefault(MLDataComponents.AMOUNT.get(), 0L), item.capacity);
    }
    public static ItemStack getFullTank(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(MLDataComponents.AMOUNT.get(), ((OxygenTankItem) item).capacity);
        return stack;
    }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) {
        OxygenStorageView storage = getStorage(stack);
        return Math.round(13.0F * (float) storage.amount / (float) storage.capacity);
    }
    @Override public int getBarColor(ItemStack stack) {
        OxygenStorageView storage = getStorage(stack);
        return Constant.Text.getStorageLevelColor(1.0 - (double) storage.amount / storage.capacity);
    }
    @Override public int getEnchantmentValue() { return -1; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isFoil(ItemStack stack) { return capacity <= 0; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendOxygenTankTooltip(stack, context, tooltip, flag);
        super.appendHoverText(stack, context, tooltip, flag);
    }
    protected void appendOxygenTankTooltip(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        OxygenStorageView storage = getStorage(stack);
        TooltipUtil.appendFluidRemainingTooltip(Translations.Tooltip.OXYGEN_REMAINING, storage.amount, storage.capacity, tooltip);
    }
    public record OxygenStorageView(long amount, long capacity) {
        public long getAmount() { return amount; }
        public long getCapacity() { return capacity; }
    }
}
