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

package dev.galacticraft.mod.world.dimension.meteor;

/**
 * The single-body meteor entry model: drag, ablation and breakup, integrated one game tick at a
 * time. Pure and free of Minecraft types so it can be unit tested directly.
 *
 * Per substep, with air density {@code rho} from {@link AtmosphereProfile}:
 *
 *   A       = SHAPE_FACTOR * (m / rho_body)^(2/3)     cross-section of a lumpy solid
 *   dv/dt   = -(Cd * A * rho * v^2) / (2m) - g        drag plus gravity
 *   dm/dt   = -(sigma * A * rho * v^3) / 2            ablation
 *   breakup  when rho * v^2 exceeds material strength
 *
 * Every planetary difference falls out of these four lines and the body's own atmosphere: Venus
 * destroys everything, Earth lets little but iron through, Mars passes fragments, and airless
 * bodies take the hit at full energy.
 *
 * Units:
 * Everything here is SI. Callers convert with {@link AtmosphereProfile#ALTITUDE_COMPRESSION}:
 * altitudes and velocities are compressed uniformly on the way into block space (so entry angles
 * are preserved), while the meteoroid body itself and the crater it digs stay at one block per
 * metre.
 */
public final class MeteorPhysics {
    /** Real seconds in one game tick. */
    public static final double TICK_SECONDS = 0.05;
    /**
     * Euler substeps per tick. Accelerations during peak heating reach thousands of m/s^2, so a
     * single step per tick would overshoot badly; eight keeps the integration stable for free.
     */
    public static final int SUBSTEPS = 8;
    /** Cross-section factor for an irregular solid, {@code A = f * (m/rho)^(2/3)}. */
    public static final double SHAPE_FACTOR = 1.21;
    /** A meteoroid counts as burned up once this little of its entry mass remains. */
    public static final double BURNOUT_FRACTION = 0.02;
    /** Velocity below which a body is no longer meaningfully in flight, m/s. */
    public static final double MIN_SPEED = 1.0;

    /** Divisor converting SI velocity (m/s) into Minecraft delta movement (blocks/tick). */
    public static final double VELOCITY_TO_BLOCKS_PER_TICK = AtmosphereProfile.ALTITUDE_COMPRESSION / TICK_SECONDS;

    private MeteorPhysics() {
    }

    /**
     * The outcome of integrating one tick.
     *
     * @param state           the state after the tick
     * @param burnedOut       the body ablated away to nothing and should vanish in a flash
     * @param breakup         peak dynamic pressure exceeded the body's strength this tick
     * @param ablatedMass     mass lost this tick, kg - drives glow and trail intensity
     * @param dynamicPressure peak dynamic pressure seen this tick, Pa
     */
    public record Step(MeteoroidState state, boolean burnedOut, boolean breakup, double ablatedMass,
                       double dynamicPressure) {
    }

    /**
     * Integrates one game tick of atmospheric flight.
     *
     * @param state     the current state
     * @param type      the meteoroid's material class
     * @param profile   the atmosphere it is falling through
     * @param entryMass the mass the body had on entry, used for the burnout threshold
     */
    public static Step step(MeteoroidState state, MeteoroidClass type, AtmosphereProfile profile, double entryMass) {
        double mass = state.mass();
        double vx = state.vx();
        double vy = state.vy();
        double vz = state.vz();
        double altitude = state.altitude();

        double burnoutMass = entryMass * BURNOUT_FRACTION;
        double dt = TICK_SECONDS / SUBSTEPS;
        double peakPressure = 0.0;
        double ablated = 0.0;
        boolean breakup = false;
        boolean burnedOut = false;

        for (int i = 0; i < SUBSTEPS; i++) {
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double density = profile.densityAt(altitude);

            if (density > 0.0 && speed > MIN_SPEED && mass > 0.0) {
                double area = SHAPE_FACTOR * Math.cbrt((mass / type.bulkDensity()) * (mass / type.bulkDensity()));
                double pressure = density * speed * speed;
                if (pressure > peakPressure) peakPressure = pressure;
                if (pressure > type.strength()) breakup = true;

                // Drag, applied along the velocity direction. The clamp keeps a huge deceleration
                // from flipping the velocity sign inside a substep.
                double deceleration = type.dragCoefficient() * area * density * speed * speed / (2.0 * mass);
                double deltaV = Math.min(deceleration * dt, speed);
                double scale = deltaV / speed;
                vx -= vx * scale;
                vy -= vy * scale;
                vz -= vz * scale;

                // Ablation. Mass is removed after drag so both use the same substep velocity.
                double lost = Math.min(mass, 0.5 * type.ablationCoefficient() * area * density * speed * speed * speed * dt);
                mass -= lost;
                ablated += lost;
            }

            vy -= profile.gravity() * dt;
            altitude += vy * dt;

            if (mass <= burnoutMass) {
                burnedOut = true;
                break;
            }
        }

        return new Step(new MeteoroidState(mass, vx, vy, vz, altitude), burnedOut, breakup, ablated, peakPressure);
    }

    /**
     * Crater diameter in metres (and so in blocks) for an impact of the given kinetic energy.
     *
     * Anchored on Meteor Crater in Arizona - a 2.5e16 J impact that left a 1200 m bowl - and
     * scaled by the standard {@code D proportional to E^(1/3.4)} law, so a small strike leaves a
     * pockmark and a large one leaves something you can see from orbit.
     */
    public static double craterDiameter(double kineticEnergy) {
        if (kineticEnergy <= 0.0) return 0.0;
        return 1200.0 * Math.pow(kineticEnergy / 2.5e16, 1.0 / 3.4);
    }

    /** Converts an SI velocity component into Minecraft blocks per tick. */
    public static double toBlocksPerTick(double metresPerSecond) {
        return metresPerSecond / VELOCITY_TO_BLOCKS_PER_TICK;
    }

    /** Converts a Minecraft blocks-per-tick component back into an SI velocity. */
    public static double toMetresPerSecond(double blocksPerTick) {
        return blocksPerTick * VELOCITY_TO_BLOCKS_PER_TICK;
    }
}
