/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.impl.internal.client.tabs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryTabLayoutTest {
    @Test
    void keepsPreferredPositionWhenItIsFree() {
        InventoryTabLayout.Position position = InventoryTabLayout.findPosition(72, 9, 2, 320, 240, List.of());

        assertEquals(new InventoryTabLayout.Position(72, 9), position);
    }

    @Test
    void keepsTabsOnScreenWhenThePreferredRowWouldBeClipped() {
        InventoryTabLayout.Position position = InventoryTabLayout.findPosition(72, -5, 2, 320, 240, List.of());

        assertEquals(new InventoryTabLayout.Position(72, 0), position);
    }

    @Test
    void appendsToAnExistingModdedTabRow() {
        List<InventoryTabLayout.Bounds> occupied = List.of(
                new InventoryTabLayout.Bounds(72, 15, 26, 22),
                new InventoryTabLayout.Bounds(99, 15, 26, 22),
                new InventoryTabLayout.Bounds(72, 41, 176, 162)
        );

        InventoryTabLayout.Position position = InventoryTabLayout.findPosition(72, 9, 2, 320, 240, occupied);

        assertEquals(new InventoryTabLayout.Position(125, 9), position);
    }

    @Test
    void movesBeforeTheConflictingRowWhenThereIsNoRoomAfterIt() {
        List<InventoryTabLayout.Bounds> occupied = List.of(new InventoryTabLayout.Bounds(120, 15, 60, 22));

        InventoryTabLayout.Position position = InventoryTabLayout.findPosition(120, 9, 2, 200, 240, occupied);

        assertEquals(new InventoryTabLayout.Position(62, 9), position);
    }

    @Test
    void usesAnotherRowWhenTheWholePreferredRowIsOccupied() {
        List<InventoryTabLayout.Bounds> occupied = List.of(
                new InventoryTabLayout.Bounds(0, 40, 176, 22),
                new InventoryTabLayout.Bounds(0, 72, 176, 128)
        );

        InventoryTabLayout.Position position = InventoryTabLayout.findPosition(0, 40, 2, 176, 200, occupied);

        assertEquals(new InventoryTabLayout.Position(0, 8), position);
    }
}
