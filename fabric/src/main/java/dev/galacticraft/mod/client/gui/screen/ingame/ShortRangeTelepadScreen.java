/* Copyright (c) 2026 Team Galacticraft. GC4 GUI assets retain their original license. */
package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.machinelib.client.api.screen.MachineScreen;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import dev.galacticraft.mod.network.c2s.TelepadAddressPayload;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShortRangeTelepadScreen extends MachineScreen<ShortRangeTelepadBlockEntity, ShortRangeTelepadMenu> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/gui/short_range_telepad.png");
    private EditBox address, destination;
    private Button toggle;
    private boolean updating;
    public ShortRangeTelepadScreen(ShortRangeTelepadMenu menu, Inventory inventory, Component title) {
        super(menu, title, TEXTURE);
        imageHeight = 209;
        capacitorHeight = 0;
    }
    private static Component text(String key, Object... args) { return Component.translatable("ui.galacticraft.telepad." + key, args); }
    private static String number(int value) { return value < 0 ? "" : Integer.toString(value); }
    @Override protected void init() {
        super.init();
        address = field(66, 16, false, menu.address);
        destination = field(122, 38, true, menu.destination);
        toggle = addRenderableWidget(Button.builder(text("disable"), button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 122, topPos + 16, 48, 20).build());
    }
    private EditBox field(int x, int y, boolean target, int value) {
        EditBox field = addRenderableWidget(new EditBox(font, leftPos + x, topPos + y, 48, 20, text(target ? "destination" : "address")));
        field.setMaxLength(6);
        field.setFilter(input -> input.chars().allMatch(c -> c >= '0' && c <= '9'));
        field.setValue(number(value));
        field.setResponder(input -> {
            if (!updating && menu.canEdit) NetworkManager.sendToServer(new TelepadAddressPayload(menu.containerId, target,
                    input.isEmpty() ? -1 : Integer.parseInt(input)));
        });
        return field;
    }
    @Override protected void containerTick() {
        super.containerTick();
        updating = true;
        if (!address.isFocused()) address.setValue(number(menu.address));
        if (!destination.isFocused()) destination.setValue(number(menu.destination));
        updating = false;
        address.setEditable(menu.canEdit);
        destination.setEditable(menu.canEdit);
        address.setTextColor(menu.addressValid ? 0x14FF14 : 0xFF1919);
        destination.setTextColor(menu.targetResult == 0 ? 0x14FF14 : menu.targetResult == 4 ? 0xFFAA19 : 0xFF1919);
        toggle.active = menu.canEdit && menu.cooldown == 0;
        toggle.setMessage(text(menu.disabled ? "enable" : "disable"));
    }
    private boolean disabled() { return menu.disabled || !menu.redstoneMode.isActive(menu.state.isPowered()); }
    private Component receiving() {
        return text(!menu.addressValid ? "invalid_address" : menu.energyStorage.getAmount() < ShortRangeTelepadBlockEntity.ENERGY_PER_TELEPORT
                ? "no_energy" : disabled() ? "disabled" : "receiving");
    }
    private Component sending() {
        return !menu.addressValid ? text("invalid_address") : menu.targetResult != 0 ? text("target." + menu.targetResult, ShortRangeTelepadBlockEntity.RANGE)
                : menu.energyStorage.getAmount() < ShortRangeTelepadBlockEntity.ENERGY_PER_TELEPORT ? text("no_energy")
                : disabled() ? text("disabled") : menu.teleportTime > 0 ? text("progress", menu.teleportTime * 100 / ShortRangeTelepadBlockEntity.TELEPORT_TIME) : text("sending");
    }
    @Override protected void drawTitle(GuiGraphics graphics) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, 0x404040, false);
        graphics.drawString(font, text("address"), 7, 22, 0x404040, false);
        graphics.drawString(font, text("destination"), 7, 44, 0x404040, false);
        boolean powered = menu.energyStorage.getAmount() >= ShortRangeTelepadBlockEntity.ENERGY_PER_TELEPORT && !disabled();
        Component receive = receiving(), send = sending();
        graphics.drawString(font, receive, 7, 66, menu.addressValid && powered ? 0x208020 : 0xB03030, false);
        if (!receive.equals(send)) graphics.drawString(font, send, 7, 88, menu.addressValid && powered && menu.targetResult == 0 ? 0x208020 : 0xB03030, false);
        graphics.drawString(font, playerInventoryTitle, 8, 115, 0x404040, false);
    }
    @Override protected void drawCapacitor(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = (int) Math.clamp(54 * menu.energyStorage.getAmount() / menu.energyStorage.getCapacity(), 0, 54);
        graphics.blit(TEXTURE, leftPos + 99, topPos + 114, 176, 0, width, 7);
        if (mouseIn(mouseX, mouseY, leftPos + 98, topPos + 113, 56, 9)) {
            var tooltip = new java.util.ArrayList<Component>();
            appendEnergyTooltip(tooltip);
            setTooltipForNextRenderPass(tooltip.stream().map(Component::getVisualOrderText).toList());
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (address.isFocused() || destination.isFocused())) {
            return address.keyPressed(key, scanCode, modifiers) || destination.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
