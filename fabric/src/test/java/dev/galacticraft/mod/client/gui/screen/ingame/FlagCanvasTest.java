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

import static org.junit.jupiter.api.Assertions.*;

class FlagCanvasTest {
    @Test
    void fastHorizontalVerticalAndReverseDragsLeaveNoGaps() {
        FlagCanvas canvas = new FlagCanvas(0xFFFFFF);
        canvas.line(0, 0, 47, 0, 0xFF0000);
        canvas.line(47, 31, 47, 0, 0x00FF00);
        canvas.line(47, 31, 0, 31, 0x0000FF);
        for (int x = 0; x < 47; x++) {
            assertEquals(0xFFFF0000, canvas.pixel(x, 0));
        }
        for (int y = 0; y < 31; y++) {
            assertEquals(0xFF00FF00, canvas.pixel(47, y));
        }
        for (int x = 0; x < 48; x++) {
            assertEquals(0xFF0000FF, canvas.pixel(x, 31));
        }
    }

    @Test
    void diagonalStrokeIsConnectedAndWorksInBothDirections() {
        FlagCanvas forward = new FlagCanvas(0xFFFFFF);
        FlagCanvas reverse = new FlagCanvas(0xFFFFFF);
        forward.line(0, 0, 47, 31, 0);
        reverse.line(47, 31, 0, 0, 0);
        assertArrayEquals(forward.copyPixels(), reverse.copyPixels());
        int previousY = 0;
        for (int x = 0; x < 48; x++) {
            int paintedY = -1;
            for (int y = 0; y < 32; y++) {
                if (forward.pixel(x, y) == 0xFF000000) {
                    paintedY = y;
                }
            }
            assertTrue(paintedY >= 0, "Every column should contain a stroke pixel");
            assertTrue(Math.abs(paintedY - previousY) <= 1, "Stroke pixels should stay connected");
            previousY = paintedY;
        }
    }

    @Test
    void outsideEndpointsDoNotWrapOrChangeCanvas() {
        FlagCanvas canvas = new FlagCanvas(0xFFFFFF);
        int[] before = canvas.copyPixels();
        canvas.line(-1, 0, 3, 0, 0);
        canvas.line(0, 0, 48, 0, 0);
        canvas.line(0, 0, 0, 32, 0);
        assertArrayEquals(before, canvas.copyPixels());
    }

    @Test
    void undoRestoresWholeStrokesAndFill() {
        FlagCanvas canvas = new FlagCanvas(0xFFFFFF);
        int[] blank = canvas.copyPixels();
        canvas.checkpoint();
        canvas.line(0, 0, 47, 31, 0);
        canvas.line(47, 31, 0, 31, 0);
        int[] stroke = canvas.copyPixels();
        canvas.fill(0xFF0000);
        assertEquals(0xFFFF0000, canvas.pixel(20, 20));
        canvas.undo();
        assertArrayEquals(stroke, canvas.copyPixels());
        canvas.undo();
        assertArrayEquals(blank, canvas.copyPixels());
        assertFalse(canvas.canUndo());
        canvas.undo();
        assertArrayEquals(blank, canvas.copyPixels());
    }

    @Test
    void editingAndExportDoNotMutateInputOrSavedSnapshots() {
        int[] original = new FlagCanvas(0xFFFFFF).copyPixels();
        FlagCanvas canvas = new FlagCanvas(original);
        canvas.line(0, 0, 0, 0, 0);
        assertEquals(0xFFFFFFFF, original[0]);
        int[] exported = canvas.copyPixels();
        exported[0] = 123;
        assertEquals(0xFF000000, canvas.pixel(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new FlagCanvas(new int[1]));
    }

    @Test
    void imageConversionPreservesRedGreenBlueAndAlpha() {
        assertEquals(0xFF0000FF, FlagCanvas.swapRedBlue(0xFFFF0000));
        assertEquals(0xFF00FF00, FlagCanvas.swapRedBlue(0xFF00FF00));
        assertEquals(0xFFFF0000, FlagCanvas.swapRedBlue(0xFF0000FF));
        assertEquals(0x78123456, FlagCanvas.swapRedBlue(FlagCanvas.swapRedBlue(0x78123456)));
    }
}
