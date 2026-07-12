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

package dev.galacticraft.neoforge.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.util.List;

/** Small baked-quad writer mirroring the Fabric renderer's normalized quad emitter. */
final class NeoBakedQuadEmitter {
    private static final int VERTEX_STRIDE = 8;
    private final List<BakedQuad> output;
    private final float[][] positions = new float[4][3];
    private final float[][] uvs = new float[4][2];
    private TextureAtlasSprite sprite;
    private Direction nominalFace;
    private boolean explicitUv;

    NeoBakedQuadEmitter(List<BakedQuad> output, TextureAtlasSprite sprite) {
        this.output = output;
        this.sprite = sprite;
    }

    NeoBakedQuadEmitter sprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
        return this;
    }

    NeoBakedQuadEmitter square(Direction face, float left, float bottom, float right, float top, float depth) {
        this.nominalFace = face;
        switch (face) {
            case UP -> {
                depth = 1.0F - depth;
                top = 1.0F - top;
                bottom = 1.0F - bottom;
                pos(0, left, depth, top).pos(1, left, depth, bottom).pos(2, right, depth, bottom).pos(3, right, depth, top);
            }
            case DOWN -> pos(0, left, depth, top).pos(1, left, depth, bottom).pos(2, right, depth, bottom).pos(3, right, depth, top);
            case EAST -> {
                depth = 1.0F - depth;
                left = 1.0F - left;
                right = 1.0F - right;
                pos(0, depth, top, left).pos(1, depth, bottom, left).pos(2, depth, bottom, right).pos(3, depth, top, right);
            }
            case WEST -> pos(0, depth, top, left).pos(1, depth, bottom, left).pos(2, depth, bottom, right).pos(3, depth, top, right);
            case SOUTH -> {
                depth = 1.0F - depth;
                left = 1.0F - left;
                right = 1.0F - right;
                pos(0, 1.0F - left, top, depth).pos(1, 1.0F - left, bottom, depth).pos(2, 1.0F - right, bottom, depth).pos(3, 1.0F - right, top, depth);
            }
            case NORTH -> pos(0, 1.0F - left, top, depth).pos(1, 1.0F - left, bottom, depth).pos(2, 1.0F - right, bottom, depth).pos(3, 1.0F - right, top, depth);
        }
        if (!this.explicitUv) {
            uv(0, left * 16.0F, top * 16.0F);
            uv(1, left * 16.0F, bottom * 16.0F);
            uv(2, right * 16.0F, bottom * 16.0F);
            uv(3, right * 16.0F, top * 16.0F);
            this.explicitUv = false;
        }
        return this;
    }

    NeoBakedQuadEmitter pos(int vertex, float x, float y, float z) {
        this.positions[vertex][0] = x;
        this.positions[vertex][1] = y;
        this.positions[vertex][2] = z;
        return this;
    }

    NeoBakedQuadEmitter uv(int vertex, float u, float v) {
        this.uvs[vertex][0] = u;
        this.uvs[vertex][1] = v;
        this.explicitUv = true;
        return this;
    }

    NeoBakedQuadEmitter nominalFace(Direction face) {
        this.nominalFace = face;
        return this;
    }

    NeoBakedQuadEmitter cullFace(Direction face) {
        return nominalFace(face);
    }

    void emit() {
        Direction face = this.nominalFace != null ? this.nominalFace : calculateFacing();
        int[] vertices = new int[VERTEX_STRIDE * 4];
        int normal = (face.getStepX() & 0xFF) | ((face.getStepY() & 0xFF) << 8) | ((face.getStepZ() & 0xFF) << 16);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            vertices[offset] = Float.floatToRawIntBits(this.positions[vertex][0]);
            vertices[offset + 1] = Float.floatToRawIntBits(this.positions[vertex][1]);
            vertices[offset + 2] = Float.floatToRawIntBits(this.positions[vertex][2]);
            vertices[offset + 3] = -1;
            // Fabric's quad emitter stores model UVs in the conventional 0-16 range.
            // TextureAtlasSprite expects normalized 0-1 coordinates on NeoForge/Mojmap.
            vertices[offset + 4] = Float.floatToRawIntBits(this.sprite.getU(this.uvs[vertex][0] / 16.0F));
            vertices[offset + 5] = Float.floatToRawIntBits(this.sprite.getV(this.uvs[vertex][1] / 16.0F));
            vertices[offset + 6] = 0;
            vertices[offset + 7] = normal;
        }
        this.output.add(new BakedQuad(vertices, -1, face, this.sprite, true, true));
        this.nominalFace = null;
        this.explicitUv = false;
    }

    private Direction calculateFacing() {
        float ax = this.positions[1][0] - this.positions[0][0];
        float ay = this.positions[1][1] - this.positions[0][1];
        float az = this.positions[1][2] - this.positions[0][2];
        float bx = this.positions[2][0] - this.positions[1][0];
        float by = this.positions[2][1] - this.positions[1][1];
        float bz = this.positions[2][2] - this.positions[1][2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        return Direction.getNearest(nx, ny, nz);
    }
}
