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

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.TerraformerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class TerraformerMenu extends MachineMenu<TerraformerBlockEntity> {
    public boolean treesDisabled;
    public boolean grassDisabled;
    public boolean bubbleVisible;
    public double bubbleSize;
    public int terraformableBlockCount;
    public int grassBlockCount;
    public int disableCooldown;

    public TerraformerMenu(int syncId, Player player, TerraformerBlockEntity machine) {
        super(GCMenuTypes.TERRAFORMER, syncId, player, machine);
        this.copyFromMachine();
    }

    public TerraformerMenu(int syncId, Inventory inventory, BlockPos pos) {
        super(GCMenuTypes.TERRAFORMER, syncId, inventory, pos, 8, 155);
    }

    private void copyFromMachine() {
        this.treesDisabled = this.be.areTreesDisabled();
        this.grassDisabled = this.be.isGrassDisabled();
        this.bubbleVisible = this.be.isBubbleVisible();
        this.bubbleSize = this.be.getBubbleSize();
        this.terraformableBlockCount = this.be.getTerraformableBlockCount();
        this.grassBlockCount = this.be.getGrassBlockCount();
        this.disableCooldown = this.be.getDisableCooldown();
    }

    @Override
    public void registerData(@NotNull MenuData data) {
        super.registerData(data);
        data.registerBoolean(this.be::areTreesDisabled, value -> this.treesDisabled = value);
        data.registerBoolean(this.be::isGrassDisabled, value -> this.grassDisabled = value);
        data.registerBoolean(this.be::isBubbleVisible, value -> this.bubbleVisible = value);
        data.registerDouble(this.be::getBubbleSize, value -> this.bubbleSize = value);
        data.registerInt(this.be::getTerraformableBlockCount, value -> this.terraformableBlockCount = value);
        data.registerInt(this.be::getGrassBlockCount, value -> this.grassBlockCount = value);
        data.registerInt(this.be::getDisableCooldown, value -> this.disableCooldown = value);
    }
}
