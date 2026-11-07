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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The flag editor's drawing tools, on the canvas alone. */
class FlagToolsTest {
    private static final int W = FlagCanvas.WIDTH, H = FlagCanvas.HEIGHT;
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, RED = 0xFFFF0000, BLUE = 0xFF0000FF;

    private static FlagCanvas white() {
        return new FlagCanvas(WHITE);
    }

    private static int count(FlagCanvas canvas, int color) {
        int count = 0;
        for (int pixel : canvas.copyPixels()) if (pixel == color) count++;
        return count;
    }

    @Test
    void largerBrushesPaintSquaresAroundTheCursorAndStopAtTheEdge() {
        FlagCanvas canvas = white();
        canvas.line(10, 10, 10, 10, new FlagCanvas.Brush(BLACK, 3, false, false));
        assertEquals(9, count(canvas, BLACK));
        assertEquals(BLACK, canvas.pixel(9, 9));
        assertEquals(BLACK, canvas.pixel(11, 11));

        FlagCanvas two = white();
        two.line(5, 5, 5, 5, new FlagCanvas.Brush(BLACK, 2, false, false));
        assertEquals(4, count(two, BLACK));
        assertEquals(BLACK, two.pixel(6, 6));

        FlagCanvas corner = white();
        corner.line(0, 0, 0, 0, new FlagCanvas.Brush(BLACK, 3, false, false));
        assertEquals(4, count(corner, BLACK), "the brush is clipped at the edge, not wrapped round it");
        assertEquals(WHITE, corner.pixel(W - 1, H - 1));
    }

    @Test
    void mirroringPaintsTheReflectionsToo() {
        FlagCanvas canvas = white();
        canvas.line(2, 3, 2, 3, new FlagCanvas.Brush(BLACK, 1, true, true));
        assertEquals(BLACK, canvas.pixel(2, 3));
        assertEquals(BLACK, canvas.pixel(W - 3, 3), "left and right");
        assertEquals(BLACK, canvas.pixel(2, H - 4), "top and bottom");
        assertEquals(BLACK, canvas.pixel(W - 3, H - 4), "both");
        assertEquals(4, count(canvas, BLACK));
    }

    @Test
    void rectanglesFillOrOutlineTheBoxFromEitherCorner() {
        FlagCanvas filled = white();
        filled.rectangle(20, 12, 5, 4, new FlagCanvas.Brush(BLACK, 1, false, false), true);
        assertEquals(16 * 9, count(filled, BLACK));

        FlagCanvas outline = white();
        outline.rectangle(5, 4, 20, 12, new FlagCanvas.Brush(BLACK, 1, false, false), false);
        assertEquals(16 * 9 - 14 * 7, count(outline, BLACK));
        assertEquals(WHITE, outline.pixel(10, 8), "an outline leaves the inside alone");

        FlagCanvas thick = white();
        thick.rectangle(5, 4, 20, 12, new FlagCanvas.Brush(BLACK, 2, false, false), false);
        assertEquals(16 * 9 - 12 * 5, count(thick, BLACK));
    }

    @Test
    void ellipsesFillTheirBoxSymmetricallyAndOutlineTheirEdge() {
        FlagCanvas filled = white();
        filled.ellipse(4, 2, 43, 29, new FlagCanvas.Brush(BLACK, 1, false, false), true);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(filled.pixel(x, y), filled.pixel(47 - x, y), "left-right symmetric at " + x + "," + y);
                assertEquals(filled.pixel(x, y), filled.pixel(x, 31 - y), "top-bottom symmetric at " + x + "," + y);
                if (x < 4 || x > 43 || y < 2 || y > 29) assertEquals(WHITE, filled.pixel(x, y), "outside its box");
            }
        }
        assertEquals(BLACK, filled.pixel(4, 15), "it reaches the left of its box");
        assertEquals(BLACK, filled.pixel(23, 2), "it reaches the top of its box");
        assertEquals(WHITE, filled.pixel(4, 2), "it rounds off the corner");

        FlagCanvas outline = white();
        outline.ellipse(4, 2, 43, 29, new FlagCanvas.Brush(BLACK, 1, false, false), false);
        assertEquals(WHITE, outline.pixel(23, 15), "an outline leaves the middle alone");
        assertEquals(BLACK, outline.pixel(4, 15));
        for (int i = 0; i < W * H; i++) {
            int x = i % W, y = i / W;
            if (outline.pixel(x, y) == BLACK) assertEquals(BLACK, filled.pixel(x, y), "the outline stays inside the shape");
        }
    }

    @Test
    void aTinyEllipseIsStillAMark() {
        FlagCanvas canvas = white();
        canvas.ellipse(7, 7, 7, 7, new FlagCanvas.Brush(BLACK, 1, false, false), false);
        assertEquals(1, count(canvas, BLACK));
    }

    @Test
    void floodFillTakesOnlyTheTouchingRegionOfOneColour() {
        FlagCanvas canvas = white();
        FlagCanvas.Brush wall = new FlagCanvas.Brush(BLACK, 1, false, false);
        canvas.line(24, 0, 24, 31, wall);
        canvas.floodFill(3, 3, new FlagCanvas.Brush(RED, 1, false, false));
        assertEquals(RED, canvas.pixel(0, 0));
        assertEquals(RED, canvas.pixel(23, 31));
        assertEquals(BLACK, canvas.pixel(24, 10), "the wall is not a matching colour");
        assertEquals(WHITE, canvas.pixel(25, 10), "it does not cross the wall");

        FlagCanvas diagonal = white();
        diagonal.line(0, 0, 31, 31, wall);
        diagonal.floodFill(10, 2, new FlagCanvas.Brush(RED, 1, false, false));
        assertEquals(WHITE, diagonal.pixel(2, 10), "it does not leak through a diagonal line's corners");
    }

    @Test
    void mirroredFillAlsoFillsTheReflectedRegion() {
        FlagCanvas canvas = white();
        FlagCanvas.Brush wall = new FlagCanvas.Brush(BLACK, 1, false, false);
        canvas.line(10, 0, 10, 31, wall);
        canvas.line(37, 0, 37, 31, wall);
        canvas.floodFill(2, 2, new FlagCanvas.Brush(RED, 1, true, false));
        assertEquals(RED, canvas.pixel(0, 0));
        assertEquals(RED, canvas.pixel(47, 0));
        assertEquals(WHITE, canvas.pixel(20, 0));
    }

    @Test
    void redoBringsBackWhatUndoTookAndAnyNewEditForgetsIt() {
        FlagCanvas canvas = white();
        canvas.checkpoint();
        canvas.line(0, 0, 10, 0, BLACK);
        int[] drawn = canvas.copyPixels();
        canvas.undo();
        assertTrue(canvas.canRedo());
        canvas.redo();
        assertArrayEquals(drawn, canvas.copyPixels());
        assertTrue(canvas.canUndo());

        canvas.undo();
        canvas.checkpoint();
        canvas.line(0, 5, 10, 5, BLUE);
        assertFalse(canvas.canRedo(), "a new edit starts a new history");
    }

    @Test
    void aCancelledShapeLeavesNoTrace() {
        FlagCanvas canvas = white();
        int[] before = canvas.copyPixels();
        canvas.checkpoint();
        int[] base = canvas.activePixels();
        canvas.rectangle(1, 1, 30, 20, new FlagCanvas.Brush(BLACK, 1, false, false), true);
        canvas.restoreActive(base);
        canvas.discardCheckpoint();
        assertArrayEquals(before, canvas.copyPixels());
        assertFalse(canvas.canUndo(), "cancelling does not leave an empty undo step");
    }

    @Test
    void shiftSnapsLinesToStraightOrDiagonal() {
        assertArrayEquals(new int[]{20, 10}, FlagCanvas.snapLine(10, 10, 20, 12));
        assertArrayEquals(new int[]{10, 20}, FlagCanvas.snapLine(10, 10, 11, 20));
        assertArrayEquals(new int[]{18, 18}, FlagCanvas.snapLine(10, 10, 18, 16));
        assertArrayEquals(new int[]{2, 18}, FlagCanvas.snapLine(10, 10, 3, 18));
        int[] clipped = FlagCanvas.snapLine(40, 25, 47, 31);
        assertTrue(clipped[0] < W && clipped[1] < H, "a snapped line stays on the canvas");
        assertEquals(clipped[0] - 40, clipped[1] - 25, "and stays diagonal");
    }

    @Test
    void shiftMakesShapesSquare() {
        assertArrayEquals(new int[]{15, 15}, FlagCanvas.square(5, 5, 15, 9));
        assertArrayEquals(new int[]{-5 + 10, 15}, FlagCanvas.square(10, 10, 7, 15));
        int[] clipped = FlagCanvas.square(40, 25, 47, 31);
        assertTrue(clipped[0] < W && clipped[1] < H, "a square stays on the canvas");
        assertEquals(clipped[0] - 40, clipped[1] - 25);
    }

    @Test
    void theFlagsOwnColoursComeFirstInTheRecentRow() {
        FlagCanvas canvas = white();
        canvas.rectangle(0, 0, 47, 9, new FlagCanvas.Brush(RED, 1, false, false), true);
        canvas.rectangle(0, 10, 3, 10, new FlagCanvas.Brush(BLUE, 1, false, false), true);
        assertEquals(List.of(WHITE, RED, BLUE), canvas.commonColors(8));
        assertEquals(List.of(WHITE), canvas.commonColors(1));
    }

    @Test
    void everyPatternUsesBothColoursWhereItShould() {
        for (FlagPattern pattern : FlagPattern.values()) {
            FlagCanvas canvas = white();
            canvas.applyPattern(pattern, RED, BLUE);
            int red = count(canvas, RED), blue = count(canvas, BLUE);
            assertEquals(W * H, red + blue, pattern + " paints every pixel");
            if (pattern == FlagPattern.SOLID) {
                assertEquals(W * H, red);
            } else {
                assertTrue(red > 0 && blue > 0, pattern + " uses both colours");
            }
        }
    }

    @Test
    void patternsLandWhereFlagsHaveThem() {
        assertTrue(FlagPattern.HORIZONTAL_HALVES.primaryAt(10, 15));
        assertFalse(FlagPattern.HORIZONTAL_HALVES.primaryAt(10, 16));
        assertTrue(FlagPattern.VERTICAL_HALVES.primaryAt(23, 0));
        assertFalse(FlagPattern.VERTICAL_HALVES.primaryAt(24, 0));

        // Bands of 11, 10 and 11 rows, and 16 columns each.
        assertFalse(FlagPattern.HORIZONTAL_BANDS.primaryAt(0, 10));
        assertTrue(FlagPattern.HORIZONTAL_BANDS.primaryAt(0, 11));
        assertTrue(FlagPattern.HORIZONTAL_BANDS.primaryAt(0, 20));
        assertFalse(FlagPattern.HORIZONTAL_BANDS.primaryAt(0, 21));
        assertFalse(FlagPattern.VERTICAL_BANDS.primaryAt(15, 0));
        assertTrue(FlagPattern.VERTICAL_BANDS.primaryAt(16, 0));
        assertTrue(FlagPattern.VERTICAL_BANDS.primaryAt(31, 0));
        assertFalse(FlagPattern.VERTICAL_BANDS.primaryAt(32, 0));

        assertTrue(FlagPattern.CENTERED_CROSS.primaryAt(24, 0));
        assertTrue(FlagPattern.CENTERED_CROSS.primaryAt(0, 16));
        assertFalse(FlagPattern.CENTERED_CROSS.primaryAt(5, 5));
        assertTrue(FlagPattern.NORDIC_CROSS.primaryAt(16, 0), "the upright sits toward the pole");
        assertFalse(FlagPattern.NORDIC_CROSS.primaryAt(24, 0));
        assertTrue(FlagPattern.NORDIC_CROSS.primaryAt(40, 16));

        assertTrue(FlagPattern.SALTIRE.primaryAt(0, 0));
        assertTrue(FlagPattern.SALTIRE.primaryAt(47, 0));
        assertTrue(FlagPattern.SALTIRE.primaryAt(24, 16));
        assertFalse(FlagPattern.SALTIRE.primaryAt(24, 2));

        assertTrue(FlagPattern.CANTON.primaryAt(23, 15));
        assertFalse(FlagPattern.CANTON.primaryAt(24, 15));
        assertTrue(FlagPattern.DIAGONAL.primaryAt(0, 0));
        assertFalse(FlagPattern.DIAGONAL.primaryAt(47, 31));
        assertTrue(FlagPattern.HOIST_TRIANGLE.primaryAt(1, 16));
        assertFalse(FlagPattern.HOIST_TRIANGLE.primaryAt(30, 16));
        assertTrue(FlagPattern.BORDER.primaryAt(0, 10));
        assertFalse(FlagPattern.BORDER.primaryAt(24, 16));
        assertTrue(FlagPattern.DISC.primaryAt(24, 16));
        assertFalse(FlagPattern.DISC.primaryAt(2, 2));
    }

    @Test
    void symmetricPatternsAreSymmetric() {
        for (FlagPattern pattern : List.of(FlagPattern.HORIZONTAL_BANDS, FlagPattern.VERTICAL_BANDS, FlagPattern.CENTERED_CROSS,
                FlagPattern.SALTIRE, FlagPattern.BORDER, FlagPattern.DISC)) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    assertEquals(pattern.primaryAt(x, y), pattern.primaryAt(W - 1 - x, y), pattern + " left-right at " + x + "," + y);
                    assertEquals(pattern.primaryAt(x, y), pattern.primaryAt(x, H - 1 - y), pattern + " top-bottom at " + x + "," + y);
                }
            }
        }
    }
}
