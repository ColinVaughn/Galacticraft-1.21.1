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

import dev.galacticraft.mod.content.entity.vehicle.RocketCargoLogic;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RocketMenu extends AbstractContainerMenu {
    public final Player player;
    public final RocketEntity rocket;

    /** Cargo slots on show; the rocket's two reserved return slots are deliberately not among them. */
    public final int storageSlots;

    private final Container hold;

    protected RocketMenu(int syncId, Inventory playerInventory, int id) {
        this(syncId, playerInventory, playerInventory.player, (RocketEntity) playerInventory.player.level().getEntity(id));
    }

    public RocketMenu(int syncId, Inventory playerInventory, Player player, RocketEntity rocket) {
        super(GCMenuTypes.ROCKET, syncId);

        this.player = player;
        this.rocket = rocket;

        // Both sides resolve the rocket by entity id and read its synced parts, so the slot count
        // agrees without sending it separately.
        this.storageSlots = rocket == null ? 0 : rocket.getStorageSlotCount();
        this.hold = rocket == null ? new SimpleContainer(0) : rocket.getVehicleInventory();

        if (this.storageSlots > 0) {
            this.hold.startOpen(player);
        }

        // Rocket cargo
        for (int slot = 0; slot < this.storageSlots; ++slot) {
            int row = slot / RocketCargoLogic.SLOTS_PER_ROW;
            int column = slot % RocketCargoLogic.SLOTS_PER_ROW;
            this.addSlot(new Slot(this.hold, slot, 8 + column * 18, RocketCargoLogic.cargoRowY(row)));
        }

        int inventoryY = RocketCargoLogic.playerInventoryY(this.storageSlots);

        // Player main inv
        for (int slotY = 0; slotY < 3; ++slotY) {
            for (int slotX = 0; slotX < 9; ++slotX) {
                this.addSlot(new Slot(playerInventory, slotX + (slotY + 1) * 9, 8 + slotX * 18, inventoryY + slotY * 18));
            }
        }

        // Player hotbar
        for (int slotY = 0; slotY < 9; ++slotY) {
            this.addSlot(new Slot(playerInventory, slotY, 8 + slotY * 18, RocketCargoLogic.hotbarY(this.storageSlots)));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (this.storageSlots == 0) return ItemStack.EMPTY;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < this.storageSlots) {
                if (!this.moveItemStackTo(stack, this.storageSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, this.storageSlots, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.storageSlots > 0) {
            this.hold.stopOpen(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getVehicle() instanceof RocketEntity;
    }
}
