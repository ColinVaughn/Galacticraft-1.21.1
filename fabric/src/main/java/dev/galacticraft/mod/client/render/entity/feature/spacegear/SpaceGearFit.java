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

package dev.galacticraft.mod.client.render.entity.feature.spacegear;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Fits stock mask and tank geometry without rebuilding cubes, preserving their UVs.
 * Coordinates are vanilla model-space pixels: Y down, front toward -Z.
 */
public final class SpaceGearFit {
    /** Edge length of the stock mask cube, in pixels. */
    public static final float HELMET_SIZE = 10.0F;
    /** How far the helmet stands off the head on every side; the stock mask leaves one pixel. */
    public static final float HELMET_MARGIN = 1.0F;
    /** The stock tank: {@code TANK_WIDTH} across, {@code TANK_LENGTH} long, {@code TANK_WIDTH} deep. */
    public static final float TANK_WIDTH = 4.0F;
    public static final float TANK_LENGTH = 8.0F;
    /** Width of the stock pair, which spans the 8-pixel humanoid back exactly. */
    public static final float PAIR_WIDTH = 2 * TANK_WIDTH;
    public static final float MIN_TANK_SCALE = 0.5F;
    public static final float MAX_TANK_SCALE = 2.0F;
    /** Gap between tank and body, so the faces never z-fight. */
    private static final float STANDOFF = 0.01F;
    /** Share of the spare length left above the tanks; reproduces the stock 1-pixel gap at the shoulders. */
    private static final float SHOULDER_SHARE = 0.25F;
    private static final float EPSILON = 1.0E-4F;

    private SpaceGearFit() {}

    /** An axis-aligned box in part space. */
    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Box union(Box other) {
            return new Box(Math.min(this.minX, other.minX), Math.min(this.minY, other.minY), Math.min(this.minZ, other.minZ),
                    Math.max(this.maxX, other.maxX), Math.max(this.maxY, other.maxY), Math.max(this.maxZ, other.maxZ));
        }

        /** Returns whether {@code other} lies entirely inside this box. */
        public boolean encloses(Box other) {
            return this.minX <= other.minX + EPSILON && this.minY <= other.minY + EPSILON && this.minZ <= other.minZ + EPSILON
                    && this.maxX >= other.maxX - EPSILON && this.maxY >= other.maxY - EPSILON && this.maxZ >= other.maxZ - EPSILON;
        }

        /** Returns whether the boxes overlap in volume; touching faces do not count. */
        public boolean overlaps(Box other) {
            return this.minX < other.maxX - EPSILON && this.maxX > other.minX + EPSILON
                    && this.minY < other.maxY - EPSILON && this.maxY > other.minY + EPSILON
                    && this.minZ < other.maxZ - EPSILON && this.maxZ > other.minZ + EPSILON;
        }

        public float size(int axis) {
            return this.max(axis) - this.min(axis);
        }

        public float min(int axis) {
            return switch (axis) {
                case 0 -> this.minX;
                case 1 -> this.minY;
                default -> this.minZ;
            };
        }

        public float max(int axis) {
            return switch (axis) {
                case 0 -> this.maxX;
                case 1 -> this.maxY;
                default -> this.maxZ;
            };
        }

        boolean hasVolume() {
            return this.size(0) > EPSILON && this.size(1) > EPSILON && this.size(2) > EPSILON;
        }

        public static Box around(Vector3f point) {
            return new Box(point.x, point.y, point.z, point.x, point.y, point.z);
        }
    }

    /**
     * Where the stock mask goes: centred on the head and stretched, axis by axis, to clear it.
     *
     * @param scaleX the mask's stretch along X relative to its stock 10 pixels
     */
    public record Helmet(float centerX, float centerY, float centerZ, float scaleX, float scaleY, float scaleZ) {
        public Box bounds() {
            float half = HELMET_SIZE / 2;
            return new Box(this.centerX - half * this.scaleX, this.centerY - half * this.scaleY, this.centerZ - half * this.scaleZ,
                    this.centerX + half * this.scaleX, this.centerY + half * this.scaleY, this.centerZ + half * this.scaleZ);
        }
    }

    /**
     * Where the two stock tanks go. Each tank's transform maps the stock tank cube, spanning
     * {@code (0,0,0)} to {@code (4,8,4)} pixels, onto the body: its length along the body, its depth
     * standing out from the face it is mounted on.
     */
    public record Tanks(float scale, Matrix4f first, Matrix4f second) {
        public Matrix4f transform(int tank) {
            return new Matrix4f(tank == 0 ? this.first : this.second);
        }

        public Box bounds(int tank) {
            Matrix4f transform = tank == 0 ? this.first : this.second;
            Box bounds = null;
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = transform.transformPosition(new Vector3f(
                        (corner & 1) == 0 ? 0 : TANK_WIDTH,
                        (corner & 2) == 0 ? 0 : TANK_LENGTH,
                        (corner & 4) == 0 ? 0 : TANK_WIDTH));
                bounds = bounds == null ? Box.around(point) : bounds.union(Box.around(point));
            }
            return bounds;
        }
    }

    /** Returns a helmet fitted around {@code head}, or empty when no head is available. */
    public static Optional<Helmet> helmet(@Nullable Box head) {
        if (head == null || !head.hasVolume()) return Optional.empty();
        return Optional.of(new Helmet(
                (head.minX + head.maxX) / 2, (head.minY + head.maxY) / 2, (head.minZ + head.maxZ) / 2,
                (head.size(0) + 2 * HELMET_MARGIN) / HELMET_SIZE,
                (head.size(1) + 2 * HELMET_MARGIN) / HELMET_SIZE,
                (head.size(2) + 2 * HELMET_MARGIN) / HELMET_SIZE));
    }

    /**
     * Mounts tanks on upright bodies' backs or horizontal bodies' tops. The resting rotation determines
     * the mounting face because quadruped bodies can be modeled upright and then rotated.
     *
     * @param body the body's cubes in part space
     * @param restRotation the resting rotation from part space to model space
     * @return the tank placement, or empty when there is no body to mount on
     */
    public static Optional<Tanks> tanks(@Nullable Box body, Matrix3f restRotation) {
        if (body == null || !body.hasVolume()) return Optional.empty();

        float[] extent = {body.size(0), body.size(1), body.size(2)};
        float modelHeight = 0;
        float modelDepth = 0;
        for (int axis = 0; axis < 3; axis++) {
            modelHeight += Math.abs(restRotation.getRowColumn(1, axis)) * extent[axis];
            modelDepth += Math.abs(restRotation.getRowColumn(2, axis)) * extent[axis];
        }
        // Model space has Y pointing down, so "up" is -Y; the back of the mob is +Z. Tanks on a back
        // stand upright; tanks on top lie along the mob, front to back.
        boolean upright = modelHeight >= modelDepth;
        Vector3f mountTowards = upright ? new Vector3f(0, 0, 1) : new Vector3f(0, -1, 0);
        Vector3f lieAlong = upright ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);

        int out = 0;
        float bestAlignment = 0;
        for (int axis = 0; axis < 3; axis++) {
            float alignment = alignment(restRotation, axis, mountTowards);
            if (Math.abs(alignment) > Math.abs(bestAlignment) + EPSILON) {
                out = axis;
                bestAlignment = alignment;
            }
        }
        float outSign = bestAlignment < 0 ? -1 : 1;

        int first = (out + 1) % 3;
        int second = (out + 2) % 3;
        float firstAlignment = Math.abs(alignment(restRotation, first, lieAlong));
        float secondAlignment = Math.abs(alignment(restRotation, second, lieAlong));
        // The face axis that runs the way the tanks should lie; a body posed at an angle falls back on the longer one.
        boolean firstRuns = Math.abs(firstAlignment - secondAlignment) > EPSILON
                ? firstAlignment > secondAlignment
                : extent[first] >= extent[second];
        int along = firstRuns ? first : second;
        int side = firstRuns ? second : first;

        float scale = Mth.clamp(Math.min(extent[side] / PAIR_WIDTH, extent[along] / TANK_LENGTH), MIN_TANK_SCALE, MAX_TANK_SCALE);

        Vector3f lengthAxis = unit(along, 1);
        Vector3f outAxis = unit(out, outSign);
        // Completing the basis this way keeps it a proper rotation; a mirrored one turns the cubes inside out.
        Vector3f widthAxis = lengthAxis.cross(outAxis, new Vector3f());
        float widthSign = widthAxis.get(side);

        float slack = extent[along] - TANK_LENGTH * scale;
        float start = body.min(along) + (slack >= 0 ? slack * SHOULDER_SHARE : slack / 2);
        float face = (outSign > 0 ? body.max(out) : body.min(out)) + outSign * STANDOFF;
        float pairLow = (body.min(side) + body.max(side)) / 2 - TANK_WIDTH * scale;

        Matrix4f[] transforms = new Matrix4f[2];
        for (int tank = 0; tank < 2; tank++) {
            float low = pairLow + tank * TANK_WIDTH * scale;
            Vector3f origin = new Vector3f();
            origin.setComponent(side, widthSign > 0 ? low : low + TANK_WIDTH * scale);
            origin.setComponent(along, start);
            origin.setComponent(out, face);
            transforms[tank] = new Matrix4f(
                    widthAxis.x * scale, widthAxis.y * scale, widthAxis.z * scale, 0,
                    lengthAxis.x * scale, lengthAxis.y * scale, lengthAxis.z * scale, 0,
                    outAxis.x * scale, outAxis.y * scale, outAxis.z * scale, 0,
                    origin.x, origin.y, origin.z, 1);
        }
        return Optional.of(new Tanks(scale, transforms[0], transforms[1]));
    }

    /** How far a part-space axis points along a model-space direction, once the part's rest rotation is applied. */
    private static float alignment(Matrix3f restRotation, int axis, Vector3f direction) {
        return restRotation.getRowColumn(0, axis) * direction.x
                + restRotation.getRowColumn(1, axis) * direction.y
                + restRotation.getRowColumn(2, axis) * direction.z;
    }

    private static Vector3f unit(int axis, float sign) {
        Vector3f vector = new Vector3f();
        vector.setComponent(axis, sign);
        return vector;
    }
}
