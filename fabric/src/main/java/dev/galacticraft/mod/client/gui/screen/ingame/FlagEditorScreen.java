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

import com.mojang.blaze3d.platform.NativeImage;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

final class FlagEditorScreen extends Screen {
    private final Screen parent;
    private final Consumer<NativeImage> save;
    private final FlagCanvas canvas;
    private int brushColor = 0xFF000000;
    private boolean saveFailed;

    FlagEditorScreen(Screen parent, int[] pixels, Consumer<NativeImage> save) {
        super(Component.translatable(Translations.SpaceRace.DRAW_FLAG));
        this.parent = parent;
        this.save = save;
        this.canvas = new FlagCanvas(pixels);
    }

    @Override
    protected void init() {
        int scale = Math.max(1, Math.min((this.width - 140) / FlagCanvas.WIDTH,
                (this.height - 90) / FlagCanvas.HEIGHT));
        int left = (this.width - FlagCanvas.WIDTH * scale - 124) / 2;
        this.addRenderableWidget(new CanvasWidget(left, 36, scale));
        int controlsX = left + FlagCanvas.WIDTH * scale + 12;
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            DyeColor color = colors[i];
            AbstractButton swatch = new AbstractButton(controlsX + i % 8 * 14, 36 + i / 8 * 16, 12, 14,
                    Component.translatable("color.minecraft." + color.getName())) {
                @Override
                public void onPress() {
                    brushColor = 0xFF000000 | color.getTextureDiffuseColor();
                    rebuildWidgets();
                }

                @Override
                protected void updateWidgetNarration(NarrationElementOutput output) {
                    this.defaultButtonNarrationText(output);
                }

                @Override
                protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                            0xFF000000 | color.getTextureDiffuseColor());
                    if (isHoveredOrFocused() || brushColor == (0xFF000000 | color.getTextureDiffuseColor())) {
                        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
                    }
                }
            };
            this.addRenderableWidget(swatch);
        }
        this.addRenderableWidget(new BrushSlider(controlsX, 76, Translations.SpaceRace.RED, 16));
        this.addRenderableWidget(new BrushSlider(controlsX, 100, Translations.SpaceRace.GREEN, 8));
        this.addRenderableWidget(new BrushSlider(controlsX, 124, Translations.SpaceRace.BLUE, 0));
        this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.fill(controlsX, 152, controlsX + 110, 164, this.brushColor);
            graphics.renderOutline(controlsX, 152, 110, 12, 0xFFAAAAAA);
        });

        int buttonX = this.width / 2 - 140;
        addButton(buttonX, Translations.SpaceRace.BACK, this::onClose);
        addButton(buttonX + 72, Translations.SpaceRace.FLAG_UNDO, this.canvas::undo);
        addButton(buttonX + 144, Translations.SpaceRace.FLAG_FILL, () -> this.canvas.fill(this.brushColor));
        addButton(buttonX + 216, Translations.SpaceRace.FLAG_USE, () -> this.save.accept(toImage()));
    }

    private void addButton(int x, String translation, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.translatable(translation), button -> action.run())
                .bounds(x, this.height - 28, 68, 20).build());
    }

    private NativeImage toImage() {
        NativeImage image = new NativeImage(FlagCanvas.WIDTH, FlagCanvas.HEIGHT, false);
        for (int y = 0; y < FlagCanvas.HEIGHT; y++) {
            for (int x = 0; x < FlagCanvas.WIDTH; x++) {
                image.setPixelRGBA(x, y, FlagCanvas.swapRedBlue(this.canvas.pixel(x, y)));
            }
        }
        return image;
    }

    void showSaveError() {
        this.saveFailed = true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable(this.saveFailed
                        ? Translations.SpaceRace.FLAG_SAVE_ERROR : Translations.SpaceRace.FLAG_DRAW_HINT),
                this.width / 2, this.height - 42, this.saveFailed ? 0xFFFF5555 : 0xFFAAAAAA);
    }

    private final class BrushSlider extends AbstractSliderButton {
        private final String translation;
        private final int shift;

        private BrushSlider(int x, int y, String translation, int shift) {
            super(x, y, 110, 20, Component.empty(), ((brushColor >>> shift) & 0xFF) / 255.0);
            this.translation = translation;
            this.shift = shift;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("options.generic_value",
                    Component.translatable(this.translation), (int) Math.round(this.value * 255)));
        }

        @Override
        protected void applyValue() {
            brushColor = (brushColor & ~(0xFF << this.shift)) | ((int) Math.round(this.value * 255) << this.shift);
        }
    }

    private final class CanvasWidget extends AbstractWidget {
        private final int scale;
        private int previousX = -1;
        private int previousY;
        private int cursorX;
        private int cursorY;

        private CanvasWidget(int x, int y, int scale) {
            super(x, y, FlagCanvas.WIDTH * scale, FlagCanvas.HEIGHT * scale,
                    Component.translatable(Translations.SpaceRace.FLAG_DRAW_HINT));
            this.scale = scale;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            for (int y = 0; y < FlagCanvas.HEIGHT; y++) {
                for (int x = 0; x < FlagCanvas.WIDTH; x++) {
                    int screenX = getX() + x * this.scale, screenY = getY() + y * this.scale;
                    graphics.fill(screenX, screenY, screenX + this.scale, screenY + this.scale, canvas.pixel(x, y));
                }
            }
            graphics.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFAAAAAA);
            if (isHovered()) {
                this.cursorX = (mouseX - getX()) / this.scale;
                this.cursorY = (mouseY - getY()) / this.scale;
            }
            if (isHoveredOrFocused()) {
                int x = getX() + this.cursorX * this.scale, y = getY() + this.cursorY * this.scale;
                graphics.renderOutline(x, y, this.scale, this.scale, 0xFF000000);
                graphics.renderOutline(x - 1, y - 1, this.scale + 2, this.scale + 2, 0xFFFFFFFF);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            canvas.checkpoint();
            this.previousX = -1;
            paint(mouseX, mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            paint(mouseX, mouseY);
        }

        private void paint(double mouseX, double mouseY) {
            if (!isMouseOver(mouseX, mouseY)) {
                this.previousX = -1;
                return;
            }
            int x = (int) (mouseX - getX()) / this.scale;
            int y = (int) (mouseY - getY()) / this.scale;
            canvas.line(this.previousX < 0 ? x : this.previousX, this.previousX < 0 ? y : this.previousY,
                    x, y, brushColor);
            this.previousX = this.cursorX = x;
            this.previousY = this.cursorY = y;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> this.cursorX = Math.max(0, this.cursorX - 1);
                case GLFW.GLFW_KEY_RIGHT -> this.cursorX = Math.min(FlagCanvas.WIDTH - 1, this.cursorX + 1);
                case GLFW.GLFW_KEY_UP -> this.cursorY = Math.max(0, this.cursorY - 1);
                case GLFW.GLFW_KEY_DOWN -> this.cursorY = Math.min(FlagCanvas.HEIGHT - 1, this.cursorY + 1);
                case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    canvas.checkpoint();
                    canvas.line(this.cursorX, this.cursorY, this.cursorX, this.cursorY, brushColor);
                }
                default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
