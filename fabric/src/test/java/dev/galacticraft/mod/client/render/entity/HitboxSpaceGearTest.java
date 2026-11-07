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

import dev.galacticraft.mod.client.render.entity.feature.spacegear.HitboxSpaceGear;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit;
import org.joml.Matrix3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fallback only knows a mob's hitbox, so its tanks must never end up hidden inside the mob. */
class HitboxSpaceGearTest {
    /** A slime fills its hitbox; tanks mounted inside it are invisible. */
    @Test
    void tanksStandOutBehindAMobThatFillsItsHitbox() {
        float side = 0.52F * 16;
        SpaceGearFit.Tanks tanks = SpaceGearFit.tanks(HitboxSpaceGear.body(side, side), new Matrix3f()).orElseThrow();
        for (int tank = 0; tank < 2; tank++) {
            SpaceGearFit.Box box = tanks.bounds(tank);
            assertTrue(box.maxZ() > side / 2, "tank " + tank + " " + box + " is buried inside a " + side + "px hitbox");
        }
    }

    @Test
    void tanksStandOutAboveAFlatMob() {
        float width = 2.0F * 16;
        float height = 0.6F * 16;
        SpaceGearFit.Tanks tanks = SpaceGearFit.tanks(HitboxSpaceGear.body(width, height), new Matrix3f()).orElseThrow();
        // Model space has Y pointing down from 24 pixels above the feet, so the hitbox top is at 24 - height.
        for (int tank = 0; tank < 2; tank++) {
            SpaceGearFit.Box box = tanks.bounds(tank);
            assertTrue(box.minY() < 24 - height, "tank " + tank + " " + box + " is buried inside a flat mob");
        }
    }
}
