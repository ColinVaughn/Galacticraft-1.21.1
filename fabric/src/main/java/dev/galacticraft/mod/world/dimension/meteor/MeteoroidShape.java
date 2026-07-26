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

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the block body of a meteoroid from a seed, deterministically enough that the client and
 * the server independently produce byte-identical results from the same {@code (seed, size)} pair.
 * That is what lets the entity sync a seed instead of a voxel list.
 *
 * <p>Shapes are a union of overlapping lobes inside an anisotropic envelope, so bodies come out
 * lumpy and elongated rather than as tidy spheres. Voxels are returned sorted from the centre
 * outwards, which makes ablation trivial for both sides: keep the first
 * {@code ceil(count * massFraction)} entries and the body visibly erodes from the outside in.
 *
 * <p>Free of world state so it can be unit tested directly.
 */
public final class MeteoroidShape {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 8;
    /** Hard ceiling on voxels per body, so the largest meteor still renders cheaply. */
    public static final int MAX_VOXELS = 320;

    /**
     * One block of a meteoroid body.
     *
     * @param x    offset from the body centre
     * @param y    offset from the body centre
     * @param z    offset from the body centre
     * @param roll a stable per-voxel value in {@code [0, 127]} that {@link MeteoroidPalette}
     *             turns into a block state; kept opaque here so shape generation stays
     *             independent of any particular material
     */
    public record Voxel(int x, int y, int z, byte roll) {
        public int distanceSquared() {
            return this.x * this.x + this.y * this.y + this.z * this.z;
        }
    }

    private MeteoroidShape() {
    }

    /** Envelope radius in blocks for a size class. */
    public static double radiusFor(int size) {
        return 0.55 + Mth.clamp(size, MIN_SIZE, MAX_SIZE) * 0.45;
    }

    /**
     * Generates the body for a seed and size class. Always returns at least one voxel, so a
     * meteoroid is never empty.
     */
    public static List<Voxel> build(int seed, int size) {
        RandomSource random = RandomSource.create(seed);
        double radius = radiusFor(size);

        // Anisotropic envelope, renormalised so stretching one axis does not inflate the volume.
        double stretchX = 0.75 + random.nextDouble() * 0.6;
        double stretchY = 0.75 + random.nextDouble() * 0.6;
        double stretchZ = 0.75 + random.nextDouble() * 0.6;
        double norm = Math.cbrt(stretchX * stretchY * stretchZ);
        stretchX /= norm;
        stretchY /= norm;
        stretchZ /= norm;

        // A core plus a handful of off-centre bulges. Their union is what makes the body irregular.
        int lobeCount = 3 + random.nextInt(4);
        double[] lobes = new double[lobeCount * 4];
        lobes[0] = 0.0;
        lobes[1] = 0.0;
        lobes[2] = 0.0;
        lobes[3] = radius * 0.82;
        for (int i = 1; i < lobeCount; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(1.0 - 2.0 * random.nextDouble());
            double offset = radius * (0.25 + random.nextDouble() * 0.45);
            lobes[i * 4] = Math.sin(phi) * Math.cos(theta) * offset;
            lobes[i * 4 + 1] = Math.cos(phi) * offset;
            lobes[i * 4 + 2] = Math.sin(phi) * Math.sin(theta) * offset;
            lobes[i * 4 + 3] = radius * (0.35 + random.nextDouble() * 0.35);
        }

        int bound = Mth.ceil(radius * 1.7) + 1;
        List<Voxel> voxels = new ArrayList<>();
        for (int x = -bound; x <= bound; x++) {
            for (int y = -bound; y <= bound; y++) {
                for (int z = -bound; z <= bound; z++) {
                    double px = x / stretchX;
                    double py = y / stretchY;
                    double pz = z / stretchZ;
                    if (!insideAnyLobe(lobes, lobeCount, px, py, pz)) continue;
                    voxels.add(new Voxel(x, y, z, (byte) (materialHash(seed, x, y, z) & 0x7F)));
                }
            }
        }

        if (voxels.isEmpty()) {
            voxels.add(new Voxel(0, 0, 0, (byte) (materialHash(seed, 0, 0, 0) & 0x7F)));
        }

        // Centre-out ordering: ablation keeps a prefix, so the outermost blocks burn away first.
        voxels.sort((a, b) -> {
            int byDistance = Integer.compare(a.distanceSquared(), b.distanceSquared());
            if (byDistance != 0) return byDistance;
            int byY = Integer.compare(a.y(), b.y());
            if (byY != 0) return byY;
            int byX = Integer.compare(a.x(), b.x());
            return byX != 0 ? byX : Integer.compare(a.z(), b.z());
        });

        return voxels.size() > MAX_VOXELS ? new ArrayList<>(voxels.subList(0, MAX_VOXELS)) : voxels;
    }

    /** How many of a body's voxels remain at the given mass fraction; never drops below one. */
    public static int survivingVoxels(int totalVoxels, float massFraction) {
        if (totalVoxels <= 0) return 0;
        if (massFraction >= 1.0f) return totalVoxels;
        if (massFraction <= 0.0f) return 0;
        return Math.max(1, Mth.ceil(totalVoxels * massFraction));
    }

    private static boolean insideAnyLobe(double[] lobes, int lobeCount, double px, double py, double pz) {
        for (int i = 0; i < lobeCount; i++) {
            double dx = px - lobes[i * 4];
            double dy = py - lobes[i * 4 + 1];
            double dz = pz - lobes[i * 4 + 2];
            double lobeRadius = lobes[i * 4 + 3];
            if (dx * dx + dy * dy + dz * dz <= lobeRadius * lobeRadius) return true;
        }
        return false;
    }

    /**
     * Position-keyed hash, so a voxel's material depends only on where it sits in the body and not
     * on iteration order — the client and server agree without exchanging anything.
     */
    private static int materialHash(int seed, int x, int y, int z) {
        int hash = seed * 0x27D4EB2D;
        hash = (hash ^ (x * 0x9E3779B1)) * 0x85EBCA6B;
        hash = (hash ^ (y * 0xC2B2AE35)) * 0x27D4EB2F;
        hash = (hash ^ (z * 0x165667B1)) * 0x2545F491;
        return hash ^ (hash >>> 15);
    }
}
