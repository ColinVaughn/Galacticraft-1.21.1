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

import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Offers the flag layouts, drawn in the editor's primary and secondary colours, to start a design from. */
final class FlagPatternScreen extends Screen {
    private static final int COLUMNS = 5;
    private final Screen parent;
    private final int primary;
    private final int secondary;
    private final Consumer<FlagPattern> apply;

    FlagPatternScreen(Screen parent, int primary, int secondary, Consumer<FlagPattern> apply) {
        super(Component.translatable(Translations.SpaceRace.FLAG_PATTERNS_TITLE));
        this.parent = parent;
        this.primary = primary;
        this.secondary = secondary;
        this.apply = apply;
    }

    static Component name(FlagPattern pattern) {
        return Component.translatable(Translations.SpaceRace.FLAG_PATTERN_PREFIX + pattern.id());
    }

    @Override
    protected void init() {
        FlagPattern[] patterns = FlagPattern.values();
        int rows = (patterns.length + COLUMNS - 1) / COLUMNS;
        int scale = Math.max(1, Math.min(2, Math.min((this.width - 40) / (COLUMNS * (FlagCanvas.WIDTH + 10)),
                (this.height - 100) / (rows * (FlagCanvas.HEIGHT + 10)))));
        int cellWidth = FlagCanvas.WIDTH * scale + 6, cellHeight = FlagCanvas.HEIGHT * scale + 6;
        int left = (this.width - COLUMNS * (cellWidth + 4)) / 2, top = 46;
        for (int i = 0; i < patterns.length; i++) {
            int x = left + i % COLUMNS * (cellWidth + 4), y = top + i / COLUMNS * (cellHeight + 4);
            this.addRenderableWidget(new PatternButton(x, y, cellWidth, cellHeight, scale, patterns[i]));
        }
        this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.BACK), button -> this.onClose())
                .bounds(this.width / 2 - 40, this.height - 28, 80, 20).build());
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
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        int y = 26;
        for (var line : this.font.split(Component.translatable(Translations.SpaceRace.FLAG_PATTERNS_HINT), this.width - 20)) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFAAAAAA);
            y += 10;
        }
    }

    private final class PatternButton extends AbstractButton {
        private final int scale;
        private final FlagPattern pattern;

        private PatternButton(int x, int y, int width, int height, int scale, FlagPattern pattern) {
            super(x, y, width, height, name(pattern));
            this.scale = scale;
            this.pattern = pattern;
            this.setTooltip(Tooltip.create(name(pattern)));
        }

        @Override
        public void onPress() {
            apply.accept(this.pattern);
            minecraft.setScreen(parent);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int frame = this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF555555;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xAA000000);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), frame);
            int left = getX() + 3, top = getY() + 3;
            for (int y = 0; y < FlagCanvas.HEIGHT; y++) {
                int x = 0;
                while (x < FlagCanvas.WIDTH) {
                    boolean primaryRun = this.pattern.primaryAt(x, y);
                    int end = x + 1;
                    while (end < FlagCanvas.WIDTH && this.pattern.primaryAt(end, y) == primaryRun) end++;
                    graphics.fill(left + x * this.scale, top + y * this.scale, left + end * this.scale, top + (y + 1) * this.scale,
                            (primaryRun ? primary : secondary) | 0xFF000000);
                    x = end;
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
