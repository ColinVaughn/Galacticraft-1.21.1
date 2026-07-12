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

package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.FluidTankBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.galacticraft.machinelib.api.transfer.FluidConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@Environment(EnvType.CLIENT)
public class FluidTankScreen extends MachineScreen<FluidTankBlockEntity, MachineMenu<FluidTankBlockEntity>> {
    public FluidTankScreen(MachineMenu<FluidTankBlockEntity> handler, Inventory inv, Component title) {
        super(handler, title, Constant.ScreenTexture.OXYGEN_STORAGE_MODULE_SCREEN);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderMachineBackground(graphics, mouseX, mouseY, delta);

        FluidResourceSlot slot = this.menu.fluidStorage.slot(FluidTankBlockEntity.FLUID_TANK);
        double scale = (double) slot.getAmount() / (double) slot.getCapacity();
        graphics.blit(Constant.ScreenTexture.OXYGEN_STORAGE_MODULE_SCREEN, this.leftPos + 52, this.topPos + 57, 176, 0, (int) (72.0D * scale), 3);

        Component current = Component.literal(millibuckets(slot.getAmount()) + " mB");
        Component max = Component.literal(millibuckets(slot.getCapacity()) + " mB");
        graphics.drawString(this.font, current, this.leftPos + (this.imageWidth - this.font.width(current)) / 2, this.topPos + 20, ChatFormatting.DARK_GRAY.getColor(), false);
        graphics.drawString(this.font, max, this.leftPos + (this.imageWidth - this.font.width(max)) / 2, this.topPos + 45, ChatFormatting.DARK_GRAY.getColor(), false);
    }

    private static long millibuckets(long droplets) {
        return droplets * 1000L / FluidConstants.BUCKET;
    }
}
