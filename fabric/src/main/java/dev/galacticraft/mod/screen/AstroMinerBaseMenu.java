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

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Custom (non-machinelib) menu for the Astro Miner Base. Slot layout is ported 1:1 from
 * legacy {@code ContainerAstroMinerDock}: one battery/charge slot, a 6x12 ore hold and
 * the player inventory.
 */
@SuppressWarnings("UnstableApiUsage")
public class AstroMinerBaseMenu extends AbstractContainerMenu {
    /** Battery slot (0) + 72 hold slots. */
    public static final int CONTAINER_SIZE = AstroMinerBaseBlockEntity.INVENTORY_SIZE;

    private final Container container;
    /** Synced [energy, maxEnergy] so the client can draw the energy bar. */
    private final ContainerData data;

    /** Client-side factory: resolves the master base's hold by position. */
    public AstroMinerBaseMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, resolveContainer(playerInventory.player.level(), pos), new SimpleContainerData(2));
    }

    /** Server-side constructor bound to the block entity. */
    public AstroMinerBaseMenu(int syncId, Inventory playerInventory, AstroMinerBaseBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getHold(), new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? (int) blockEntity.getEnergyStored() : (int) blockEntity.getMaxEnergyStored();
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        });
    }

    private AstroMinerBaseMenu(int syncId, Inventory playerInventory, Container container, ContainerData data) {
        super(GCMenuTypes.ASTRO_MINER_BASE, syncId);
        checkContainerSize(container, CONTAINER_SIZE);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);
        this.addDataSlots(data);

        // Battery / charge slot (legacy x=230, y=108); only accepts energy items.
        this.addSlot(new Slot(container, 0, 230, 108) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ResourceFilters.CAN_EXTRACT_ENERGY.test(stack.getItem(), stack.getComponentsPatch());
            }
        });

        // Ore hold: 6 rows x 12 columns, slot index = col + row*12 + 1
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 12; col++) {
                this.addSlot(new Slot(container, col + row * 12 + 1, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 197));
        }
    }

    private static Container resolveContainer(Level level, BlockPos pos) {
        AstroMinerBaseBlockEntity be = GCBlockEntityTypes.ASTRO_MINER_BASE.getBlockEntity(level, pos);
        return be != null ? be.getHold() : new SimpleContainer(CONTAINER_SIZE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();
            if (index < CONTAINER_SIZE) {
                // from base container -> player inventory
                if (!this.moveItemStackTo(stackInSlot, CONTAINER_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (ResourceFilters.CAN_EXTRACT_ENERGY.test(stackInSlot.getItem(), stackInSlot.getComponentsPatch())) {
                // energy item -> battery slot
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, 1, CONTAINER_SIZE, false)) {
                // otherwise -> ore hold
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
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

    /** Current energy (synced from the server). */
    public int getEnergy() {
        return this.data.get(0);
    }

    /** Max energy capacity (synced from the server); never zero, so callers can divide. */
    public int getMaxEnergy() {
        return Math.max(1, this.data.get(1));
    }
}
