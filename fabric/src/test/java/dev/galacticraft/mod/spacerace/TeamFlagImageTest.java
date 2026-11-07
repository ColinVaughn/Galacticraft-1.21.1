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

package dev.galacticraft.mod.spacerace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The wire and storage form of a team flag: 48 x 32 pixels, three bytes each, red first. */
class TeamFlagImageTest {
    @Test
    void pixelsAreSentRedGreenBlueRowByRow() {
        int[] argb = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
        argb[0] = 0xFF112233;
        argb[TeamFlagImage.WIDTH + 1] = 0xFFAABBCC;

        byte[] rgb = TeamFlagImage.toRgb(argb);

        assertEquals(TeamFlagImage.BYTES, rgb.length);
        assertEquals(0x11, rgb[0] & 0xFF);
        assertEquals(0x22, rgb[1] & 0xFF);
        assertEquals(0x33, rgb[2] & 0xFF);
        int second = (TeamFlagImage.WIDTH + 1) * 3;
        assertEquals(0xAA, rgb[second] & 0xFF);
        assertEquals(0xBB, rgb[second + 1] & 0xFF);
        assertEquals(0xCC, rgb[second + 2] & 0xFF);
    }

    @Test
    void aFlagSurvivesTheRoundTripOpaque() {
        int[] argb = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFF000000 | ((i * 7919) & 0xFFFFFF);

        assertArrayEquals(argb, TeamFlagImage.toArgb(TeamFlagImage.toRgb(argb)));
    }

    @Test
    void transparencyIsDroppedBecauseAFlagIsCloth() {
        int[] argb = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
        argb[5] = 0x00123456;

        assertEquals(0xFF123456, TeamFlagImage.toArgb(TeamFlagImage.toRgb(argb))[5]);
    }

    @Test
    void aTextureColourIsSwappedToAndFromItsBlueFirstForm() {
        assertEquals(0xFF332211, TeamFlagImage.swapRedBlue(0xFF112233));
        assertEquals(0xFF112233, TeamFlagImage.swapRedBlue(TeamFlagImage.swapRedBlue(0xFF112233)));
    }

    @Test
    void anImageOfTheWrongSizeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TeamFlagImage.toArgb(new byte[TeamFlagImage.BYTES - 3]));
        assertThrows(IllegalArgumentException.class, () -> TeamFlagImage.toRgb(new int[10]));
    }
}
