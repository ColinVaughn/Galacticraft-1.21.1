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
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity;
import dev.galacticraft.mod.screen.jupiter.StormheartPedestalMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/** Encounter control panel: phase, relay and participant counts, and the Begin / Reset / Claim buttons. */
@Environment(EnvType.CLIENT)
public class StormheartPedestalScreen extends AbstractContainerScreen<StormheartPedestalMenu> {
    private static final ResourceLocation TEXTURE = JupiterMachineScreen.texture("stormheart_pedestal");
    /** The lit lamp per phase, read left to right: ready, storm raging, core exposed. */
    private static final JupiterMachineScreen.Lamp[] LAMPS = {JupiterMachineScreen.Lamp.AMBER, JupiterMachineScreen.Lamp.RED, JupiterMachineScreen.Lamp.GREEN};
    private static final int LAMP_V = 204, LAMP_SIZE = 9, FILL_V = 168 + 7 * JupiterMachineScreen.Fill.GREEN.ordinal();
    private Button begin, reset, claim;

    public StormheartPedestalScreen(StormheartPedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 112;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.begin = this.addRenderableWidget(this.button(JupiterText.BEGIN, StormheartPedestalBlockEntity.BUTTON_BEGIN, 8));
        this.reset = this.addRenderableWidget(this.button(JupiterText.RESET, StormheartPedestalBlockEntity.BUTTON_RESET, 63));
        this.claim = this.addRenderableWidget(this.button(JupiterText.CLAIM, StormheartPedestalBlockEntity.BUTTON_CLAIM, 118));
    }

    private Button button(String key, int id, int x) {
        return Button.builder(Component.translatable(key), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }).bounds(this.leftPos + x, this.topPos + 82, 50, 20).build();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        StormheartPedestalBlock.Phase phase = this.menu.phase();
        this.begin.active = phase == StormheartPedestalBlock.Phase.IDLE;
        this.reset.active = phase != StormheartPedestalBlock.Phase.IDLE;
        this.claim.active = phase == StormheartPedestalBlock.Phase.CORE_EXPOSED;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        StormheartPedestalBlock.Phase phase = this.menu.phase();
        Component state = switch (phase) {
            case IDLE -> Component.translatable(JupiterText.ENCOUNTER_IDLE);
            case ACTIVE -> Component.translatable(JupiterText.ENCOUNTER_ACTIVE).withColor(JupiterMachineScreen.FAULT);
            case CORE_EXPOSED -> Component.translatable(JupiterText.ENCOUNTER_EXPOSED).withColor(JupiterMachineScreen.GOOD);
        };
        graphics.drawString(this.font, Component.translatable(JupiterText.ENCOUNTER_STATE, state), x + 11, y + 20, JupiterMachineScreen.TEXT, false);
        for (int i = 0; i < LAMPS.length; i++) {
            JupiterMachineScreen.Lamp lamp = i == phase.ordinal() ? LAMPS[i] : JupiterMachineScreen.Lamp.OFF;
            graphics.blit(TEXTURE, x + 135 + i * 12, y + 19, lamp.ordinal() * LAMP_SIZE, LAMP_V, LAMP_SIZE, LAMP_SIZE);
        }

        int relays = this.menu.relays(), disabled = this.menu.disabledRelays();
        graphics.drawString(this.font, Component.translatable(JupiterText.RELAYS, disabled, relays), x + 11, y + 31, JupiterMachineScreen.TEXT, false);
        int filled = relays == 0 ? 0 : Math.round(155F * Math.min(disabled, relays) / relays);
        if (filled > 0) graphics.blit(TEXTURE, x + 11, y + 41, 0, FILL_V, filled, 6);
        graphics.drawString(this.font, Component.translatable(JupiterText.PARTICIPANTS, this.menu.participants()), x + 11, y + 52, JupiterMachineScreen.TEXT, false);

        String hint = switch (phase) {
            case IDLE -> JupiterText.HINT_IDLE;
            case ACTIVE -> JupiterText.HINT_ACTIVE;
            case CORE_EXPOSED -> JupiterText.HINT_EXPOSED;
        };
        int row = y + 63;
        for (FormattedCharSequence line : this.font.split(Component.translatable(hint), 154).stream().limit(2).toList()) {
            graphics.drawString(this.font, line, x + 11, row, JupiterMachineScreen.ASIDE, false);
            row += 9;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, JupiterMachineScreen.TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
