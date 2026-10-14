/* Copyright (c) 2026 Team Galacticraft. GC4 GUI assets retain their original license. */
package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.GeothermalGeneratorBlockEntity;
import dev.galacticraft.mod.screen.GeothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;

public class GeothermalGeneratorScreen extends MachineScreen<GeothermalGeneratorBlockEntity, GeothermalGeneratorMenu> {
    private static final ResourceLocation BACKGROUND = Constant.id("textures/gui/geothermal.png");
    private Button toggle;

    public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, title, BACKGROUND);
        imageHeight = 201;
        capacitorHeight = 0;
    }

    @Override protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.translatable("ui.galacticraft.geothermal.disable"),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 52, topPos + 82, 72, 20).build());
    }

    @Override protected void containerTick() {
        super.containerTick();
        toggle.setMessage(Component.translatable(menu.disabled ? "ui.galacticraft.geothermal.enable" : "ui.galacticraft.geothermal.disable"));
        toggle.active = menu.cooldown == 0;
    }

    @Override protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (menu.validSpout) {
            int height = 16 * menu.generation / (GeothermalGeneratorBlockEntity.MAX_GENERATION - 1);
            int frame = (int) ((minecraft.level.getGameTime() / 3) % 7);
            graphics.blit(BACKGROUND, leftPos + 33, topPos + 37 - height, 176, 26 + frame * 16 - height, 46, height);
        }
    }

    @Override protected void drawCapacitor(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = (int) Math.clamp(54 * menu.energyStorage.getAmount() / menu.energyStorage.getCapacity(), 0, 54);
        graphics.blit(BACKGROUND, leftPos + 97, topPos + 25, 187, 0, width, 7);
        if (width > 0) graphics.blit(BACKGROUND, leftPos + 83, topPos + 24, 176, 0, 11, 10);
        if (mouseIn(mouseX, mouseY, leftPos + 96, topPos + 24, 56, 9)) {
            var tooltip = new ArrayList<Component>();
            appendEnergyTooltip(tooltip);
            setTooltipForNextRenderPass(tooltip.stream().map(Component::getVisualOrderText).toList());
        }
    }

    @Override protected void drawTitle(GuiGraphics graphics) {
        centered(graphics, title, 7);
        centered(graphics, Component.translatable("ui.galacticraft.geothermal.generating", menu.generation), 46);
        Component status = menu.disabled ? Component.translatable("ui.galacticraft.geothermal.disabled")
                : menu.state.getStatusText(menu.redstoneMode);
        centered(graphics, status, 57);
        if (!menu.disabled && !menu.validSpout) {
            centered(graphics, Component.translatable("ui.galacticraft.geothermal.spout_hint"), 68);
        }
        graphics.drawString(font, Component.translatable("container.inventory"), 8, 107, 0x404040, false);
    }

    private void centered(GuiGraphics graphics, Component text, int y) {
        graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, y, 0x404040, false);
    }
}
