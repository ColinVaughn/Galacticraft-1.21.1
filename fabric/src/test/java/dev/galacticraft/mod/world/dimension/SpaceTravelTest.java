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

package dev.galacticraft.mod.world.dimension;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceTravelTest {
    @Test void departureCannotImmediatelyTriggerReentryOrOverlapAnotherBody() {
        for (var departure : SpaceTravel.DESTINATIONS) {
            // Spinning corners must never sweep over the departure point.
            for (double days = 0; days < 3; days += .037) {
                assertTrue(departure.distance(departure.departurePosition(), days) > SpaceTravel.ENTRY_DISTANCE);
                assertEquals(departure, SpaceTravel.nearest(departure.departurePosition(), days));
            }
            for (var other : SpaceTravel.DESTINATIONS) {
                if (other != departure) assertTrue(departure.center().distanceTo(other.center())
                        > Math.sqrt(3) * (departure.radius() + other.radius()) + 2 * SpaceTravel.PRELOAD_DISTANCE);
            }
        }
    }

    @Test void planetsShareAnOrderedSolarPlaneAndMoonStaysLocalToEarth() {
        double previousOrbit = SpaceTravel.SUN_RADIUS;
        for (int index : new int[]{4, 3, 0, 2, 5, 6}) {
            var planet = SpaceTravel.DESTINATIONS.get(index);
            assertEquals(SpaceTravel.SUN_POSITION.y, planet.center().y);
            double orbit = planet.center().distanceTo(SpaceTravel.SUN_POSITION);
            assertTrue(orbit - planet.radius() > previousOrbit, "Planetary orbits must not overlap");
            previousOrbit = orbit + planet.radius();
        }
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var moon = SpaceTravel.DESTINATIONS.get(1);
        assertEquals(2400, moon.center().distanceTo(earth.center()), 1e-9);
        assertEquals(.271, moon.radius() / earth.radius(), .001);
    }

    @Test void planetsKeepTrueRelativeSizesAndTheSunOutsizesAndClearsThem() {
        // Mean radii in km, in DESTINATIONS order: Earth, Moon, Mars, Venus, Mercury, Ceres (the asteroid), Jupiter.
        double[] real = {6371, 1737.4, 3389.5, 6051.8, 2439.7, 469.7, 69911};
        double earth = SpaceTravel.DESTINATIONS.getFirst().radius();
        for (int i = 0; i < real.length; i++) {
            var body = SpaceTravel.DESTINATIONS.get(i);
            String name = body.body().location().getPath();
            assertEquals(earth * real[i] / real[0], body.radius(), 1, name + " is not to scale with Earth");
            assertTrue(SpaceTravel.SUN_RADIUS > body.radius(), "The Sun must outsize " + name);
            // Even the cube's far corner stays a full approach distance away from the Sun's surface.
            assertTrue(body.center().distanceTo(SpaceTravel.SUN_POSITION) - Math.sqrt(3) * body.radius() - SpaceTravel.SUN_RADIUS
                    >= SpaceTravel.PRELOAD_DISTANCE, name + " sits too close to the Sun");
        }
    }

    @Test void earthsSkyStaysDistant() {
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var jupiter = SpaceTravel.DESTINATIONS.getLast();
        // Angular diameter seen from Earth; large bodies at compressed distances otherwise crowd the sky.
        double sun = 2 * Math.toDegrees(Math.atan(SpaceTravel.SUN_RADIUS / earth.center().distanceTo(SpaceTravel.SUN_POSITION)));
        double giant = 2 * Math.toDegrees(Math.atan(jupiter.radius() / earth.center().distanceTo(jupiter.center())));
        assertTrue(sun < 20, "The Sun spans " + sun + " degrees of Earth's sky");
        assertTrue(giant < 10, "Jupiter spans " + giant + " degrees of Earth's sky");
    }

    /** A point given in the body's own (rotating, tilted) frame, placed in the world at that time. */
    private static Vec3 onBody(SpaceTravel.Destination body, double days, Vec3 offset) {
        var world = body.spin().orientation(days).transform(new org.joml.Vector3d(offset.x, offset.y, offset.z));
        return body.center().add(world.x, world.y, world.z);
    }

    @Test void cubeFacesEdgesAndCornersMatchTheVisibleArrivalBoundary() {
        var body = SpaceTravel.DESTINATIONS.getFirst();
        double r = body.radius();
        for (double days : new double[]{0, .13, .5, 7.7}) {
            assertEquals(-r, body.distance(body.center(), days), 1e-9);
            assertEquals(0, body.distance(onBody(body, days, new Vec3(r, r, r)), days), 1e-9);
            assertEquals(16, body.distance(onBody(body, days, new Vec3(r + 16, 0, 0)), days), 1e-9);
            assertEquals(20, body.distance(onBody(body, days, new Vec3(r + 12, r + 16, 0)), days), 1e-9);
            assertEquals(Math.sqrt(192), body.distance(onBody(body, days, new Vec3(-r - 8, r + 8, -r - 8)), days), 1e-9);
        }
    }

    @Test void bodiesSpinWithTheirRealPeriodsAndTilts() {
        // Sidereal rotation in Earth days (one Earth day per Minecraft day), axial tilt in degrees.
        double[][] real = {{0.99727, 23.44}, {0, 1.54}, {1.02596, 25.19}, {243.025, 177.36}, {58.646, 0.03}, {0.3781, 4}, {0.41354, 3.13}};
        for (int i = 0; i < real.length; i++) {
            var spin = SpaceTravel.DESTINATIONS.get(i).spin();
            assertEquals(real[i][0], spin.period(), 1e-6);
            var pole = spin.orientation(3.3).transform(new org.joml.Vector3d(0, 1, 0));
            assertEquals(real[i][1], Math.toDegrees(Math.acos(Math.clamp(pole.y, -1, 1))), 1e-6, "axial tilt of body " + i);
            if (spin.period() > 0) {
                var start = spin.orientation(5);
                var again = spin.orientation(5 + spin.period());
                assertTrue(start.equals(again, 1e-6), "body " + i + " must come round once per sidereal period");
                assertFalse(start.equals(spin.orientation(5 + spin.period() / 2), 1e-3));
            }
        }
        assertEquals(25.38, SpaceTravel.SUN_SPIN.period(), 1e-6);
        assertEquals(7.25, SpaceTravel.SUN_SPIN.tilt(), 1e-6);
    }

    @Test void moonIsTidallyLockedToEarthAndVenusSpinsBackwards() {
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var moon = SpaceTravel.DESTINATIONS.get(1);
        Vec3 toEarth = earth.center().subtract(moon.center()).normalize();
        for (double days = 0; days < 40; days += 1.7) {
            // The near side (longitude 0, the +X face of the cube net) always faces Earth.
            var nearSide = moon.spin().orientation(days).transform(new org.joml.Vector3d(1, 0, 0));
            assertTrue(nearSide.x * toEarth.x + nearSide.y * toEarth.y + nearSide.z * toEarth.z > Math.cos(Math.toRadians(2)));
        }
        // Seen from above the solar plane, Earth's surface turns counterclockwise and Venus's clockwise.
        assertTrue(eastwardSpin(earth) > 0, "Earth must spin prograde");
        assertTrue(eastwardSpin(SpaceTravel.DESTINATIONS.get(3)) < 0, "Venus must spin retrograde");
    }

    private static double eastwardSpin(SpaceTravel.Destination body) {
        var a = body.spin().orientation(1).transform(new org.joml.Vector3d(1, 0, 0));
        var b = body.spin().orientation(1 + body.spin().period() / 100).transform(new org.joml.Vector3d(1, 0, 0));
        // Angular velocity about world up: positive is counterclockwise seen from above.
        return a.z * b.x - a.x * b.z;
    }

    @Test void approachAndRejectedEntryStayOutsideEveryCubeFaceAndCorner() {
        var body = SpaceTravel.DESTINATIONS.getFirst();
        double r = body.radius();
        for (double days : new double[]{0, .21, 3.9})
            for (var offset : new Vec3[]{new Vec3(r + 16, 0, 0), new Vec3(-r - 12, r + 16, 0),
                    new Vec3(r + 8, r + 8, r + 8), new Vec3(0, 0, -r - 1), Vec3.ZERO, new Vec3(r - 1, r - 2, r - 3)})
                for (double altitude : new double[]{.6, 16, 32})
                    assertEquals(altitude, body.distance(body.approachPosition(onBody(body, days, offset), altitude, days), days), 1e-8);
    }

    @Test void cruisePreloadsTheBodyAheadBeforeTheNearApproachBand() {
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var moon = SpaceTravel.DESTINATIONS.get(1);
        Vec3 departure = earth.departurePosition();
        assertTrue(moon.distance(departure, 0) > SpaceTravel.PRELOAD_DISTANCE);
        assertEquals(moon, SpaceTravel.preloadTarget(departure, moon.center().subtract(departure).normalize()));
        assertEquals(earth, SpaceTravel.preloadTarget(departure, earth.center().subtract(departure).normalize()));
        assertNull(SpaceTravel.preloadTarget(departure, Vec3.ZERO));
        assertNull(SpaceTravel.preloadTarget(departure, new Vec3(1, 0, 0)));
        assertNull(SpaceTravel.preloadTarget(moon.center().add(0, 10000, 0), new Vec3(0, -1, 0)));
    }

    @Test void approachStartsBeforeThePlanetFillsTheViewAndHasTimeToPreload() {
        assertTrue(SpaceTravel.ENTRY_DISTANCE > SpaceTravel.boostSpeed(3) * 10);
        assertTrue(SpaceTravel.PRELOAD_DISTANCE - SpaceTravel.ENTRY_DISTANCE >= SpaceTravel.boostSpeed(3) * 40);
        for (var body : SpaceTravel.DESTINATIONS) {
            assertEquals(SpaceTravel.ENTRY_DISTANCE + 16,
                    body.distance(body.approachPosition(body.center(), SpaceTravel.ENTRY_DISTANCE + 16, 0), 0), 1e-8);
        }
    }

    @Test void arrivalsRestoreGc4HeightsWithoutDependingOnRenderDistance() {
        for (var dimension : java.util.List.of(GCDimensions.MOON, GCDimensions.MARS, GCDimensions.VENUS))
            assertEquals(900, SpaceTravel.landingHeight(dimension, 100));
        assertEquals(310, SpaceTravel.landingHeight(GCDimensions.ASTEROID, 128));
        assertEquals(250, SpaceTravel.landingHeight(net.minecraft.world.level.Level.OVERWORLD, 64));
        assertEquals(384, SpaceTravel.landingHeight(net.minecraft.world.level.Level.OVERWORLD, 320));
        assertEquals(364, SpaceTravel.landingHeight(GCDimensions.ASTEROID, 300));
        assertEquals(1014, SpaceTravel.landingHeight(GCDimensions.MOON, 950));
    }

    @Test void noseMatchesRocketPitchAndYawInEveryAxis() {
        assertEquals(0, SpaceTravel.forward(0, 0).distanceTo(new Vec3(0, 1, 0)), 1e-9);
        assertEquals(0, SpaceTravel.forward(90, 0).distanceTo(new Vec3(0, 0, 1)), 1e-9);
        assertEquals(0, SpaceTravel.forward(90, 90).distanceTo(new Vec3(-1, 0, 0)), 1e-9);
        assertEquals(0, SpaceTravel.forward(180, 0).distanceTo(new Vec3(0, -1, 0)), 1e-9);
        for (int pitch = -180; pitch <= 180; pitch += 15)
            for (int yaw = -360; yaw <= 360; yaw += 15) assertEquals(1, SpaceTravel.forward(pitch, yaw).length(), 1e-9);
    }

    @Test void boostAndBrakingHaveBoundedAccelerationAndStopExactly() {
        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 100; tick++) {
            Vec3 next = SpaceTravel.accelerate(velocity, new Vec3(0, 0, SpaceTravel.BOOST_SPEED));
            assertTrue(next.distanceTo(velocity) <= SpaceTravel.ACCELERATION + 1e-9);
            assertTrue(next.length() <= SpaceTravel.BOOST_SPEED);
            velocity = next;
        }
        assertEquals(SpaceTravel.BOOST_SPEED, velocity.z);
        for (int tick = 0; tick < 100; tick++) velocity = SpaceTravel.accelerate(velocity, Vec3.ZERO);
        assertEquals(Vec3.ZERO, velocity);
    }

    @Test void higherTierEnginesBoostSlightlyFasterButNeverPastTheEntryBand() {
        assertEquals(SpaceTravel.BOOST_SPEED, SpaceTravel.boostSpeed(1));
        assertEquals(10, SpaceTravel.boostSpeed(2));
        assertEquals(12, SpaceTravel.boostSpeed(3));
        assertEquals(SpaceTravel.boostSpeed(1), SpaceTravel.boostSpeed(0), "unknown engines fly as tier one");
        for (int tier = 1; tier <= 9; tier++) {
            // One tick of flight must never skip the atmospheric entry band.
            assertTrue(SpaceTravel.boostSpeed(tier) < SpaceTravel.ENTRY_DISTANCE);
            // Every tier reaches its top speed in the same time.
            Vec3 velocity = Vec3.ZERO;
            int ticks = 0;
            while (velocity.length() < SpaceTravel.boostSpeed(tier) - 1e-9 && ticks < 500) {
                Vec3 next = SpaceTravel.accelerate(velocity, new Vec3(0, 0, SpaceTravel.boostSpeed(tier)), SpaceTravel.acceleration(tier));
                assertTrue(next.distanceTo(velocity) <= SpaceTravel.acceleration(tier) + 1e-9);
                velocity = next;
                ticks++;
            }
            assertEquals(Math.ceil(SpaceTravel.BOOST_SPEED / SpaceTravel.ACCELERATION), ticks, 1, "tier " + tier);
        }
    }

    @Test void moonBoostFlightTakesUnderTwentySeconds() {
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var moon = SpaceTravel.DESTINATIONS.get(1);
        Vec3 position = earth.departurePosition(), velocity = Vec3.ZERO;
        int ticks = 0;
        while (moon.distance(position, 0) > SpaceTravel.ENTRY_DISTANCE && ticks < 2000) {
            velocity = SpaceTravel.accelerate(velocity, moon.center().subtract(position).normalize().scale(SpaceTravel.BOOST_SPEED));
            position = position.add(velocity);
            ticks++;
        }
        assertTrue(ticks < 400, "Earth to Moon boost flight should take under 20 seconds");
    }
}