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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders belt rocks at their angular size within the sky depth range. Sorts rocks and planets
 * back to front; distant rocks use pixel-sized specks.
 */
public final class AsteroidBeltRenderer {
    public static final AsteroidBeltRenderer INSTANCE = new AsteroidBeltRenderer();
    /** Small rocks are gathered within this distance; large ones are drawn from anywhere. */
    public static final double NEAR_RADIUS = 1000;
    private static final double SKY_DISTANCE = 100;
    private static final String[] TEXTURES = {"asteroid_rock", "asteroid_rock_block_1", "asteroid_rock_block_2",
            "asteroid_iron_ore", "asteroid_aluminum_ore"};
    private static final Vec3[] NORMALS = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0),
            new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)};

    private record Rock(AsteroidBeltField.Cube cube, double distance) { }

    private static final double NEAR_MARGIN = 64;
    private final List<Rock> rocks = new ArrayList<>();
    private final List<AsteroidBeltField.Cube> nearCubes = new ArrayList<>();
    private Vec3 nearCenter;
    private VertexBuffer buffer, speckBuffer;
    private ByteBufferBuilder speckBytes;
    private int next;
    private Vec3 camera;
    private Matrix4f view, projection;
    private Vector3f right, up, forward;
    private double pixel;

    private AsteroidBeltRenderer() { }

    /** Gathers the rocks in front of the camera for this frame, farthest first; with visible false, draws none. */
    public void prepare(Vec3 camera, Matrix4f view, Matrix4f projection, boolean visible) {
        this.camera = camera;
        this.view = view;
        this.projection = projection;
        Matrix4f inverse = new Matrix4f(view).invert();
        this.right = inverse.transformDirection(new Vector3f(1, 0, 0)).normalize();
        this.up = inverse.transformDirection(new Vector3f(0, 1, 0)).normalize();
        this.forward = inverse.transformDirection(new Vector3f(0, 0, -1)).normalize();
        // World size of one screen pixel at the sky distance.
        this.pixel = 2 * SKY_DISTANCE / (Math.max(projection.m11(), 1e-3) * Math.max(1, Minecraft.getInstance().getWindow().getHeight()));
        double cosine = Math.cos(Math.min(Math.PI / 2, Math.atan(1 / Math.max(projection.m11(), 1e-3)) * 2.2));
        rocks.clear();
        next = 0;
        if (!visible) return;
        // The small-rock query costs about 2 ms, so it is cached with a margin and redone only after moving that far.
        if (nearCenter == null || nearCenter.distanceToSqr(camera) > NEAR_MARGIN * NEAR_MARGIN) {
            nearCenter = camera;
            nearCubes.clear();
            AsteroidBeltField.near(camera, NEAR_RADIUS + NEAR_MARGIN, nearCubes::add);
        }
        for (var cube : AsteroidBeltField.largeIfReady()) consider(cube, cosine);
        for (var cube : nearCubes) consider(cube, cosine);
        rocks.sort(Comparator.comparingDouble(Rock::distance).reversed());
    }

    private void consider(AsteroidBeltField.Cube cube, double cosine) {
        double dx = cube.x() - camera.x, dy = cube.y() - camera.y, dz = cube.z() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > cube.half() * 2 && (dx * forward.x + dy * forward.y + dz * forward.z) / distance < cosine) return;
        rocks.add(new Rock(cube, distance));
    }

    /** Draws the prepared rocks that lie farther away than the given distance (behind the next planet). */
    public void drawFartherThan(double distance) {
        int end = next;
        while (end < rocks.size() && rocks.get(end).distance() > distance) end++;
        if (end == next || camera == null) return;
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[TEXTURES.length];
        for (int i = 0; i < TEXTURES.length; i++) sprites[i] = atlas.apply(Constant.id("block/" + TEXTURES[i]));
        if (speckBytes == null) {
            speckBytes = new ByteBufferBuilder(1 << 18);
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            speckBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        BufferBuilder cubes = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        BufferBuilder specks = new BufferBuilder(speckBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = next; i < end; i++) emit(cubes, specks, rocks.get(i), sprites[rocks.get(i).cube().texture()]);
        next = end;
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        draw(speckBuffer, specks.build(), GameRenderer.getPositionColorShader());
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        draw(buffer, cubes.build(), GameRenderer.getPositionTexColorShader());
    }

    private void draw(VertexBuffer target, MeshData mesh, ShaderInstance shader) {
        if (mesh == null) return;
        target.bind();
        target.upload(mesh);
        target.drawWithShader(view, projection, shader);
        VertexBuffer.unbind();
    }

    public void drawRest() {
        drawFartherThan(-1);
    }

    private void emit(BufferBuilder cubes, BufferBuilder specks, Rock rock, TextureAtlasSprite sprite) {
        var cube = rock.cube();
        double scale = Math.min(1, SKY_DISTANCE / rock.distance()), half = cube.half() * scale;
        double cx = (cube.x() - camera.x) * scale, cy = (cube.y() - camera.y) * scale, cz = (cube.z() - camera.z) * scale;
        Vec3 sun = SpaceTravel.SUN_POSITION.subtract(cube.x(), cube.y(), cube.z()).normalize();
        if (half < pixel * 1.2) {
            // A speck: a camera-facing dot of at least a pixel, brightest when the camera sees the rock's sunlit side.
            double size = Math.max(half, pixel * .7);
            double lit = .4 + .6 * Math.max(0, sun.dot(camera.subtract(cube.x(), cube.y(), cube.z()).normalize()));
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 0 || corner == 3 ? -size : size, b = corner < 2 ? -size : size;
                specks.addVertex((float) (cx + right.x * a + up.x * b), (float) (cy + right.y * a + up.y * b), (float) (cz + right.z * a + up.z * b))
                        .setColor((int) (190 * lit), (int) (178 * lit), (int) (160 * lit), 255);
            }
            return;
        }
        for (Vec3 n : NORMALS) {
            // Only faces turned toward the camera; depth testing is off in the sky.
            if ((camera.x - cube.x()) * n.x + (camera.y - cube.y()) * n.y + (camera.z - cube.z()) * n.z <= cube.half()) continue;
            // More ambient than the planets get, so small rocks still read against black space.
            int shade = (int) (255 * (.22 + .78 * Math.pow(Math.max(0, n.dot(sun)), .8)));
            Vec3 tu = Math.abs(n.y) > .5 ? new Vec3(1, 0, 0) : new Vec3(n.z, 0, -n.x), tv = n.cross(tu);
            float[][] uv = {{sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}, {sprite.getU0(), sprite.getV0()}};
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 0 || corner == 3 ? -half : half, b = corner < 2 ? -half : half;
                cubes.addVertex((float) (cx + n.x * half + tu.x * a + tv.x * b), (float) (cy + n.y * half + tu.y * a + tv.y * b),
                                (float) (cz + n.z * half + tu.z * a + tv.z * b))
                        .setUv(uv[corner][0], uv[corner][1]).setColor(shade, shade, shade, 255);
            }
        }
    }
}
