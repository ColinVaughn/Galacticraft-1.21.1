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
 * The instantaneous physical state of one meteoroid, entirely in SI units.
 *
 * @param mass     remaining mass, kg
 * @param vx       velocity east, m/s
 * @param vy       velocity up, m/s (negative while falling)
 * @param vz       velocity south, m/s
 * @param altitude height above the body's sea level, m
 */
public record MeteoroidState(double mass, double vx, double vy, double vz, double altitude) {
    public double speed() {
        return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
    }

    /** Kinetic energy in joules, which drives crater size and airburst yield. */
    public double kineticEnergy() {
        double speed = speed();
        return 0.5 * this.mass * speed * speed;
    }

    public MeteoroidState withAltitude(double altitude) {
        return new MeteoroidState(this.mass, this.vx, this.vy, this.vz, altitude);
    }

    public MeteoroidState withMass(double mass) {
        return new MeteoroidState(mass, this.vx, this.vy, this.vz, this.altitude);
    }
}
