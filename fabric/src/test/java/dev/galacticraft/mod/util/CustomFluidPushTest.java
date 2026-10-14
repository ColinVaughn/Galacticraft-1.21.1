/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomFluidPushTest {
    @Test void shallowSamplesUseRunningMaximumDepth() {
        var samples = new CustomFluidPush.Accumulator();
        samples.add(0.3, new Vec3(1, 0, 0), true);
        samples.add(0.1, new Vec3(0, 0, 1), true);
        assertTrue(samples.isPresent());
        assertEquals(0.3, samples.height(), 1.0E-12);
        assertEquals(new Vec3(0.3, 0, 0.3), samples.flow());
        assertEquals(2, samples.samples());
        samples.add(0.4, new Vec3(1, 0, 0), true);
        samples.add(0.1, new Vec3(0, 0, 1), true);
        assertEquals(new Vec3(1.3, 0, 1.3), samples.flow());
    }

    @Test void unpushedEntitiesStillRecordContactAndDepth() {
        var samples = new CustomFluidPush.Accumulator();
        samples.add(0.7, new Vec3(1, 0, 1), false);
        assertTrue(samples.isPresent());
        assertEquals(0.7, samples.height());
        assertEquals(Vec3.ZERO, samples.flow());
        assertEquals(0, samples.samples());
    }

    @Test void contactAtSurfaceIsPresentEvenWithZeroDepth() {
        var samples = new CustomFluidPush.Accumulator();
        samples.add(0, new Vec3(1, 0, 0), true);
        assertTrue(samples.isPresent());
        assertEquals(0, samples.height());
        assertEquals(Vec3.ZERO, samples.flow());
        assertEquals(1, samples.samples());
    }

    @Test void separateFluidsKeepTheirOwnDepthAndSampleCount() {
        var oil = new CustomFluidPush.Accumulator();
        var fuel = new CustomFluidPush.Accumulator();
        oil.add(0.6, new Vec3(1, 0, 0), true);
        fuel.add(0.2, new Vec3(0, 0, 1), true);
        fuel.add(0.1, new Vec3(0, 0, 1), true);
        assertEquals(new Vec3(1, 0, 0), oil.flow());
        assertEquals(new Vec3(0, 0, 0.4), fuel.flow());
        assertEquals(1, oil.samples());
        assertEquals(2, fuel.samples());
    }

    @Test void pushingPreservesPlayerAveragingAndNonPlayerNormalization() {
        Vec3 movement = new Vec3(0.01, 0, 0);
        assertEquals(new Vec3(0.0014, 0, 0), CustomFluidPush.calculate(new Vec3(1, 0, 0), 2, true, movement, 0.0028));
        assertEquals(new Vec3(0.0028, 0, 0), CustomFluidPush.calculate(new Vec3(1, 0, 0), 2, false, movement, 0.0028));
        assertEquals(Vec3.ZERO, CustomFluidPush.calculate(Vec3.ZERO, 0, false, Vec3.ZERO, 0.0028));
        assertEquals(new Vec3(0.0045000000000000005, 0, 0), CustomFluidPush.calculate(new Vec3(1, 0, 0), 1, true, Vec3.ZERO, 0.0028));
    }
}
