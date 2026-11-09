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
import dev.galacticraft.mod.content.block.jupiter.machine.PressureStabilizerBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A sealed lamp and the habitat's volume and rating, or what is keeping the habitat from sealing. */
@Environment(EnvType.CLIENT)
public class PressureStabilizerScreen extends JupiterMachineScreen<PressureStabilizerBlockEntity> {
    public PressureStabilizerScreen(JupiterMachineMenu<PressureStabilizerBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "pressure_stabilizer");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        // The result travels in the readout: the client's status is a copy, so it cannot be compared by identity.
        var results = PressureStabilizerBlockEntity.Result.values();
        var result = results[Math.floorMod(this.value(3), results.length)];
        boolean fault = result == PressureStabilizerBlockEntity.Result.UNRATED_WALL
                || result == PressureStabilizerBlockEntity.Result.LEAK
                || result == PressureStabilizerBlockEntity.Result.NO_HABITAT;
        this.lamp(graphics, 32, 19, this.working() ? Lamp.GREEN : fault ? Lamp.RED : Lamp.OFF);
        this.statusText(graphics, 45, 20, 125);

        int volume = this.value(0), max = Math.max(1, this.value(1));
        this.text(graphics, Component.translatable(JupiterText.HABITAT_VOLUME, volume, max), 32, 31);
        this.gauge(graphics, 32, 42, 134, volume / (float) max, Fill.GREEN);
        this.text(graphics, Component.translatable(JupiterText.HABITAT_RATING, this.value(2)), 32, 54);

        if (result == PressureStabilizerBlockEntity.Result.UNRATED_WALL) {
            String name = this.menu.lines.isEmpty() ? "" : this.menu.lines.get(0);
            this.clipped(graphics, Component.translatable(JupiterText.HABITAT_WALL, name).getString(), 32, 64, 136, FAULT);
            this.text(graphics, Component.translatable(JupiterText.HABITAT_WALL_AT, this.value(4), this.value(5), this.value(6)), 32, 73, FAULT);
            this.tooltip(32, 63, 136, 18, Component.translatable(JupiterText.HABITAT_WALL_HINT));
        } else if (result == PressureStabilizerBlockEntity.Result.LEAK) {
            this.wrapped(graphics, Component.translatable(JupiterText.HABITAT_LEAK, 16), 32, 64, 136, 2, FAULT);
        } else if (result == PressureStabilizerBlockEntity.Result.NO_HABITAT) {
            this.wrapped(graphics, Component.translatable(JupiterText.HABITAT_NONE), 32, 64, 136, 2, FAULT);
        }
    }
}
