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

/**
 * Maps a 48 x 32 flag design to a 24 x 36 x 1 cloth box on a 64 x 64 model sheet. Each design pixel
 * covers 3 x 3 texels. The front maps the flag top to the right edge and pole end to the top edge;
 * the back mirrors the front.
 */
public final class TeamFlagLayout {
    /** The texture's size in texels. */
    public static final int SIZE = 256;
    /** Texels to a unit of the 64 x 64 model sheet. */
    private static final int SCALE = 4;
    /** Texels to a design pixel. */
    private static final int PIXEL = 3;
    /** The cloth box, in model units: as tall on its side as the flag is high, as long as it flies. */
    public static final int BOX_WIDTH = 24, BOX_HEIGHT = 36, BOX_DEPTH = 1;

    private static final int FACE_TOP = BOX_DEPTH * SCALE;
    private static final int FACE_WIDTH = BOX_WIDTH * SCALE, FACE_HEIGHT = BOX_HEIGHT * SCALE;
    private static final int FRONT_LEFT = BOX_DEPTH * SCALE;
    private static final int TOP_EDGE_LEFT = FRONT_LEFT + FACE_WIDTH;
    private static final int BACK_LEFT = TOP_EDGE_LEFT + BOX_DEPTH * SCALE;

    private TeamFlagLayout() {
    }

    /** Returns the cloth texture as a {@link #SIZE}-square ARGB image. */
    public static int[] layout(int[] design) {
        if (design.length != TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT) {
            throw new IllegalArgumentException("A team flag has " + TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT + " pixels, not " + design.length);
        }
        int[] texture = new int[SIZE * SIZE];
        int lastRow = TeamFlagImage.HEIGHT - 1, lastColumn = TeamFlagImage.WIDTH - 1;
        for (int along = 0; along < FACE_HEIGHT; along++) {
            int x = along / PIXEL;
            int row = FACE_TOP + along;
            for (int across = 0; across < FACE_WIDTH; across++) {
                int fromTop = (FACE_WIDTH - 1 - across) / PIXEL;
                texture[row * SIZE + FRONT_LEFT + across] = pixel(design, x, fromTop);
                texture[row * SIZE + BACK_LEFT + across] = pixel(design, x, across / PIXEL);
            }
            for (int edge = 0; edge < BOX_DEPTH * SCALE; edge++) {
                texture[row * SIZE + edge] = pixel(design, x, lastRow);
                texture[row * SIZE + TOP_EDGE_LEFT + edge] = pixel(design, x, 0);
            }
        }
        for (int end = 0; end < FACE_TOP; end++) {
            for (int across = 0; across < FACE_WIDTH; across++) {
                texture[end * SIZE + FRONT_LEFT + across] = pixel(design, 0, (FACE_WIDTH - 1 - across) / PIXEL);
                texture[end * SIZE + TOP_EDGE_LEFT + across] = pixel(design, lastColumn, across / PIXEL);
            }
        }
        return texture;
    }

    private static int pixel(int[] design, int x, int y) {
        return design[y * TeamFlagImage.WIDTH + x];
    }
}
