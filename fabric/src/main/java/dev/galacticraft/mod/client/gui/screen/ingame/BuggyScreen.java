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

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.screen.BuggyMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BuggyScreen extends AbstractContainerScreen<BuggyMenu> {
    private static final ResourceLocation[] TEXTURES = {
            Constant.id("textures/gui/buggy_0.png"),
            Constant.id("textures/gui/buggy_18.png"),
            Constant.id("textures/gui/buggy_36.png")
    };

    public BuggyScreen(BuggyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 145 + menu.getStorageRows() * 18;
        this.inventoryLabelY = 52 + menu.getStorageRows() * 18;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int variant = Math.min(TEXTURES.length - 1, this.menu.getStorageRows() / 2);
        graphics.blit(TEXTURES[variant], this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 256, 256);

        Buggy buggy = this.menu.getBuggy();
        if (buggy != null) {
            int fuelLevel = Math.round(buggy.getScaledFuelLevel(38));
            if (fuelLevel > 0) {
                graphics.blit(TEXTURES[variant], this.leftPos + 72, this.topPos + 45 - fuelLevel,
                        176, 38 - fuelLevel, 42, fuelLevel, 256, 256);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable(Translations.Ui.BUGGY_FUEL), 8, 5, 0xFF404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                0xFF404040, false);

        Buggy buggy = this.menu.getBuggy();
        int percentage = buggy == null ? 0 : Math.round(buggy.getScaledFuelLevel(100));
        int color = percentage > 80 ? 0xFF20A020 : percentage > 40 ? 0xFFD08020 : 0xFFB02020;
        graphics.drawString(this.font, Component.translatable(Translations.Ui.BUGGY_FUEL_PERCENT, percentage),
                116, 18, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
