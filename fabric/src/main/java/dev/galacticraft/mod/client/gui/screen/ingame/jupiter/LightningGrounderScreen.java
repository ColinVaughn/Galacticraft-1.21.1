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
import dev.galacticraft.mod.content.block.jupiter.machine.LightningGrounderBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The grounding mast, sparking while a storm is overhead, with its strike count and the storm on a gauge. */
@Environment(EnvType.CLIENT)
public class LightningGrounderScreen extends JupiterMachineScreen<LightningGrounderBlockEntity> {
    /** Above this the storm is overhead and the gauge turns from cyan to amber. */
    private static final int STORMY = 60;
    private static final int BOLT_U = 48, BOLT_V = 204, BOLT_WIDTH = 7, BOLT_HEIGHT = 11;

    public LightningGrounderScreen(JupiterMachineMenu<LightningGrounderBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lightning_grounder");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        int activity = this.value(2);
        if (activity > STORMY && (System.currentTimeMillis() / 120) % 3 != 0) {
            graphics.blit(texture("lightning_grounder"), x + 44, y + 13, BOLT_U, BOLT_V, BOLT_WIDTH, BOLT_HEIGHT);
        }
        this.text(graphics, Component.translatable(JupiterText.STRIKES, this.value(0)), 56, 20);
        this.text(graphics, Component.translatable(JupiterText.PROTECTED_RADIUS, this.value(1)), 56, 31);
        this.text(graphics, Component.translatable(JupiterText.STORM_ACTIVITY, activity), 56, 44);
        this.gauge(graphics, 56, 55, 110, activity / 100F, activity > STORMY ? Fill.AMBER : Fill.CYAN);
        this.status(graphics, 56, 69, 112);
    }
}
