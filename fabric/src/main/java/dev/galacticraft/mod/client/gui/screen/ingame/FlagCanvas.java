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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layered flag canvas with drawing tools and undo history. Transparent pixels reveal lower layers;
 * call {@link #checkpoint} before each stroke so undo restores the whole operation.
 */
final class FlagCanvas {
    static final int WIDTH = 48;
    static final int HEIGHT = 32;
    static final int MAX_BRUSH = 3;
    static final int MAX_LAYERS = 6;
    /** A pixel with nothing painted on it, which lets the layers below show; on the flag it is white cloth. */
    static final int CLEAR = 0;
    private static final int CLOTH = 0xFFFFFFFF;
    private static final int HISTORY = 64;
    private final List<Layer> layers = new ArrayList<>();
    private int active;
    private final Deque<Snapshot> history = new ArrayDeque<>();
    private final Deque<Snapshot> future = new ArrayDeque<>();

    /**
     * What a tool paints with.
     *
     * @param color an opaque colour, or {@link #CLEAR} to erase
     * @param size the side of the square stamped at each point, 1 to {@link #MAX_BRUSH}, and the thickness of outlines
     * @param mirrorX whether everything is also painted mirrored left to right
     * @param mirrorY whether everything is also painted mirrored top to bottom
     */
    record Brush(int color, int size, boolean mirrorX, boolean mirrorY) {
        Brush {
            color = color == CLEAR ? CLEAR : color | 0xFF000000;
            size = Math.max(1, Math.min(MAX_BRUSH, size));
        }
    }

    private static final class Layer {
        private String name;
        private boolean visible = true;
        private final int[] pixels;

        private Layer(String name, int[] pixels) {
            this.name = name;
            this.pixels = pixels;
        }

        private Layer copy() {
            Layer copy = new Layer(this.name, this.pixels.clone());
            copy.visible = this.visible;
            return copy;
        }
    }

    private record Snapshot(List<Layer> layers, int active) {
    }

    FlagCanvas(int color) {
        int[] pixels = new int[WIDTH * HEIGHT];
        Arrays.fill(pixels, color | 0xFF000000);
        this.layers.add(new Layer("Background", pixels));
    }

    FlagCanvas(int[] pixels) {
        if (pixels.length != WIDTH * HEIGHT) {
            throw new IllegalArgumentException("Flags must be 48 x 32 pixels");
        }
        int[] opaque = pixels.clone();
        for (int i = 0; i < opaque.length; i++) {
            opaque[i] |= 0xFF000000;
        }
        this.layers.add(new Layer("Background", opaque));
    }

    private FlagCanvas() {
    }

    /** Returns the top visible pixel, or {@link #CLEAR} when no layer paints it. */
    int pixel(int x, int y) {
        int index = y * WIDTH + x;
        for (int i = this.layers.size() - 1; i >= 0; i--) {
            Layer layer = this.layers.get(i);
            if (layer.visible && layer.pixels[index] != CLEAR) {
                return layer.pixels[index];
            }
        }
        return CLEAR;
    }

    /** Returns the composited canvas pixels. */
    int[] copyPixels() {
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = pixel(x, y);
            }
        }
        return pixels;
    }

    /** Returns an opaque flag image, with empty cloth rendered white. */
    int[] exportPixels() {
        int[] pixels = copyPixels();
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] == CLEAR) pixels[i] = CLOTH;
        }
        return pixels;
    }

    static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color & 0xFF) << 16) | ((color >>> 16) & 0xFF);
    }

    int layerCount() {
        return this.layers.size();
    }

    int activeLayer() {
        return this.active;
    }

    String layerName(int layer) {
        return this.layers.get(layer).name;
    }

    void setLayerName(int layer, String name) {
        this.layers.get(layer).name = name;
    }

    boolean layerVisible(int layer) {
        return this.layers.get(layer).visible;
    }

    int layerPixel(int layer, int x, int y) {
        return this.layers.get(layer).pixels[y * WIDTH + x];
    }

    void selectLayer(int layer) {
        this.active = Math.max(0, Math.min(this.layers.size() - 1, layer));
    }

    /**
     * Adds and selects an empty layer above the current layer.
     *
     * @return false if {@link #MAX_LAYERS} has been reached
     */
    boolean addLayer(String name) {
        if (this.layers.size() >= MAX_LAYERS) return false;
        checkpoint();
        this.layers.add(this.active + 1, new Layer(name, new int[WIDTH * HEIGHT]));
        this.active++;
        return true;
    }

    /** Copies the selected layer to just above itself and selects the copy. */
    boolean duplicateLayer(String name) {
        if (this.layers.size() >= MAX_LAYERS) return false;
        checkpoint();
        Layer copy = this.layers.get(this.active).copy();
        copy.name = name;
        this.layers.add(this.active + 1, copy);
        this.active++;
        return true;
    }

    /** Deletes the selected layer, unless it is the last, and selects the one below it. */
    boolean deleteLayer() {
        if (this.layers.size() <= 1) return false;
        checkpoint();
        this.layers.remove(this.active);
        this.active = Math.max(0, this.active - 1);
        return true;
    }

    /** Moves the selected layer up ({@code +1}) or down ({@code -1}) the stack, keeping it selected. */
    boolean moveLayer(int direction) {
        int target = this.active + Integer.signum(direction);
        if (direction == 0 || target < 0 || target >= this.layers.size()) return false;
        checkpoint();
        this.layers.add(target, this.layers.remove(this.active));
        this.active = target;
        return true;
    }

    /** Paints the selected layer down onto the one below, which keeps its name and takes its place. */
    boolean mergeDown() {
        if (this.active == 0) return false;
        checkpoint();
        Layer upper = this.layers.remove(this.active);
        Layer lower = this.layers.get(this.active - 1);
        for (int i = 0; i < upper.pixels.length; i++) {
            if (upper.pixels[i] != CLEAR) lower.pixels[i] = upper.pixels[i];
        }
        this.active--;
        return true;
    }

    void toggleVisible(int layer) {
        checkpoint();
        Layer toggled = this.layers.get(layer);
        toggled.visible = !toggled.visible;
    }

    /** Returns a copy of the active layer for shape previews. */
    int[] activePixels() {
        return this.layers.get(this.active).pixels.clone();
    }

    /** Puts back the selected layer's pixels, outside the history. */
    void restoreActive(int[] pixels) {
        System.arraycopy(pixels, 0, this.layers.get(this.active).pixels, 0, WIDTH * HEIGHT);
    }

    private int[] activeLayerPixels() {
        return this.layers.get(this.active).pixels;
    }

    /**
     * Saves an undo checkpoint and clears redo history.
     */
    void checkpoint() {
        push(this.history, snapshot());
        this.future.clear();
    }

    /**
     * Discards the checkpoint for a cancelled operation without changing the canvas.
     */
    void discardCheckpoint() {
        this.history.pollLast();
    }

    boolean canUndo() {
        return !this.history.isEmpty();
    }

    boolean canRedo() {
        return !this.future.isEmpty();
    }

    void undo() {
        if (canUndo()) {
            push(this.future, snapshot());
            apply(this.history.removeLast());
        }
    }

    void redo() {
        if (canRedo()) {
            push(this.history, snapshot());
            apply(this.future.removeLast());
        }
    }

    private Snapshot snapshot() {
        List<Layer> copies = new ArrayList<>(this.layers.size());
        for (Layer layer : this.layers) copies.add(layer.copy());
        return new Snapshot(copies, this.active);
    }

    private void apply(Snapshot snapshot) {
        this.layers.clear();
        for (Layer layer : snapshot.layers()) this.layers.add(layer.copy());
        this.active = snapshot.active();
    }

    private static void push(Deque<Snapshot> stack, Snapshot state) {
        if (stack.size() == HISTORY) {
            stack.removeFirst();
        }
        stack.addLast(state);
    }

    /** Returns serialized layers without undo history. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Layer layer : this.layers) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", layer.name);
            entry.putBoolean("visible", layer.visible);
            entry.putIntArray("pixels", layer.pixels);
            list.add(entry);
        }
        tag.put("layers", list);
        tag.putInt("active", this.active);
        return tag;
    }

    /** Returns saved layers, or {@code null} when absent or damaged. */
    static @Nullable FlagCanvas load(CompoundTag tag) {
        ListTag list = tag.getList("layers", Tag.TAG_COMPOUND);
        if (list.isEmpty() || list.size() > MAX_LAYERS) return null;
        FlagCanvas canvas = new FlagCanvas();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int[] pixels = entry.getIntArray("pixels");
            if (pixels.length != WIDTH * HEIGHT) return null;
            Layer layer = new Layer(entry.getString("name"), pixels.clone());
            layer.visible = !entry.contains("visible") || entry.getBoolean("visible");
            canvas.layers.add(layer);
        }
        canvas.selectLayer(tag.getInt("active"));
        return canvas;
    }

    void fill(int color) {
        checkpoint();
        Arrays.fill(activeLayerPixels(), color | 0xFF000000);
    }

    /** Draws a one-pixel line in an opaque colour; {@code 0} here is black, not {@link #CLEAR}. */
    void line(int x0, int y0, int x1, int y1, int color) {
        line(x0, y0, x1, y1, new Brush(color | 0xFF000000, 1, false, false));
    }

    /** Draws a connected line, stamping the brush at each point; nothing if either end is off the canvas. */
    void line(int x0, int y0, int x1, int y1, Brush brush) {
        if (!contains(x0, y0) || !contains(x1, y1)) {
            return;
        }
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1, stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            stamp(x0, y0, brush);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int twiceError = 2 * error;
            if (twiceError >= dy) {
                error += dy;
                x0 += stepX;
            }
            if (twiceError <= dx) {
                error += dx;
                y0 += stepY;
            }
        }
    }

    /** Draws the box between two corners, filled or as an outline as thick as the brush. */
    void rectangle(int x0, int y0, int x1, int y1, Brush brush, boolean filled) {
        int left = clampX(Math.min(x0, x1)), right = clampX(Math.max(x0, x1));
        int top = clampY(Math.min(y0, y1)), bottom = clampY(Math.max(y0, y1));
        int thickness = brush.size();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                boolean edge = x - left < thickness || right - x < thickness || y - top < thickness || bottom - y < thickness;
                if (filled || edge) {
                    plot(x, y, brush);
                }
            }
        }
    }

    /** Draws the ellipse that fits the box between two corners, filled or as an outline as thick as the brush. */
    void ellipse(int x0, int y0, int x1, int y1, Brush brush, boolean filled) {
        int left = clampX(Math.min(x0, x1)), right = clampX(Math.max(x0, x1));
        int top = clampY(Math.min(y0, y1)), bottom = clampY(Math.max(y0, y1));
        double centerX = (left + right + 1) / 2.0, centerY = (top + bottom + 1) / 2.0;
        double radiusX = (right - left + 1) / 2.0, radiusY = (bottom - top + 1) / 2.0;
        int thickness = brush.size();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!insideEllipse(x, y, centerX, centerY, radiusX, radiusY)) {
                    continue;
                }
                if (filled || nearEllipseEdge(x, y, thickness, centerX, centerY, radiusX, radiusY)) {
                    plot(x, y, brush);
                }
            }
        }
    }

    private static boolean insideEllipse(int x, int y, double centerX, double centerY, double radiusX, double radiusY) {
        double dx = (x + 0.5 - centerX) / radiusX, dy = (y + 0.5 - centerY) / radiusY;
        return dx * dx + dy * dy <= 1.0;
    }

    private static boolean nearEllipseEdge(int x, int y, int thickness, double centerX, double centerY, double radiusX, double radiusY) {
        for (int dy = -thickness; dy <= thickness; dy++) {
            int reach = thickness - Math.abs(dy);
            for (int dx = -reach; dx <= reach; dx++) {
                if ((dx != 0 || dy != 0) && !insideEllipse(x + dx, y + dy, centerX, centerY, radiusX, radiusY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Recolours the region of the selected layer that matches this pixel - one colour, or empty - never across a
     * corner; and its mirror images.
     */
    void floodFill(int x, int y, Brush brush) {
        for (int[] seed : mirrors(x, y, brush)) {
            floodFrom(seed[0], seed[1], brush.color());
        }
    }

    private void floodFrom(int x, int y, int color) {
        if (!contains(x, y)) {
            return;
        }
        int[] pixels = activeLayerPixels();
        int target = pixels[y * WIDTH + x];
        if (target == color) {
            return;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(y * WIDTH + x);
        pixels[y * WIDTH + x] = color;
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int px = index % WIDTH, py = index / WIDTH;
            int[][] neighbours = {{px - 1, py}, {px + 1, py}, {px, py - 1}, {px, py + 1}};
            for (int[] next : neighbours) {
                if (contains(next[0], next[1]) && pixels[next[1] * WIDTH + next[0]] == target) {
                    pixels[next[1] * WIDTH + next[0]] = color;
                    queue.add(next[1] * WIDTH + next[0]);
                }
            }
        }
    }

    void applyPattern(FlagPattern pattern, int primary, int secondary) {
        int[] pixels = activeLayerPixels();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = (pattern.primaryAt(x, y) ? primary : secondary) | 0xFF000000;
            }
        }
    }

    /** Returns visible colors in descending usage order. */
    List<Integer> commonColors(int max) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int pixel : copyPixels()) {
            if (pixel != CLEAR) counts.merge(pixel, 1, Integer::sum);
        }
        List<Integer> colors = new ArrayList<>(counts.keySet());
        colors.sort((a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        return colors.subList(0, Math.min(max, colors.size()));
    }

    /** Returns a line endpoint snapped to horizontal, vertical, or diagonal. */
    static int[] snapLine(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0, dy = y1 - y0;
        if (Math.abs(dy) * 2 < Math.abs(dx)) {
            return new int[]{x1, y0};
        }
        if (Math.abs(dx) * 2 < Math.abs(dy)) {
            return new int[]{x0, y1};
        }
        return diagonal(x0, y0, dx, dy);
    }

    /** Returns a shape endpoint constrained to equal width and height. */
    static int[] square(int x0, int y0, int x1, int y1) {
        return diagonal(x0, y0, x1 - x0, y1 - y0);
    }

    private static int[] diagonal(int x0, int y0, int dx, int dy) {
        int signX = dx < 0 ? -1 : 1, signY = dy < 0 ? -1 : 1;
        int length = Math.max(Math.abs(dx), Math.abs(dy));
        int roomX = signX > 0 ? WIDTH - 1 - x0 : x0, roomY = signY > 0 ? HEIGHT - 1 - y0 : y0;
        length = Math.min(length, Math.min(roomX, roomY));
        return new int[]{x0 + signX * length, y0 + signY * length};
    }

    private void stamp(int x, int y, Brush brush) {
        int from = -(brush.size() - 1) / 2, to = brush.size() / 2;
        for (int dy = from; dy <= to; dy++) {
            for (int dx = from; dx <= to; dx++) {
                plot(x + dx, y + dy, brush);
            }
        }
    }

    private void plot(int x, int y, Brush brush) {
        int[] pixels = activeLayerPixels();
        for (int[] point : mirrors(x, y, brush)) {
            if (contains(point[0], point[1])) {
                pixels[point[1] * WIDTH + point[0]] = brush.color();
            }
        }
    }

    private static int[][] mirrors(int x, int y, Brush brush) {
        int mirroredX = WIDTH - 1 - x, mirroredY = HEIGHT - 1 - y;
        if (brush.mirrorX() && brush.mirrorY()) {
            return new int[][]{{x, y}, {mirroredX, y}, {x, mirroredY}, {mirroredX, mirroredY}};
        }
        if (brush.mirrorX()) {
            return new int[][]{{x, y}, {mirroredX, y}};
        }
        if (brush.mirrorY()) {
            return new int[][]{{x, y}, {x, mirroredY}};
        }
        return new int[][]{{x, y}};
    }

    private static int clampX(int x) {
        return Math.max(0, Math.min(WIDTH - 1, x));
    }

    private static int clampY(int y) {
        return Math.max(0, Math.min(HEIGHT - 1, y));
    }

    private static boolean contains(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }
}
