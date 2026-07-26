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

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import dev.galacticraft.mod.client.util.Graphics;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.machine.storage.VariableSizedContainer;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static dev.galacticraft.mod.Constant.RocketWorkbench.*;

public class RocketWorkbenchScreen extends AbstractContainerScreen<RocketWorkbenchMenu> implements VariableSizedContainer.Listener {
    private static final Component MARKER = Component.literal("!");

    private static final int UI_WIDTH = 176;
    private static final int MAIN_UI_WIDTH = 176;
    private static final int UI_HEIGHT = 249;

    private final RocketEntity entity;

    public RocketWorkbenchScreen(RocketWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventoryLabelX = this.inventoryLabelY = Integer.MAX_VALUE;

        this.entity = new RocketEntity(GCEntityTypes.ROCKET, menu.workbench.getLevel());
        this.entity.setData(menu.previewRocket());
        this.entity.setYRot(60);
    }

    @Override
    public void onSizeChanged() {
        this.onItemChanged();
    }

    @Override
    public void onItemChanged() {
        this.entity.setData(this.menu.previewRocket());
    }

    @Override
    protected void init() {
        this.imageWidth = MAIN_UI_WIDTH;
        this.imageHeight = UI_HEIGHT;
        super.init();

        if (Platform.isModLoaded("emi")) {
            this.topPos = Mth.clamp(this.height - this.imageHeight - 23, 2, this.topPos);
        }
    }

    @Override
    public void added() {
        super.added();
        this.menu.workbench.ingredients.addListener(this);
        this.menu.workbench.ingredients.addListener(this.menu);
        this.menu.workbench.chests.addListener(this.menu);
    }

    @Override
    public void removed() {
        super.removed();
        this.menu.workbench.ingredients.removeListener(this);
        this.menu.workbench.ingredients.removeListener(this.menu);
        this.menu.workbench.chests.removeListener(this.menu);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        this.inventoryLabelY = this.imageHeight - 96;

        try (Graphics graphics = Graphics.managed(guiGraphics, this.font)) {
            try (Graphics.Texture texture = graphics.texture(SCREEN_TEXTURE, 256, 256)) {
                texture.blit(this.leftPos, this.topPos, 0, 0, UI_WIDTH, UI_HEIGHT);

                // Only the first chest slot has a frame baked into the background, so draw all three
                // ourselves to keep them uniform.
                for (Slot slot : this.menu.slots) {
                    if (slot.container instanceof VariableSizedContainer || slot.container == this.menu.workbench.chests) {
                        texture.blit(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, SLOT_U, SLOT_V, SLOT_WIDTH, SLOT_HEIGHT);
                    }
                }
            }
        }

        renderEntityInInventory(guiGraphics, this.leftPos + ROCKET_X, this.topPos + ROCKET_Y, 15, SmithingScreen.ARMOR_STAND_ANGLE, null, this.entity);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.entity.setYRot(this.entity.getYRot() + delta);
        this.renderMissingResearchMarker(context);
        this.renderTooltip(context, mouseX, mouseY);
        this.renderMissingResearchTooltip(context, mouseX, mouseY);
    }

    /**
     * Marks the empty result slot when the assembly is complete but the player has not researched
     * a part. Without it the slot just stays blank and looks like the parts are wrong.
     */
    private void renderMissingResearchMarker(GuiGraphics graphics) {
        if (this.menu.getMissingResearch().isEmpty()) {
            return;
        }

        graphics.drawCenteredString(this.font, MARKER, this.leftPos + OUTPUT_X + 8, this.topPos + OUTPUT_Y + 4, 0xFFFF5555);
    }

    private void renderMissingResearchTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> missing = this.menu.getMissingResearch();
        if (missing.isEmpty() || !this.isHovering(OUTPUT_X, OUTPUT_Y, 16, 16, mouseX, mouseY)) {
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

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < (double)left || mouseY < (double)top || mouseX >= (double)(left + UI_WIDTH) || mouseY >= (double)(top + UI_HEIGHT);
    }

    public static void renderEntityInInventory(
            GuiGraphics guiGraphics, double x, double y, int scale, Quaternionf pose, @Nullable Quaternionf cameraOrientation, RocketEntity entity
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 50.0);
        guiGraphics.pose().mulPose(new Matrix4f().scaling((float)scale, (float)scale, (float)(-scale)));
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
