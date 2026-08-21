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

package dev.galacticraft.mod.content.block.entity.machine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

import static dev.galacticraft.mod.content.block.entity.machine.AbstractSolarPanelBlockEntity.NO_SKYLIGHT_SCAN_HEIGHT;
import static dev.galacticraft.mod.content.block.entity.machine.AbstractSolarPanelBlockEntity.hasSunlight;
import static dev.galacticraft.mod.content.block.entity.machine.AbstractSolarPanelBlockEntity.isColumnShaded;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shade sampling for panels in dimensions without skylight, such as the asteroid belt. Sky
 * brightness is pinned to zero there, so the column scan replaces the usual sky visibility check.
 */
class SolarPanelBlockageTest {
    private static final int PLATE_Y = 100;
    private static final int WORLD_TOP = 320;

    private static IntPredicate absorbingAt(int... heights) {
        Set<Integer> blocked = new HashSet<>();
        for (int height : heights) blocked.add(height);
        return blocked::contains;
    }

    @Test
    void dayNightFollowsTheRenderedSun() {
        assertTrue(hasSunlight(AbstractSolarPanelBlockEntity.NOON));
        assertFalse(hasSunlight(AbstractSolarPanelBlockEntity.MIDNIGHT));
    }

    @Test
    void openSkyGeneratesPower() {
        assertFalse(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt()));
    }

    @Test
    void ignoresThePanelsOwnPlate() {
        // The nine sampled positions are the panel's own multiblock parts, so the scan must start above them.
        assertFalse(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt(PLATE_Y)));
    }

    @Test
    void shadedByBlockDirectlyAbove() {
        assertTrue(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt(PLATE_Y + 1)));
    }

    @Test
    void shadedAtTheEdgeOfRange() {
        assertTrue(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt(PLATE_Y + NO_SKYLIGHT_SCAN_HEIGHT)));
    }

    @Test
    void distantDebrisDoesNotShade() {
        // The asteroid field overhead sits far above the panel and must not count against it.
        assertFalse(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt(PLATE_Y + NO_SKYLIGHT_SCAN_HEIGHT + 1)));
        assertFalse(isColumnShaded(PLATE_Y, WORLD_TOP, absorbingAt(PLATE_Y + 120)));
    }

    @Test
    void stopsAtTheTopOfTheWorld() {
        int nearTop = WORLD_TOP - 4;
        assertFalse(isColumnShaded(nearTop, WORLD_TOP, height -> {
            if (height >= WORLD_TOP) throw new AssertionError("scanned past the top of the world: " + height);
            return false;
        }));
    }
}
