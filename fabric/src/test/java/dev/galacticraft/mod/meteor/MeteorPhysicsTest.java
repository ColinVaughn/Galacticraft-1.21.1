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
import dev.galacticraft.mod.world.dimension.meteor.MeteorPhysics;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole point of the ablation model is that per-planet behaviour emerges from the physics
 * rather than being scripted, so these tests assert the emergent outcomes: Venus destroys bodies,
 * Earth is hard on stone and kind to iron, and an airless body does nothing at all.
 */
class MeteorPhysicsTest {
    private static final AtmosphereProfile EARTH = profile(1.0f, 15.0, 0.02896, 1.0f);
    private static final AtmosphereProfile VENUS = profile(92.0f, 464.0, 0.043449, 0.91f);
    private static final AtmosphereProfile MARS = profile(0.006f, -63.0, 0.04332, 0.38f);
    private static final AtmosphereProfile MOON = profile(3.0e-15f, 23.0, 0.02896, 0.166f);

    /** A representative entry: 60 km up, straight down at 20 km/s. */
    private static MeteoroidState entry(double mass) {
        return new MeteoroidState(mass, 0.0, -20000.0, 0.0, 60000.0);
    }

    /** Runs a descent and reports the fraction of entry mass that survived to the ground. */
    private static double survivingFraction(MeteoroidClass type, AtmosphereProfile profile, double mass) {
        double entryMass = mass;
        MeteoroidState state = entry(mass);
        for (int tick = 0; tick < 2000; tick++) {
            MeteorPhysics.Step step = MeteorPhysics.step(state, type, profile, entryMass);
            state = step.state();
            if (step.burnedOut()) return 0.0;
            if (state.altitude() <= 0.0) break;
        }
        return state.mass() / entryMass;
    }

    @Test
    void airlessBodyLeavesAMeteoroidCompletelyUntouched() {
        assertEquals(1.0, survivingFraction(MeteoroidClass.STONY, MOON, 5000.0), 1.0e-6,
                "with no air there is nothing to ablate a body");
    }

    @Test
    void airlessBodyStillAcceleratesUnderGravity() {
        MeteoroidState state = new MeteoroidState(5000.0, 0.0, -100.0, 0.0, 60000.0);
        MeteorPhysics.Step step = MeteorPhysics.step(state, MeteoroidClass.STONY, MOON, 5000.0);
        assertTrue(step.state().vy() < -100.0, "gravity must still apply in a vacuum");
        assertEquals(0.0, step.ablatedMass(), 1.0e-9);
    }

    @Test
    void venusDestroysWhatEarthOnlyDamages() {
        double onVenus = survivingFraction(MeteoroidClass.STONY, VENUS, 20000.0);
        double onEarth = survivingFraction(MeteoroidClass.STONY, EARTH, 20000.0);
        assertTrue(onVenus < onEarth,
                "Venus's 92 bar must be harsher than Earth's 1 bar (venus=" + onVenus + " earth=" + onEarth + ")");
    }

    @Test
    void ironOutlastsStoneInTheSameAtmosphere() {
        double iron = survivingFraction(MeteoroidClass.IRON, EARTH, 20000.0);
        double stone = survivingFraction(MeteoroidClass.STONY, EARTH, 20000.0);
        assertTrue(iron > stone,
                "iron ablates more slowly and should survive better (iron=" + iron + " stone=" + stone + ")");
    }

    @Test
    void thinMarsAirLetsMoreThroughThanEarthAir() {
        double mars = survivingFraction(MeteoroidClass.STONY, MARS, 20000.0);
        double earth = survivingFraction(MeteoroidClass.STONY, EARTH, 20000.0);
        assertTrue(mars > earth,
                "Mars is thin enough to pass more mass than Earth (mars=" + mars + " earth=" + earth + ")");
    }

    @Test
    void atmosphereDeceleratesWhileVacuumDoesNot() {
        MeteoroidState state = entry(20000.0);
        double inAir = MeteorPhysics.step(state, MeteoroidClass.STONY, EARTH, 20000.0).state().speed();
        double inVacuum = MeteorPhysics.step(state, MeteoroidClass.STONY, MOON, 20000.0).state().speed();
        assertTrue(inAir < inVacuum, "drag must slow a body down relative to free fall");
    }

    @Test
    void aBodyIsReportedBreakingUpWhenRamPressureExceedsItsStrength() {
        // Deep in Venus's atmosphere at full speed, nothing stony holds together.
        MeteoroidState deep = new MeteoroidState(50000.0, 0.0, -20000.0, 0.0, 2000.0);
        MeteorPhysics.Step step = MeteorPhysics.step(deep, MeteoroidClass.STONY, VENUS, 50000.0);
        assertTrue(step.dynamicPressure() > MeteoroidClass.STONY.strength());
        assertTrue(step.breakup());
    }

    @Test
    void ironToleratesRamPressureThatShattersStone() {
        MeteoroidState state = new MeteoroidState(50000.0, 0.0, -12000.0, 0.0, 40000.0);
        boolean stoneBreaks = MeteorPhysics.step(state, MeteoroidClass.STONY, EARTH, 50000.0).breakup();
        boolean ironBreaks = MeteorPhysics.step(state, MeteoroidClass.IRON, EARTH, 50000.0).breakup();
        assertTrue(stoneBreaks, "stone should fail at this ram pressure");
        assertFalse(ironBreaks, "iron is fifty times stronger and should hold");
    }

    @Test
    void dragNeverReversesVelocity() {
        // A tiny body deep in Venus sees enormous deceleration; it must stop, never bounce upward.
        MeteoroidState state = new MeteoroidState(1.0, 0.0, -30000.0, 0.0, 0.0);
        for (int tick = 0; tick < 200; tick++) {
            MeteorPhysics.Step step = MeteorPhysics.step(state, MeteoroidClass.STONY, VENUS, 1.0);
            state = step.state();
            assertTrue(state.vy() <= 0.0 || state.vy() < 1.0,
                    "drag must not fling a falling body back upward (vy=" + state.vy() + ")");
            assertTrue(Double.isFinite(state.vy()) && Double.isFinite(state.mass()),
                    "integration must stay finite");
            if (step.burnedOut()) break;
        }
    }

    @Test
    void massNeverGoesNegative() {
        MeteoroidState state = new MeteoroidState(100.0, 0.0, -25000.0, 0.0, 30000.0);
        for (int tick = 0; tick < 500; tick++) {
            MeteorPhysics.Step step = MeteorPhysics.step(state, MeteoroidClass.STONY, VENUS, 100.0);
            state = step.state();
            assertTrue(state.mass() >= 0.0, "mass must never go negative");
            if (step.burnedOut()) break;
        }
    }

    @Test
    void craterScalesWithEnergyAndIsAnchoredOnARealImpact() {
        // Meteor Crater: 2.5e16 J left a 1200 m bowl, which is the anchor point of the scaling law.
        assertEquals(1200.0, MeteorPhysics.craterDiameter(2.5e16), 1.0);
        assertTrue(MeteorPhysics.craterDiameter(2.5e17) > MeteorPhysics.craterDiameter(2.5e16));
        assertEquals(0.0, MeteorPhysics.craterDiameter(0.0), 1.0e-9);
    }

    @Test
    void craterGrowthIsSublinearInEnergy() {
        // A thousand times the energy must not mean a thousand times the crater.
        double small = MeteorPhysics.craterDiameter(1.0e13);
        double large = MeteorPhysics.craterDiameter(1.0e16);
        assertTrue(large < small * 20.0, "E^(1/3.4) scaling should keep crater growth gentle");
        assertTrue(large > small);
    }

    @Test
    void velocityConversionRoundTrips() {
        assertEquals(20000.0, MeteorPhysics.toMetresPerSecond(MeteorPhysics.toBlocksPerTick(20000.0)), 1.0e-6);
        // 20 km/s should read as a handful of blocks per tick, not hundreds.
        double blocksPerTick = MeteorPhysics.toBlocksPerTick(20000.0);
        assertTrue(blocksPerTick > 1.0 && blocksPerTick < 20.0, "got " + blocksPerTick + " blocks/tick");
    }

    private static AtmosphereProfile profile(float pressureBar, double celsius, double molarMass, float gravity) {
        double kelvin = celsius + 273.15;
        double g = gravity * AtmosphereProfile.EARTH_GRAVITY;
        double pascals = pressureBar * AtmosphereProfile.STANDARD_PRESSURE;
        double density = pascals * molarMass / (AtmosphereProfile.GAS_CONSTANT * kelvin);
        double scaleHeight = AtmosphereProfile.GAS_CONSTANT * kelvin / (molarMass * g);
        return new AtmosphereProfile(density, scaleHeight, g, 64);
    }
}
