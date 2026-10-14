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

package dev.galacticraft.mod.client.gui.screen.ingame;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import dev.galacticraft.api.accessor.ResearchAccessor;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.util.Graphics;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.machine.storage.VariableSizedContainer;
import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static dev.galacticraft.mod.Constant.RocketWorkbench.*;

/**
 * Draws one page of the workbench flip-book. Each page brings its own background, geometry and, on
 * rocket pages, the live preview; the Back/Next buttons ask the server to reopen the menu on the
 * neighbouring page, exactly as legacy Galacticraft's schematic GUIs did.
 */
public class RocketWorkbenchScreen extends AbstractContainerScreen<RocketWorkbenchMenu> implements VariableSizedContainer.Listener, net.minecraft.world.ContainerListener {
    private static final Component MARKER = Component.literal("!");

    private final WorkbenchPageDisplay display;
    private final @Nullable RocketEntity entity;

    private Button backButton;
    private Button nextButton;
    private Button paintButton;

    public RocketWorkbenchScreen(RocketWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventoryLabelX = this.inventoryLabelY = Integer.MAX_VALUE;
        this.display = menu.display();

        if (this.display.rocketPreview()) {
            this.entity = new RocketEntity(GCEntityTypes.ROCKET, menu.workbench.getLevel());
            this.entity.setData(menu.previewRocket());
            this.entity.setYRot(60);
        } else {
            this.entity = null;
        }
    }

    @Override
    public void onSizeChanged() {
        this.onItemChanged();
    }

    @Override
    public void containerChanged(net.minecraft.world.Container container) { this.onItemChanged(); }

    @Override
    public void onItemChanged() {
        if (this.entity != null) {
            this.entity.setData(this.menu.previewRocket());
        }
    }

    @Override
    protected void init() {
        this.imageWidth = this.display.width();
        this.imageHeight = this.display.height();
        super.init();

        // Page heights vary, but consecutive navigation clicks must land in the same place.
        int navigationTop = (this.height - ROCKET_PAGE_HEIGHT) / 2;
        if (Platform.isModLoaded("emi")) {
            this.topPos = Mth.clamp(this.height - this.imageHeight - 23, 2, this.topPos);
            navigationTop = Mth.clamp(this.height - ROCKET_PAGE_HEIGHT - 23, 2, navigationTop);
        }

        this.backButton = this.addRenderableWidget(Button.builder(
                        Component.translatable(Translations.RocketWorkbench.BUTTON_BACK),
                        button -> this.flip(RocketWorkbenchMenu.BUTTON_BACK))
                .bounds(this.leftPos + FLIP_BUTTON_X, navigationTop + FLIP_BUTTON_Y, FLIP_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.nextButton = this.addRenderableWidget(Button.builder(
                        Component.translatable(Translations.RocketWorkbench.BUTTON_NEXT),
                        button -> this.flip(RocketWorkbenchMenu.BUTTON_NEXT))
                .bounds(this.leftPos + FLIP_BUTTON_X, navigationTop + FLIP_BUTTON_Y + FLIP_BUTTON_SPACING, FLIP_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        this.backButton.active = this.menu.adjacentPage(-1) != null;
        this.nextButton.active = this.menu.adjacentPage(1) != null;

        if (this.display.rocketPreview()) {
            this.paintButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.galacticraft.rocket_paint.open"),
                            button -> this.flip(RocketWorkbenchMenu.BUTTON_PAINT))
                    .bounds(this.leftPos + FLIP_BUTTON_X, navigationTop + FLIP_BUTTON_Y + 2 * FLIP_BUTTON_SPACING, FLIP_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        if (this.menu.page().id().equals(WorkbenchPages.ADD_SCHEMATIC_ID)) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable(Translations.RocketWorkbench.BUTTON_UNLOCK),
                            button -> this.flip(RocketWorkbenchMenu.BUTTON_UNLOCK))
                    .bounds(this.leftPos + UNLOCK_BUTTON_X, this.topPos + UNLOCK_BUTTON_Y, UNLOCK_BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void flip(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        }
    }

    @Override
    public void added() {
        super.added();
        this.menu.workbench.ingredients.addListener(this);
        this.menu.workbench.ingredients.addListener(this.menu);
        this.menu.workbench.chests.addListener(this.menu);
        this.menu.workbench.refitInput.addListener(this);
    }

    @Override
    public void removed() {
        super.removed();
        this.menu.workbench.ingredients.removeListener(this);
        this.menu.workbench.ingredients.removeListener(this.menu);
        this.menu.workbench.chests.removeListener(this.menu);
        this.menu.workbench.refitInput.removeListener(this);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        this.inventoryLabelY = this.imageHeight - 96;

        try (Graphics graphics = Graphics.managed(guiGraphics, this.font)) {
            try (Graphics.Texture texture = graphics.texture(this.display.texture(), 256, 256)) {
                texture.blit(this.leftPos, this.topPos, 0, this.display.textureV(), this.display.width(), this.display.height());

                // Only the first chest slot has a frame baked into this port's rocket art, so draw
                // all of them ourselves. The legacy page textures already have their wells painted.
                if (this.display.slotFrames()) {
                    for (Slot slot : this.menu.slots) {
                        if (slot.container instanceof VariableSizedContainer || slot.container == this.menu.workbench.chests || slot.container == this.menu.workbench.refitInput) {
                            texture.blit(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, SLOT_U, SLOT_V, SLOT_WIDTH, SLOT_HEIGHT);
                        }
                    }
                }
            }
        }

        if (this.entity != null) {
            renderEntityInInventory(guiGraphics, this.leftPos + ROCKET_X, this.topPos + ROCKET_Y, 15, SmithingScreen.ARMOR_STAND_ANGLE, null, this.entity);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.menu.isRefitting() ? Component.translatable("gui.galacticraft.rocket_refit.title") : this.display.title(), 8, 6, 0x404040, false);
        if (this.menu.page().id().equals(Constant.id("rocket/modular"))) {
            graphics.drawString(this.font, Component.translatable("gui.galacticraft.rocket_refit.label"), REFIT_INPUT_X, REFIT_INPUT_Y - 12, 0x404040, false);
            for (var slot : dev.galacticraft.mod.recipe.ModularRocketRecipe.SLOTS) {
                Component label = Component.translatable("gui.galacticraft.rocket_workbench.module." + slot.partType().name().toLowerCase(java.util.Locale.ROOT));
                graphics.drawString(this.font, label, slot.x() - 5 - this.font.width(label), slot.y() + 4, 0x404040, false);
            }
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (this.entity != null) {
            this.entity.setYRot(this.entity.getYRot() + delta);
        }
        this.renderMissingResearchMarker(context);
        this.renderTooltip(context, mouseX, mouseY);
        this.renderMissingResearchTooltip(context, mouseX, mouseY);
        this.renderSchematicSlotHint(context, mouseX, mouseY);
        this.renderRefitHints(context, mouseX, mouseY);
    }

    private void renderRefitHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.refitInputSlot() == null) return;
        if (this.isHovering(REFIT_INPUT_X, REFIT_INPUT_Y, 16, 16, mouseX, mouseY) && this.menu.refitRocket().isEmpty()) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.galacticraft.rocket_refit.insert"),
                    Component.translatable("gui.galacticraft.rocket_refit.clear").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
        if (!this.menu.isRefitting()) return;
        var data = this.menu.previewRocket();
        this.renderRefitPartHints(graphics, mouseX, mouseY, data);
        this.renderRefitResultHint(graphics, mouseX, mouseY, data);
    }

    private void renderRefitPartHints(GuiGraphics graphics, int mouseX, int mouseY, dev.galacticraft.api.rocket.RocketData data) {
        for (var part : dev.galacticraft.mod.recipe.ModularRocketRecipe.SLOTS) {
            boolean present = dev.galacticraft.mod.machine.workbench.RocketRefit.hasPart(data, part.partType());
            boolean unsupported = present && dev.galacticraft.mod.machine.workbench.RocketRefit.module(data, part.partType()).isEmpty();
            if (unsupported) graphics.drawCenteredString(this.font, MARKER, this.leftPos + part.x() + 8, this.topPos + part.y() + 4, 0xFFFFAA00);
            if (this.isHovering(part.x(), part.y(), 16, 16, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                ItemStack installed = dev.galacticraft.mod.machine.workbench.RocketRefit.module(data, part.partType());
                if (!installed.isEmpty()) lines.add(installed.getHoverName());
                else dev.galacticraft.mod.machine.workbench.RocketRefit.key(data, part.partType()).ifPresent(key -> lines.add(Component.literal(key.location().toString())));
                lines.add(Component.translatable("gui.galacticraft.rocket_refit."
                        + (unsupported ? "unsupported" : present ? "remove" : "install")).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            }
        }
    }

    private void renderRefitResultHint(GuiGraphics graphics, int mouseX, int mouseY, dev.galacticraft.api.rocket.RocketData data) {
        if (this.isHovering(this.display.resultSlot().x(), this.display.resultSlot().y(), 16, 16, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.galacticraft.rocket_refit." + (data.isValid() ? "take" : "incomplete")));
            for (var part : dev.galacticraft.mod.recipe.ModularRocketRecipe.SLOTS) {
                if (!dev.galacticraft.mod.machine.workbench.RocketRefit.hasPart(data, part.partType()))
                    lines.add(Component.translatable("gui.galacticraft.rocket_refit.missing", part.partType().name).withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    /**
     * Marks the empty result slot when the assembly is complete but the player has not researched
     * a part. Without it the slot just stays blank and looks like the parts are wrong.
     */
    private void renderMissingResearchMarker(GuiGraphics graphics) {
        if (this.menu.getMissingResearch().isEmpty() || this.display.resultSlot() == null) {
            return;
        }

        graphics.drawCenteredString(this.font, MARKER,
                this.leftPos + this.display.resultSlot().x() + 8,
                this.topPos + this.display.resultSlot().y() + 4, 0xFFFF5555);
    }

    private void renderMissingResearchTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> missing = this.menu.getMissingResearch();
        if (missing.isEmpty() || this.display.resultSlot() == null
                || !this.isHovering(this.display.resultSlot().x(), this.display.resultSlot().y(), 16, 16, mouseX, mouseY)) {
            return;
        }

        List<Component> lines = new ArrayList<>(missing.size() + 2);
        lines.add(Component.translatable(Translations.RocketWorkbench.MISSING_RESEARCH).withStyle(ChatFormatting.RED));
        for (Component part : missing) {
            lines.add(Component.literal(" ").append(part).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(Translations.RocketWorkbench.MISSING_RESEARCH_HINT).withStyle(ChatFormatting.DARK_GRAY));

        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /**
     * Legacy annotated the unlock well with a four-line explanation. This says the same thing, and
     * also explains a schematic the player has already spent - otherwise the Unlock button simply
     * does nothing and looks broken.
     */
    private void renderSchematicSlotHint(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.menu.page().id().equals(WorkbenchPages.ADD_SCHEMATIC_ID)
                || !this.isHovering(ADD_SCHEMATIC_SLOT_X, ADD_SCHEMATIC_SLOT_Y, 16, 16, mouseX, mouseY)) {
            return;
        }

        ItemStack held = this.menu.workbench.schematic.getItem(0);
        if (held.isEmpty()) {
            graphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable(Translations.RocketWorkbench.SCHEMATIC_SLOT_HINT).withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        } else if (this.isAlreadyUnlocked(held)) {
            graphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable(Translations.RocketWorkbench.SCHEMATIC_ALREADY_UNLOCKED).withStyle(ChatFormatting.YELLOW)),
                    mouseX, mouseY);
        }
    }

    private boolean isAlreadyUnlocked(ItemStack schematic) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(schematic.getItem());
        return ((ResearchAccessor) this.menu.playerInventory.player).galacticraft$isUnlocked(WorkbenchPages.unlockId(id));
    }

    /** The flip buttons sit outside the panel, so clicks on them must not count as "outside". */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (this.backButton != null && (this.backButton.isMouseOver(mouseX, mouseY) || this.nextButton.isMouseOver(mouseX, mouseY)
                || this.paintButton != null && this.paintButton.isMouseOver(mouseX, mouseY))) {
            return false;
        }
        return mouseX < (double) left || mouseY < (double) top
                || mouseX >= (double) (left + this.display.width()) || mouseY >= (double) (top + this.display.height());
    }

    public static void renderEntityInInventory(
            GuiGraphics guiGraphics, double x, double y, int scale, Quaternionf pose, @Nullable Quaternionf cameraOrientation, RocketEntity entity
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 50.0);
        guiGraphics.pose().mulPose(new Matrix4f().scaling((float) scale, (float) scale, (float) (-scale)));
        guiGraphics.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (cameraOrientation != null) {
            cameraOrientation.conjugate();
            entityRenderDispatcher.overrideCameraOrientation(cameraOrientation);
        }

        entityRenderDispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, guiGraphics.pose(), guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT));
        guiGraphics.flush();
        entityRenderDispatcher.setRenderShadow(true);
        guiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
