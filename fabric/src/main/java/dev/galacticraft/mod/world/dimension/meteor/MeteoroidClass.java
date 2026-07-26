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

import net.minecraft.util.RandomSource;

/**
 * The three real meteorite classes, carrying the material constants that decide how each one
 * behaves on the way down. Nothing here scripts an outcome: iron survives re-entry because it is
 * dense, strong and slow to ablate, and stone airbursts because it is none of those things.
 *
 * <p>Spawn weights follow observed fall statistics, so the overwhelming majority of what you see
 * is ordinary chondrite and a pallasite is a genuine event.
 *
 * <p>Deliberately free of Minecraft types so the physics can be unit tested without a running
 * game; block palettes live in {@link MeteoroidPalette}.
 *
 * <p>Each class carries these material constants:
 * <ul>
 *   <li><b>bulk density</b> - density of the solid body, kg/m^3</li>
 *   <li><b>ablation coefficient</b> - sigma in the classic ablation equation, s^2/m^2: the single
 *       number that separates a stone that burns from an iron that does not</li>
 *   <li><b>strength</b> - dynamic pressure the body tolerates before breaking up, Pa</li>
 *   <li><b>drag coefficient</b> - dimensionless drag coefficient</li>
 *   <li><b>spawn weight</b> - relative frequency, following observed fall statistics</li>
 * </ul>
 */
public enum MeteoroidClass {
    /** Ordinary chondrite: fragile, light, ablates fast. Around 94% of observed falls. */
    STONY(3400.0, 1.4e-8, 1.0e6, 1.00, 94),
    /** Iron: dense, strong, and the class most likely to reach the ground intact. Around 5%. */
    IRON(7800.0, 7.0e-9, 5.0e7, 0.90, 5),
    /** Stony-iron pallasite: an iron matrix studded with olivine. Around 1% of falls. */
    PALLASITE(4800.0, 1.0e-8, 1.2e7, 0.95, 1);

    private static final MeteoroidClass[] BY_ID = values();
    private static final int TOTAL_WEIGHT;

    static {
        int total = 0;
        for (MeteoroidClass value : BY_ID) total += value.spawnWeight;
        TOTAL_WEIGHT = total;
    }

    private final double bulkDensity;
    private final double ablationCoefficient;
    private final double strength;
    private final double dragCoefficient;
    private final int spawnWeight;

    MeteoroidClass(double bulkDensity, double ablationCoefficient, double strength, double dragCoefficient, int spawnWeight) {
        this.bulkDensity = bulkDensity;
        this.ablationCoefficient = ablationCoefficient;
        this.strength = strength;
        this.dragCoefficient = dragCoefficient;
        this.spawnWeight = spawnWeight;
    }

    public double bulkDensity() {
        return this.bulkDensity;
    }

    public double ablationCoefficient() {
        return this.ablationCoefficient;
    }

    public double strength() {
        return this.strength;
    }

    public double dragCoefficient() {
        return this.dragCoefficient;
    }

    public int spawnWeight() {
        return this.spawnWeight;
    }

    public byte id() {
        return (byte) this.ordinal();
    }

    public static MeteoroidClass byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return STONY;
        return BY_ID[id];
    }

    /** Picks a class using observed fall frequencies. */
    public static MeteoroidClass random(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (MeteoroidClass value : BY_ID) {
            roll -= value.spawnWeight;
            if (roll < 0) return value;
        }
        return STONY;
    }
}
