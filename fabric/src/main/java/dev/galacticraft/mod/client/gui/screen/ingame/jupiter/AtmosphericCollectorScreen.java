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
import dev.galacticraft.mod.content.block.jupiter.machine.AtmosphericCollectorBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Gas rates beside the hydrogen and helium tanks, and the ambient pressure on a P1-P40 gauge. */
@Environment(EnvType.CLIENT)
public class AtmosphericCollectorScreen extends JupiterMachineScreen<AtmosphericCollectorBlockEntity> {
    /** The deepest layer's pressure, where the gauge is full. */
    private static final float MAX_PRESSURE = 40;

    public AtmosphericCollectorScreen(JupiterMachineMenu<AtmosphericCollectorBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "atmospheric_collector");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        this.text(graphics, Component.translatable(JupiterText.HYDROGEN_RATE, this.value(0)), 76, 20);
        this.text(graphics, Component.translatable(JupiterText.HELIUM_RATE, this.value(1)), 76, 31);
        float pressure = this.value(2) / 10F;
        this.text(graphics, Component.translatable(JupiterText.AMBIENT_PRESSURE, String.format("%.1f", pressure)), 76, 44);
        this.gauge(graphics, 76, 55, 90, pressure / MAX_PRESSURE, Fill.AMBER);
        this.status(graphics, 76, 69, 92);
    }
}
