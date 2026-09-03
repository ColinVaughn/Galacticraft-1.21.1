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
import dev.galacticraft.mod.content.block.entity.machine.RadarBlockEntity;
import dev.galacticraft.mod.screen.RadarMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class RadarScreen extends MachineScreen<RadarBlockEntity, RadarMenu> {
    private static final int FRAME = 0xFF353934;
    private static final int FRAME_LIGHT = 0xFF555B53;
    private static final int FRAME_DARK = 0xFF171A18;
    private static final int SCREEN = 0xFF07110C;
    private static final int GRID = 0xFF254332;
    private static final int PHOSPHOR = 0xFF78B58A;
    private static final int TEXT = 0xFFC8CABD;
    private static final int MUTED = 0xFF747B71;
    private static final int AMBER = 0xFFE0A14B;
    private static final int DANGER = 0xFFDF6552;

    public RadarScreen(RadarMenu menu, Inventory inventory, Component title) {
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
        graphics.fill(x + 118, y + 36, x + 218, y + 133, 0xFF111511);
        graphics.fill(x + 8, y + 140, x + 220, y + 150, FRAME_DARK);

        graphics.fill(x + 8, y + 8, x + 10, y + 10, MUTED);
        graphics.fill(x + 218, y + 8, x + 220, y + 10, MUTED);
        graphics.fill(x + 8, y + 146, x + 10, y + 148, MUTED);
        graphics.fill(x + 218, y + 146, x + 220, y + 148, MUTED);
        graphics.fill(x + 180, y + 11, x + 183, y + 14, PHOSPHOR);

        int centerX = x + 58;
        int centerY = y + 84;
        for (int radius : new int[]{15, 30, 44}) drawCircle(graphics, centerX, centerY, radius, GRID);
        graphics.hLine(centerX - 44, centerX + 44, centerY, GRID);
        graphics.vLine(centerX, centerY - 44, centerY + 44, GRID);
        graphics.hLine(centerX - 3, centerX + 3, centerY - 44, PHOSPHOR);
        graphics.vLine(centerX + 44, centerY - 3, centerY + 3, PHOSPHOR);
        graphics.hLine(centerX - 3, centerX + 3, centerY + 44, PHOSPHOR);
        graphics.vLine(centerX - 44, centerY - 3, centerY + 3, PHOSPHOR);

        if (this.menu.isPowered()) {
            double sweep = (System.currentTimeMillis() % 8000L) * Math.PI * 2.0 / 8000.0;
            drawSweep(graphics, centerX, centerY, sweep - 0.12, 0xFF1C4F34);
            drawSweep(graphics, centerX, centerY, sweep - 0.06, 0xFF397451);
            drawSweep(graphics, centerX, centerY, sweep, PHOSPHOR);
        }

        int range = this.menu.getDetectionRange();
        for (int i = 0; i < this.menu.getTrackCount(); i++) {
            RadarMenu.TrackDisplay track = this.menu.getTrack(i);
            int dx = track.impactX() - this.menu.getRadarPos().getX();
            int dz = track.impactZ() - this.menu.getRadarPos().getZ();
            int blipX = centerX + Mth.clamp(dx * 44 / range, -44, 44);
            int blipY = centerY + Mth.clamp(dz * 44 / range, -44, 44);
            graphics.fill(blipX - 1, blipY - 1, blipX + 2, blipY + 2, DANGER);
            graphics.hLine(blipX - 3, blipX + 3, blipY - 3, AMBER);
            graphics.hLine(blipX - 3, blipX + 3, blipY + 3, AMBER);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos, this.topPos, 0);
        graphics.drawString(this.font, this.title, 13, 10, TEXT, false);
        boolean powered = this.menu.isPowered();
        graphics.drawString(this.font, Component.translatable(
                        powered ? Translations.Ui.MACHINE_ONLINE : Translations.Ui.MACHINE_OFFLINE), 186, 9,
                powered ? PHOSPHOR : DANGER, false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_ARRAY,
                twoDigits(this.menu.getLinkedRadarCount()), this.menu.getDetectionRange()), 13, 20, MUTED, false);
        drawRight(graphics, Component.translatable(Translations.Ui.RADAR_TRACK_COUNT,
                twoDigits(this.menu.getTrackCount())), 215, 20, MUTED);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_NORTH), 55, 39, PHOSPHOR,
                false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_RANGE,
                this.menu.getDetectionRange()), 76, 123, MUTED, false);

        if (!powered) {
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_SCAN_STATUS), 123, 43,
                    MUTED, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.MACHINE_NO_POWER), 123, 60,
                    DANGER, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_CONNECT_ENERGY), 123, 73,
                    TEXT, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_SWEEP_STOPPED), 123, 116,
                    MUTED, false);
        } else if (this.menu.getTrackCount() == 0) {
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_SCAN_STATUS), 123, 43,
                    MUTED, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_ALL_CLEAR), 123, 60,
                    PHOSPHOR, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_NO_INBOUND_TRACKS), 123, 73,
                    TEXT, false);
            graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_SWEEP_ACTIVE), 123, 116,
                    MUTED, false);
        } else {
            for (int i = 0; i < this.menu.getTrackCount(); i++) {
                RadarMenu.TrackDisplay track = this.menu.getTrack(i);
                int y = 41 + i * 22;
                Component type = Component.translatable(switch (track.type()) {
                    case STONY -> Translations.Ui.RADAR_CLASS_STONY;
                    case IRON -> Translations.Ui.RADAR_CLASS_IRON;
                    case PALLASITE -> Translations.Ui.RADAR_CLASS_PALLASITE;
                });
                graphics.drawString(this.font, Component.translatable(Translations.Ui.RADAR_CONTACT,
                        twoDigits(i + 1), type), 122, y, DANGER, false);
                drawRight(graphics, Component.translatable(Translations.Ui.RADAR_ETA,
                        String.format("%.1f", track.ticksToImpact() / 20.0)), 214, y, AMBER);
                graphics.drawString(this.font,
                        Component.literal(track.impactX() + "," + track.impactZ()), 122, y + 10, MUTED, false);
                drawRight(graphics, Component.literal("+/-" + track.uncertaintyRadius()), 214, y + 10, MUTED);
            }
        }

        int energyPercent = this.menu.getEnergy() * 100 / this.menu.getEnergyCapacity();
        graphics.drawString(this.font, Component.translatable(Translations.Ui.MACHINE_POWER, energyPercent), 13,
                141, powered ? PHOSPHOR : DANGER, false);
        int cannons = this.menu.getLinkedCannonCount();
        drawRight(graphics, Component.translatable(Translations.Ui.RADAR_FIRE_CONTROL, twoDigits(cannons)), 215,
                141, cannons > 0 ? PHOSPHOR : MUTED);
        graphics.pose().popPose();
    }

    @Override
    protected void drawTitle(GuiGraphics graphics) {
    }

    private static void drawCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 3) {
            double angle = Math.toRadians(degrees);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawSweep(GuiGraphics graphics, int centerX, int centerY, double angle, int color) {
        drawLine(graphics, centerX, centerY, centerX + (int) (Math.cos(angle) * 44),
                centerY + (int) (Math.sin(angle) * 44), color);
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = Mth.lerpInt((float) i / steps, x0, x1);
            int y = Mth.lerpInt((float) i / steps, y0, y1);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
