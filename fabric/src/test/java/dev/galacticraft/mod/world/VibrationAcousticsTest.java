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

package dev.galacticraft.mod.world;

import dev.galacticraft.mod.world.atmosphere.AtmosphericDensity;
import dev.galacticraft.mod.world.atmosphere.VibrationAcoustics;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VibrationAcousticsTest {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @Test
    void earthAirIsVanillaRange() {
        assertEquals(1.0F, VibrationAcoustics.rangeMultiplier(1.0F), 1e-6F);
    }

    @Test
    void vacuumAndBadDataCarryNothing() {
        assertEquals(0.0F, VibrationAcoustics.rangeMultiplier(0.0F));
        assertEquals(0.0F, VibrationAcoustics.rangeMultiplier(-3.0F));
        assertEquals(0.0F, VibrationAcoustics.rangeMultiplier(Float.NaN));
    }

    @Test
    void thinAirFallsShortAndThickAirReachesFurther() {
        float mars = VibrationAcoustics.rangeMultiplier(0.006F);
        float venus = VibrationAcoustics.rangeMultiplier(92.0F);
        assertTrue(mars > 0.0F && mars < 0.3F, "mars " + mars);
        assertTrue(venus > 2.0F && venus < 3.0F, "venus " + venus);
    }

    @Test
    void thickAirIsCappedWhereElytraDragIs() {
        assertEquals(VibrationAcoustics.rangeMultiplier(AtmosphericDensity.MAX_PRESSURE), VibrationAcoustics.rangeMultiplier(92.0F));
    }

    @Test
    void steppingOnTheSensorConducts() {
        Set<BlockPos> solid = floorWith(ORIGIN);
        assertTrue(VibrationAcoustics.conducts(ORIGIN, ORIGIN, solid::contains));
    }

    @Test
    void footstepsBesideASensorConductThroughItsFloor() {
        assertTrue(VibrationAcoustics.conducts(new BlockPos(1, 0, 0), ORIGIN, floorWith(ORIGIN)::contains));
    }

    @Test
    void footstepsTwoBlocksAwayDoNotConduct() {
        assertFalse(VibrationAcoustics.conducts(new BlockPos(2, 0, 0), ORIGIN, floorWith(ORIGIN)::contains));
    }

    @Test
    void nothingConductsThroughEmptySpace() {
        assertFalse(VibrationAcoustics.conducts(new BlockPos(0, 2, 0), ORIGIN, Set.of(ORIGIN)::contains));
    }

    @Test
    void aBlockPlacedOnANeighbourConducts() {
        Set<BlockPos> solid = Set.of(ORIGIN, new BlockPos(1, 0, 0), new BlockPos(1, 1, 0));
        assertTrue(VibrationAcoustics.conducts(new BlockPos(1, 1, 0), ORIGIN, solid::contains));
    }

    @Test
    void woolUnderTheSensorBreaksTheFloorPath() {
        Set<BlockPos> solid = floorWith(ORIGIN);
        solid.remove(ORIGIN.below());
        assertFalse(VibrationAcoustics.conducts(new BlockPos(1, 0, 0), ORIGIN, solid::contains));
    }

    // A warden's anchor is the air block at its feet.
    @Test
    void anEntityFeelsFootstepsOnItsFloor() {
        Predicate<BlockPos> solid = floorWith(null)::contains;
        assertTrue(VibrationAcoustics.conducts(new BlockPos(1, 0, 0), ORIGIN, solid));
        assertFalse(VibrationAcoustics.conducts(new BlockPos(2, 0, 0), ORIGIN, solid));
    }

    private static Set<BlockPos> floorWith(BlockPos sensor) {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) solid.add(new BlockPos(x, -1, z));
        if (sensor != null) solid.add(sensor);
        return solid;
    }
}
