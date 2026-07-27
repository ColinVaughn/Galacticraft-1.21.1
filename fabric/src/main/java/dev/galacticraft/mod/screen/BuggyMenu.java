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

package dev.galacticraft.mod.screen;

import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BuggyMenu extends AbstractContainerMenu {
    private final Buggy buggy;
    private final Container container;
    private final int storageSlots;
    private final int storageRows;

    public BuggyMenu(int syncId, Inventory playerInventory, OpeningData data) {
        this(syncId, playerInventory, resolveBuggy(playerInventory.player.level(), data.entityId()), data.storageSlots());
    }

    private static Buggy resolveBuggy(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof Buggy buggy ? buggy : null;
    }

    public BuggyMenu(int syncId, Inventory playerInventory, Buggy buggy) {
        this(syncId, playerInventory, buggy, buggy == null ? 0 : buggy.getVehicleInventory().getContainerSize());
    }

    private BuggyMenu(int syncId, Inventory playerInventory, Buggy buggy, int storageSlots) {
        super(GCMenuTypes.BUGGY, syncId);
        this.buggy = buggy;
        Container buggyInventory = buggy == null ? null : buggy.getVehicleInventory();
        this.container = buggyInventory != null && buggyInventory.getContainerSize() == storageSlots
                ? buggyInventory
                : new SimpleContainer(storageSlots);
        this.storageSlots = storageSlots;
        this.storageRows = (this.storageSlots + 8) / 9;
        this.container.startOpen(playerInventory.player);

        for (int slot = 0; slot < this.storageSlots; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            this.addSlot(new Slot(this.container, slot, 8 + column * 18, 50 + row * 18));
        }

        int playerInventoryY = 63 + this.storageRows * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, playerInventoryY + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18,
                    playerInventoryY + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < this.storageSlots) {
            if (!this.moveItemStackTo(stack, this.storageSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.storageSlots == 0
                || !this.moveItemStackTo(stack, 0, this.storageSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.buggy != null && !this.buggy.isRemoved() && player.distanceToSqr(this.buggy) <= 64.0D;
    }

    public Buggy getBuggy() {
        return this.buggy;
    }

    public int getStorageRows() {
        return this.storageRows;
    }

    /**
     * Includes the authoritative storage size because the menu-open packet can be handled before
     * the client receives the buggy's latest variant entity data.
     */
    public record OpeningData(int entityId, int storageSlots) {
        public static final StreamCodec<ByteBuf, OpeningData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpeningData::entityId,
                ByteBufCodecs.VAR_INT, OpeningData::storageSlots,
                OpeningData::new
        );
    }
}
