/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.network.c2s.RocketPaintPayload;
import dev.galacticraft.mod.screen.RocketPaintMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RocketPaintScreen extends AbstractContainerScreen<RocketPaintMenu> {
    private final RocketEntity preview;
    private ItemStack lastInput = ItemStack.EMPTY;
    private int lastRevision = -1;
    private RocketPaint draft = RocketPaint.NONE;
    private RocketPaint pending;
    private int pendingTicks;
    private List<RocketPartTypes> parts = List.of();
    private int selection;
    private boolean updating, custom;
    private EditBox hex;
    private Button target, apply, reset, resetAll, customButton;
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<ChannelSlider> sliders = new ArrayList<>();
    private Component status = Component.empty();

    public RocketPaintScreen(RocketPaintMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RocketPaintMenu.WIDTH;
        imageHeight = Constant.RocketWorkbench.ROCKET_PAGE_HEIGHT;
        preview = new RocketEntity(GCEntityTypes.ROCKET, inventory.player.level());
        preview.setYRot(35);
    }

    private static Component text(String key, Object... args) { return Component.translatable("gui.galacticraft.rocket_paint." + key, args); }

    @Override protected void init() {
        super.init();
        swatches.clear();
        sliders.clear();
        target = addRenderableWidget(Button.builder(text("whole"), button -> {
            selection = (selection + 1) % (parts.size() + (parts.size() > 1 ? 1 : 0));
            refreshControls();
        }).bounds(leftPos + 126, topPos + 22, 164, 20).build());
        int i = 0;
        for (DyeColor dye : DyeColor.values()) {
            Swatch swatch = new Swatch(leftPos + 126 + i % 8 * 20, topPos + 46 + i / 8 * 20, dye);
            swatches.add(addRenderableWidget(swatch));
            i++;
        }
        for (int channel = 0; channel < 3; channel++) sliders.add(addRenderableWidget(new ChannelSlider(channel)));
        hex = addRenderableWidget(new EditBox(font, leftPos + 126, topPos + 92, 72, 18, text("hex")));
        hex.setMaxLength(7);
        hex.setResponder(value -> {
            if (!updating) {
                parseColor().ifPresent(this::chooseColor);
                refreshActive();
            }
        });
        hex.setTooltip(Tooltip.create(text("hex_hint")));
        customButton = addRenderableWidget(Button.builder(text("custom"), button -> {
            custom = !custom;
            refreshControls();
        }).bounds(leftPos + 202, topPos + 92, 88, 18).build());
        addRenderableWidget(Button.builder(text("back"), button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 10, topPos + 122, 42, 20).build());
        reset = addRenderableWidget(Button.builder(text("original"), button -> {
            for (RocketPartTypes part : selectedParts()) draft = draft.withColor(part, null);
            refreshControls();
        }).bounds(leftPos + 56, topPos + 122, 92, 20).build());
        resetAll = addRenderableWidget(Button.builder(text("reset_all"), button -> {
            for (RocketPartTypes part : parts) draft = draft.withColor(part, null);
            refreshControls();
        }).bounds(leftPos + 152, topPos + 122, 64, 20).build());
        apply = addRenderableWidget(Button.builder(text("apply"), button -> {
            pending = draft;
            pendingTicks = 0;
            status = text("saving");
            NetworkManager.sendToServer(new RocketPaintPayload(menu.containerId, lastRevision, draft));
            refreshActive();
        }).bounds(leftPos + 220, topPos + 122, 70, 20).build());
        refreshControls();
    }

    private List<RocketPartTypes> selectedParts() {
        if (parts.isEmpty()) return List.of();
        return parts.size() > 1 && selection == 0 ? parts : List.of(parts.get(selection - (parts.size() > 1 ? 1 : 0)));
    }

    private Optional<Integer> shownColor() {
        var selected = selectedParts();
        if (selected.isEmpty()) return Optional.empty();
        Optional<Integer> color = draft.color(selected.getFirst());
        return selected.stream().allMatch(part -> draft.color(part).equals(color)) ? color : Optional.empty();
    }

    private Optional<Integer> parseColor() {
        if (hex == null) return Optional.empty();
        String value = hex.getValue();
        if (value.startsWith("#")) value = value.substring(1);
        return value.matches("[0-9a-fA-F]{6}") ? Optional.of(0xFF000000 | Integer.parseInt(value, 16)) : Optional.empty();
    }

    private void chooseColor(int color) {
        for (RocketPartTypes part : selectedParts()) draft = draft.withColor(part, color);
        preview.setData(RocketPaintMenu.data(menu.input()).withPaint(draft));
        updating = true;
        for (ChannelSlider slider : sliders) slider.setColor(color);
        updating = false;
        status = Component.empty();
    }

    private void refreshControls() {
        if (hex == null) return;
        updating = true;
        int color = shownColor().orElse(0xFFFFFFFF);
        hex.setValue(String.format("#%06X", color & 0xFFFFFF));
        for (ChannelSlider slider : sliders) slider.setColor(color);
        updating = false;
        var selected = selectedParts();
        target.setMessage(selected.size() == 1 ? selected.getFirst().name : text("whole"));
        customButton.setMessage(text(custom ? "swatches" : "custom"));
        for (Swatch swatch : swatches) swatch.visible = !custom;
        for (ChannelSlider slider : sliders) slider.visible = custom;
        if (!menu.input().isEmpty()) preview.setData(RocketPaintMenu.data(menu.input()).withPaint(draft));
        refreshActive();
    }

    private void refreshActive() {
        if (apply == null) return;
        boolean editable = !parts.isEmpty() && pending == null;
        target.active = editable && parts.size() > 1;
        hex.setEditable(editable);
        customButton.active = editable;
        for (Swatch swatch : swatches) swatch.active = editable;
        for (ChannelSlider slider : sliders) slider.active = editable;
        reset.active = editable;
        resetAll.active = editable;
        apply.active = editable && parseColor().isPresent() && !draft.equals(RocketPaintMenu.data(menu.input()).paint());
        hex.setTextColor(parseColor().isPresent() ? 0xFFFFFF : 0xFF5555);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (!ItemStack.matches(lastInput, menu.input()) || lastRevision != menu.revision()) {
            status = pending == null ? Component.empty() : text(RocketPaintMenu.canPaint(menu.input()) && pending.equals(RocketPaintMenu.data(menu.input()).paint()) ? "saved" : "changed");
            pending = null;
            var nextParts = RocketPaintMenu.parts(menu.input());
            if (!parts.equals(nextParts) || !ItemStack.isSameItem(lastInput, menu.input())) selection = 0;
            lastInput = menu.input().copy();
            lastRevision = menu.revision();
            parts = nextParts;
            draft = RocketPaintMenu.data(lastInput).paint();
            refreshControls();
        }
        if (pending != null && ++pendingTicks > 60) {
            pending = null;
            status = text("changed");
            refreshActive();
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        frame(graphics, leftPos, topPos, imageWidth, imageHeight, 0xFFC6C6C6);
        frame(graphics, leftPos + 48, topPos + 22, 70, 94, 0xFF383838);
        graphics.blit(Constant.RocketWorkbench.SCREEN_TEXTURE, leftPos + 21, topPos + 33,
                Constant.RocketWorkbench.SLOT_U, Constant.RocketWorkbench.SLOT_V, 18, 18);
        graphics.blit(Constant.RocketWorkbench.SCREEN_TEXTURE, leftPos + RocketPaintMenu.INVENTORY_X - 1,
                topPos + Constant.RocketWorkbench.ROCKET_INVENTORY_Y - 1,
                7, Constant.RocketWorkbench.ROCKET_INVENTORY_Y - 1, 162, 54);
        graphics.blit(Constant.RocketWorkbench.SCREEN_TEXTURE, leftPos + RocketPaintMenu.INVENTORY_X - 1,
                topPos + Constant.RocketWorkbench.ROCKET_HOTBAR_Y - 1,
                7, Constant.RocketWorkbench.ROCKET_HOTBAR_Y - 1, 162, 18);
        if (!parts.isEmpty()) RocketWorkbenchScreen.renderEntityInInventory(graphics, leftPos + 83, topPos + 112,
                14, SmithingScreen.ARMOR_STAND_ANGLE, null, preview);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF373737);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF373737);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        if (menu.input().isEmpty()) graphics.drawString(font, text("input"), 10, 22, 0x404040, false);
        if (!status.getString().isEmpty()) graphics.drawString(font, status, 126, 112, 0x404040, false);
        else if (parseColor().isEmpty()) graphics.drawString(font, text("invalid"), 126, 112, 0xA03030, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && isHovering(48, 22, 70, 94, x, y)) {
            preview.setYRot(preview.getYRot() + (float) dx * 3);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && hex.isFocused()) return hex.keyPressed(key, scanCode, modifiers);
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class Swatch extends Button {
        private final int color;
        Swatch(int x, int y, DyeColor dye) {
            super(x, y, 18, 18, Component.translatable("color.minecraft." + dye.getName()), button -> {
                chooseColor(0xFF000000 | dye.getTextureDiffuseColor());
                refreshControls();
            }, DEFAULT_NARRATION);
            color = 0xFF000000 | dye.getTextureDiffuseColor();
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF373737);
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, active ? color : 0xFF777777);
            if (shownColor().filter(value -> value == color).isPresent()) {
                graphics.drawCenteredString(font, "*", getX() + width / 2, getY() + 5, 0xFFFFFFFF);
            }
        }
    }

    private final class ChannelSlider extends AbstractSliderButton {
        private final int channel;
        ChannelSlider(int channel) {
            super(leftPos + 126, topPos + 46 + channel * 14, 164, 14, Component.empty(), 1);
            this.channel = channel;
            updateMessage();
        }
        void setColor(int color) { value = (color >>> (16 - channel * 8) & 255) / 255.0; updateMessage(); }
        @Override protected void updateMessage() { setMessage(text("channel." + channel, Math.round(value * 255))); }
        @Override protected void applyValue() {
            if (updating) return;
            int shift = 16 - channel * 8;
            int color = parseColor().orElse(0xFFFFFFFF) & ~(255 << shift) | (int) Math.round(value * 255) << shift;
            chooseColor(color);
            updating = true;
            hex.setValue(String.format("#%06X", color & 0xFFFFFF));
            updating = false;
            refreshActive();
        }
    }
}