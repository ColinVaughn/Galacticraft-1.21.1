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
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Deterministic cosmetic asteroid field between Mars and Jupiter, with no collision or network sync.
 * Density peaks at the belt center and leaves clear lanes at the Kirkwood gaps.
 */
public final class AsteroidBeltField {
    public static final double INNER = 14_000, OUTER = 19_500, HALF_THICKNESS = 800;
    /** Space kept around every planet's cube, so nothing crowds the asteroid destination or an arrival. */
    public static final double CLEARANCE = 300;
    /** Kirkwood gaps (3:1, 5:2, 7:3 and 2:1 resonances with Jupiter), mapped from 2.1-3.3 AU onto the belt. */
    private static final double[] GAPS = {15_833, 17_300, 17_896, 19_363};
    private static final int SMALL_CELL = 128, LARGE_CELL = 512;
    private static final Vec3 SUN = SpaceTravel.SUN_POSITION;
    private static volatile List<SpaceTravel.Destination> clearanceBodies = SpaceTravel.DESTINATIONS;
    public static synchronized void configure(List<SpaceTravel.Destination> bodies) {
        if (!clearanceBodies.equals(bodies)) { clearanceBodies = List.copyOf(bodies); large = null; }
    }
    private static volatile List<Cube> large;
    private static volatile boolean warming;

    /** One cube of a rock, centred at (x, y, z) with the given half-width; texture indexes the renderer's variants. */
    public record Cube(double x, double y, double z, double half, int texture) { }

    private AsteroidBeltField() { }

    public static double density(Vec3 p) {
        return density(p.x, p.y, p.z);
    }

    /** Relative rock density at a point, 0 (empty) to about 1 (belt core). */
    public static double density(double x, double y, double z) {
        double r = Math.hypot(x - SUN.x, z - SUN.z), h = y - SUN.y;
        if (r <= INNER || r >= OUTER || Math.abs(h) >= HALF_THICKNESS) return 0;
        double t = (r - INNER) / (OUTER - INNER);
        double d = smooth(t / .12) * smooth((1 - t) / .15) * (.45 + .55 * Math.sin(Math.PI * t));
        for (double gap : GAPS) d *= 1 - .9 * Math.exp(-square((r - gap) / 110));
        d *= Math.exp(-square(h / (HALF_THICKNESS * .5)));
        for (var body : clearanceBodies) {
            double clear = body.radius() * Math.sqrt(3) + CLEARANCE;
            Vec3 c = body.center();
            d *= smooth((Math.sqrt(square(x - c.x) + square(y - c.y) + square(z - c.z)) - clear) / CLEARANCE);
        }
        return d;
    }

    /** Small rocks in the cells around a point, for close-up rendering. */
    public static void near(Vec3 center, double radius, Consumer<Cube> out) {
        cells(center, radius, SMALL_CELL, out);
    }

    /** Every large rock in the belt; they stay visible from anywhere in the solar system. Computed once (about half a second). */
    public static synchronized List<Cube> large() {
        if (large == null) {
            List<Cube> rocks = new ArrayList<>();
            cells(SUN, OUTER, LARGE_CELL, rocks::add);
            large = List.copyOf(rocks);
        }
        return large;
    }

    /** Starts building the large rocks off the render thread; safe to call repeatedly. */
    public static synchronized void warm() {
        if (large == null && !warming) {
            warming = true;
            // A sample near-field query also lets the JIT compile it here, not on the first flight frame.
            CompletableFuture.runAsync(() -> {
                try {
                    large();
                    near(SUN.add(16_000, 0, 0), 600, cube -> { });
                } finally {
                    synchronized (AsteroidBeltField.class) { warming = false; }
                }
            }, Util.backgroundExecutor());
        }
    }

    /** The large rocks if they are built, otherwise none (and the build is started); never blocks a frame. */
    public static List<Cube> largeIfReady() {
        List<Cube> ready = large;
        if (ready == null) warm();
        return ready == null ? List.of() : ready;
    }

    private static void cells(Vec3 center, double radius, int size, Consumer<Cube> out) {
        int y0 = Math.floorDiv((int) Math.floor(Math.max(center.y - radius, SUN.y - HALF_THICKNESS)), size);
        int y1 = Math.floorDiv((int) Math.floor(Math.min(center.y + radius, SUN.y + HALF_THICKNESS)), size);
        int x0 = Math.floorDiv((int) Math.floor(center.x - radius), size), x1 = Math.floorDiv((int) Math.floor(center.x + radius), size);
        int z0 = Math.floorDiv((int) Math.floor(center.z - radius), size), z1 = Math.floorDiv((int) Math.floor(center.z + radius), size);
        double reach = radius + size * .87;
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) {
            double mx = (cx + .5) * size, mz = (cz + .5) * size, orbit = Math.hypot(mx - SUN.x, mz - SUN.z);
            if (orbit < INNER - size || orbit > OUTER + size) continue;
            for (int cy = y0; cy <= y1; cy++) {
                double my = (cy + .5) * size;
                if (square(mx - center.x) + square(my - center.y) + square(mz - center.z) > reach * reach) continue;
                cell(cx, cy, cz, size, out);
            }
        }
    }

    private static void cell(int cx, int cy, int cz, int size, Consumer<Cube> out) {
        boolean large = size == LARGE_CELL;
        var random = new SplittableRandom(cx * 0x9E3779B97F4A7C15L ^ cy * 0xC2B2AE3D27D4EB4FL ^ cz * 0x165667B19E3779F9L ^ size);
        for (int attempt = 0; attempt < (large ? 3 : 2); attempt++) {
            double x = (cx + random.nextDouble()) * size, y = (cy + random.nextDouble()) * size, z = (cz + random.nextDouble()) * size;
            double half = large ? 6 + random.nextDouble() * 8 : 1 + square(random.nextDouble()) * 4;
            double roll = random.nextDouble();
            int texture = roll < .06 ? 3 : roll < .1 ? 4 : random.nextInt(3);
            int companions = random.nextInt(3);
            if (random.nextDouble() >= density(x, y, z) * (large ? .5 : .7)) continue;
            out.accept(new Cube(x, y, z, half, texture));
            // Companion cubes give lumpy, irregular silhouettes.
            for (int i = 0; i < companions; i++) {
                double part = half * (.45 + random.nextDouble() * .4);
                out.accept(new Cube(x + (random.nextDouble() * 2 - 1) * half, y + (random.nextDouble() * 2 - 1) * half,
                        z + (random.nextDouble() * 2 - 1) * half, part, texture));
            }
        }
    }

    private static double smooth(double t) {
        t = Math.min(1, Math.max(0, t));
        return t * t * (3 - 2 * t);
    }

    private static double square(double v) {
        return v * v;
    }
}
