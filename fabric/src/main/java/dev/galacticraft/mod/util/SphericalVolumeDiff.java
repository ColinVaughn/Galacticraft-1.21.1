/*
 * Copyright (c) 2026 colinvaughn
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

/** Iterates only the blocks that differ between two integer-radius spheres. */
public final class SphericalVolumeDiff {
    private SphericalVolumeDiff() {
    }

    public static void forEach(int oldRadiusSquared, int newRadiusSquared, BlockChange consumer) {
        if (oldRadiusSquared == newRadiusSquared) return;

        int radius = integerSqrt(Math.max(oldRadiusSquared, newRadiusSquared));
        for (int x = -radius; x <= radius; x++) {
            int xSquared = x * x;
            for (int y = -radius; y <= radius; y++) {
                int horizontalSquared = xSquared + y * y;
                int oldZ = extent(oldRadiusSquared, horizontalSquared);
                int newZ = extent(newRadiusSquared, horizontalSquared);
                if (oldZ == newZ) continue;

                int outer = Math.max(oldZ, newZ);
                int inner = Math.min(oldZ, newZ);
                boolean added = newZ > oldZ;
                for (int z = -outer; z <= outer; z++) {
                    if (Math.abs(z) > inner) consumer.accept(x, y, z, added);
                }
            }
        }
    }

    private static int extent(int radiusSquared, int horizontalSquared) {
        return horizontalSquared > radiusSquared ? -1 : integerSqrt(radiusSquared - horizontalSquared);
    }

    private static int integerSqrt(int value) {
        return value < 0 ? -1 : (int) Math.sqrt(value);
    }

    @FunctionalInterface
    public interface BlockChange {
        void accept(int x, int y, int z, boolean added);
    }
}
