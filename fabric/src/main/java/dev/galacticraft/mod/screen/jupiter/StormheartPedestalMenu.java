/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.screen.jupiter;

import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity;
import dev.galacticraft.mod.screen.GCMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The Stormheart Pedestal's control panel: encounter phase, relay count, and the Begin / Reset / Claim buttons. */
public class StormheartPedestalMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 4;
    private final @Nullable StormheartPedestalBlockEntity pedestal;
    private final ContainerData data;

    public StormheartPedestalMenu(int syncId, Inventory inventory) {
        this(syncId, inventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public StormheartPedestalMenu(int syncId, Inventory inventory, @Nullable StormheartPedestalBlockEntity pedestal, ContainerData data) {
        super(GCMenuTypes.STORMHEART_PEDESTAL, syncId);
        this.pedestal = pedestal;
        this.data = data;
        this.addDataSlots(data);
    }

    public StormheartPedestalBlock.Phase phase() {
        StormheartPedestalBlock.Phase[] phases = StormheartPedestalBlock.Phase.values();
        return phases[Math.floorMod(this.data.get(0), phases.length)];
    }

    public int relays() {
        return this.data.get(1);
    }

    public int disabledRelays() {
        return this.data.get(2);
    }

    public int participants() {
        return this.data.get(3);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.pedestal == null) return false;
        switch (id) {
            case StormheartPedestalBlockEntity.BUTTON_BEGIN -> this.pedestal.begin(player);
            case StormheartPedestalBlockEntity.BUTTON_RESET -> this.pedestal.reset(true);
            case StormheartPedestalBlockEntity.BUTTON_CLAIM -> this.pedestal.claim(player);
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.pedestal == null || (!this.pedestal.isRemoved() && player.distanceToSqr(this.pedestal.getBlockPos().getCenter()) <= 64);
    }
}
