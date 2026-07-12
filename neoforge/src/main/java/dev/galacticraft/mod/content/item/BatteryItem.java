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

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.TooltipUtil;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/** Galacticraft battery backed by a synced item data component and NeoForge energy capability. */
public class BatteryItem extends Item {
    private final long capacity;
    private final long transfer;

    public BatteryItem(Properties settings, long capacity, long transfer) {
        super(settings);
        this.capacity = capacity;
        this.transfer = transfer;
    }

    public long getStoredEnergy(ItemStack stack) {
        return Math.min(this.capacity, Math.max(0, stack.getOrDefault(GCDataComponents.AMOUNT, 0L)));
    }

    public void setStoredEnergy(ItemStack stack, long amount) {
        stack.set(GCDataComponents.AMOUNT, Math.min(this.capacity, Math.max(0, amount)));
    }

    public long getEnergyCapacity(ItemStack stack) { return this.capacity; }
    public long getEnergyMaxInput(ItemStack stack) { return this.transfer; }
    public long getEnergyMaxOutput(ItemStack stack) { return this.transfer; }

    public IEnergyStorage createEnergyStorage(ItemStack stack) {
        return new BatteryEnergyStorage(stack);
    }

    private final class BatteryEnergyStorage implements IEnergyStorage {
        private final ItemStack stack;

        private BatteryEnergyStorage(ItemStack stack) { this.stack = stack; }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            long accepted = Math.min(Math.min(maxReceive, transfer), capacity - getStoredEnergy(this.stack));
            if (accepted > 0 && !simulate) setStoredEnergy(this.stack, getStoredEnergy(this.stack) + accepted);
            return (int) Math.max(accepted, 0);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            long extracted = Math.min(Math.min(maxExtract, transfer), getStoredEnergy(this.stack));
            if (extracted > 0 && !simulate) setStoredEnergy(this.stack, getStoredEnergy(this.stack) - extracted);
            return (int) Math.max(extracted, 0);
        }

        @Override public int getEnergyStored() { return (int) Math.min(getStoredEnergy(this.stack), Integer.MAX_VALUE); }
        @Override public int getMaxEnergyStored() { return (int) Math.min(capacity, Integer.MAX_VALUE); }
        @Override public boolean canExtract() { return transfer > 0; }
        @Override public boolean canReceive() { return transfer > 0; }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        TooltipUtil.appendRemainingTooltip(Translations.Tooltip.ENERGY_REMAINING, this.getStoredEnergy(stack), this.capacity, tooltip);
        super.appendHoverText(stack, context, tooltip, type);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13.0F * ((float) this.getStoredEnergy(stack) / (float) this.capacity)); }

    @Override
    public int getBarColor(ItemStack stack) {
        double scale = 1.0 - Math.max(0.0, (double) this.getStoredEnergy(stack) / (double) this.capacity);
        return Constant.Text.getStorageLevelColor(scale);
    }

    @Override public int getEnchantmentValue() { return -1; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack repairMaterial) { return false; }
}
