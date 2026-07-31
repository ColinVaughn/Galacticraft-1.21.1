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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.TerraformerBlockEntity;
import dev.galacticraft.mod.network.c2s.TerraformerTogglePayload;
import dev.galacticraft.mod.screen.TerraformerMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TerraformerScreen extends MachineScreen<TerraformerBlockEntity, TerraformerMenu> {
    private static final int BUTTON_X = 98;
    private static final int TREE_BUTTON_Y = 85;
    private static final int GRASS_BUTTON_Y = 109;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CHECKBOX_X = 85;
    private static final int CHECKBOX_Y = 132;
    private static final int CHECKBOX_WIDTH = 85;
    private static final int CHECKBOX_HEIGHT = 13;

    private Button treeButton;
    private Button grassButton;
    private int localCooldown;

    public TerraformerScreen(TerraformerMenu menu, Inventory inventory, Component title) {
        super(menu, title, Constant.ScreenTexture.TERRAFORMER_SCREEN);
        this.imageWidth = 176;
        this.imageHeight = 237;
        this.titleLabelY = 5;
        this.capacitorHeight = 0;
    }

    @Override
    protected void init() {
        super.init();
        this.treeButton = this.addRenderableWidget(Button.builder(this.treeButtonText(), button -> {
                    this.menu.treesDisabled = !this.menu.treesDisabled;
                    this.localCooldown = 10;
                    NetworkManager.sendToServer(new TerraformerTogglePayload((byte) 0));
                })
                .bounds(this.leftPos + BUTTON_X, this.topPos + TREE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.grassButton = this.addRenderableWidget(Button.builder(this.grassButtonText(), button -> {
                    this.menu.grassDisabled = !this.menu.grassDisabled;
                    this.localCooldown = 10;
                    NetworkManager.sendToServer(new TerraformerTogglePayload((byte) 1));
                })
                .bounds(this.leftPos + BUTTON_X, this.topPos + GRASS_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.localCooldown > 0) {
            this.localCooldown--;
        }
        this.treeButton.setMessage(this.treeButtonText());
        this.grassButton.setMessage(this.grassButtonText());
        boolean active = this.localCooldown <= 0 && this.menu.disableCooldown <= 0;
        this.treeButton.active = active;
        this.grassButton.active = active;
    }

    private Component treeButtonText() {
        return Component.translatable(this.menu.treesDisabled
                ? Translations.Ui.TERRAFORMER_ENABLE_TREES
                : Translations.Ui.TERRAFORMER_DISABLE_TREES);
    }

    private Component grassButtonText() {
        return Component.translatable(this.menu.grassDisabled
                ? Translations.Ui.TERRAFORMER_ENABLE_GRASS
                : Translations.Ui.TERRAFORMER_DISABLE_GRASS);
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        long capacity = this.menu.energyStorage.getCapacity();
        int energyWidth = capacity == 0 ? 0
                : (int) Math.min(54L, this.menu.energyStorage.getAmount() * 54L / capacity);
        if (energyWidth > 0) {
            graphics.blit(Constant.ScreenTexture.TERRAFORMER_SCREEN,
                    this.leftPos + 45, this.topPos + 48, 176, 26, energyWidth, 7);
        }

        int x = this.leftPos + CHECKBOX_X;
        int y = this.topPos + CHECKBOX_Y;
        graphics.fill(x, y, x + 12, y + 12, 0xFF303030);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, 0xFFC0C0C0);
        if (this.menu.bubbleVisible) {
            graphics.drawString(this.font, "x", x + 3, y + 1, 0xFF208020, false);
        }
        graphics.drawString(this.font, Component.translatable(Translations.Ui.TERRAFORMER_BUBBLE_VISIBLE),
                x + 15, y + 2, 0xFF404040, false);
    }

    @Override
    protected void drawTanks(GuiGraphics graphics, int mouseX, int mouseY) {
        long amount = this.menu.fluidStorage.slot(TerraformerBlockEntity.WATER_TANK).getAmount();
        int waterHeight = (int) (amount * 26L / TerraformerBlockEntity.MAX_WATER);
        if (waterHeight > 0) {
            graphics.blit(Constant.ScreenTexture.TERRAFORMER_SCREEN,
                    this.leftPos + 56, this.topPos + 17 + 27 - waterHeight,
                    176, 26 - waterHeight, 39, waterHeight);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + 8, this.topPos + 144,
                0xFF404040, false);
        graphics.drawWordWrap(this.font, this.menu.state.getStatusText(this.menu.redstoneMode),
                this.leftPos + 105, this.topPos + 24, 65, 0xFF404040);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseIn(mouseX, mouseY,
                this.leftPos + CHECKBOX_X, this.topPos + CHECKBOX_Y, CHECKBOX_WIDTH, CHECKBOX_HEIGHT)) {
            this.menu.bubbleVisible = !this.menu.bubbleVisible;
            NetworkManager.sendToServer(new TerraformerTogglePayload((byte) 2));
            this.playButtonSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
