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

import dev.galacticraft.api.gas.GasComposition;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * The atmospheric physics of one celestial body, derived from the body's own
 * {@link GasComposition} instead of being hand-tuned per planet.
 *
 * Surface density comes straight from the ideal gas law, {@code rho = P*M/(R*T)}, using the
 * body's stored pressure and temperature and a mean molar mass computed from its actual gas
 * mixture. Scale height follows from {@code H = R*T/(M*g)}. Nothing here is a magic number per
 * planet: Earth falls out at ~1.22 kg/m^3, Mars at ~0.015, a corrected Venus at ~66, and the
 * airless bodies at effectively zero.
 *
 * All fields are in SI units (kilograms, metres, seconds). The one concession to Minecraft is
 * {@link #ALTITUDE_COMPRESSION}, which maps block-space to real altitude.
 */
public record AtmosphereProfile(double surfaceDensity, double scaleHeight, double gravity, int seaLevel) {
    /**
     * Metres of real altitude represented by one block of Minecraft height.
     *
     * This is the single deliberate fudge in the model. Earth's true 8.4 km scale height would
     * be perfectly flat across a 384-block world, so meteors would either burn instantly or not at
     * all. Compressing altitude by this factor puts Earth's scale height at roughly 60 blocks, so
     * density climbs meaningfully as a meteor crosses the visible sky, and a body spawned above the
     * build limit starts near 60 km - the altitude band where real ablation actually peaks.
     */
    public static final double ALTITUDE_COMPRESSION = 140.0;

    /** Universal gas constant, J/(mol*K). */
    public static final double GAS_CONSTANT = 8.314462618;
    /** One standard atmosphere in pascals; celestial bodies store pressure relative to this. */
    public static final double STANDARD_PRESSURE = 101325.0;
    /** Earth surface gravity, m/s^2. Celestial bodies store gravity relative to this. */
    public static final double EARTH_GRAVITY = 9.80665;
    /** Mean molar mass assumed when a body declares pressure but no gas mixture, kg/mol. */
    public static final double DEFAULT_MOLAR_MASS = 0.02896;

    /** A perfect vacuum at Earth gravity; used for levels with no celestial body attached. */
    public static final AtmosphereProfile VACUUM = new AtmosphereProfile(0.0, 1.0, EARTH_GRAVITY, 64);

    /**
     * Molar masses in kg/mol for every gas Galacticraft can put in an atmosphere.
     *
     * Held in a lazy holder rather than a static block on purpose: the keys are
     * {@link ResourceLocation}s, so touching this table needs the game bootstrapped. Deferring it
     * keeps {@link AtmosphereProfile} itself constructible from plain numbers, which is what lets
     * the physics be unit tested without a running Minecraft.
     */
    private static final class MolarMasses {
        private static final Object2DoubleMap<ResourceLocation> TABLE = build();

        private static Object2DoubleMap<ResourceLocation> build() {
            Object2DoubleMap<ResourceLocation> table = new Object2DoubleOpenHashMap<>();
            table.put(Gases.HYDROGEN_ID, 0.002016);
            table.put(Gases.NITROGEN_ID, 0.028014);
            table.put(Gases.OXYGEN_ID, 0.031998);
            table.put(Gases.OZONE_ID, 0.047997);
            table.put(Gases.WATER_VAPOR_ID, 0.018015);
            table.put(Gases.HYDROGEN_DEUTERIUM_OXIDE_ID, 0.019021);
            table.put(Gases.CARBON_MONOXIDE_ID, 0.028010);
            table.put(Gases.CARBON_DIOXIDE_ID, 0.044009);
            table.put(Gases.METHANE_ID, 0.016043);
            table.put(Gases.NITRIC_OXIDE_ID, 0.030006);
            table.put(Gases.NITROUS_OXIDE_ID, 0.044013);
            table.put(Gases.NITROGEN_DIOXIDE_ID, 0.046006);
            table.put(Gases.HELIUM_ID, 0.004003);
            table.put(Gases.NEON_ID, 0.020180);
            table.put(Gases.ARGON_ID, 0.039948);
            table.put(Gases.KRYPTON_ID, 0.083798);
            table.put(Gases.XENON_ID, 0.131293);
            table.put(Gases.IODINE_ID, 0.253809);
            return table;
        }
    }

    /**
     * Builds the profile for a level from its celestial body, or {@link #VACUUM} if the level has
     * none (an unregistered or third-party dimension). Usable on either side: the client needs it
     * to decide whether a sky is thick enough to show meteors burning up at all.
     */
    public static AtmosphereProfile of(Level level) {
        Holder<CelestialBody<?, ?>> holder = level.galacticraft$getCelestialBody();
        if (holder == null) return VACUUM;
        CelestialBody<?, ?> body = holder.value();
        return of(body.atmosphere(), body.gravity(), level.getSeaLevel());
    }

    /** Derives a profile from raw atmospheric data; separated from {@link #of(ServerLevel)} for testing. */
    public static AtmosphereProfile of(GasComposition atmosphere, float relativeGravity, int seaLevel) {
        double gravity = Math.max(0.01, relativeGravity) * EARTH_GRAVITY;
        double kelvin = atmosphere.temperature() + 273.15;
        if (kelvin <= 0.0) kelvin = 1.0;

        double molarMass = meanMolarMass(atmosphere.composition());
        double pressure = Math.max(0.0, atmosphere.pressure()) * STANDARD_PRESSURE;

        double density = pressure * molarMass / (GAS_CONSTANT * kelvin);
        double scaleHeight = GAS_CONSTANT * kelvin / (molarMass * gravity);
        return new AtmosphereProfile(density, scaleHeight, gravity, seaLevel);
    }

    /**
     * Mixture-weighted mean molar mass in kg/mol. Falls back to Earth air when a body declares no
     * composition, which only matters for bodies that also declare a meaningful pressure.
     */
    public static double meanMolarMass(Object2DoubleMap<ResourceKey<Fluid>> composition) {
        if (composition.isEmpty()) return DEFAULT_MOLAR_MASS;

        double totalPpm = 0.0;
        double weighted = 0.0;
        for (Object2DoubleMap.Entry<ResourceKey<Fluid>> entry : composition.object2DoubleEntrySet()) {
            double ppm = entry.getDoubleValue();
            if (ppm <= 0.0) continue;
            double mass = MolarMasses.TABLE.getOrDefault(entry.getKey().location(), DEFAULT_MOLAR_MASS);
            weighted += mass * ppm;
            totalPpm += ppm;
        }
        if (totalPpm <= 0.0) return DEFAULT_MOLAR_MASS;
        return weighted / totalPpm;
    }

    /** Air density in kg/m^3 at a real altitude above sea level, in metres. */
    public double densityAt(double altitudeMeters) {
        if (this.surfaceDensity <= 0.0) return 0.0;
        if (altitudeMeters <= 0.0) return this.surfaceDensity;
        return this.surfaceDensity * Math.exp(-altitudeMeters / this.scaleHeight);
    }

    /** Real altitude in metres corresponding to a Minecraft y coordinate. */
    public double altitudeOf(double blockY) {
        return (blockY - this.seaLevel) * ALTITUDE_COMPRESSION;
    }

    /** Minecraft y coordinate corresponding to a real altitude in metres. */
    public double blockYOf(double altitudeMeters) {
        return altitudeMeters / ALTITUDE_COMPRESSION + this.seaLevel;
    }

    /** Air density in kg/m^3 at a Minecraft y coordinate. */
    public double densityAtBlockY(double blockY) {
        return densityAt(altitudeOf(blockY));
    }

    /** Whether this body has enough atmosphere to meaningfully ablate anything. */
    public boolean hasAtmosphere() {
        return this.surfaceDensity > 1.0e-6;
    }
}
