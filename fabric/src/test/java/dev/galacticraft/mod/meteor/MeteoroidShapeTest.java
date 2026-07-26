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

package dev.galacticraft.mod.meteor;

import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seed-only sync depends entirely on this generator being deterministic and centre-ordered:
 * if the client and server disagreed on a single voxel, the body would render wrong, and if the
 * ordering were not centre-out, ablation would eat holes in the middle instead of the surface.
 */
class MeteoroidShapeTest {
    @Test
    void sameSeedAndSizeProduceIdenticalBodies() {
        List<MeteoroidShape.Voxel> first = MeteoroidShape.build(0x5EED, 5);
        List<MeteoroidShape.Voxel> second = MeteoroidShape.build(0x5EED, 5);
        assertEquals(first, second, "client and server must derive byte-identical bodies");
    }

    @Test
    void differentSeedsProduceDifferentBodies() {
        List<MeteoroidShape.Voxel> a = MeteoroidShape.build(1, 5);
        List<MeteoroidShape.Voxel> b = MeteoroidShape.build(2, 5);
        assertFalse(a.equals(b), "distinct seeds should give distinct shapes");
    }

    @Test
    void voxelsAreOrderedFromTheCentreOutwards() {
        for (int seed = 0; seed < 40; seed++) {
            List<MeteoroidShape.Voxel> voxels = MeteoroidShape.build(seed, 6);
            int previous = -1;
            for (MeteoroidShape.Voxel voxel : voxels) {
                assertTrue(voxel.distanceSquared() >= previous,
                        "ablation erodes a prefix, so voxels must be sorted centre-out");
                previous = voxel.distanceSquared();
            }
        }
    }

    @Test
    void bodiesContainNoDuplicatePositions() {
        for (int seed = 0; seed < 40; seed++) {
            List<MeteoroidShape.Voxel> voxels = MeteoroidShape.build(seed, 7);
            Set<Long> seen = new HashSet<>();
            for (MeteoroidShape.Voxel voxel : voxels) {
                long key = (((long) voxel.x() & 0xFFFF) << 32) | (((long) voxel.y() & 0xFFFF) << 16) | ((long) voxel.z() & 0xFFFF);
                assertTrue(seen.add(key), "each block position may appear only once");
            }
        }
    }

    @Test
    void everySizeProducesANonEmptyBodyWithinTheVoxelCap() {
        for (int size = MeteoroidShape.MIN_SIZE; size <= MeteoroidShape.MAX_SIZE; size++) {
            for (int seed = 0; seed < 25; seed++) {
                List<MeteoroidShape.Voxel> voxels = MeteoroidShape.build(seed, size);
                assertFalse(voxels.isEmpty(), "size " + size + " seed " + seed + " produced nothing");
                assertTrue(voxels.size() <= MeteoroidShape.MAX_VOXELS,
                        "size " + size + " exceeded the voxel cap with " + voxels.size());
            }
        }
    }

    @Test
    void largerSizeClassesProduceLargerBodiesOnAverage() {
        assertTrue(averageVoxels(7) > averageVoxels(2), "size should meaningfully drive volume");
        assertTrue(MeteoroidShape.radiusFor(8) > MeteoroidShape.radiusFor(1));
    }

    @Test
    void bodiesAreLumpyRatherThanPerfectSpheres() {
        // A perfect sphere has a single distance shell at its surface; a lumpy body has voxels
        // present at one distance and absent at a nearer one.
        int irregular = 0;
        for (int seed = 0; seed < 30; seed++) {
            List<MeteoroidShape.Voxel> voxels = MeteoroidShape.build(seed, 6);
            Set<Long> present = new HashSet<>();
            for (MeteoroidShape.Voxel voxel : voxels) {
                present.add((((long) voxel.x() & 0xFFFF) << 32) | (((long) voxel.y() & 0xFFFF) << 16) | ((long) voxel.z() & 0xFFFF));
            }
            int maxDistance = voxels.get(voxels.size() - 1).distanceSquared();
            for (MeteoroidShape.Voxel voxel : voxels) {
                if (voxel.distanceSquared() < maxDistance) continue;
                // A voxel at maximum distance whose mirror is missing means the body is asymmetric.
                long mirror = (((long) -voxel.x() & 0xFFFF) << 32) | (((long) -voxel.y() & 0xFFFF) << 16) | ((long) -voxel.z() & 0xFFFF);
                if (!present.contains(mirror)) {
                    irregular++;
                    break;
                }
            }
        }
        assertTrue(irregular > 0, "at least some bodies should be visibly irregular");
    }

    @Test
    void ablationKeepsAShrinkingPrefixOfTheBody() {
        assertEquals(100, MeteoroidShape.survivingVoxels(100, 1.0f));
        assertEquals(50, MeteoroidShape.survivingVoxels(100, 0.5f));
        assertEquals(0, MeteoroidShape.survivingVoxels(100, 0.0f));
        assertEquals(0, MeteoroidShape.survivingVoxels(0, 1.0f));
    }

    @Test
    void aBodyWithAnySurvivingMassKeepsAtLeastOneBlock() {
        assertEquals(1, MeteoroidShape.survivingVoxels(100, 0.0001f),
                "a body that has not fully burned out must still render something");
    }

    @Test
    void survivingCountDecreasesMonotonicallyAsMassIsLost() {
        int previous = Integer.MAX_VALUE;
        for (float fraction = 1.0f; fraction >= 0.0f; fraction -= 0.05f) {
            int surviving = MeteoroidShape.survivingVoxels(200, fraction);
            assertTrue(surviving <= previous, "erosion must never add blocks back");
            previous = surviving;
        }
    }

    private static double averageVoxels(int size) {
        int total = 0;
        for (int seed = 0; seed < 30; seed++) {
            total += MeteoroidShape.build(seed, size).size();
        }
        return total / 30.0;
    }
}
