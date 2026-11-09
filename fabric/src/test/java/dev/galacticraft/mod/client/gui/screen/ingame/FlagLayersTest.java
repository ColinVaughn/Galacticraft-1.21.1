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

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Layers in the flag editor: each tool draws on one, and the flag is what shows through them all. */
class FlagLayersTest {
    private static final int WHITE = 0xFFFFFFFF, RED = 0xFFFF0000, BLUE = 0xFF0000FF, GREEN = 0xFF00FF00;

    private static FlagCanvas.Brush brush(int color) {
        return new FlagCanvas.Brush(color, 1, false, false);
    }

    @Test
    void aNewLayerIsEmptyAboveTheOneSelectedAndBecomesSelected() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        assertTrue(canvas.addLayer("Stripe"));
        assertEquals(2, canvas.layerCount());
        assertEquals(1, canvas.activeLayer());
        assertEquals("Stripe", canvas.layerName(1));
        assertEquals(FlagCanvas.CLEAR, canvas.layerPixel(1, 10, 10));
        assertEquals(BLUE, canvas.pixel(10, 10), "an empty layer lets the one below show through");
    }

    @Test
    void toolsDrawOnTheSelectedLayerOnly() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Stripe");
        canvas.rectangle(0, 0, 47, 3, brush(RED), true);
        assertEquals(RED, canvas.pixel(5, 2));
        assertEquals(BLUE, canvas.layerPixel(0, 5, 2), "the background underneath is untouched");
        assertEquals(BLUE, canvas.pixel(5, 10));
    }

    @Test
    void theEraserClearsToShowTheLayerBelow() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Cover");
        canvas.applyPattern(FlagPattern.SOLID, RED, RED);
        canvas.line(3, 3, 3, 3, brush(FlagCanvas.CLEAR));
        assertEquals(BLUE, canvas.pixel(3, 3));
        assertEquals(RED, canvas.pixel(4, 3));
    }

    @Test
    void emptyClothFliesWhite() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.line(0, 0, 0, 0, brush(FlagCanvas.CLEAR));
        assertEquals(FlagCanvas.CLEAR, canvas.pixel(0, 0), "the editor shows it as empty");
        assertEquals(WHITE, canvas.exportPixels()[0], "the flag shows it as white cloth");
    }

    @Test
    void aHiddenLayerIsLeftOutOfTheFlag() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Stripe");
        canvas.applyPattern(FlagPattern.SOLID, RED, RED);
        canvas.toggleVisible(1);
        assertFalse(canvas.layerVisible(1));
        assertEquals(BLUE, canvas.pixel(0, 0));
        assertEquals(BLUE, canvas.exportPixels()[0]);
    }

    @Test
    void layersMoveAndMergeInOrder() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Red");
        canvas.line(0, 0, 0, 0, brush(RED));
        canvas.addLayer("Green");
        canvas.line(0, 0, 1, 0, brush(GREEN));
        assertEquals(GREEN, canvas.pixel(0, 0));

        canvas.moveLayer(-1);
        assertEquals(1, canvas.activeLayer(), "the moved layer stays selected");
        assertEquals("Green", canvas.layerName(1));
        assertEquals(RED, canvas.pixel(0, 0), "red is on top now");

        canvas.selectLayer(2);
        canvas.mergeDown();
        assertEquals(2, canvas.layerCount());
        assertEquals("Green", canvas.layerName(1), "merging keeps the lower layer");
        assertEquals(RED, canvas.layerPixel(1, 0, 0));
        assertEquals(GREEN, canvas.layerPixel(1, 1, 0), "and what the upper layer left clear");
        assertEquals(1, canvas.activeLayer());
    }

    @Test
    void duplicatingCopiesTheLayerAboveItselfAndDeletingTakesItAway() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Star");
        canvas.line(5, 5, 5, 5, brush(RED));
        canvas.duplicateLayer("Star copy");
        assertEquals(3, canvas.layerCount());
        assertEquals(2, canvas.activeLayer());
        assertEquals(RED, canvas.layerPixel(2, 5, 5));

        canvas.deleteLayer();
        assertEquals(2, canvas.layerCount());
        assertEquals(1, canvas.activeLayer(), "the layer below is selected next");
        canvas.selectLayer(0);
        canvas.deleteLayer();
        canvas.deleteLayer();
        assertEquals(1, canvas.layerCount(), "the last layer cannot be deleted");
    }

    @Test
    void thereIsAnUpperLimitOfLayers() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        for (int i = 1; i < FlagCanvas.MAX_LAYERS; i++) assertTrue(canvas.addLayer("Layer " + i));
        assertFalse(canvas.addLayer("One too many"));
        assertEquals(FlagCanvas.MAX_LAYERS, canvas.layerCount());
    }

    @Test
    void undoTakesBackLayerChangesToo() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Stripe");
        canvas.checkpoint();
        canvas.applyPattern(FlagPattern.SOLID, RED, RED);
        canvas.toggleVisible(1);
        canvas.undo();
        assertTrue(canvas.layerVisible(1), "undo shows the layer again");
        canvas.undo();
        assertEquals(BLUE, canvas.pixel(0, 0), "then takes the paint off it");
        canvas.undo();
        assertEquals(1, canvas.layerCount(), "then takes the layer away");
        canvas.redo();
        assertEquals(2, canvas.layerCount());
        assertEquals(1, canvas.activeLayer());
    }

    @Test
    void fillingAnEmptyLayerFillsTheEmptyRegion() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Fill");
        canvas.line(10, 0, 10, 31, brush(GREEN));
        canvas.floodFill(2, 2, brush(RED));
        assertEquals(RED, canvas.pixel(9, 31));
        assertEquals(BLUE, canvas.pixel(11, 0), "beyond the wall the empty layer still shows the background");
    }

    @Test
    void aShapePreviewRedrawsTheSelectedLayerOnly() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Shape");
        int[] before = canvas.activePixels();
        canvas.rectangle(0, 0, 20, 20, brush(RED), true);
        canvas.restoreActive(before);
        assertEquals(BLUE, canvas.pixel(5, 5));
        assertEquals(2, canvas.layerCount());
    }

    @Test
    void layersSurviveSavingAndLoading() {
        FlagCanvas canvas = new FlagCanvas(BLUE);
        canvas.addLayer("Stripe");
        canvas.rectangle(0, 0, 47, 3, brush(RED), true);
        canvas.addLayer("Hidden");
        canvas.line(0, 31, 47, 31, brush(GREEN));
        canvas.toggleVisible(2);
        canvas.selectLayer(1);

        CompoundTag saved = canvas.save();
        FlagCanvas loaded = FlagCanvas.load(saved);
        assertNotNull(loaded);
        assertEquals(3, loaded.layerCount());
        assertEquals(1, loaded.activeLayer());
        assertEquals("Hidden", loaded.layerName(2));
        assertFalse(loaded.layerVisible(2));
        assertArrayEquals(canvas.exportPixels(), loaded.exportPixels());
        assertFalse(loaded.canUndo(), "history is not saved");
    }

    @Test
    void aDamagedSaveIsIgnored() {
        CompoundTag damaged = new FlagCanvas(BLUE).save();
        damaged.getList("layers", 10).getCompound(0).putIntArray("pixels", new int[3]);
        assertNull(FlagCanvas.load(damaged));
        assertNull(FlagCanvas.load(new CompoundTag()));
    }
}
