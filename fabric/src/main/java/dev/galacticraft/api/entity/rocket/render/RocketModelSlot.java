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

package dev.galacticraft.api.entity.rocket.render;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Stock rocket slot dimensions in model units (16 per block). Custom layouts shift the mount
 * positions; use {@link dev.galacticraft.api.rocket.RocketGeometry} for an assembled rocket.
 */
public enum RocketModelSlot {
    ENGINE(7, 8, 0), BOOSTER(15, 0, 0), FIN(15, 16, 40), BODY(31, 28, 0), CONE(59, 25, 0);

    private final double y;
    private final float defaultHeight;
    private final float defaultWidth;

    RocketModelSlot(int modelY, float defaultHeight, float defaultWidth) {
        this.y = modelY / 16.0;
        this.defaultHeight = defaultHeight;
        this.defaultWidth = defaultWidth;
    }

    /** The stock mount height, in blocks. */
    public double y() { return y; }

    /** The extent of a stock part in this slot along the rocket's axis, in model units. */
    public float defaultHeight() { return defaultHeight; }

    /** The extent of a stock part in this slot across the rocket, in model units; 0 if it does not set the hitbox. */
    public float defaultWidth() { return defaultWidth; }

    /** Translates to the stock mount height. Call inside a separate push/pop pair for each part. */
    public void mount(PoseStack matrices) { matrices.translate(0, y, 0); }
}
