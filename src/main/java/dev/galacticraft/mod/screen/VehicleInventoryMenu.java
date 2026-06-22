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

import dev.galacticraft.mod.content.entity.vehicle.ContainerVehicle;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A generic chest-like container menu shared by inventory-bearing vehicles
 * (the Astro Miner and the Cargo Rocket). The layout adapts to any container
 * size, laying the vehicle slots out in rows of nine above the player inventory
 * and drawing on the vanilla {@code generic_54} chest texture.
 *
 * <p>The opening data is simply the vehicle entity's id; both sides resolve the
 * backing {@link Container} from the entity, so the client sees a display of the
 * correct size while the server drives the authoritative container.</p>
 */
public class VehicleInventoryMenu extends AbstractContainerMenu {
    private final Container container;
    private final int containerRows;
    private final int vehicleSlotCount;

    public VehicleInventoryMenu(int syncId, Inventory playerInventory, int entityId) {
        this(syncId, playerInventory, resolveContainer(playerInventory.player.level(), entityId));
    }

    private static Container resolveContainer(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        if (entity instanceof ContainerVehicle vehicle) {
            return vehicle.getVehicleInventory();
        }
        // Fallback: an empty container keeps the menu robust if the entity has gone.
        return new SimpleContainer(0);
    }

    public VehicleInventoryMenu(int syncId, Inventory playerInventory, Container container) {
        super(GCMenuTypes.VEHICLE_INVENTORY, syncId);
        this.container = container;
        this.vehicleSlotCount = container.getContainerSize();
        this.containerRows = Math.max(1, (this.vehicleSlotCount + 8) / 9);
        container.startOpen(playerInventory.player);

        int extraRowOffset = (this.containerRows - 4) * 18;

        // Vehicle inventory slots (rows of nine, last row may be partial).
        int index = 0;
        for (int row = 0; row < this.containerRows && index < this.vehicleSlotCount; ++row) {
            for (int col = 0; col < 9 && index < this.vehicleSlotCount; ++col) {
                this.addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18));
                index++;
            }
        }

        // Player main inventory.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + extraRowOffset));
            }
        }

        // Player hotbar.
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + extraRowOffset));
        }
    }

    public int getRowCount() {
        return this.containerRows;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            if (slotIndex < this.vehicleSlotCount) {
                if (!this.moveItemStackTo(slotStack, this.vehicleSlotCount, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(slotStack, 0, this.vehicleSlotCount, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
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
        this.container.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    public Container getContainer() {
        return this.container;
    }
}
