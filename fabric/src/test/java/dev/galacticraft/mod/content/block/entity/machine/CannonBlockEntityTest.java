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

package dev.galacticraft.mod.content.block.entity.machine;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CannonBlockEntityTest {
    @Test
    void firesOnlyAfterLockWithRangeAndLineOfSight() {
        assertTrue(CannonBlockEntity.readyToFire(100.0, true, 24, 24, 0, true));
        assertFalse(CannonBlockEntity.readyToFire(100.0, false, 24, 24, 0, true));
        assertFalse(CannonBlockEntity.readyToFire(100.0, true, 23, 24, 0, true));
        assertFalse(CannonBlockEntity.readyToFire(100.0, true, 24, 24, 1, true));
        assertFalse(CannonBlockEntity.readyToFire(100.0, true, 24, 24, 0, false));
        assertFalse(CannonBlockEntity.readyToFire((CannonBlockEntity.RANGE + 1.0)
                * (CannonBlockEntity.RANGE + 1.0), true, 24, 24, 0, true));
    }

    @Test
    void lockTimeUsesRadarAccuracyAndLeavesTimeForImpact() {
        assertEquals(64, CannonBlockEntity.requiredLockTicks(48, 200));
        assertEquals(52, CannonBlockEntity.requiredLockTicks(24, 200));
        assertEquals(10, CannonBlockEntity.requiredLockTicks(48, 20));
    }

    @Test
    void shotTravelsBeforeReachingItsTarget() {
        assertEquals(new Vec3(24.0, 0.0, 0.0),
                CannonBlockEntity.advanceShot(Vec3.ZERO, new Vec3(100.0, 0.0, 0.0)));
        assertEquals(new Vec3(10.0, 0.0, 0.0),
                CannonBlockEntity.advanceShot(Vec3.ZERO, new Vec3(10.0, 0.0, 0.0)));
    }
}
