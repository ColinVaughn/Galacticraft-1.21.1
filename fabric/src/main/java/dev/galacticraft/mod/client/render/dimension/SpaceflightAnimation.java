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

/** Presentation timing never delays destination readiness or changes the physical rocket. */
public final class SpaceflightAnimation {
    public static final float REVEAL_SECONDS = .45F;

    private SpaceflightAnimation() { }

    public static float reveal(float secondsReady) {
        float progress = Math.clamp(secondsReady / REVEAL_SECONDS, 0, 1);
        return progress * progress * (3 - 2 * progress);
    }

    public static double approachAltitude(double start, double radius, double seconds, float reveal) {
        // Close the empty approach quickly, then keep magnifying the surface without crossing its near plane.
        double near = Math.max(.12, radius * .002);
        double travel = 1 + Math.max(0, seconds) * 2;
        return near + Math.max(0, start - near) / (travel * travel) * (1 - .85 * reveal);
    }
}