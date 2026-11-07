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

package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidBeltFieldTest {
    private static final Vec3 SUN = SpaceTravel.SUN_POSITION;

    private static Vec3 onBelt(double radius, double angle) {
        double a = Math.toRadians(angle);
        return SUN.add(radius * Math.cos(a), 0, radius * Math.sin(a));
    }

    private static double orbit(AsteroidBeltField.Cube cube) {
        return Math.hypot(cube.x() - SUN.x, cube.z() - SUN.z);
    }

    @Test void placementIsDeterministic() {
        List<AsteroidBeltField.Cube> first = new ArrayList<>(), second = new ArrayList<>();
        AsteroidBeltField.near(onBelt(18_500, 200), 600, first::add);
        AsteroidBeltField.near(onBelt(18_500, 200), 600, second::add);
        assertFalse(first.isEmpty(), "The belt core must hold rocks");
        assertEquals(first, second);
    }

    @Test void rocksStayInsideTheBeltAndClearOfEveryBody() {
        List<AsteroidBeltField.Cube> rocks = new ArrayList<>(AsteroidBeltField.large());
        for (double radius = 14_000; radius <= 19_500; radius += 1_100)
            for (double angle = 0; angle < 360; angle += 45) AsteroidBeltField.near(onBelt(radius, angle), 900, rocks::add);
        assertTrue(rocks.size() > 1_000, "Expected a populated belt, found " + rocks.size());
        for (var cube : rocks) {
            double orbit = orbit(cube);
            // Cluster companions may sit a cube-width outside the field's own bounds.
            assertTrue(orbit > AsteroidBeltField.INNER - 40 && orbit < AsteroidBeltField.OUTER + 40, "Rock outside the belt at " + orbit);
            assertTrue(Math.abs(cube.y() - SUN.y) < AsteroidBeltField.HALF_THICKNESS + 40, "Rock outside the belt's thickness");
            for (var body : SpaceTravel.DESTINATIONS)
                assertTrue(body.center().distanceTo(new Vec3(cube.x(), cube.y(), cube.z()))
                        > body.radius() * Math.sqrt(3) + AsteroidBeltField.CLEARANCE * 0.5, "Rock crowds " + body.body().location());
        }
    }

    @Test void kirkwoodGapsAreClearLanes() {
        for (double gap : new double[]{15_833, 17_300, 17_896}) {
            assertTrue(AsteroidBeltField.density(onBelt(gap, 120)) < 0.25 * AsteroidBeltField.density(onBelt(gap + 400, 120)),
                    "The gap at " + gap + " is not clearly emptier than its surroundings");
        }
        assertEquals(0, AsteroidBeltField.density(onBelt(12_200, 45)), "Mars's orbit must be outside the belt");
        assertEquals(0, AsteroidBeltField.density(onBelt(21_000, 30)), "The belt must stop short of Jupiter");
    }

    @Test void nearFieldStaysWithinTheRenderBudget() {
        int[] count = {0};
        AsteroidBeltField.near(onBelt(18_500, 200), AsteroidBeltRenderer.NEAR_RADIUS, cube -> count[0]++);
        assertTrue(count[0] > 200 && count[0] < 6_000, "Near field holds " + count[0] + " cubes");
        assertTrue(AsteroidBeltField.large().size() < 12_000, "Far field holds " + AsteroidBeltField.large().size() + " cubes");
    }
}
