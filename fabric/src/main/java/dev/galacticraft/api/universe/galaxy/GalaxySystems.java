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

package dev.galacticraft.api.universe.galaxy;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.star.Star;
import net.minecraft.core.Registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The star systems of each galaxy. A star system is a {@link Star} with no parent; a galaxy is any
 * entry of the galaxy registry, so an addon adds one with a single datapack file.
 */
public final class GalaxySystems {
    private GalaxySystems() {
    }

    /**
     * The root star of every star system, grouped by galaxy. Galaxies and stars come in registry id
     * order, and galaxies with no systems are left out.
     */
    public static Map<Galaxy, List<CelestialBody<?, ?>>> byGalaxy(Registry<Galaxy> galaxies, Registry<CelestialBody<?, ?>> bodies) {
        Map<Galaxy, List<CelestialBody<?, ?>>> systems = new LinkedHashMap<>();
        galaxies.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> systems.put(entry.getValue(), new ArrayList<>()));
        bodies.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .map(Map.Entry::getValue)
                .filter(body -> body.parent().isEmpty() && body.type() instanceof Star)
                .forEach(star -> star.galaxy(bodies)
                        .map(galaxies::get)
                        .map(systems::get)
                        .ifPresent(list -> list.add(star)));
        systems.values().removeIf(List::isEmpty);
        return systems;
    }

    /** Whether two celestial bodies belong to the same galaxy; false if either has none. */
    public static boolean sameGalaxy(Registry<CelestialBody<?, ?>> bodies, CelestialBody<?, ?> a, CelestialBody<?, ?> b) {
        var galaxy = a.galaxy(bodies);
        return galaxy.isPresent() && galaxy.equals(b.galaxy(bodies));
    }
}
