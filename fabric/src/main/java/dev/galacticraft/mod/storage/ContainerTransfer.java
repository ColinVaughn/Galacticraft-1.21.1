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

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Transaction-sized vanilla-container transfers used by docked cargo automation. */
public final class ContainerTransfer {
    private ContainerTransfer() {
    }

    public static int move(Container source, int sourceStart, int sourceCount,
                           Container target, int targetStart, int targetCount, int limit) {
        int remaining = Math.max(0, limit);
        int moved = 0;
        int sourceEnd = Math.min(source.getContainerSize(), sourceStart + sourceCount);
        for (int sourceSlot = Math.max(0, sourceStart); sourceSlot < sourceEnd && remaining > 0; sourceSlot++) {
            ItemStack stack = source.getItem(sourceSlot);
            if (stack.isEmpty()) continue;
            int inserted = insert(target, targetStart, targetCount, stack, remaining);
            if (inserted > 0) {
                source.removeItem(sourceSlot, inserted);
                remaining -= inserted;
                moved += inserted;
            }
        }
        if (moved > 0) {
            source.setChanged();
            target.setChanged();
        }
        return moved;
    }

    /** Inserts without mutating {@code offered}; returns the exact accepted item count. */
    public static int insert(Container target, int targetStart, int targetCount,
                             ItemStack offered, int limit) {
        if (offered.isEmpty() || limit <= 0) return 0;
        int remaining = Math.min(offered.getCount(), limit);
        int targetEnd = Math.min(target.getContainerSize(), targetStart + targetCount);

        for (int targetSlot = Math.max(0, targetStart); targetSlot < targetEnd && remaining > 0; targetSlot++) {
            ItemStack existing = target.getItem(targetSlot);
            if (existing.isEmpty() || !target.canPlaceItem(targetSlot, offered)
                    || !ItemStack.isSameItemSameComponents(existing, offered)) continue;
            int capacity = Math.min(target.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
            int inserted = Math.min(remaining, Math.max(0, capacity));
            if (inserted > 0) {
                existing.grow(inserted);
                remaining -= inserted;
            }
        }

        for (int targetSlot = Math.max(0, targetStart); targetSlot < targetEnd && remaining > 0; targetSlot++) {
            if (!target.getItem(targetSlot).isEmpty() || !target.canPlaceItem(targetSlot, offered)) continue;
            int inserted = Math.min(remaining, Math.min(target.getMaxStackSize(), offered.getMaxStackSize()));
            target.setItem(targetSlot, offered.copyWithCount(inserted));
            remaining -= inserted;
        }

        int inserted = Math.min(offered.getCount(), limit) - remaining;
        if (inserted > 0) target.setChanged();
        return inserted;
    }
}
