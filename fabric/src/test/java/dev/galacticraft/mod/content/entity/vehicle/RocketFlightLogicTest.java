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

package dev.galacticraft.mod.content.entity.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocketFlightLogicTest {
    private static final double ESCAPE_HEIGHT = 1200.0;
    private static final double SEA_LEVEL = 64.0;

    // Surface gravities from the generated celestial_body data.
    private static final double EARTH_GRAVITY = 1.0;
    private static final double VENUS_GRAVITY = 0.91;
    private static final double MARS_GRAVITY = 0.38;
    private static final double MERCURY_GRAVITY = 0.38;
    private static final double MOON_GRAVITY = 0.166;
    private static final double ASTEROID_GRAVITY = 0.1;

    @Test
    void ignitionCountdownDoesNotConsumeFuel() {
        assertEquals(0, RocketFlightLogic.countdownBurnTicks());
    }

    @Test
    void fullTankReachesEscapeHeightFromSeaLevelOnEarth() {
        int required = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, EARTH_GRAVITY);
        assertTrue(required <= RocketFlightLogic.burnTicksAvailable(),
                "a full tank must reach orbit: needs " + required + " ticks of burn, tank holds "
                        + RocketFlightLogic.burnTicksAvailable());
    }

    @Test
    void launchFromSeaLevelLeavesAtLeastHalfTheTankInReserve() {
        int required = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, EARTH_GRAVITY);
        assertTrue(required <= RocketFlightLogic.burnTicksAvailable() / 2,
                "an Earth launch should cost at most half a tank like legacy Galacticraft, but costs "
                        + required + " of " + RocketFlightLogic.burnTicksAvailable() + " ticks");
    }

    @Test
    void fullTankReachesEscapeHeightFromAHighAltitudeLaunchPad() {
        int required = RocketFlightLogic.burnTicksRequiredToEscape(-64.0, ESCAPE_HEIGHT, EARTH_GRAVITY);
        assertTrue(required <= RocketFlightLogic.burnTicksAvailable(),
                "a pad at bedrock level must still reach orbit: needs " + required + " ticks");
    }

    @Test
    void everyLandableBodyCanBeLeftOnAFullTank() {
        assertEscapableOnAFullTank("earth", EARTH_GRAVITY);
        assertEscapableOnAFullTank("venus", VENUS_GRAVITY);
        assertEscapableOnAFullTank("mars", MARS_GRAVITY);
        assertEscapableOnAFullTank("mercury", MERCURY_GRAVITY);
        assertEscapableOnAFullTank("moon", MOON_GRAVITY);
        assertEscapableOnAFullTank("asteroid", ASTEROID_GRAVITY);
    }

    /**
     * A rocket keeps whatever fuel it had when it left the pad, and there is no guarantee of oil or
     * a refinery at the destination, so the outbound and return launches have to share one tank.
     */
    @Test
    void aRoundTripSharesASingleTankWithoutRefuelling() {
        assertRoundTripFitsInOneTank("venus", VENUS_GRAVITY);
        assertRoundTripFitsInOneTank("mars", MARS_GRAVITY);
        assertRoundTripFitsInOneTank("mercury", MERCURY_GRAVITY);
        assertRoundTripFitsInOneTank("moon", MOON_GRAVITY);
        assertRoundTripFitsInOneTank("asteroid", ASTEROID_GRAVITY);
    }

    private static void assertEscapableOnAFullTank(String body, double gravity) {
        int required = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, gravity);
        assertTrue(required <= RocketFlightLogic.burnTicksAvailable(),
                "launching from " + body + " needs " + required + " ticks of burn, tank holds "
                        + RocketFlightLogic.burnTicksAvailable());
    }

    private static void assertRoundTripFitsInOneTank(String body, double gravity) {
        int outbound = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, EARTH_GRAVITY);
        int homeward = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, gravity);
        assertTrue(outbound + homeward <= RocketFlightLogic.burnTicksAvailable(),
                "earth <-> " + body + " needs " + (outbound + homeward) + " ticks of burn, tank holds "
                        + RocketFlightLogic.burnTicksAvailable());
    }

    @Test
    void lowGravityBodiesCostLessFuelThanEarth() {
        int earth = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, EARTH_GRAVITY);
        int moon = RocketFlightLogic.burnTicksRequiredToEscape(SEA_LEVEL, ESCAPE_HEIGHT, MOON_GRAVITY);
        assertTrue(moon < earth, "moon launch (" + moon + ") should be cheaper than earth (" + earth + ")");
    }

    @Test
    void thrustRampsToFullAndStopsThere() {
        double thrust = RocketFlightLogic.INITIAL_LAUNCH_THRUST;
        for (int i = 0; i < 1000; i++) {
            thrust = RocketFlightLogic.nextThrust(thrust);
        }
        assertEquals(1.0, thrust, 1.0E-9);
    }

    @Test
    void aRocketWithoutThrustFallsUnderGravity() {
        assertTrue(RocketFlightLogic.nextVerticalVelocity(0.0, 0.0, 0.0, EARTH_GRAVITY) < 0.0);
    }
}
