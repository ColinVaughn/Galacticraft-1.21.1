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

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.content.block.entity.machine.OxygenCompressorBlockEntity;
import dev.galacticraft.mod.util.DrawableUtil;
import dev.galacticraft.mod.util.Translations;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import dev.galacticraft.mod.network.c2s.CompressionModePayload;
import dev.galacticraft.mod.screen.OxygenCompressorMenu;
import org.jetbrains.annotations.NotNull;

import static dev.galacticraft.mod.Constant.OxygenCompressor.*;

@Environment(EnvType.CLIENT)
public class OxygenCompressorScreen extends MachineScreen<OxygenCompressorBlockEntity, OxygenCompressorMenu> {
    public OxygenCompressorScreen(OxygenCompressorMenu menu, Inventory inventory, Component title) {
        super(menu, title, SCREEN_TEXTURE);
    }

    @Override
    protected void drawTitle(@NotNull GuiGraphics graphics) {
        graphics.drawString(this.font, this.menu.compressionMode ? this.title : Component.translatable(Translations.Ui.OXYGEN_COMPRESSOR_DECOMPRESSOR_TITLE), this.titleLabelX, this.titleLabelY, 0xFF404040, false);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX += 18;
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderMachineBackground(graphics, mouseX, mouseY, delta);
        if (this.menu.state.isActive()) {
            int height = -(int) (System.currentTimeMillis() % 2250) / 125;
            DrawableUtil.drawProgressTexture(graphics.pose(), this.leftPos + 93, this.topPos + 64, 187, 18, -11, height);
        }

        int buttonX = this.leftPos + BUTTON_X;
        int buttonY = this.topPos + BUTTON_Y;
        int buttonU, buttonV;

        if (this.menu.compressionMode) {
            if (DrawableUtil.mouseIn(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                buttonU = BUTTON_GREEN_HOVER_U;
                buttonV = BUTTON_GREEN_HOVER_V;
            }
            else {
                buttonU = BUTTON_GREEN_U;
                buttonV = BUTTON_GREEN_V;
            }
        }
        else {
            if (DrawableUtil.mouseIn(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                buttonU = BUTTON_RED_HOVER_U;
                buttonV = BUTTON_RED_HOVER_V;
            }
            else {
                buttonU = BUTTON_RED_U;
                buttonV = BUTTON_RED_V;
            }
        }
        graphics.blit(SCREEN_TEXTURE, buttonX, buttonY, buttonU, buttonV, BUTTON_WIDTH, BUTTON_HEIGHT);

        graphics.drawString(this.font, "Compression",
                this.leftPos + TEXT_X, this.topPos + TEXT_Y, 0xFF404040, false);
        graphics.drawString(this.font, "Mode",
                this.leftPos + TEXT_X + 18, this.topPos + TEXT_Y + 12, 0xFF404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseIn(mouseX, mouseY,
                this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            this.menu.compressionMode = !this.menu.compressionMode;
            NetworkManager.sendToServer(CompressionModePayload.STATIC_INSTANCE);
            this.playButtonSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
