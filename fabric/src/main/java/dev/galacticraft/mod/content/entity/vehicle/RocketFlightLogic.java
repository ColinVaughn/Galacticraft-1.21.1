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

/**
 * Fuel budget and ascent maths for {@link RocketEntity}, kept free of Minecraft types so the
 * launch can be simulated in a plain unit test.
 *
 * A rocket's tank is measured here in "burn ticks" -- the number of ticks a tank holds rather
 * than a volume -- so the numbers stay exact and independent of the droplets-per-bucket constant.
 */
public final class RocketFlightLogic {
    /**
     * Capacity of a rocket's fuel tank, in buckets, before the config is consulted.
     *
     * @see dev.galacticraft.mod.api.config.Config#rocketFuelTankCapacity()
     */
    public static final int DEFAULT_FUEL_TANK_CAPACITY_BUCKETS = 100;

    /**
     * How many ticks of powered flight one bucket of fuel provides, before the config is consulted.
     * A climb from sea level to {@code ESCAPE_HEIGHT} takes roughly 1600 ticks, so a full tank
     * covers about three trips -- the same margin legacy Galacticraft gave its one-bucket tier 1
     * tank.
     *
     * @see dev.galacticraft.mod.api.config.Config#rocketBurnTicksPerBucket()
     */
    public static final int DEFAULT_BURN_TICKS_PER_BUCKET = 50;

    /** Ticks the rocket sits on the pad between ignition and lift-off. */
    static final int PRE_LAUNCH_WAIT_TICKS = 400;

    /** Whether the ignition countdown draws from the tank. Legacy Galacticraft burned nothing on the pad. */
    static final boolean BURNS_FUEL_DURING_COUNTDOWN = false;

    /** Thrust the moment the rocket leaves the pad. */
    static final double INITIAL_LAUNCH_THRUST = Math.sqrt(2.0) / 2.0;

    private static final double THRUST_RAMP_PER_TICK = 0.005;
    private static final double VERTICAL_DRAG = 0.955;
    private static final double THRUST_ACCELERATION = 0.08 * Math.sqrt(2.0);
    private static final double GRAVITY_ACCELERATION = 0.08;
    private static final double DEG_TO_RAD = Math.PI / 180.0;

    /** Safety valve for {@link #poweredFlightTicksToEscape}; an unreachable ceiling returns this. */
    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final int SIMULATION_TICK_LIMIT = 100_000;

    private RocketFlightLogic() {
    }

    /** Total ticks of engine burn a full tank provides. */
    static int burnTicksAvailable(int fuelTankCapacityBuckets, int burnTicksPerBucket) {
        return fuelTankCapacityBuckets * burnTicksPerBucket;
    }

    /** Total ticks of engine burn a full tank provides at the shipped defaults. */
    static int burnTicksAvailable() {
        return burnTicksAvailable(DEFAULT_FUEL_TANK_CAPACITY_BUCKETS, DEFAULT_BURN_TICKS_PER_BUCKET);
    }

    /** Ticks of burn spent on the pad before the rocket ever moves. */
    static int countdownBurnTicks() {
        return BURNS_FUEL_DURING_COUNTDOWN ? PRE_LAUNCH_WAIT_TICKS : 0;
    }

    /** Thrust after one tick of powered flight. */
    static double nextThrust(double thrust) {
        return Math.clamp(thrust + THRUST_RAMP_PER_TICK, 0.0, 1.0);
    }

    /**
     * One tick of vertical velocity integration. Mirrors {@link RocketEntity#tickInAir()}.
     *
     * @param pitchDegrees the rocket's x-rotation; 0 is straight up
     */
    static double nextVerticalVelocity(double velocityY, double thrust, double pitchDegrees, double gravity) {
        return VERTICAL_DRAG * velocityY
                + THRUST_ACCELERATION * thrust * Math.cos(pitchDegrees * DEG_TO_RAD)
                - gravity * GRAVITY_ACCELERATION;
    }

    /** Ticks of powered flight needed to climb from {@code startY} to {@code escapeHeight}. */
    static int poweredFlightTicksToEscape(double startY, double escapeHeight, double gravity) {
        double y = startY;
        double velocityY = 0.0;
        double thrust = INITIAL_LAUNCH_THRUST;

        for (int ticks = 1; ticks <= SIMULATION_TICK_LIMIT; ticks++) {
            thrust = nextThrust(thrust);
            velocityY = nextVerticalVelocity(velocityY, thrust, 0.0, gravity);
            y += velocityY;
            if (y >= escapeHeight) return ticks;
        }
        return UNREACHABLE;
    }

    /** Total ticks of burn a launch costs, countdown included. */
    static int burnTicksRequiredToEscape(double startY, double escapeHeight, double gravity) {
        int flight = poweredFlightTicksToEscape(startY, escapeHeight, gravity);
        if (flight == UNREACHABLE) return UNREACHABLE;
        return countdownBurnTicks() + flight;
    }
}
