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

package dev.galacticraft.mod.client.render.entity;

import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit.Box;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit.Helmet;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit.Tanks;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fitted space gear is the stock mask and tank cubes, scaled and placed from the shape of a
 * mob's own head and body. Model space here is vanilla's: pixels, Y pointing down, front at -Z.
 */
class SpaceGearFitTest {
    private static final float EPS = 0.0001F;
    private static final Matrix3f UPRIGHT = new Matrix3f();

    /** The vanilla humanoid head and body, which the hand-tuned gear was drawn around. */
    private static final Box HUMANOID_HEAD = new Box(-4, -8, -4, 4, 0, 4);
    private static final Box HUMANOID_BODY = new Box(-4, 0, -2, 4, 12, 2);
    /** Vanilla's quadruped body: modelled standing up, then tipped forward a quarter turn. */
    private static final Box COW_BODY = new Box(-6, -10, -7, 6, 8, 3);
    private static final Matrix3f TIPPED_FORWARD = new Matrix3f().rotationX(Mth.HALF_PI);

    @Test
    void aHumanoidHeadGetsExactlyTheStockHelmet() {
        Helmet helmet = SpaceGearFit.helmet(HUMANOID_HEAD).orElseThrow();
        assertBox(new Box(-5, -9, -5, 5, 1, 5), helmet.bounds());
        assertEquals(1.0F, helmet.scaleX(), EPS);
        assertEquals(1.0F, helmet.scaleY(), EPS);
        assertEquals(1.0F, helmet.scaleZ(), EPS);
    }

    @Test
    void aTinyHeadIsStillEnclosed() {
        Box head = new Box(-1, -2, -1, 1, 0, 1);
        Helmet helmet = SpaceGearFit.helmet(head).orElseThrow();
        assertTrue(helmet.bounds().encloses(head), () -> helmet.bounds() + " does not enclose " + head);
        assertTrue(helmet.scaleX() < 1.0F, "a tiny head should get a smaller helmet");
    }

    @Test
    void aHugeLopsidedHeadIsStillEnclosed() {
        Box head = new Box(-10, -12, -8, 10, 0, 8);
        Helmet helmet = SpaceGearFit.helmet(head).orElseThrow();
        assertTrue(helmet.bounds().encloses(head), () -> helmet.bounds() + " does not enclose " + head);
    }

    @Test
    void aMissingHeadGetsNoHelmet() {
        assertTrue(SpaceGearFit.helmet(null).isEmpty());
        assertTrue(SpaceGearFit.helmet(new Box(0, 0, 0, 0, 0, 0)).isEmpty());
    }

    @Test
    void anUprightBodyGetsTheStockTanksOnItsBack() {
        Tanks tanks = SpaceGearFit.tanks(HUMANOID_BODY, UPRIGHT).orElseThrow();
        assertEquals(1.0F, tanks.scale(), EPS);
        Box right = tanks.bounds(0);
        Box left = tanks.bounds(1);
        Box pair = right.union(left);
        // The hand-tuned layer drew them at x -4..4, y 1..9, z 2.01..6.01.
        assertBox(new Box(-4, 1, 2.01F, 4, 9, 6.01F), pair);
        assertEquals(4.0F, right.maxX() - right.minX(), EPS);
        assertFalse(right.overlaps(left), "the two tanks should sit side by side, not inside each other");
    }

    @Test
    void aTippedQuadrupedBodyGetsTanksOnTop() {
        Tanks tanks = SpaceGearFit.tanks(COW_BODY, TIPPED_FORWARD).orElseThrow();
        Box body = rotated(COW_BODY, TIPPED_FORWARD);
        for (int i = 0; i < 2; i++) {
            Box tank = rotated(tanks.bounds(i), TIPPED_FORWARD);
            // Y points down, so "on top" means the tank ends where the body begins.
            assertTrue(tank.maxY() <= body.minY() + EPS, "tank " + i + " " + tank + " is not on top of " + body);
        }
    }

    @Test
    void aFlatQuadrupedBodyGetsTanksOnTop() {
        Box body = new Box(-4, -4, -8, 4, 4, 8);
        Tanks tanks = SpaceGearFit.tanks(body, UPRIGHT).orElseThrow();
        for (int i = 0; i < 2; i++) {
            Box tank = tanks.bounds(i);
            assertTrue(tank.maxY() <= body.minY() + EPS, "tank " + i + " " + tank + " is not on top of " + body);
            assertTrue(tank.maxZ() - tank.minZ() > tank.maxX() - tank.minX(),
                    "on a long body the tanks should lie along it, but tank " + i + " is " + tank);
        }
    }

    /** An iron golem's chest is wider than it is tall; tanks on a back still stand upright. */
    @Test
    void aWideUprightBodyStillGetsUprightTanks() {
        Tanks tanks = SpaceGearFit.tanks(new Box(-9, -2, -6, 9, 10, 5), UPRIGHT).orElseThrow();
        for (int i = 0; i < 2; i++) {
            Box tank = tanks.bounds(i);
            assertTrue(tank.size(1) > tank.size(0), "tank " + i + " " + tank + " lies on its side across a wide back");
        }
    }

    @Test
    void tankScaleIsClampedAtBothEnds() {
        assertEquals(SpaceGearFit.MIN_TANK_SCALE, SpaceGearFit.tanks(new Box(-1, 0, -1, 1, 2, 1), UPRIGHT).orElseThrow().scale(), EPS);
        assertEquals(SpaceGearFit.MAX_TANK_SCALE, SpaceGearFit.tanks(new Box(-30, 0, -10, 30, 80, 10), UPRIGHT).orElseThrow().scale(), EPS);
    }

    @Test
    void tankTransformsAreProperRotations() {
        // A mirrored transform turns the cubes inside out, which flips their lighting.
        for (Matrix3f rest : new Matrix3f[]{UPRIGHT, TIPPED_FORWARD, new Matrix3f().rotationY(Mth.HALF_PI)}) {
            Tanks tanks = SpaceGearFit.tanks(COW_BODY, rest).orElseThrow();
            for (int i = 0; i < 2; i++) {
                Matrix3f basis = new Matrix3f();
                tanks.transform(i).get3x3(basis);
                assertTrue(basis.determinant() > 0, "tank " + i + " is mirrored under rest pose " + rest);
            }
        }
    }

    @Test
    void aMissingBodyGetsNoTanks() {
        assertTrue(SpaceGearFit.tanks(null, UPRIGHT).isEmpty());
    }

    private static Box rotated(Box box, Matrix3f rotation) {
        Box out = null;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f p = new Vector3f(
                    (corner & 1) == 0 ? box.minX() : box.maxX(),
                    (corner & 2) == 0 ? box.minY() : box.maxY(),
                    (corner & 4) == 0 ? box.minZ() : box.maxZ());
            rotation.transform(p);
            Box point = new Box(p.x, p.y, p.z, p.x, p.y, p.z);
            out = out == null ? point : out.union(point);
        }
        return out;
    }

    private static void assertBox(Box expected, Box actual) {
        String message = "expected " + expected + " but was " + actual;
        assertEquals(expected.minX(), actual.minX(), EPS, message);
        assertEquals(expected.minY(), actual.minY(), EPS, message);
        assertEquals(expected.minZ(), actual.minZ(), EPS, message);
        assertEquals(expected.maxX(), actual.maxX(), EPS, message);
        assertEquals(expected.maxY(), actual.maxY(), EPS, message);
        assertEquals(expected.maxZ(), actual.maxZ(), EPS, message);
    }
}
