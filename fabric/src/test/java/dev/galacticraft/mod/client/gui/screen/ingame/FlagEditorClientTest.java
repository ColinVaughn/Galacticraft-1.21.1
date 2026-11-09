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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.Translations;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.DyeColor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Exercises flag editing through real clicks, drags, and keys; checks the saved image and captures
 * the editor and pattern picker at two GUI sizes.
 * Run with {@code gradlew :fabric:runClient -PflagEditorClientTest -Prei=false -Pastrocraft=false}.
 */
public final class FlagEditorClientTest implements ClientModInitializer {
    private static final int YELLOW = 0xFFFECC02;
    private static final int BLUE = 0xFF000000 | DyeColor.BLUE.getTextureDiffuseColor();
    private static final int RED = 0xFF000000 | DyeColor.RED.getTextureDiffuseColor();
    private static final int WHITE = 0xFF000000 | DyeColor.WHITE.getTextureDiffuseColor();

    private int step, wait, timeout;
    private FlagEditorScreen editor;
    private NativeImage saved;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("galacticraft.flag-editor.client-test")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (++timeout > 4000) throw new AssertionError("Flag editor check timed out at step " + step + ": " + failures);
        if (client.getOverlay() != null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        wait = 3;
        switch (step++) {
            case 0 -> {
                if (client.screen == null) {
                    step = 0;
                    return;
                }
                org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
                client.options.guiScale().set(0);
                client.resizeDisplay();
                this.editor = new FlagEditorScreen(client.screen, new FlagCanvas(0xFFFFFFFF).copyPixels(), image -> this.saved = image);
                client.setScreen(this.editor);
                wait = 10;
            }
            case 1 -> {
                // Type the primary colour into the hex box, and right click blue as the secondary.
                AbstractWidget hex = widget(Translations.SpaceRace.FLAG_HEX);
                click(hex, 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                for (int i = 0; i < 7; i++) this.editor.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
                for (char c : "#FECC02".toCharArray()) this.editor.charTyped(c, 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                click(widget("color.minecraft.blue"), 1);
                click(widget(Translations.SpaceRace.FLAG_PATTERNS), 0);
            }
            case 2 -> {
                if (!(client.screen instanceof FlagPatternScreen)) failures.add("Patterns... did not open the pattern picker");
                shot(client, "patterns");
            }
            case 3 -> click(widget(Translations.SpaceRace.FLAG_PATTERN_PREFIX + "nordic_cross"), 0);
            case 4 -> {
                if (client.screen != this.editor) failures.add("choosing a pattern did not return to the editor");
                expectCanvas(16, 0, YELLOW, "the Nordic cross's upright, in the primary colour");
                expectCanvas(0, 0, BLUE, "the field, in the secondary colour");
                shot(client, "sweden");
            }
            case 5 -> {
                // A filled red ellipse in the upper fly quarter.
                click(widget("color.minecraft.red"), 0);
                click(widget(Translations.SpaceRace.FLAG_FILLED_SHAPES), 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_O, 0, 0);
                drag(30, 3, 40, 9, 0);
                expectCanvas(35, 6, RED, "the middle of the filled ellipse");
                expectCanvas(30, 3, BLUE, "the rounded-off corner of the ellipse's box");
            }
            case 6 -> {
                // A size-2 pencil stroke, mirrored left and right.
                click(widget(Translations.SpaceRace.FLAG_MIRROR_X), 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_P, 0, 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_2, 0, 0);
                drag(2, 24, 8, 24, 0);
                expectCanvas(5, 25, RED, "the size-2 stroke");
                expectCanvas(42, 25, RED, "the stroke's mirror image");
            }
            case 7 -> {
                // A mirrored fill takes both upper field quarters, around the ellipse.
                click(widget("color.minecraft.white"), 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
                drag(2, 2, 2, 2, 0);
                expectCanvas(2, 2, WHITE, "the filled upper quarter by the pole");
                expectCanvas(45, 2, WHITE, "its mirror image at the fly");
                expectCanvas(35, 6, RED, "the ellipse the fill went round");
                expectCanvas(2, 28, BLUE, "the lower quarter across the cross, untouched");
            }
            case 8 -> {
                // The eyedropper picks red as the secondary and hands back to the fill tool.
                this.editor.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
                drag(35, 6, 35, 6, 1);
                drag(2, 28, 2, 28, 1);
                expectCanvas(2, 28, RED, "a right-click fill with the picked secondary colour");
            }
            case 9 -> {
                click(widget(Translations.SpaceRace.FLAG_UNDO), 0);
                expectCanvas(2, 28, BLUE, "undoing the last fill");
            }
            case 10 -> {
                // Redo only lights up once the screen has drawn the undo, as a player would see it.
                click(widget(Translations.SpaceRace.FLAG_REDO), 0);
                expectCanvas(2, 28, RED, "redoing it");
                this.editor.keyPressed(GLFW.GLFW_KEY_G, 0, 0);
            }
            case 11 -> shot(client, "drawing");
            case 12 -> click(widget(Translations.SpaceRace.FLAG_TAB_LAYERS), 0);
            case 13 -> {
                // A new layer over the stripes: a white bar drawn on it leaves the background red underneath.
                click(widget(Translations.SpaceRace.FLAG_LAYER_ADD), 0);
                this.editor.keyPressed(GLFW.GLFW_KEY_P, 0, 0);
                drag(20, 29, 27, 29, 0);
                expectCanvas(21, 29, WHITE, "the bar on the new layer");
                expectLayer(0, 21, 29, RED, "the background under the new layer's bar");
            }
            case 14 -> {
                this.editor.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                drag(24, 29, 24, 29, 0);
                expectCanvas(24, 29, RED, "an erased gap showing the background through the bar");
                expectCanvas(21, 29, WHITE, "the rest of the bar");
            }
            case 15 -> {
                clickLayerEye(0);
                expectCanvas(21, 29, RED, "the flag with the bar's layer hidden");
            }
            case 16 -> shot(client, "layers");
            case 17 -> {
                clickLayerEye(0);
                expectCanvas(21, 29, WHITE, "the flag with the bar's layer shown again");
            }
            case 18 -> {
                client.options.guiScale().set(2);
                client.resizeDisplay();
                wait = 10;
            }
            case 19 -> shot(client, "larger");
            case 20 -> {
                // The narrowest standard layout: 320 GUI pixels wide.
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 640, 480);
                client.options.guiScale().set(2);
                client.resizeDisplay();
                wait = 10;
            }
            case 21 -> shot(client, "narrow-layers");
            case 22 -> click(widget(Translations.SpaceRace.FLAG_TAB_COLORS), 0);
            case 23 -> shot(client, "narrow-colors");
            case 24 -> click(widget(Translations.SpaceRace.FLAG_PATTERNS), 0);
            case 25 -> shot(client, "narrow-patterns");
            case 26 -> click(widget(Translations.SpaceRace.BACK), 0);
            case 27 -> {
                click(widget(Translations.SpaceRace.FLAG_USE), 0);
                if (this.saved == null) {
                    failures.add("Use Flag saved nothing");
                } else {
                    expectSaved(16, 0, YELLOW, "the cross");
                    expectSaved(2, 2, WHITE, "the filled quarter");
                    expectSaved(35, 6, RED, "the ellipse");
                    expectSaved(2, 28, RED, "the eyedropped fill");
                    expectSaved(21, 29, WHITE, "the bar on the upper layer");
                    expectSaved(24, 29, RED, "the gap erased in it");
                    // Opening the editor on the saved flag has to bring its layers back.
                    int[] flag = new int[FlagCanvas.WIDTH * FlagCanvas.HEIGHT];
                    for (int i = 0; i < flag.length; i++) {
                        flag[i] = FlagCanvas.swapRedBlue(this.saved.getPixelRGBA(i % FlagCanvas.WIDTH, i / FlagCanvas.WIDTH));
                    }
                    FlagCanvas reopened = canvasOf(new FlagEditorScreen(this.editor, flag, image -> {}));
                    if (reopened.layerCount() != 2 || !"Layer 1".equals(reopened.layerName(1))) {
                        failures.add("reopening the editor did not bring the layers back: " + reopened.layerCount() + " layers");
                    }
                }
                if (!failures.isEmpty()) throw new AssertionError("Flag editor check failed: " + String.join("; ", failures));
                Constant.LOGGER.info("FLAG EDITOR CLIENT CHECK PASSED");
                client.stop();
            }
            default -> {
            }
        }
    }

    /** The editor's or picker's widget whose name is this translation key. */
    private static AbstractWidget widget(String key) {
        Screen screen = Minecraft.getInstance().screen;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(key)) {
                return widget;
            }
        }
        throw new AssertionError("No widget named " + key + " on " + screen);
    }

    private static void click(AbstractWidget widget, int button) {
        Screen screen = Minecraft.getInstance().screen;
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    /** Presses on one canvas pixel, drags through to another, and lets go. */
    private void drag(int fromX, int fromY, int toX, int toY, int button) {
        AbstractWidget canvas = widget(Translations.SpaceRace.FLAG_DRAW_HINT);
        double scale = canvas.getWidth() / (double) FlagCanvas.WIDTH;
        double x0 = canvas.getX() + (fromX + 0.5) * scale, y0 = canvas.getY() + (fromY + 0.5) * scale;
        double x1 = canvas.getX() + (toX + 0.5) * scale, y1 = canvas.getY() + (toY + 0.5) * scale;
        this.editor.mouseClicked(x0, y0, button);
        for (int i = 1; i <= 4; i++) {
            double x = x0 + (x1 - x0) * i / 4, y = y0 + (y1 - y0) * i / 4;
            this.editor.mouseDragged(x, y, button, (x1 - x0) / 4, (y1 - y0) / 4);
        }
        this.editor.mouseReleased(x1, y1, button);
    }

    private static FlagCanvas canvasOf(FlagEditorScreen editor) {
        try {
            var field = FlagEditorScreen.class.getDeclaredField("canvas");
            field.setAccessible(true);
            return (FlagCanvas) field.get(editor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void expectCanvas(int x, int y, int color, String what) {
        int actual = canvasOf(this.editor).pixel(x, y);
        if (actual != color) failures.add(String.format("%s at %d,%d was %08X, not %08X", what, x, y, actual, color));
    }

    private void expectLayer(int layer, int x, int y, int color, String what) {
        int actual = canvasOf(this.editor).layerPixel(layer, x, y);
        if (actual != color) failures.add(String.format("%s at %d,%d was %08X, not %08X", what, x, y, actual, color));
    }

    /** Clicks the eye of a row of the layer list, counted from the top. */
    private void clickLayerEye(int row) {
        for (GuiEventListener child : this.editor.children()) {
            if (child instanceof AbstractWidget widget && widget.getClass().getSimpleName().equals("LayerList")) {
                double x = widget.getX() + 5, y = widget.getY() + row * 17 + 8;
                this.editor.mouseClicked(x, y, 0);
                this.editor.mouseReleased(x, y, 0);
                return;
            }
        }
        throw new AssertionError("The layer list is not showing");
    }

    private void expectSaved(int x, int y, int color, String what) {
        int actual = FlagCanvas.swapRedBlue(this.saved.getPixelRGBA(x, y));
        if (actual != color) failures.add(String.format("saved %s at %d,%d was %08X, not %08X", what, x, y, actual, color));
    }

    private static void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, "flag-editor-" + name + ".png", client.getMainRenderTarget(), ignored -> {});
    }
}
