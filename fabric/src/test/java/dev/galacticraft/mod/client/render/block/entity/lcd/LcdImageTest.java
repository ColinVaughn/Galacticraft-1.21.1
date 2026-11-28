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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LcdImageTest {
    private static final String[] RING = {
            "..####..",
            "..####..",
            "##....##",
            "##....##",
            "##....##",
            "##....##",
            "..####..",
            "..####..",
    };

    @Test
    void rectanglesCoverExactlyTheLitPixelsWithoutOverlapping() {
        LcdImage image = LcdImage.parse(32, 12, 12, RING);
        Set<List<Integer>> covered = new HashSet<>();
        for (LcdImage.Rect rect : image.rects()) {
            for (int x = rect.x1(); x < rect.x2(); x++) {
                for (int y = rect.y1(); y < rect.y2(); y++) {
                    assertTrue(covered.add(List.of(x, y)), "pixel drawn twice: " + x + ", " + y);
                }
            }
        }
        Set<List<Integer>> lit = new HashSet<>();
        for (int row = 0; row < RING.length; row++) {
            for (int column = 0; column < RING[row].length(); column++) {
                if (RING[row].charAt(column) == '#') lit.add(List.of(12 + column, 12 + row));
            }
        }
        assertEquals(lit, covered);
    }

    @Test
    void equalRunsInConsecutiveRowsMergeIntoOneRectangle() {
        assertEquals(4, LcdImage.parse(32, 0, 0, RING).rects().size());
    }

    @Test
    void anEmptyGaugeDrawsNothing() {
        assertTrue(LcdImage.rect(16, 2, 1, 0, 1).rects().isEmpty());
    }
}
