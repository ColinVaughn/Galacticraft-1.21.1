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

/**
 * A team flag as it is sent and stored: 48 x 32 opaque pixels, row by row from the top left, three bytes each in
 * red, green, blue order. The space race menu draws in ARGB, and textures hold their pixels blue first, so both
 * conversions live here.
 */
public final class TeamFlagImage {
    public static final int WIDTH = 48;
    public static final int HEIGHT = 32;
    public static final int BYTES = WIDTH * HEIGHT * 3;

    private TeamFlagImage() {
    }

    /** Returns stored RGB bytes from ARGB pixels, discarding transparency. */
    public static byte[] toRgb(int[] argb) {
        if (argb.length != WIDTH * HEIGHT) throw new IllegalArgumentException("A team flag has " + WIDTH * HEIGHT + " pixels, not " + argb.length);
        byte[] rgb = new byte[BYTES];
        for (int i = 0, b = 0; i < argb.length; i++) {
            int color = argb[i];
            rgb[b++] = (byte) (color >> 16);
            rgb[b++] = (byte) (color >> 8);
            rgb[b++] = (byte) color;
        }
        return rgb;
    }

    /** Returns opaque ARGB pixels from stored RGB bytes. */
    public static int[] toArgb(byte[] rgb) {
        if (rgb.length != BYTES) throw new IllegalArgumentException("A team flag is " + BYTES + " bytes, not " + rgb.length);
        int[] argb = new int[WIDTH * HEIGHT];
        for (int i = 0, b = 0; i < argb.length; i++) {
            argb[i] = 0xFF000000 | (rgb[b++] & 0xFF) << 16 | (rgb[b++] & 0xFF) << 8 | rgb[b++] & 0xFF;
        }
        return argb;
    }

    /** Swaps red and blue between ARGB and texture color formats. */
    public static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | (color & 0xFF) << 16 | (color >>> 16) & 0xFF;
    }
}
