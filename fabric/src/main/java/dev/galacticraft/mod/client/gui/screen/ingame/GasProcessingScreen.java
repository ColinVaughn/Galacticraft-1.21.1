/* Copyright (c) 2026 Team Galacticraft. GC4 GUI assets retain their original license. */
package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.block.entity.machine.GasLiquefierBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.GasProcessingBlockEntity;
import dev.galacticraft.mod.screen.GasProcessingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;

public class GasProcessingScreen<M extends GasProcessingBlockEntity> extends MachineScreen<M, GasProcessingMenu<M>> {
    private static final ResourceLocation GASES = Constant.id("textures/gui/gases_methane_oxygen_nitrogen.png");
    private final ResourceLocation background;
    private final boolean synthesizer;
    private Button toggle;

    public GasProcessingScreen(GasProcessingMenu<M> menu, Inventory inventory, Component title) {
        super(menu, title, Constant.id("textures/gui/" + (menu.be instanceof GasLiquefierBlockEntity ? "gas_liquefier" : "methane_synthesizer") + ".png"));
        synthesizer = !(menu.be instanceof GasLiquefierBlockEntity);
        background = Constant.id("textures/gui/" + (synthesizer ? "methane_synthesizer" : "gas_liquefier") + ".png");
        imageHeight = 168;
        capacitorHeight = 0;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.translatable("ui.galacticraft.gas_machine.stop"), button -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + (synthesizer ? 60 : 47), topPos + 28, 76, 20).build());
    }

    @Override protected void containerTick() {
        super.containerTick();
        toggle.setMessage(Component.translatable(menu.disabled ? "ui.galacticraft.gas_machine.start" : "ui.galacticraft.gas_machine.stop"));
        toggle.active = menu.cooldown == 0;
    }

    @Override
    protected void drawTanks(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredTank = null;
        for (var tank : menu.tanks) {
            Fluid fluid = tank.getFluid();
            int amount = (int) (tank.getAmount() * tank.getHeight() / tank.getCapacity());
            if (amount > 0) {
                int column = synthesizer ? (tank.getIndex() == 0 ? 1 : 2) : gasColumn(fluid);
                ResourceLocation texture = fluid == GCFluids.FUEL ? background : GASES;
                int u = fluid == GCFluids.FUEL ? 192 : 1 + column * 17;
                graphics.blit(texture, leftPos + tank.getX(), topPos + tank.getY() + tank.getHeight() - amount,
                        u, 38 - amount, 16, amount);
            }
            if (mouseIn(mouseX, mouseY, leftPos + tank.getX(), topPos + tank.getY(), tank.getWidth(), tank.getHeight())) {
                hoveredTank = tank;
                setTooltipForNextRenderPass(tank.getTooltip().stream().map(Component::getVisualOrderText).toList());
            }
        }
    }

    private static int gasColumn(Fluid fluid) {
        if (fluid == Gases.METHANE) return 0;
        if (fluid == Gases.OXYGEN) return 1;
        if (fluid == GCFluids.LIQUID_OXYGEN) return 3;
        if (fluid == GCFluids.LIQUID_NITROGEN || fluid == GCFluids.LIQUID_ARGON) return 4;
        return 2;
    }

    @Override
    protected void drawCapacitor(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = synthesizer ? 66 : 42;
        int width = (int) Math.clamp(54 * menu.energyStorage.getAmount() / menu.energyStorage.getCapacity(), 0, 54);
        graphics.blit(background, leftPos + x, topPos + 17, 176, 38, width, 7);
        if (width > 0) graphics.blit(background, leftPos + x - 14, topPos + 16, 208, 0, 11, 10);
        if (mouseIn(mouseX, mouseY, leftPos + x, topPos + 16, 54, 9)) {
            var tooltip = new ArrayList<Component>();
            appendEnergyTooltip(tooltip);
            setTooltipForNextRenderPass(tooltip.stream().map(Component::getVisualOrderText).toList());
        }
    }

    @Override
    protected void drawTitle(GuiGraphics graphics) {
        // GC4 places the machine title between the input and output slots.
        graphics.drawString(font, title, synthesizer ? 50 : 28, 5, 0x404040, false);
        graphics.drawString(font, menu.state.getStatusText(menu.redstoneMode), synthesizer ? 72 : 56, 58, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"), 8, 75, 0x404040, false);
    }
}
