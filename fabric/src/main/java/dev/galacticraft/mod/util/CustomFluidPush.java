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

/**
 * @author colinvaughn
 */
public final class CustomFluidPush {
    private static final double STATIONARY_MOVEMENT_THRESHOLD = 0.003D;
    private static final double MINIMUM_PUSH = 0.0045000000000000005D;

    private CustomFluidPush() {
    }

    public static Vec3 calculate(Vec3 flow, int samples, boolean player, Vec3 movement, double pushScale) {
        if (flow.length() <= 0.0D) return Vec3.ZERO;
        if (samples > 0) flow = flow.scale(1.0D / samples);
        if (!player) flow = flow.normalize();

        flow = flow.scale(pushScale);
        if (Math.abs(movement.x) < STATIONARY_MOVEMENT_THRESHOLD
                && Math.abs(movement.z) < STATIONARY_MOVEMENT_THRESHOLD
                && flow.length() < MINIMUM_PUSH) {
            flow = flow.normalize().scale(MINIMUM_PUSH);
        }
        return flow;
    }
}
