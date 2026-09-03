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

final class RadarTrackingLogic {
    private static final int SINGLE_RADAR_UNCERTAINTY = 48;
    private static final int MINIMUM_UNCERTAINTY = 4;

    private RadarTrackingLogic() {
    }

    static int uncertaintyRadius(int linkedRadars) {
        return Math.max(MINIMUM_UNCERTAINTY,
                (int) Math.ceil(SINGLE_RADAR_UNCERTAINTY / Math.sqrt(Math.max(1, linkedRadars))));
    }

    static int estimatedCoordinate(double actualCoordinate, int uncertaintyRadius) {
        return (int) Math.round(actualCoordinate / uncertaintyRadius) * uncertaintyRadius;
    }
}
