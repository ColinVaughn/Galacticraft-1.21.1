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

package dev.galacticraft.mod.client.render.block.entity.lcd;

import java.util.ArrayList;
import java.util.List;

/**
 * Monochrome pixel art for a block-face display, kept as non-overlapping rectangles so translucent
 * colors blend evenly. Pixel coordinates run across the face from its top-left corner.
 */
public record LcdImage(int pixelsPerBlock, List<Rect> rects) {
    public record Rect(int x1, int y1, int x2, int y2) {
    }

    public LcdImage {
        rects = List.copyOf(rects);
    }

    public static LcdImage rect(int pixelsPerBlock, int x, int y, int width, int height) {
        return new LcdImage(pixelsPerBlock, width <= 0 || height <= 0 ? List.of() : List.of(new Rect(x, y, x + width, y + height)));
    }

    /** Reads rows of {@code #} (lit) and any other character (unlit), placed with its top-left pixel at {@code left, top}. */
    public static LcdImage parse(int pixelsPerBlock, int left, int top, String... rows) {
        List<Rect> rects = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int start = -1;
            for (int column = 0; column <= line.length(); column++) {
                boolean lit = column < line.length() && line.charAt(column) == '#';
                if (lit && start < 0) {
                    start = column;
                } else if (!lit && start >= 0) {
                    extendOrAdd(rects, new Rect(left + start, top + row, left + column, top + row + 1));
                    start = -1;
                }
            }
        }
        return new LcdImage(pixelsPerBlock, rects);
    }

    private static void extendOrAdd(List<Rect> rects, Rect run) {
        for (int i = 0; i < rects.size(); i++) {
            Rect above = rects.get(i);
            if (above.x1() == run.x1() && above.x2() == run.x2() && above.y2() == run.y1()) {
                rects.set(i, new Rect(run.x1(), above.y1(), run.x2(), run.y2()));
                return;
            }
        }
        rects.add(run);
    }
}
