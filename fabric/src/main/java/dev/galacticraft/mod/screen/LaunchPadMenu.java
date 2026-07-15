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

import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class LaunchPadMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 4;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public LaunchPadMenu(int syncId, Inventory inventory, BlockPos pos) {
        this(syncId, inventory, pos, new SimpleContainerData(DATA_COUNT));
    }

    public LaunchPadMenu(int syncId, Inventory inventory, LaunchPadBlockEntity pad) {
        this(syncId, inventory, pad.getBlockPos(), pad.createRouteData());
    }

    private LaunchPadMenu(int syncId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(GCMenuTypes.LAUNCH_PAD, syncId);
        this.pos = pos.immutable();
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        checkContainerDataCount(data, DATA_COUNT);
        this.addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, blockPos) ->
                level.getBlockEntity(blockPos) instanceof LaunchPadBlockEntity
                        && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D,
                        blockPos.getZ() + 0.5D) <= 64.0D, false);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getAddress() {
        return this.data.get(0);
    }

    public int getDestinationAddress() {
        return this.data.get(1);
    }

    public boolean hasAddress() {
        return this.data.get(2) != 0;
    }

    public boolean hasValidDestination() {
        return this.data.get(3) != 0;
    }
}
