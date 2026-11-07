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
import dev.galacticraft.mod.content.block.jupiter.machine.StormCapacitorBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Energy Storage Module's layout, with the charge beside the slots. */
@Environment(EnvType.CLIENT)
public class StormCapacitorScreen extends JupiterMachineScreen<StormCapacitorBlockEntity> {
    public StormCapacitorScreen(JupiterMachineMenu<StormCapacitorBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "storm_capacitor");
        this.capacitorX = 54;
        this.capacitorY = 20;
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        Component label = Component.translatable(JupiterText.CHARGE);
        Component percent = Component.literal(this.value(0) + "%");
        this.text(graphics, label, 146 - this.font.width(label) / 2, 32);
        this.text(graphics, percent, 146 - this.font.width(percent) / 2, 43);
    }
}
