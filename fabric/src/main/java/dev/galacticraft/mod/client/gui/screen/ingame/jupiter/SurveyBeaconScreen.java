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
import dev.galacticraft.mod.content.block.jupiter.machine.SurveyBeaconBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** This beacon's name and a ruled list of the nearest others broadcasting, with range and bearing. */
@Environment(EnvType.CLIENT)
public class SurveyBeaconScreen extends JupiterMachineScreen<SurveyBeaconBlockEntity> {
    /** Rows between the rules the texture etches at y 30, 41, 51 and 61. */
    private static final int[] ROWS = {33, 43, 53};

    public SurveyBeaconScreen(JupiterMachineMenu<SurveyBeaconBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "survey_beacon");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        var lines = this.menu.lines;
        this.lamp(graphics, 32, 19, this.working() ? Lamp.GREEN : Lamp.RED);
        this.clipped(graphics, Component.translatable(JupiterText.BEACON_NAME, lines.isEmpty() ? "" : lines.get(0)).getString(), 45, 20, 121, TEXT);

        if (!this.working()) {
            this.statusText(graphics, 32, ROWS[0], 134);
        } else if (lines.size() <= 1) {
            this.text(graphics, Component.translatable(JupiterText.NO_BEACONS), 32, ROWS[0], ASIDE);
        }
        for (int i = 1; this.working() && i < lines.size() && i <= ROWS.length; i++) {
            String[] parts = lines.get(i).split("\\|");
            Component where = Component.literal(parts.length >= 3 ? parts[1] + "m " + parts[2] : "");
            this.clipped(graphics, parts[0], 32, ROWS[i - 1], 134 - this.font.width(where) - 6, TEXT);
            this.textRight(graphics, where, 166, ROWS[i - 1], ASIDE);
        }
        this.text(graphics, Component.translatable(JupiterText.BEACON_RENAME_HINT), 32, 66, ASIDE);
    }
}
