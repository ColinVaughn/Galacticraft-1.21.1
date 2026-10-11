/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 * Licensed under the MIT license. See LICENSE in the project root.
 */
package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.LaunchControllerBlockEntity;
import dev.galacticraft.mod.network.c2s.LaunchControllerSettingsPayload;
import dev.galacticraft.mod.screen.LaunchControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import static dev.galacticraft.mod.content.block.entity.machine.LaunchControllerBlockEntity.*;

public class LaunchControllerScreen extends MachineScreen<LaunchControllerBlockEntity, LaunchControllerMenu> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/gui/launch_controller.png");
    private EditBox frequency;
    private EditBox destination;
    private Button enable;
    private Button hide;
    private Button save;
    private Button keepPad;
    private Button schedule;
    private Button condition;
    private Button page;
    private boolean advanced;

    public LaunchControllerScreen(LaunchControllerMenu menu, Inventory inventory, Component title) {
        super(menu, title, TEXTURE);
        this.imageHeight = 209;
        this.capacitorHeight = 0;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("ui.galacticraft.launch_controller." + key, args);
    }

    @Override
    protected void init() {
        String oldFrequency = this.frequency == null ? number(this.menu.frequency) : this.frequency.getValue();
        String oldDestination = this.destination == null ? number(this.menu.destination) : this.destination.getValue();
        super.init();
        this.frequency = this.field(18, "frequency", oldFrequency);
        this.destination = this.field(38, "destination", oldDestination);
        this.enable = this.button(8, 66, 76, "enable", button -> this.toggle(ENABLED));
        this.hide = this.button(88, 66, 80, "hide", button -> this.toggle(HIDE_DESTINATION));
        this.save = this.button(8, 86, 76, "save", button -> this.send(this.menu.condition, this.menu.options));
        this.keepPad = this.button(8, 23, 160, "keep_pad", button -> this.toggle(KEEP_PAD));
        this.schedule = this.button(8, 44, 160, "schedule", button -> this.toggle(SCHEDULING));
        this.condition = this.button(8, 65, 160, "condition.0", button ->
                this.send((this.menu.condition + 1) % LaunchCondition.values().length, this.menu.options));
        this.page = this.button(88, 86, 80, "advanced", button -> {
            this.advanced = !this.advanced;
            this.updateButtons();
        });
        this.updateButtons();
    }

    private EditBox field(int y, String label, String value) {
        EditBox field = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 96, this.topPos + y,
                70, 16, text(label)));
        field.setMaxLength(6);
        field.setFilter(input -> input.chars().allMatch(c -> c >= '0' && c <= '9'));
        field.setValue(value);
        field.setResponder(input -> this.updateButtons());
        return field;
    }

    private Button button(int x, int y, int width, String key, Button.OnPress action) {
        return this.addRenderableWidget(Button.builder(text(key), action)
                .bounds(this.leftPos + x, this.topPos + y, width, 18).build());
    }

    private static String number(int value) { return value < 0 ? "" : Integer.toString(value); }
    private static int number(EditBox field) { return field.getValue().isEmpty() ? -1 : Integer.parseInt(field.getValue()); }

    private void toggle(int option) { this.send(this.menu.condition, this.menu.options ^ option); }

    private void send(int condition, int options) {
        NetworkManager.sendToServer(new LaunchControllerSettingsPayload(this.menu.containerId,
                number(this.frequency), number(this.destination), condition, options));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!this.menu.canEdit) {
            this.destination.setValue(number(this.menu.destination));
            this.destination.setHint((this.menu.options & HIDE_DESTINATION) != 0 ? text("hidden") : null);
        }
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.page == null) return;
        this.frequency.visible = this.destination.visible = !this.advanced;
        this.frequency.setEditable(this.menu.canEdit);
        this.destination.setEditable(this.menu.canEdit);
        if (this.advanced) {
            this.frequency.setFocused(false);
            this.destination.setFocused(false);
        }
        this.enable.visible = this.hide.visible = this.save.visible = !this.advanced;
        this.keepPad.visible = this.schedule.visible = this.condition.visible = this.advanced;
        boolean valid = this.menu.canEdit && validSettings(number(this.frequency), number(this.destination),
                this.menu.condition, this.menu.options);
        this.enable.active = this.hide.active = this.save.active = this.keepPad.active = this.schedule.active = this.condition.active = valid;
        this.enable.setMessage(text((this.menu.options & ENABLED) != 0 ? "disable" : "enable"));
        this.hide.setMessage(text((this.menu.options & HIDE_DESTINATION) != 0 ? "show" : "hide"));
        this.keepPad.setMessage(text("keep_pad", Component.translatable((this.menu.options & KEEP_PAD) != 0 ? "options.on" : "options.off")));
        this.schedule.setMessage(text("schedule", Component.translatable((this.menu.options & SCHEDULING) != 0 ? "options.on" : "options.off")));
        this.condition.setMessage(text("condition." + this.menu.condition));
        this.page.setMessage(text(this.advanced ? "back" : "advanced"));
    }

    @Override
    protected void renderMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (this.menu.energyStorage.getCapacity() > 0) {
            int width = (int) (52 * this.menu.energyStorage.getAmount() / this.menu.energyStorage.getCapacity());
            graphics.blit(TEXTURE, this.leftPos + 99, this.topPos + 114, 176, 0, width, 7);
        }
    }

    @Override
    protected void drawTitle(GuiGraphics graphics) {
        super.drawTitle(graphics);
        if (!this.advanced) {
            graphics.drawString(this.font, text("frequency"), 8, 22, 0x404040, false);
            graphics.drawString(this.font, text("destination"), 8, 42, 0x404040, false);
            String status = this.menu.saveResult < 0 ? "conflict" : !this.menu.padConnected ? "no_pad"
                    : (this.menu.options & ENABLED) == 0 || !this.menu.redstoneMode.isActive(this.menu.state.isPowered()) ? "disabled"
                    : this.menu.energyStorage.getAmount() < ENERGY_PER_TICK ? "no_power"
                    : !this.menu.frequencyValid ? "invalid_frequency" : !this.menu.destinationValid ? "invalid_destination" : "ready";
            graphics.drawCenteredString(this.font, text(status), this.imageWidth / 2, 56,
                    status.equals("ready") ? 0x268020 : 0xB03030);
        }
        graphics.drawString(this.font, this.playerInventoryTitle, 8, 116, 0x404040, false);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (this.frequency.isFocused() || this.destination.isFocused())) {
            return this.frequency.keyPressed(key, scanCode, modifiers) || this.destination.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
