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

package dev.galacticraft.mod.client.gui.screen.ingame.jupiter;

import dev.galacticraft.mod.content.block.jupiter.JupiterText;
import dev.galacticraft.mod.content.block.jupiter.machine.CondensateSeparatorBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Condensate in, crystals and water out; the forked arrow fills white as each batch separates. */
@Environment(EnvType.CLIENT)
public class CondensateSeparatorScreen extends JupiterMachineScreen<CondensateSeparatorBlockEntity> {
    private static final int ARROW_X = 60, ARROW_Y = 20, ARROW_U = 176, ARROW_WIDTH = 33, ARROW_HEIGHT = 31;

    public CondensateSeparatorScreen(JupiterMachineMenu<CondensateSeparatorBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "condensate_separator");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        int progress = this.value(0), max = Math.max(1, this.value(1));
        int filled = ARROW_WIDTH * progress / max;
        if (filled > 0) graphics.blit(texture("condensate_separator"), x + ARROW_X, y + ARROW_Y, ARROW_U, 0, filled, ARROW_HEIGHT);
        this.tooltip(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, Component.translatable(JupiterText.SEPARATING, 100 * progress / max));
        this.status(graphics, 30, 68, 140);
    }
}
