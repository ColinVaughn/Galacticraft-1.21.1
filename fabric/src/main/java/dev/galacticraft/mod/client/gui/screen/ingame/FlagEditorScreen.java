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
import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Layered flag editor with drawing tools, patterns, mirrored strokes, and undo history.
 * Left click paints the primary color; right click paints the secondary.
 */
final class FlagEditorScreen extends Screen {
    private static final int TOP = 28;
    private static final int TOOL_COLUMN = 20;
    private static final int PANEL_WIDTH = 112;
    private static final int RECENT = 8;
    private static final int LAYER_ROW = 17;
    /** Vanilla's button backgrounds, which its own buttons keep private. */
    private static final WidgetSprites BUTTON = new WidgetSprites(ResourceLocation.withDefaultNamespace("widget/button"),
            ResourceLocation.withDefaultNamespace("widget/button_disabled"), ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

    enum Tool {
        PENCIL(FlagEditorIcons.PENCIL, Translations.SpaceRace.FLAG_TOOL_PENCIL, Translations.SpaceRace.FLAG_HINT_PENCIL, GLFW.GLFW_KEY_P),
        ERASER(FlagEditorIcons.ERASER, Translations.SpaceRace.FLAG_TOOL_ERASER, Translations.SpaceRace.FLAG_HINT_ERASER, GLFW.GLFW_KEY_E),
        LINE(FlagEditorIcons.LINE, Translations.SpaceRace.FLAG_TOOL_LINE, Translations.SpaceRace.FLAG_HINT_LINE, GLFW.GLFW_KEY_L),
        RECTANGLE(FlagEditorIcons.RECTANGLE, Translations.SpaceRace.FLAG_TOOL_RECTANGLE, Translations.SpaceRace.FLAG_HINT_RECTANGLE, GLFW.GLFW_KEY_R),
        ELLIPSE(FlagEditorIcons.ELLIPSE, Translations.SpaceRace.FLAG_TOOL_ELLIPSE, Translations.SpaceRace.FLAG_HINT_ELLIPSE, GLFW.GLFW_KEY_O),
        FILL(FlagEditorIcons.FILL, Translations.SpaceRace.FLAG_TOOL_FILL, Translations.SpaceRace.FLAG_HINT_FILL, GLFW.GLFW_KEY_F),
        PICKER(FlagEditorIcons.PICKER, Translations.SpaceRace.FLAG_TOOL_PICKER, Translations.SpaceRace.FLAG_HINT_PICKER, GLFW.GLFW_KEY_I);

        private final String[] icon;
        private final String name;
        private final String hint;
        private final int key;

        Tool(String[] icon, String name, String hint, int key) {
            this.icon = icon;
            this.name = name;
            this.hint = hint;
            this.key = key;
        }

        /** Whether the tool draws a shape from where the drag starts to where it ends. */
        boolean shaped() {
            return this == LINE || this == RECTANGLE || this == ELLIPSE;
        }

        /** Whether the tool paints wherever the cursor is dragged. */
        boolean freehand() {
            return this == PENCIL || this == ERASER;
        }
    }

    private enum Panel {
        COLORS(Translations.SpaceRace.FLAG_TAB_COLORS),
        LAYERS(Translations.SpaceRace.FLAG_TAB_LAYERS);

        private final String name;

        Panel(String name) {
            this.name = name;
        }
    }

    /**
     * Canvas snapshot used to redraw or cancel the current stroke.
     */
    private static final class Stroke {
        private final int color;
        private final int startX;
        private final int startY;
        private final int[] before;
        private int lastX;
        private int lastY;

        private Stroke(int color, int x, int y, int[] before) {
            this.color = color;
            this.startX = this.lastX = x;
            this.startY = this.lastY = y;
            this.before = before;
        }
    }

    private final Screen parent;
    private final Consumer<NativeImage> save;
    private final FlagCanvas canvas;
    private final List<Integer> recent = new ArrayList<>();
    private Tool tool = Tool.PENCIL;
    private Tool beforePicker = Tool.PENCIL;
    private Panel panel = Panel.COLORS;
    private int primary = 0xFF000000;
    private int secondary = 0xFFFFFFFF;
    private int brushSize = 1;
    private boolean filledShapes;
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean grid;
    private boolean saveFailed;
    private @Nullable Stroke stroke;
    private CanvasWidget canvasWidget;
    private EditBox hexBox;
    private Button undoButton;
    private Button redoButton;

    FlagEditorScreen(Screen parent, int[] pixels, Consumer<NativeImage> save) {
        super(Component.translatable(Translations.SpaceRace.DRAW_FLAG));
        this.parent = parent;
        this.save = save;
        FlagCanvas layered = FlagLayerFile.load(pixels);
        if (layered != null) {
            this.canvas = layered;
        } else {
            this.canvas = new FlagCanvas(pixels);
            this.canvas.setLayerName(0, Component.translatable(Translations.SpaceRace.FLAG_LAYER_BACKGROUND).getString());
        }
        this.recent.addAll(this.canvas.commonColors(RECENT));
    }

    @Override
    protected void init() {
        int scale = Math.max(1, Math.min(10, Math.min((this.width - 160) / FlagCanvas.WIDTH,
                (this.height - 106) / FlagCanvas.HEIGHT)));
        int canvasWidth = FlagCanvas.WIDTH * scale, canvasHeight = FlagCanvas.HEIGHT * scale;
        int left = (this.width - (TOOL_COLUMN + 8 + canvasWidth + 10 + PANEL_WIDTH)) / 2;
        int canvasX = left + TOOL_COLUMN + 8, panelX = canvasX + canvasWidth + 10;

        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            Tool each = tools[i];
            this.addRenderableWidget(new IconButton(left, TOP + i * 22, 20, each.icon,
                    Component.translatable(each.name), () -> this.selectTool(each), () -> this.tool == each));
        }

        this.canvasWidget = this.addRenderableWidget(new CanvasWidget(canvasX, TOP, scale));

        int optionsY = TOP + canvasHeight + 6, x = canvasX;
        String[][] sizes = {FlagEditorIcons.SIZE_1, FlagEditorIcons.SIZE_2, FlagEditorIcons.SIZE_3};
        for (int size = 1; size <= FlagCanvas.MAX_BRUSH; size++) {
            int each = size;
            this.addRenderableWidget(new IconButton(x, optionsY, 16, sizes[size - 1],
                    Component.translatable(Translations.SpaceRace.FLAG_BRUSH_SIZE, size), () -> this.brushSize = each, () -> this.brushSize == each));
            x += 18;
        }
        x += 6;
        this.addRenderableWidget(new IconButton(x, optionsY, 16, FlagEditorIcons.FILLED,
                Component.translatable(Translations.SpaceRace.FLAG_FILLED_SHAPES), () -> this.filledShapes = !this.filledShapes, () -> this.filledShapes));
        this.addRenderableWidget(new IconButton(x + 18, optionsY, 16, FlagEditorIcons.MIRROR_X,
                Component.translatable(Translations.SpaceRace.FLAG_MIRROR_X), () -> this.mirrorX = !this.mirrorX, () -> this.mirrorX));
        this.addRenderableWidget(new IconButton(x + 36, optionsY, 16, FlagEditorIcons.MIRROR_Y,
                Component.translatable(Translations.SpaceRace.FLAG_MIRROR_Y), () -> this.mirrorY = !this.mirrorY, () -> this.mirrorY));
        this.addRenderableWidget(new IconButton(x + 54, optionsY, 16, FlagEditorIcons.GRID,
                Component.translatable(Translations.SpaceRace.FLAG_GRID), () -> this.grid = !this.grid, () -> this.grid));

        this.addRenderableWidget(new ColorPair(panelX, TOP));
        this.addRenderableWidget(new IconButton(panelX + 30, TOP + 5, 16, FlagEditorIcons.SWAP,
                Component.translatable(Translations.SpaceRace.FLAG_SWAP), this::swapColors, () -> false));
        this.hexBox = this.addRenderableWidget(new EditBox(this.font, panelX + 50, TOP + 5, PANEL_WIDTH - 50, 16,
                Component.translatable(Translations.SpaceRace.FLAG_HEX)));
        this.hexBox.setMaxLength(7);
        this.hexBox.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
        this.hexBox.setValue(hex(this.primary));
        this.hexBox.setResponder(text -> {
            String digits = text.startsWith("#") ? text.substring(1) : text;
            if (digits.length() == 6 && this.hexBox.isFocused()) {
                this.primary = 0xFF000000 | Integer.parseInt(digits, 16);
            }
        });
        this.hexBox.setTooltip(Tooltip.create(Component.translatable(Translations.SpaceRace.FLAG_HEX)));

        int tabWidth = PANEL_WIDTH / 2 - 1;
        this.addRenderableWidget(new TabButton(panelX, TOP + 28, tabWidth, Panel.COLORS));
        this.addRenderableWidget(new TabButton(panelX + PANEL_WIDTH - tabWidth, TOP + 28, tabWidth, Panel.LAYERS));
        int contentY = TOP + 46;
        if (this.panel == Panel.COLORS) {
            DyeColor[] dyes = DyeColor.values();
            for (int i = 0; i < dyes.length; i++) {
                DyeColor dye = dyes[i];
                this.addRenderableWidget(new ColorCell(panelX + i % 8 * 14, contentY + i / 8 * 14, 0xFF000000 | dye.getTextureDiffuseColor(),
                        Component.translatable("color.minecraft." + dye.getName())));
            }
            this.addRenderableWidget(new RecentColors(panelX, contentY + 30));
            this.addRenderableWidget(new PrimarySlider(panelX, contentY + 48, Translations.SpaceRace.RED, 16));
            this.addRenderableWidget(new PrimarySlider(panelX, contentY + 68, Translations.SpaceRace.GREEN, 8));
            this.addRenderableWidget(new PrimarySlider(panelX, contentY + 88, Translations.SpaceRace.BLUE, 0));
        } else {
            this.addRenderableWidget(new IconButton(panelX, contentY, 16, FlagEditorIcons.LAYER_ADD,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_ADD), this::addLayer, () -> false,
                    () -> this.canvas.layerCount() < FlagCanvas.MAX_LAYERS));
            this.addRenderableWidget(new IconButton(panelX + 19, contentY, 16, FlagEditorIcons.LAYER_DUPLICATE,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_DUPLICATE), this::duplicateLayer, () -> false,
                    () -> this.canvas.layerCount() < FlagCanvas.MAX_LAYERS));
            this.addRenderableWidget(new IconButton(panelX + 38, contentY, 16, FlagEditorIcons.LAYER_DELETE,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_DELETE), this.canvas::deleteLayer, () -> false,
                    () -> this.canvas.layerCount() > 1));
            this.addRenderableWidget(new IconButton(panelX + 57, contentY, 16, FlagEditorIcons.LAYER_UP,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_UP), () -> this.canvas.moveLayer(1), () -> false,
                    () -> this.canvas.activeLayer() < this.canvas.layerCount() - 1));
            this.addRenderableWidget(new IconButton(panelX + 76, contentY, 16, FlagEditorIcons.LAYER_DOWN,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_DOWN), () -> this.canvas.moveLayer(-1), () -> false,
                    () -> this.canvas.activeLayer() > 0));
            this.addRenderableWidget(new IconButton(panelX + 95, contentY, 16, FlagEditorIcons.LAYER_MERGE,
                    Component.translatable(Translations.SpaceRace.FLAG_LAYER_MERGE), this.canvas::mergeDown, () -> false,
                    () -> this.canvas.activeLayer() > 0));
            this.addRenderableWidget(new LayerList(panelX, contentY + 20));
        }

        int buttonWidth = Math.min(68, (this.width - 16 - 4 * 4) / 5);
        int rowX = (this.width - (5 * buttonWidth + 4 * 4)) / 2, rowY = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.BACK), button -> this.onClose())
                .bounds(rowX, rowY, buttonWidth, 20).build());
        this.undoButton = this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.FLAG_UNDO), button -> this.canvas.undo())
                .bounds(rowX + (buttonWidth + 4), rowY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(Translations.SpaceRace.FLAG_UNDO_SHORTCUT))).build());
        this.redoButton = this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.FLAG_REDO), button -> this.canvas.redo())
                .bounds(rowX + 2 * (buttonWidth + 4), rowY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(Translations.SpaceRace.FLAG_REDO_SHORTCUT))).build());
        this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.FLAG_PATTERNS), button -> this.openPatterns())
                .bounds(rowX + 3 * (buttonWidth + 4), rowY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable(Translations.SpaceRace.FLAG_USE), button -> this.useFlag())
                .bounds(rowX + 4 * (buttonWidth + 4), rowY, buttonWidth, 20).build());
    }

    private void selectTool(Tool tool) {
        if (tool == Tool.PICKER && this.tool != Tool.PICKER) {
            this.beforePicker = this.tool;
        }
        this.tool = tool;
    }

    private void showPanel(Panel panel) {
        if (this.panel != panel) {
            this.panel = panel;
            this.rebuildWidgets();
        }
    }

    private void setPrimary(int color) {
        this.primary = color | 0xFF000000;
    }

    private void setSecondary(int color) {
        this.secondary = color | 0xFF000000;
    }

    private void swapColors() {
        int primary = this.primary;
        this.primary = this.secondary;
        this.secondary = primary;
    }

    private void remember(int color) {
        if (color == FlagCanvas.CLEAR) return;
        this.recent.remove((Integer) (color | 0xFF000000));
        this.recent.addFirst(color | 0xFF000000);
        while (this.recent.size() > RECENT) this.recent.removeLast();
    }

    private FlagCanvas.Brush brush(int color) {
        return new FlagCanvas.Brush(color, this.brushSize, this.mirrorX, this.mirrorY);
    }

    private void addLayer() {
        this.canvas.addLayer(Component.translatable(Translations.SpaceRace.FLAG_LAYER_NAME, this.canvas.layerCount()).getString());
    }

    private void duplicateLayer() {
        String name = this.canvas.layerName(this.canvas.activeLayer());
        this.canvas.duplicateLayer(Component.translatable(Translations.SpaceRace.FLAG_LAYER_COPY, name).getString());
    }

    private void openPatterns() {
        int primary = this.primary, secondary = this.secondary;
        this.minecraft.setScreen(new FlagPatternScreen(this, primary, secondary, pattern -> {
            this.canvas.checkpoint();
            this.canvas.applyPattern(pattern, primary, secondary);
            this.remember(secondary);
            this.remember(primary);
        }));
    }

    private void useFlag() {
        FlagLayerFile.save(this.canvas);
        this.save.accept(this.toImage());
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void begin(int x, int y, int button) {
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hasAltDown())) {
            this.pick(x, y, false);
            return;
        }
        int color = this.tool == Tool.ERASER ? FlagCanvas.CLEAR : right ? this.secondary : this.primary;
        switch (this.tool) {
            case PICKER -> {
                this.pick(x, y, right);
                this.tool = this.beforePicker;
            }
            case FILL -> {
                this.canvas.checkpoint();
                this.canvas.floodFill(x, y, this.brush(color));
                this.remember(color);
            }
            default -> {
                this.canvas.checkpoint();
                this.stroke = new Stroke(color, x, y, this.canvas.activePixels());
                this.draw(this.stroke);
            }
        }
    }

    private void pick(int x, int y, boolean secondary) {
        int color = this.canvas.pixel(x, y);
        if (color == FlagCanvas.CLEAR) return;
        if (secondary) this.setSecondary(color);
        else this.setPrimary(color);
    }

    private void drag(int x, int y, boolean onCanvas) {
        Stroke stroke = this.stroke;
        if (stroke == null) return;
        if (this.tool.freehand()) {
            // Lifting off the canvas breaks the stroke, so coming back in elsewhere does not draw a line across.
            if (!onCanvas) {
                stroke.lastX = -1;
                return;
            }
            FlagCanvas.Brush brush = this.brush(stroke.color);
            if (stroke.lastX < 0) this.canvas.line(x, y, x, y, brush);
            else this.canvas.line(stroke.lastX, stroke.lastY, x, y, brush);
        }
        stroke.lastX = x;
        stroke.lastY = y;
        this.redrawShape();
    }

    private void finish() {
        Stroke stroke = this.stroke;
        if (stroke == null) return;
        this.redrawShape();
        this.remember(stroke.color);
        this.stroke = null;
    }

    private void cancel() {
        if (this.stroke != null) {
            this.canvas.restoreActive(this.stroke.before);
            this.canvas.discardCheckpoint();
            this.stroke = null;
        }
    }

    private void redrawShape() {
        if (this.stroke != null && this.tool.shaped()) {
            this.canvas.restoreActive(this.stroke.before);
            this.draw(this.stroke);
        }
    }

    private void draw(Stroke stroke) {
        FlagCanvas.Brush brush = this.brush(stroke.color);
        if (this.tool.freehand()) {
            this.canvas.line(stroke.startX, stroke.startY, stroke.startX, stroke.startY, brush);
            return;
        }
        int[] end = {stroke.lastX, stroke.lastY};
        if (hasShiftDown()) {
            end = this.tool == Tool.LINE ? FlagCanvas.snapLine(stroke.startX, stroke.startY, end[0], end[1])
                    : FlagCanvas.square(stroke.startX, stroke.startY, end[0], end[1]);
        }
        switch (this.tool) {
            case LINE -> this.canvas.line(stroke.startX, stroke.startY, end[0], end[1], brush);
            case RECTANGLE -> this.canvas.rectangle(stroke.startX, stroke.startY, end[0], end[1], brush, this.filledShapes);
            case ELLIPSE -> this.canvas.ellipse(stroke.startX, stroke.startY, end[0], end[1], brush, this.filledShapes);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.stroke != null) {
            // A second mouse button cancels the current drag.
            this.cancel();
            return true;
        }
        if (this.canvasWidget.isMouseOver(mouseX, mouseY)) {
            this.setFocused(this.canvasWidget);
            this.hexBox.setFocused(false);
            this.begin(this.canvasWidget.cellX(mouseX), this.canvasWidget.cellY(mouseY), button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.stroke != null) {
            this.drag(this.canvasWidget.cellX(mouseX), this.canvasWidget.cellY(mouseY), this.canvasWidget.isMouseOver(mouseX, mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.stroke != null) {
            this.finish();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (this.stroke != null) {
            // Mid-drag, Escape cancels and Shift reshapes the preview at once; nothing else may touch the canvas.
            if (key == GLFW.GLFW_KEY_ESCAPE) this.cancel();
            else this.redrawShape();
            return true;
        }
        if (this.hexBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.hexBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (hasControlDown()) {
            if (key == GLFW.GLFW_KEY_Z) {
                if (hasShiftDown()) this.canvas.redo();
                else this.canvas.undo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                this.canvas.redo();
                return true;
            }
        }
        for (Tool each : Tool.values()) {
            if (key == each.key) {
                this.selectTool(each);
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_X -> this.swapColors();
            case GLFW.GLFW_KEY_G -> this.grid = !this.grid;
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3 -> this.brushSize = key - GLFW.GLFW_KEY_0;
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (this.stroke != null) {
            this.redrawShape();
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private NativeImage toImage() {
        int[] flag = this.canvas.exportPixels();
        NativeImage image = new NativeImage(FlagCanvas.WIDTH, FlagCanvas.HEIGHT, false);
        for (int y = 0; y < FlagCanvas.HEIGHT; y++) {
            for (int x = 0; x < FlagCanvas.WIDTH; x++) {
                image.setPixelRGBA(x, y, FlagCanvas.swapRedBlue(flag[y * FlagCanvas.WIDTH + x]));
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
        this.undoButton.active = this.canvas.canUndo();
        this.redoButton.active = this.canvas.canRedo();
        if (!this.hexBox.isFocused() && !this.hexBox.getValue().equalsIgnoreCase(hex(this.primary))) {
            this.hexBox.setValue(hex(this.primary));
        }
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable(this.saveFailed
                        ? Translations.SpaceRace.FLAG_SAVE_ERROR : this.tool.hint),
                this.width / 2, this.height - 38, this.saveFailed ? 0xFFFF5555 : 0xFFAAAAAA);
    }

    private static int checker(int x, int y) {
        return ((x + y) & 1) == 0 ? 0xFFE6E6E6 : 0xFFC4C4C4;
    }

    private final class IconButton extends AbstractButton {
        private final String[] icon;
        private final Runnable action;
        private final BooleanSupplier selected;
        private final BooleanSupplier enabled;

        private IconButton(int x, int y, int size, String[] icon, Component name, Runnable action, BooleanSupplier selected) {
            this(x, y, size, icon, name, action, selected, () -> true);
        }

        private IconButton(int x, int y, int size, String[] icon, Component name, Runnable action, BooleanSupplier selected,
                           BooleanSupplier enabled) {
            super(x, y, size, size, name);
            this.icon = icon;
            this.action = action;
            this.selected = selected;
            this.enabled = enabled;
            this.setTooltip(Tooltip.create(name));
        }

        @Override
        public void onPress() {
            this.action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.active = this.enabled.getAsBoolean();
            boolean on = this.selected.getAsBoolean();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            graphics.blitSprite(BUTTON.get(this.active, this.isHoveredOrFocused() || on), getX(), getY(), getWidth(), getHeight());
            if (on) {
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            }
            if (!this.active) graphics.setColor(1.0F, 1.0F, 1.0F, 0.4F);
            FlagEditorIcons.draw(graphics, this.icon, getX() + (getWidth() - FlagEditorIcons.SIZE) / 2,
                    getY() + (getHeight() - FlagEditorIcons.SIZE) / 2);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private final class TabButton extends AbstractButton {
        private final Panel tab;

        private TabButton(int x, int y, int width, Panel tab) {
            super(x, y, width, 14, Component.translatable(tab.name));
            this.tab = tab;
        }

        @Override
        public void onPress() {
            showPanel(this.tab);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean on = panel == this.tab;
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            graphics.blitSprite(BUTTON.get(true, on || this.isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());
            if (on) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            graphics.drawCenteredString(font, this.getMessage(), getX() + getWidth() / 2, getY() + 3, on ? 0xFFFFFFFF : 0xFFC6C6C6);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** A colour to paint with: left click makes it the primary, right click the secondary. */
    private final class ColorCell extends AbstractWidget {
        private final int color;

        private ColorCell(int x, int y, int color, Component name) {
            super(x, y, 12, 12, name);
            this.color = color;
            this.setTooltip(Tooltip.create(name));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || !this.isMouseOver(mouseX, mouseY)) return false;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setPrimary(this.color);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) setSecondary(this.color);
            else return false;
            this.playDownSound(minecraft.getSoundManager());
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            drawSwatch(graphics, getX(), getY(), this.color, this.isHovered());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private void drawSwatch(GuiGraphics graphics, int x, int y, int color, boolean hovered) {
        graphics.fill(x, y, x + 12, y + 12, color);
        if (color == this.primary) {
            graphics.renderOutline(x - 1, y - 1, 14, 14, 0xFFFFFFFF);
        } else if (color == this.secondary) {
            graphics.renderOutline(x - 1, y - 1, 14, 14, 0xFF8B8B8B);
        } else {
            // Outline dark swatches so they remain visible against the background.
            graphics.renderOutline(x - 1, y - 1, 14, 14, hovered ? 0xFFC6C6C6 : 0xFF3A3A3A);
        }
    }

    private final class RecentColors extends AbstractWidget {
        private RecentColors(int x, int y) {
            super(x, y, RECENT * 14, 12, Component.empty());
        }

        private int indexAt(double mouseX) {
            int index = (int) (mouseX - getX()) / 14;
            return index < recent.size() && (mouseX - getX()) % 14 < 12 ? index : -1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.isMouseOver(mouseX, mouseY)) return false;
            int index = this.indexAt(mouseX);
            if (index < 0) return false;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setPrimary(recent.get(index));
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) setSecondary(recent.get(index));
            else return false;
            this.playDownSound(minecraft.getSoundManager());
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int hovered = this.isHovered() ? this.indexAt(mouseX) : -1;
            for (int i = 0; i < RECENT; i++) {
                int x = getX() + i * 14;
                if (i < recent.size()) {
                    drawSwatch(graphics, x, getY(), recent.get(i), i == hovered);
                } else {
                    graphics.renderOutline(x, getY(), 12, 12, 0xFF3A3A3A);
                }
            }
            if (hovered >= 0) {
                setTooltipForNextRenderPass(Component.literal(hex(recent.get(hovered))));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    private final class LayerList extends AbstractWidget {
        private LayerList(int x, int y) {
            super(x, y, PANEL_WIDTH, FlagCanvas.MAX_LAYERS * LAYER_ROW, Component.translatable(Translations.SpaceRace.FLAG_TAB_LAYERS));
        }

        /** Returns the layer under the mouse, or -1 outside the list. */
        private int layerAt(double mouseY) {
            int row = (int) (mouseY - getY()) / LAYER_ROW;
            return row >= 0 && row < canvas.layerCount() ? canvas.layerCount() - 1 - row : -1;
        }

        private boolean onEye(double mouseX) {
            return mouseX < getX() + 14;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.isMouseOver(mouseX, mouseY)) return false;
            int layer = this.layerAt(mouseY);
            if (layer < 0) return false;
            if (this.onEye(mouseX)) canvas.toggleVisible(layer);
            else canvas.selectLayer(layer);
            this.playDownSound(minecraft.getSoundManager());
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int hovered = this.isHovered() ? this.layerAt(mouseY) : -1;
            for (int row = 0; row < canvas.layerCount(); row++) {
                int layer = canvas.layerCount() - 1 - row;
                int x = getX(), y = getY() + row * LAYER_ROW;
                if (layer == canvas.activeLayer()) {
                    graphics.fill(x, y, x + getWidth(), y + LAYER_ROW - 1, 0x50FFFFFF);
                    graphics.renderOutline(x, y, getWidth(), LAYER_ROW - 1, 0xFFFFFFFF);
                } else if (layer == hovered) {
                    graphics.fill(x, y, x + getWidth(), y + LAYER_ROW - 1, 0x25FFFFFF);
                }
                boolean visible = canvas.layerVisible(layer);
                FlagEditorIcons.draw(graphics, visible ? FlagEditorIcons.EYE : FlagEditorIcons.EYE_CLOSED, x + 1, y + 2);
                this.thumbnail(graphics, layer, x + 15, y);
                graphics.drawString(font, font.plainSubstrByWidth(canvas.layerName(layer), getWidth() - 44),
                        x + 42, y + 4, visible ? 0xFFFFFFFF : 0xFF8B8B8B);
            }
            if (hovered >= 0) {
                setTooltipForNextRenderPass(this.onEye(mouseX)
                        ? Component.translatable(Translations.SpaceRace.FLAG_LAYER_VISIBILITY)
                        : Component.literal(canvas.layerName(hovered)));
            }
        }

        private void thumbnail(GuiGraphics graphics, int layer, int left, int top) {
            for (int y = 0; y < FlagCanvas.HEIGHT / 2; y++) {
                for (int x = 0; x < FlagCanvas.WIDTH / 2; x++) {
                    int color = canvas.layerPixel(layer, x * 2, y * 2);
                    int empty = ((x + y) & 1) == 0 ? 0xFF8A8A8A : 0xFF6E6E6E;
                    graphics.fill(left + x, top + y, left + x + 1, top + y + 1, color == FlagCanvas.CLEAR ? empty : color);
                }
            }
            graphics.renderOutline(left - 1, top - 1, FlagCanvas.WIDTH / 2 + 2, FlagCanvas.HEIGHT / 2 + 2, 0xFF3A3A3A);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private final class ColorPair extends AbstractWidget {
        private ColorPair(int x, int y) {
            super(x, y, 26, 26, Component.translatable(Translations.SpaceRace.FLAG_SWAP));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            swapColors();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            graphics.fill(x + 9, y + 9, x + 26, y + 26, 0xFF000000);
            graphics.fill(x + 10, y + 10, x + 25, y + 25, secondary);
            graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, x + 16, y + 16, primary);
            if (this.isHovered()) {
                boolean front = mouseX < x + 17 && mouseY < y + 17;
                setTooltipForNextRenderPass(Component.translatable(front ? Translations.SpaceRace.FLAG_PRIMARY : Translations.SpaceRace.FLAG_SECONDARY)
                        .append("\n").append(Component.translatable(Translations.SpaceRace.FLAG_SWAP)));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private final class PrimarySlider extends AbstractSliderButton {
        private final String translation;
        private final int shift;
        private int shown;

        private PrimarySlider(int x, int y, String translation, int shift) {
            super(x, y, PANEL_WIDTH, 18, Component.empty(), ((primary >>> shift) & 0xFF) / 255.0);
            this.translation = translation;
            this.shift = shift;
            this.shown = primary;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("options.generic_value",
                    Component.translatable(this.translation), (int) Math.round(this.value * 255)));
        }

        @Override
        protected void applyValue() {
            primary = (primary & ~(0xFF << this.shift)) | ((int) Math.round(this.value * 255) << this.shift);
            this.shown = primary;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (this.shown != primary) {
                this.shown = primary;
                this.value = ((primary >>> this.shift) & 0xFF) / 255.0;
                this.updateMessage();
            }
            super.renderWidget(graphics, mouseX, mouseY, delta);
        }
    }

    private final class CanvasWidget extends AbstractWidget {
        private final int scale;
        private int cursorX;
        private int cursorY;

        private CanvasWidget(int x, int y, int scale) {
            super(x, y, FlagCanvas.WIDTH * scale, FlagCanvas.HEIGHT * scale,
                    Component.translatable(Translations.SpaceRace.FLAG_DRAW_HINT));
            this.scale = scale;
        }

        int cellX(double mouseX) {
            return Math.max(0, Math.min(FlagCanvas.WIDTH - 1, (int) Math.floor((mouseX - getX()) / this.scale)));
        }

        int cellY(double mouseY) {
            return Math.max(0, Math.min(FlagCanvas.HEIGHT - 1, (int) Math.floor((mouseY - getY()) / this.scale)));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int left = getX(), top = getY(), s = this.scale;
            int[] pixels = canvas.copyPixels();
            for (int y = 0; y < FlagCanvas.HEIGHT; y++) {
                int x = 0;
                while (x < FlagCanvas.WIDTH) {
                    int color = pixels[y * FlagCanvas.WIDTH + x];
                    int end = x + 1;
                    if (color == FlagCanvas.CLEAR) {
                        graphics.fill(left + x * s, top + y * s, left + end * s, top + (y + 1) * s, checker(x, y));
                    } else {
                        while (end < FlagCanvas.WIDTH && pixels[y * FlagCanvas.WIDTH + end] == color) end++;
                        graphics.fill(left + x * s, top + y * s, left + end * s, top + (y + 1) * s, color);
                    }
                    x = end;
                }
            }
            if (grid && s >= 3) {
                for (int x = 1; x < FlagCanvas.WIDTH; x++) {
                    graphics.fill(left + x * s, top, left + x * s + 1, top + getHeight(), guide(x, FlagCanvas.WIDTH));
                }
                for (int y = 1; y < FlagCanvas.HEIGHT; y++) {
                    graphics.fill(left, top + y * s, left + getWidth(), top + y * s + 1, guide(y, FlagCanvas.HEIGHT));
                }
            }
            if (mirrorX) {
                int middle = left + getWidth() / 2;
                for (int y = top; y < top + getHeight(); y += 4) graphics.fill(middle - 1, y, middle + 1, Math.min(y + 2, top + getHeight()), 0xC0FFD24A);
            }
            if (mirrorY) {
                int middle = top + getHeight() / 2;
                for (int x = left; x < left + getWidth(); x += 4) graphics.fill(x, middle - 1, Math.min(x + 2, left + getWidth()), middle + 1, 0xC0FFD24A);
            }
            graphics.renderOutline(left - 1, top - 1, getWidth() + 2, getHeight() + 2, 0xFFAAAAAA);

            if (this.isHovered()) {
                this.cursorX = this.cellX(mouseX);
                this.cursorY = this.cellY(mouseY);
            }
            if (this.isHoveredOrFocused() && stroke == null) {
                int size = tool.freehand() || tool.shaped() ? brushSize : 1;
                int from = -(size - 1) / 2;
                this.outlineCells(graphics, this.cursorX + from, this.cursorY + from, size, 0xFFFFFFFF);
                if (mirrorX) this.outlineCells(graphics, FlagCanvas.WIDTH - 1 - this.cursorX + from, this.cursorY + from, size, 0x80FFFFFF);
                if (mirrorY) this.outlineCells(graphics, this.cursorX + from, FlagCanvas.HEIGHT - 1 - this.cursorY + from, size, 0x80FFFFFF);
                if (mirrorX && mirrorY) {
                    this.outlineCells(graphics, FlagCanvas.WIDTH - 1 - this.cursorX + from, FlagCanvas.HEIGHT - 1 - this.cursorY + from, size, 0x80FFFFFF);
                }
            }
        }

        private int guide(int line, int length) {
            if (line * 2 == length) return 0x90FFFFFF;
            if (line * 3 == length || line * 3 == length * 2) return 0x60FFFFFF;
            return 0x30000000;
        }

        private void outlineCells(GuiGraphics graphics, int cellX, int cellY, int size, int color) {
            int x = getX() + cellX * this.scale, y = getY() + cellY * this.scale, side = size * this.scale;
            graphics.renderOutline(x - 1, y - 1, side + 2, side + 2, 0xFF000000);
            graphics.renderOutline(x - 2, y - 2, side + 4, side + 4, color);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> this.cursorX = Math.max(0, this.cursorX - 1);
                case GLFW.GLFW_KEY_RIGHT -> this.cursorX = Math.min(FlagCanvas.WIDTH - 1, this.cursorX + 1);
                case GLFW.GLFW_KEY_UP -> this.cursorY = Math.max(0, this.cursorY - 1);
                case GLFW.GLFW_KEY_DOWN -> this.cursorY = Math.min(FlagCanvas.HEIGHT - 1, this.cursorY + 1);
                case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    // From the keyboard every tool acts on the cursor's pixel: shapes put down a single point.
                    begin(this.cursorX, this.cursorY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                    finish();
                }
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
