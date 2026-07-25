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

package dev.galacticraft.mod.content.entity.vehicle;

import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A vehicle with a persistent container inventory. */
public interface ContainerVehicle {
    Container getVehicleInventory();

    /**
     * The leading slots a cargo loader or unloader may touch. Defaults to the whole inventory; the
     * crewed rocket narrows it so machines cannot fill the reserved slots that carry the rocket item
     * and launch pad back down.
     */
    default int getCargoSlotCount() {
        return this.getVehicleInventory().getContainerSize();
    }

    /**
     * Finds an inventory-bearing vehicle docked to a launch pad adjacent to {@code machinePos}.
     * Both the cargo rocket and a crewed rocket carrying chests can be loaded, as in Galacticraft
     * Legacy where every auto-rocket was a cargo target.
     */
    static @Nullable ContainerVehicle findDocked(Level level, BlockPos machinePos) {
        for (Direction direction : Direction.values()) {
            BlockPos partPos = machinePos.relative(direction);
            if (!(level.getBlockState(partPos).getBlock() instanceof AbstractLaunchPad)) continue;

            BlockPos center = partPos.offset(AbstractLaunchPad.partToCenterPos(
                    level.getBlockState(partPos).getValue(AbstractLaunchPad.PART)));

            if (level.getBlockEntity(center) instanceof LaunchPadBlockEntity pad) {
                Dockable docked = pad.getDockedEntity();

                if (docked instanceof CargoRocketEntity rocket && !rocket.inFlight()) {
                    return rocket;
                }
                if (docked instanceof RocketEntity rocket && rocket.getCargoSlotCount() > 0) {
                    return rocket;
                }
            }
        }
        return null;
    }

    static void loadInventory(CompoundTag nbt, Container container, HolderLookup.Provider registries) {
        if (nbt.contains("Inventory", Tag.TAG_LIST)) {
            if (container instanceof net.minecraft.world.SimpleContainer simpleContainer) {
                simpleContainer.fromTag(nbt.getList("Inventory", Tag.TAG_COMPOUND), registries);
            }
            return;
        }

        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt.getCompound("Inventory"), items, registries);
        for (int slot = 0; slot < items.size(); slot++) {
            container.setItem(slot, items.get(slot));
        }
    }

    static void saveInventory(CompoundTag nbt, Container container, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, container.getItem(slot));
        }
        CompoundTag inventory = new CompoundTag();
        ContainerHelper.saveAllItems(inventory, items, registries);
        nbt.put("Inventory", inventory);
    }
}
