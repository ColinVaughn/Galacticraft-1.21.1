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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Opaque ARGB pixels for the flag editor, independent of the rendering context. */
final class FlagCanvas {
    static final int WIDTH = 48;
    static final int HEIGHT = 32;
    private final int[] pixels;
    private final Deque<int[]> history = new ArrayDeque<>();

    FlagCanvas(int color) {
        this.pixels = new int[WIDTH * HEIGHT];
        Arrays.fill(this.pixels, color | 0xFF000000);
    }

    FlagCanvas(int[] pixels) {
        if (pixels.length != WIDTH * HEIGHT) {
            throw new IllegalArgumentException("Flags must be 48 x 32 pixels");
        }
        this.pixels = pixels.clone();
        for (int i = 0; i < this.pixels.length; i++) {
            this.pixels[i] |= 0xFF000000;
        }
    }

    int pixel(int x, int y) {
        return this.pixels[y * WIDTH + x];
    }

    int[] copyPixels() {
        return this.pixels.clone();
    }

    static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color & 0xFF) << 16) | ((color >>> 16) & 0xFF);
    }

    void checkpoint() {
        if (this.history.size() == 32) {
            this.history.removeFirst();
        }
        this.history.addLast(copyPixels());
    }

    boolean canUndo() {
        return !this.history.isEmpty();
    }

    void undo() {
        if (canUndo()) {
            System.arraycopy(this.history.removeLast(), 0, this.pixels, 0, this.pixels.length);
        }
    }

    void fill(int color) {
        checkpoint();
        Arrays.fill(this.pixels, color | 0xFF000000);
    }

    void line(int x0, int y0, int x1, int y1, int color) {
        if (!contains(x0, y0) || !contains(x1, y1)) {
            return;
        }
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1, stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            this.pixels[y0 * WIDTH + x0] = color | 0xFF000000;
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

    private static boolean contains(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }
}
