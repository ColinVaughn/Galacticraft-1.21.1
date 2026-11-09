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

package dev.galacticraft.mod.client.spacerace;

import dev.galacticraft.mod.spacerace.TeamFlagImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A team flag is drawn on a box laid out like the preset flag textures: on the front face the top of the flag
 * runs along the right-hand edge and the pole end along the top, and the back face is the mirror of it. Each
 * design pixel becomes a crisp 3 x 3 block of texels.
 */
class TeamFlagLayoutTest {
    private static final int W = TeamFlagImage.WIDTH, H = TeamFlagImage.HEIGHT;

    /** Every pixel a different colour, so each texel says exactly which pixel it came from. */
    private static int[] numbered() {
        int[] design = new int[W * H];
        for (int i = 0; i < design.length; i++) design[i] = 0xFF000000 | (i + 1);
        return design;
    }

    private static int pixel(int x, int y) {
        return 0xFF000000 | (y * W + x + 1);
    }

    private static int texel(int[] layout, int x, int y) {
        return layout[y * TeamFlagLayout.SIZE + x];
    }

    @Test
    void theFrontReadsWithItsTopAlongTheRightAndThePoleAlongTheTop() {
        int[] layout = TeamFlagLayout.layout(numbered());

        assertEquals(pixel(0, 0), texel(layout, 98, 5), "top corner at the pole");
        assertEquals(pixel(47, 0), texel(layout, 98, 146), "top corner at the fly");
        assertEquals(pixel(0, 31), texel(layout, 5, 5), "bottom corner at the pole");
        assertEquals(pixel(47, 31), texel(layout, 5, 146), "bottom corner at the fly");
    }

    @Test
    void theBackIsTheFrontSeenFromBehind() {
        int[] layout = TeamFlagLayout.layout(numbered());

        assertEquals(pixel(0, 0), texel(layout, 105, 5), "top corner at the pole");
        assertEquals(pixel(0, 31), texel(layout, 198, 5), "bottom corner at the pole");
        assertEquals(pixel(47, 31), texel(layout, 198, 146), "bottom corner at the fly");
    }

    @Test
    void everyDesignPixelIsAThreeByThreeBlockOnEachFace() {
        int[] layout = TeamFlagLayout.layout(numbered());
        int target = pixel(10, 20);
        int front = 0, back = 0;
        for (int y = 4; y < 148; y++) {
            for (int x = 4; x < 100; x++) if (texel(layout, x, y) == target) front++;
            for (int x = 104; x < 200; x++) if (texel(layout, x, y) == target) back++;
        }
        assertEquals(9, front);
        assertEquals(9, back);
    }

    @Test
    void theEdgesTakeTheColourOfTheClothBesideThem() {
        int[] layout = TeamFlagLayout.layout(numbered());

        assertEquals(pixel(0, 0), texel(layout, 101, 5), "top edge");
        assertEquals(pixel(0, 31), texel(layout, 1, 5), "bottom edge");
        assertEquals(pixel(0, 0), texel(layout, 98, 1), "pole end");
        assertEquals(pixel(47, 0), texel(layout, 101, 1), "fly end");
    }
}
