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
import dev.galacticraft.mod.content.block.special.launchpad.CargoPadRegistry;
import dev.galacticraft.mod.network.c2s.LaunchPadRoutePayload;
import dev.galacticraft.mod.screen.LaunchPadMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LaunchPadScreen extends AbstractContainerScreen<LaunchPadMenu> {
    private EditBox addressField;
    private EditBox destinationField;
    private Button saveButton;

    public LaunchPadScreen(LaunchPadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 210;
        this.imageHeight = 128;
    }

    @Override
    protected void init() {
        super.init();
        this.addressField = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 105,
                this.topPos + 30, 88, 18, Component.translatable(Translations.Ui.LAUNCH_PAD_ADDRESS)));
        this.destinationField = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 105,
                this.topPos + 57, 88, 18, Component.translatable(Translations.Ui.LAUNCH_PAD_DESTINATION)));
        this.addressField.setMaxLength(6);
        this.destinationField.setMaxLength(6);
        this.addressField.setFilter(LaunchPadScreen::isAddressText);
        this.destinationField.setFilter(LaunchPadScreen::isAddressText);
        if (this.menu.hasAddress()) this.addressField.setValue(Integer.toString(this.menu.getAddress()));
        if (this.menu.getDestinationAddress() >= 0) {
            this.destinationField.setValue(Integer.toString(this.menu.getDestinationAddress()));
        }
        this.addressField.setResponder(value -> this.updateSaveButton());
        this.destinationField.setResponder(value -> this.updateSaveButton());
        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable(Translations.Ui.LAUNCH_PAD_SAVE), button -> this.saveRoute())
                .bounds(this.leftPos + 65, this.topPos + 91, 80, 20).build());
        this.updateSaveButton();
    }

    private static boolean isAddressText(String text) {
        return text.isEmpty() || text.length() <= 6 && text.chars().allMatch(Character::isDigit);
    }

    private void updateSaveButton() {
        if (this.saveButton == null) return;
        int address = this.parseAddress(this.addressField.getValue(), -1);
        int destination = this.parseAddress(this.destinationField.getValue(), -1);
        this.saveButton.active = CargoPadRegistry.isValidAddress(address)
                && (destination == -1 || CargoPadRegistry.isValidAddress(destination))
                && address != destination;
    }

    private int parseAddress(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void saveRoute() {
        int address = this.parseAddress(this.addressField.getValue(), -1);
        int destination = this.parseAddress(this.destinationField.getValue(), -1);
        NetworkManager.sendToServer(new LaunchPadRoutePayload(address, destination));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight,
                0xEE24272B);
        graphics.fill(this.leftPos + 4, this.topPos + 4, this.leftPos + this.imageWidth - 4,
                this.topPos + this.imageHeight - 4, 0xFF3A3F45);
        graphics.fill(this.leftPos + 10, this.topPos + 20, this.leftPos + this.imageWidth - 10,
                this.topPos + 82, 0xFF202326);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 10, 8, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.LAUNCH_PAD_ADDRESS), 18, 35,
                0xD8D8D8, false);
        graphics.drawString(this.font, Component.translatable(Translations.Ui.LAUNCH_PAD_DESTINATION), 18, 62,
                0xD8D8D8, false);
        Component status = Component.translatable(this.menu.hasValidDestination()
                ? Translations.Ui.LAUNCH_PAD_ROUTE_READY : Translations.Ui.LAUNCH_PAD_ROUTE_UNAVAILABLE);
        graphics.drawCenteredString(this.font, status, this.imageWidth / 2, 116,
                this.menu.hasValidDestination() ? 0x55FF55 : 0xFFAA55);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
