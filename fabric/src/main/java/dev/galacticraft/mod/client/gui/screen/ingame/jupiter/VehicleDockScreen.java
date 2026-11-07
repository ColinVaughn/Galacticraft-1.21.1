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

import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.mod.content.block.jupiter.JupiterText;
import dev.galacticraft.mod.content.block.jupiter.machine.VehicleDockBlockEntity;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A docked lamp and the serviced Cloud Diver's hull, battery and cabin oxygen, each on a gauge. */
@Environment(EnvType.CLIENT)
public class VehicleDockScreen extends JupiterMachineScreen<VehicleDockBlockEntity> {
    private static final int HULL = 300, DAMAGED_HULL = 90;

    public VehicleDockScreen(JupiterMachineMenu<VehicleDockBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "vehicle_dock");
    }

    @Override
    protected void drawReadout(GuiGraphics graphics, int x, int y) {
        boolean docked = this.value(0) != 0;
        int hull = this.value(1), power = this.value(2), oxygen = this.value(3);
        MachineStatus status = this.menu.state.getStatus();
        boolean unpowered = status != null && status.getType() == MachineStatus.Type.MISSING_ENERGY;

        if (!docked) {
            this.lamp(graphics, 54, 19, Lamp.OFF);
            this.text(graphics, Component.translatable(JupiterText.NO_VEHICLE), 66, 20);
            this.tooltip(54, 18, 118, 11, Component.translatable(JupiterText.NO_VEHICLE_HINT));
        } else if (unpowered) {
            this.lamp(graphics, 54, 19, Lamp.RED);
            this.statusText(graphics, 66, 20, 104);
        } else {
            this.lamp(graphics, 54, 19, Lamp.GREEN);
            this.text(graphics, Component.translatable(this.working() ? JupiterText.VEHICLE_DOCKED : JupiterText.VEHICLE_READY), 66, 20);
        }

        String none = "--";
        this.text(graphics, Component.translatable(JupiterText.VEHICLE_HULL, docked ? hull + "/" + HULL : none), 54, 29);
        this.gauge(graphics, 54, 39, 112, docked ? hull / (float) HULL : 0, hull <= DAMAGED_HULL ? Fill.RED : Fill.GREEN);
        this.text(graphics, Component.translatable(JupiterText.VEHICLE_POWER, docked ? power + "%" : none), 54, 47);
        this.gauge(graphics, 54, 57, 112, docked ? power / 100F : 0, Fill.AMBER);
        this.text(graphics, Component.translatable(JupiterText.VEHICLE_OXYGEN, docked ? oxygen + "%" : none), 54, 65);
        this.gauge(graphics, 54, 75, 112, docked ? oxygen / 100F : 0, Fill.BLUE);
    }
}
