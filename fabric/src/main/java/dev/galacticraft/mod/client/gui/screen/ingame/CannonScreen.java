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

package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.CannonBlockEntity;
import dev.galacticraft.mod.screen.PowerPortMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CannonScreen extends MachineScreen<CannonBlockEntity, PowerPortMenu<CannonBlockEntity>> {
    private static final int FRAME = 0xFF353934;
    private static final int FRAME_LIGHT = 0xFF555B53;
    private static final int FRAME_DARK = 0xFF171A18;
    private static final int SCREEN = 0xFF111511;
    private static final int GRID = 0xFF254332;
    private static final int TEXT = 0xFFC8CABD;
    private static final int MUTED = 0xFF747B71;
    private static final int PHOSPHOR = 0xFF78B58A;
    private static final int AMBER = 0xFFE0A14B;
    private static final int DANGER = 0xFFDF6552;

    public CannonScreen(PowerPortMenu<CannonBlockEntity> menu, Inventory inventory, Component title) {
        super(menu, title, Constant.ScreenTexture.ENERGY_STORAGE_MODULE_SCREEN);
        this.imageWidth = 228;
        this.imageHeight = 156;
        this.capacitorHeight = 0;
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x + 3, y + 4, x + this.imageWidth + 3, y + this.imageHeight + 4, 0x80000000);
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, FRAME);
        graphics.hLine(x, x + this.imageWidth - 1, y, FRAME_LIGHT);
        graphics.vLine(x, y, y + this.imageHeight - 1, FRAME_LIGHT);
        graphics.hLine(x, x + this.imageWidth - 1, y + this.imageHeight - 1, FRAME_DARK);
        graphics.vLine(x + this.imageWidth - 1, y, y + this.imageHeight - 1, FRAME_DARK);

        graphics.fill(x + 5, y + 5, x + 223, y + 28, FRAME_DARK);
        graphics.fill(x + 8, y + 34, x + 109, y + 135, FRAME_DARK);
        graphics.fill(x + 10, y + 36, x + 107, y + 133, SCREEN);
        graphics.fill(x + 116, y + 34, x + 220, y + 135, 0xFF20241F);
        graphics.fill(x + 118, y + 36, x + 218, y + 133, SCREEN);
        graphics.fill(x + 8, y + 140, x + 220, y + 150, FRAME_DARK);

        graphics.fill(x + 8, y + 8, x + 10, y + 10, MUTED);
        graphics.fill(x + 218, y + 8, x + 220, y + 10, MUTED);
        graphics.fill(x + 8, y + 146, x + 10, y + 148, MUTED);
        graphics.fill(x + 218, y + 146, x + 220, y + 148, MUTED);

        int centerX = x + 58;
        int centerY = y + 84;
        graphics.hLine(centerX - 42, centerX + 42, centerY, GRID);
        graphics.vLine(centerX, centerY - 42, centerY + 42, GRID);
        graphics.hLine(centerX - 30, centerX - 12, centerY - 30, GRID);
        graphics.hLine(centerX + 12, centerX + 30, centerY - 30, GRID);
        graphics.hLine(centerX - 30, centerX - 12, centerY + 30, GRID);
        graphics.hLine(centerX + 12, centerX + 30, centerY + 30, GRID);
        graphics.vLine(centerX - 30, centerY - 30, centerY - 12, GRID);
        graphics.vLine(centerX - 30, centerY + 12, centerY + 30, GRID);
        graphics.vLine(centerX + 30, centerY - 30, centerY - 12, GRID);
        graphics.vLine(centerX + 30, centerY + 12, centerY + 30, GRID);

        if (this.menu.be.getTargetPosition() != null) {
            int bracket = 7 + (int) (System.currentTimeMillis() / 250L % 3L);
            graphics.hLine(centerX - bracket, centerX - 3, centerY - bracket, AMBER);
            graphics.hLine(centerX + 3, centerX + bracket, centerY - bracket, AMBER);
            graphics.hLine(centerX - bracket, centerX - 3, centerY + bracket, AMBER);
            graphics.hLine(centerX + 3, centerX + bracket, centerY + bracket, AMBER);
            graphics.vLine(centerX - bracket, centerY - bracket, centerY - 3, AMBER);
            graphics.vLine(centerX - bracket, centerY + 3, centerY + bracket, AMBER);
            graphics.vLine(centerX + bracket, centerY - bracket, centerY - 3, AMBER);
            graphics.vLine(centerX + bracket, centerY + 3, centerY + bracket, AMBER);
            graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, DANGER);
        }

        long capacity = Math.max(1L, this.menu.energyStorage.getCapacity());
        int energyWidth = (int) (this.menu.energyStorage.getAmount() * 210L / capacity);
        graphics.fill(x + 9, y + 141, x + 9 + energyWidth, y + 149, GRID);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos, this.topPos, 0);
        graphics.drawString(this.font, this.title, 13, 10, TEXT, false);
        long capacity = Math.max(1L, this.menu.energyStorage.getCapacity());
        long percent = this.menu.energyStorage.getAmount() * 100L / capacity;
        boolean online = this.menu.energyStorage.getAmount() >= CannonBlockEntity.TRACKING_ENERGY_PER_TICK
                && this.menu.redstoneMode.isActive(this.menu.state.isPowered());
        boolean target = this.menu.be.getTargetPosition() != null;
        drawRight(graphics, Component.translatable(
                online ? Translations.Ui.MACHINE_ONLINE : Translations.Ui.MACHINE_OFFLINE), 215, 9,
                online ? PHOSPHOR : DANGER);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.CANNON_HEADER,
                CannonBlockEntity.RANGE), 13, 20, MUTED, false);

        graphics.drawString(this.font, Component.translatable(Translations.Ui.CANNON_TARGETING), 37, 43, MUTED,
                false);
        drawRight(graphics, Component.translatable(
                target ? Translations.Ui.CANNON_LOCK : Translations.Ui.CANNON_SCAN), 101, 122,
                target ? AMBER : MUTED);

        graphics.drawString(this.font, Component.translatable(Translations.Ui.CANNON_WEAPON_STATUS), 123, 43,
                MUTED, false);
        graphics.drawString(this.font, Component.translatable(!online ? Translations.Ui.MACHINE_NO_POWER
                : target ? Translations.Ui.CANNON_TRACKING : Translations.Ui.CANNON_STANDBY), 123, 60,
                !online ? DANGER : target ? AMBER : PHOSPHOR, false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.CANNON_TARGET), 123, 79, MUTED,
                false);
        graphics.drawString(this.font, Component.translatable(
                target ? Translations.Ui.CANNON_CONTACT_LOCK : Translations.Ui.CANNON_NO_CONTACT), 123, 92,
                target ? TEXT : MUTED, false);
        boolean ready = this.menu.energyStorage.getAmount() >= CannonBlockEntity.SHOT_ENERGY;
        graphics.drawString(this.font, Component.translatable(Translations.Ui.CANNON_CAPACITOR), 123, 105, MUTED,
                false);
        graphics.drawString(this.font, Component.translatable(
                ready ? Translations.Ui.CANNON_READY : Translations.Ui.CANNON_CHARGING), 123, 116,
                ready ? PHOSPHOR : online ? AMBER : MUTED, false);

        graphics.drawString(this.font, Component.translatable(Translations.Ui.MACHINE_POWER, percent), 13, 141,
                online ? PHOSPHOR : DANGER, false);
        drawRight(graphics, Component.translatable(Translations.Ui.CANNON_AUTO_DEFENSE), 215, 141,
                online ? PHOSPHOR : MUTED);
        graphics.pose().popPose();
    }

    @Override
    protected void drawTitle(GuiGraphics graphics) {
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }
}
