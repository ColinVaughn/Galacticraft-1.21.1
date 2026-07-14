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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.network.c2s.AirlockPlayerNamePayload;
import dev.galacticraft.mod.screen.AirlockControllerMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.function.BooleanSupplier;

public class AirlockControllerScreen extends AbstractContainerScreen<AirlockControllerMenu> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/gui/air_lock_controller.png");
    private static final String[] DISTANCE_TRANSLATIONS = {
            Translations.Ui.AIRLOCK_DISTANCE_1,
            Translations.Ui.AIRLOCK_DISTANCE_2,
            Translations.Ui.AIRLOCK_DISTANCE_5,
            Translations.Ui.AIRLOCK_DISTANCE_10
    };

    private AirlockToggleButton redstoneButton;
    private AirlockToggleButton playerDistanceButton;
    private DistanceButton distanceButton;
    private AirlockToggleButton playerNameButton;
    private EditBox playerNameField;
    private AirlockToggleButton invertButton;
    private AirlockToggleButton horizontalButton;
    private boolean syncingPlayerName;

    public AirlockControllerScreen(AirlockControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 181;
        this.imageHeight = 139;
    }

    @Override
    protected void init() {
        super.init();
        this.redstoneButton = this.addRenderableWidget(new AirlockToggleButton(
                this.leftPos + 7, this.topPos + 18, 167,
                Component.translatable(Translations.Ui.AIRLOCK_REDSTONE_SIGNAL),
                this.menu::redstoneActivation,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_REDSTONE)));
        this.playerDistanceButton = this.addRenderableWidget(new AirlockToggleButton(
                this.leftPos + 7, this.topPos + 33, 91,
                Component.translatable(Translations.Ui.AIRLOCK_PLAYER_DISTANCE),
                this.menu::playerDistanceActivation,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_PLAYER_DISTANCE)));
        this.distanceButton = this.addRenderableWidget(new DistanceButton(
                this.leftPos + 99, this.topPos + 32, 75, 13,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_DISTANCE)));
        this.playerNameButton = this.addRenderableWidget(new AirlockToggleButton(
                this.leftPos + 23, this.topPos + 49, 151,
                Component.translatable(Translations.Ui.AIRLOCK_PLAYER_NAME),
                this.menu::playerNameMatches,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_PLAYER_NAME)));

        this.playerNameField = this.addRenderableWidget(new EditBox(
                this.font, this.leftPos + 29, this.topPos + 64, 110, 15,
                Component.translatable(Translations.Ui.AIRLOCK_PLAYER_NAME)));
        this.playerNameField.setMaxLength(16);
        this.playerNameField.setFilter(AirlockPlayerNamePayload::isValidName);
        this.playerNameField.setValue(this.menu.playerName());
        this.playerNameField.setResponder(name -> {
            if (!this.syncingPlayerName) {
                NetworkManager.sendToServer(new AirlockPlayerNamePayload(name));
            }
        });

        this.invertButton = this.addRenderableWidget(new AirlockToggleButton(
                this.leftPos + 7, this.topPos + 80, 167,
                Component.translatable(Translations.Ui.AIRLOCK_INVERT_SELECTION),
                this.menu::invertSelection,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_INVERT)));
        this.horizontalButton = this.addRenderableWidget(new AirlockToggleButton(
                this.leftPos + 7, this.topPos + 96, 167,
                Component.translatable(Translations.Ui.AIRLOCK_HORIZONTAL_MODE),
                this.menu::horizontalModeEnabled,
                () -> this.pressMenuButton(AirlockControllerMenu.BUTTON_HORIZONTAL)));
        this.updateControlState();
    }

    private void pressMenuButton(int buttonId) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) {
            return;
        }
        if (this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
            this.updateControlState();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!this.playerNameField.isFocused()) {
            String playerName = this.menu.playerName();
            if (!playerName.equals(this.playerNameField.getValue())) {
                this.syncingPlayerName = true;
                this.playerNameField.setValue(playerName);
                this.syncingPlayerName = false;
            }
        }
        this.updateControlState();
    }

    private void updateControlState() {
        boolean canEdit = this.menu.canEdit();
        boolean usesDistance = this.menu.playerDistanceActivation();
        this.redstoneButton.active = canEdit;
        this.playerDistanceButton.active = canEdit;
        this.distanceButton.active = canEdit && usesDistance;
        this.playerNameButton.active = canEdit && usesDistance;
        this.playerNameField.setEditable(canEdit && usesDistance && this.menu.playerNameMatches());
        this.invertButton.active = canEdit;
        this.horizontalButton.active = canEdit;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 256, 256);
        graphics.blit(TEXTURE, this.leftPos + 11, this.topPos + 51, 181, 0,
                7, 9, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font, this.title, this.imageWidth / 2, 6, 0xFF404040);

        Component statusLabel = Component.translatable(Translations.Ui.AIRLOCK_STATUS);
        graphics.drawCenteredString(this.font, statusLabel, this.imageWidth / 2, 113, 0xFF404040);
        Component status = Component.translatable(this.menu.active()
                ? Translations.Ui.AIRLOCK_CLOSED
                : Translations.Ui.AIRLOCK_OPEN);
        graphics.drawCenteredString(this.font, status, this.imageWidth / 2, 124,
                this.menu.active() ? 0xFFAA2020 : 0xFF208A20);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private static class AirlockToggleButton extends AbstractButton {
        private final BooleanSupplier selected;

        AirlockToggleButton(int x, int y, int width, Component message, BooleanSupplier selected, Runnable onPress) {
            super(x, y, width, 12, message);
            this.selected = selected;
            this.onPress = onPress;
        }

        private final Runnable onPress;

        @Override
        public void onPress() {
            this.onPress.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int boxColor = this.active ? 0xFF373737 : 0xFF6A6A6A;
            int fillColor = this.active ? 0xFFC6C6C6 : 0xFF9A9A9A;
            graphics.fill(this.getX(), this.getY() + 1, this.getX() + 10, this.getY() + 11, boxColor);
            graphics.fill(this.getX() + 1, this.getY() + 2, this.getX() + 9, this.getY() + 10, fillColor);
            if (this.selected.getAsBoolean()) {
                int checkColor = this.active ? 0xFF2E8B36 : 0xFF5F735F;
                graphics.fill(this.getX() + 2, this.getY() + 5, this.getX() + 4, this.getY() + 8, checkColor);
                graphics.fill(this.getX() + 4, this.getY() + 7, this.getX() + 6, this.getY() + 9, checkColor);
                graphics.fill(this.getX() + 6, this.getY() + 3, this.getX() + 8, this.getY() + 8, checkColor);
            }

            int textColor = !this.active ? 0xFF777777 : this.isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFF404040;
            graphics.drawString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + 14, this.getY() + 2, textColor, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private class DistanceButton extends AbstractButton {
        private final Runnable onPress;

        DistanceButton(int x, int y, int width, int height, Runnable onPress) {
            super(x, y, width, height, Component.empty());
            this.onPress = onPress;
        }

        @Override
        public Component getMessage() {
            return Component.translatable(
                    DISTANCE_TRANSLATIONS[AirlockControllerScreen.this.menu.playerDistanceSelection()]);
        }

        @Override
        public void onPress() {
            this.onPress.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = this.active && this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF373737;
            int fill = this.active ? 0xFF8A8A8A : 0xFF777777;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, border);
            graphics.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.width - 1, this.getY() + this.height - 1, fill);
            graphics.drawCenteredString(AirlockControllerScreen.this.font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + 2, this.active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
