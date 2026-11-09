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

package dev.galacticraft.mod.client.render.dimension;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SpaceflightViewAreaTest {
    @Test void wrappedColumnsMatchVanillaAtPositiveAndNegativeBoundaries() {
        for (int size : new int[]{5, 25, 65}) {
            for (double camera : new double[]{-1024.1, -16.01, -16, -.01, 0, .01, 15.99, 16, 1024.1}) {
                var origins = new HashSet<Integer>();
                int base = (int) Math.ceil(camera) - 8 - size * 8;
                for (int column = 0; column < size; column++) {
                    int origin = SpaceflightViewArea.sectionOrigin(camera, column, size);
                    assertEquals(0, Math.floorMod(origin, 16));
                    assertEquals(column, Math.floorMod(origin / 16, size));
                    assertTrue(origin >= base && origin < base + size * 16);
                    assertTrue(origins.add(origin), "Each column must own a distinct section");
                }
            }
        }
    }

    @Test void crossingOneSectionMovesOnlyOneColumn() {
        int changed = 0;
        for (int column = 0; column < 25; column++)
            if (SpaceflightViewArea.sectionOrigin(0, column, 25) != SpaceflightViewArea.sectionOrigin(16, column, 25)) changed++;
        assertEquals(1, changed);
    }
}