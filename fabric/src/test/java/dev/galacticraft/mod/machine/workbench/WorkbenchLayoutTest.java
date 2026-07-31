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

package dev.galacticraft.mod.machine.workbench;

import dev.galacticraft.mod.Constant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The workbench pages reuse legacy Galacticraft's GUI textures, so their slot geometry has to line
 * up with the slot wells painted into those textures. A slot that overlaps a neighbour or spills off
 * the panel is invisible in-game until someone opens that exact page, so it is pinned here instead.
 */
class WorkbenchLayoutTest {
    private static final int SLOT = 18;

    @Test
    void legacySlotCountsArePreserved() {
        // 12 body plates + 4 wheels, as in legacy's ContainerBuggyBench.
        assertEquals(16, WorkbenchLayout.BUGGY.ingredientSlots().size());
        // Nose cone + wafer + 6 body plates + 4 fins + engine, as in ContainerSchematicCargoRocket.
        assertEquals(13, WorkbenchLayout.CARGO_ROCKET.ingredientSlots().size());
        // 4 + 5 + 3 hull slots and 2 laser slots, as in ContainerSchematicAstroMiner.
        assertEquals(14, WorkbenchLayout.ASTRO_MINER.ingredientSlots().size());
    }

    /**
     * Legacy carried chests as recipe ingredients; this port treats them as a workbench upgrade, so
     * the vehicle pages keep legacy's three addon wells and the astro miner keeps none.
     */
    @Test
    void vehiclePagesKeepLegacysThreeUpgradeWells() {
        assertEquals(3, WorkbenchLayout.BUGGY.chestSlots().size());
        assertEquals(3, WorkbenchLayout.CARGO_ROCKET.chestSlots().size());
        assertEquals(0, WorkbenchLayout.ASTRO_MINER.chestSlots().size());
    }

    @Test
    void noSlotOverlapsAnother() {
        for (WorkbenchLayout layout : WorkbenchLayout.values()) {
            List<WorkbenchLayout.Position> all = allSlots(layout);
            for (int a = 0; a < all.size(); a++) {
                for (int b = a + 1; b < all.size(); b++) {
                    assertFalse(overlaps(all.get(a), all.get(b)),
                            layout + " slot " + a + " at " + all.get(a) + " overlaps slot " + b + " at " + all.get(b));
                }
            }
        }
    }

    @Test
    void everySlotSitsInsideItsPanel() {
        for (WorkbenchLayout layout : WorkbenchLayout.values()) {
            for (WorkbenchLayout.Position slot : allSlots(layout)) {
                assertTrue(slot.x() >= 0 && slot.x() + SLOT <= layout.imageWidth(),
                        layout + " slot " + slot + " is outside the " + layout.imageWidth() + "px wide panel");
                assertTrue(slot.y() >= 0 && slot.y() + SLOT <= layout.imageHeight(),
                        layout + " slot " + slot + " is outside the " + layout.imageHeight() + "px tall panel");
            }
        }
    }

    /**
     * Recipe viewers crop away the player inventory, but every painted ingredient and result well
     * still has to remain visible. Astro Miner's left laser well is the page's leftmost slot and is
     * the regression guard for viewers that previously reused the narrower rocket crop.
     */
    @Test
    void everyCraftingSlotFitsTheSchematicRecipeViewerCrop() {
        int left = Constant.RocketWorkbench.SCHEMATIC_RECIPE_VIEWER_X;
        int right = left + Constant.RocketWorkbench.SCHEMATIC_RECIPE_VIEWER_WIDTH;

        for (WorkbenchLayout layout : WorkbenchLayout.values()) {
            int bottom = Math.min(Constant.RocketWorkbench.RECIPE_VIEWER_HEIGHT, layout.playerInventoryY());
            List<WorkbenchLayout.Position> crafting = new ArrayList<>(layout.ingredientSlots());
            crafting.add(layout.resultSlot());
            for (WorkbenchLayout.Position slot : crafting) {
                assertTrue(slot.x() - 1 >= left && slot.x() + SLOT <= right,
                        layout + " slot " + slot + " is outside the recipe viewer's horizontal crop");
                assertTrue(slot.y() - 1 >= 0 && slot.y() + SLOT <= bottom,
                        layout + " slot " + slot + " is outside the recipe viewer's vertical crop");
            }
        }
    }

    /**
     * The player's own inventory has to clear the crafting area, or the page steals clicks from it.
     */
    @Test
    void thePlayerInventoryClearsTheCraftingArea() {
        for (WorkbenchLayout layout : WorkbenchLayout.values()) {
            int lowestCraftingSlot = allSlots(layout).stream().mapToInt(slot -> slot.y() + SLOT).max().orElseThrow();
            assertTrue(layout.playerInventoryY() >= lowestCraftingSlot,
                    layout + " draws the player inventory at y=" + layout.playerInventoryY()
                            + " but its crafting slots reach y=" + lowestCraftingSlot);
            assertTrue(layout.hotbarY() >= layout.playerInventoryY() + 3 * SLOT,
                    layout + " overlaps its hotbar with the player inventory");
            assertTrue(layout.hotbarY() + SLOT <= layout.imageHeight(),
                    layout + " pushes its hotbar off the bottom of the panel");
        }
    }

    private static List<WorkbenchLayout.Position> allSlots(WorkbenchLayout layout) {
        List<WorkbenchLayout.Position> all = new ArrayList<>(layout.ingredientSlots());
        all.addAll(layout.chestSlots());
        all.add(layout.resultSlot());
        return all;
    }

    private static boolean overlaps(WorkbenchLayout.Position a, WorkbenchLayout.Position b) {
        return Math.abs(a.x() - b.x()) < SLOT && Math.abs(a.y() - b.y()) < SLOT;
    }
}
