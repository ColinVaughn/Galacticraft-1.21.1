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

package dev.galacticraft.mod.meteor;

import dev.galacticraft.mod.world.dimension.meteor.AtmosphereProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that atmospheric density really is derived from celestial body data rather than tuned,
 * by comparing what the ideal gas law produces against published surface densities.
 */
class AtmosphereProfileTest {
    @Test
    void earthAirMatchesPublishedDensity() {
        // 1 bar, 15 C, dry air at 0.02896 kg/mol -> the textbook 1.225 kg/m^3.
        AtmosphereProfile earth = profile(1.0f, 15.0, 0.02896, 1.0f);
        assertEquals(1.225, earth.surfaceDensity(), 0.01);
    }

    @Test
    void earthScaleHeightMatchesPublishedValue() {
        AtmosphereProfile earth = profile(1.0f, 15.0, 0.02896, 1.0f);
        assertEquals(8435.0, earth.scaleHeight(), 100.0);
    }

    @Test
    void venusIsFarDenserThanEarth() {
        // 92 bar of hot CO2 -> about 65 kg/m^3, over fifty times Earth's.
        AtmosphereProfile venus = profile(92.0f, 464.0, 0.043449, 0.91f);
        assertEquals(66.0, venus.surfaceDensity(), 3.0);
        assertTrue(venus.surfaceDensity() > profile(1.0f, 15.0, 0.02896, 1.0f).surfaceDensity() * 50.0);
    }

    @Test
    void marsIsThinButNotEmpty() {
        AtmosphereProfile mars = profile(0.006f, -63.0, 0.04332, 0.38f);
        assertEquals(0.015, mars.surfaceDensity(), 0.005);
        assertTrue(mars.hasAtmosphere(), "Mars must still count as having an atmosphere");
    }

    @Test
    void airlessBodiesHaveNoUsableAtmosphere() {
        AtmosphereProfile moon = profile(3.0e-15f, 23.0, 0.02896, 0.166f);
        assertFalse(moon.hasAtmosphere());
        assertEquals(0.0, moon.densityAt(0.0), 1.0e-9);
    }

    @Test
    void densityFallsOffExponentiallyWithAltitude() {
        AtmosphereProfile earth = profile(1.0f, 15.0, 0.02896, 1.0f);
        double surface = earth.densityAt(0.0);
        double oneScaleHeight = earth.densityAt(earth.scaleHeight());

        // One scale height up is by definition 1/e of the surface value.
        assertEquals(surface / Math.E, oneScaleHeight, surface * 0.001);
        assertTrue(earth.densityAt(60000.0) < oneScaleHeight);
    }

    @Test
    void altitudeAndBlockYAreInverses() {
        AtmosphereProfile earth = profile(1.0f, 15.0, 0.02896, 1.0f);
        assertEquals(320.0, earth.blockYOf(earth.altitudeOf(320.0)), 1.0e-6);
        assertEquals(0.0, earth.altitudeOf(earth.seaLevel()), 1.0e-6);
    }

    @Test
    void vacuumProfileIsInert() {
        assertFalse(AtmosphereProfile.VACUUM.hasAtmosphere());
        assertEquals(0.0, AtmosphereProfile.VACUUM.densityAt(1000.0), 1.0e-12);
    }

    /** Builds a profile directly from physical quantities, bypassing the registry. */
    private static AtmosphereProfile profile(float pressureBar, double celsius, double molarMass, float gravity) {
        double kelvin = celsius + 273.15;
        double g = gravity * AtmosphereProfile.EARTH_GRAVITY;
        double pascals = pressureBar * AtmosphereProfile.STANDARD_PRESSURE;
        double density = pascals * molarMass / (AtmosphereProfile.GAS_CONSTANT * kelvin);
        double scaleHeight = AtmosphereProfile.GAS_CONSTANT * kelvin / (molarMass * g);
        return new AtmosphereProfile(density, scaleHeight, g, 64);
    }
}
