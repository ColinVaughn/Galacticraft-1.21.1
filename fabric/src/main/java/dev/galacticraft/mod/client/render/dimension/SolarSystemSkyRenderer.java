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
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;

/** Textured cube planets share their faces and corners with the flight boundary; the Sun has a glowing cube surface. */
public final class SolarSystemSkyRenderer extends SpaceSkyRenderer {
    public static final SolarSystemSkyRenderer INSTANCE = new SolarSystemSkyRenderer();
    private static final ResourceLocation CLOUDS = texture("earth_clouds");
    private static final ResourceLocation NIGHT = texture("earth_night");

    private static ResourceLocation texture(String name) {
        return Constant.id("textures/environment/solar_system/" + name + ".png");
    }

    /** Initializes the star mesh and schedules texture decoding while boarding, before a handoff. */
    public static void preload() {
        var textures = Minecraft.getInstance().getTextureManager();
        var executor = net.minecraft.Util.backgroundExecutor();
        var level = Minecraft.getInstance().level;
        for (var body : level == null ? SpaceTravel.DESTINATIONS : SpaceTravel.bodies(level.registryAccess())) textures.preload(body.texture(), executor);
        AsteroidBeltField.warm();
        for (String name : new String[]{"sun", "earth_clouds", "earth_night"})
            textures.preload(Constant.id("textures/environment/solar_system/" + name + ".png"), executor);
    }

    @Override
    public void render(GCWorldRenderContext context) {
        renderFlight(context.world(), context.positionMatrix(), context.projectionMatrix(),
                context.camera().getPosition(), context.tickCounter().getGameTimeDeltaPartialTick(true),
                (context.world().getGameTime() % 240000L + context.tickCounter().getGameTimeDeltaPartialTick(true)) / 24000F);
    }

    public void renderFlight(ClientLevel world, Matrix4f view, Matrix4f projection, Vec3 camera, float partialTick, float time) {
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        FogRenderer.setupNoFog();
        PoseStack matrices = new PoseStack();
        matrices.mulPose(view);
        starManager.render(matrices, projection, world, partialTick, 0.55F);
        double days = SpaceTravel.days(world, partialTick);
        var belt = AsteroidBeltRenderer.INSTANCE;
        // The belt's one-off warm-up would otherwise land on the dimension handoff, where frame time matters most.
        AsteroidBeltField.configure(SpaceTravel.destinations(world.registryAccess()));
        belt.prepare(camera, view, projection, !SpaceflightTransition.isActive());
        ShaderInstance shader = GCRenderTypes.getSpacePlanetShader();
        if (shader != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(() -> shader);
            shader.safeGetUniform("ViewMat").set(view);
            shader.safeGetUniform("HorizonFade").set(0F);
            shader.safeGetUniform("Opacity").set(1F);
            // Painter's order also lets planets eclipse the Sun without exposing stars on their night sides.
            var bodies = new ArrayList<>(SpaceTravel.bodies(world.registryAccess()));
            bodies.sort(Comparator.comparingDouble((SpaceTravel.Destination d) -> d.center().distanceToSqr(camera)).reversed());
            for (var body : bodies) {
                boolean sun = body.resolve(world.registryAccess()).type() instanceof dev.galacticraft.api.universe.celestialbody.star.Star;
                if (!sun && body.resolve(world.registryAccess()) == null) continue;
                // Belt rocks behind this body first, so it hides them.
                belt.drawFartherThan(body.center().distanceTo(camera));
                RenderSystem.enableBlend();
                RenderSystem.setShader(() -> shader);
                String name = sun ? "sun" : body.body().location().getNamespace().equals(Constant.MOD_ID) ? body.body().location().getPath() : "addon";
                renderBody(shader, body, camera, name, time, body.spin().orientation(days),
                        SpaceTravel.sun(world.registryAccess(), body, false).center().subtract(body.center()).normalize());
            }
            belt.drawRest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            belt.drawRest();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    static void renderBody(ShaderInstance shader, SpaceTravel.Destination body,
                           Vec3 camera, String name, float time, Matrix3d orientation, Vec3 sunlight) {
        Vec3 offset = body.center().subtract(camera);
        boolean sun = name.equals("sun"), earth = name.equals("earth");
        double distance = Math.max(.001, offset.length());
        Vec3 direction = offset.normalize();
        float thickness = switch (name) {
            case "earth" -> .018F;
            case "venus" -> .035F;
            case "mars" -> .006F;
            case "jupiter" -> .012F;
            default -> 0;
        };
        Vec3 atmosphere = switch (name) {
            case "earth" -> new Vec3(.16, .44, 1);
            case "venus" -> new Vec3(.92, .65, .3);
            case "mars" -> new Vec3(.85, .34, .16);
            case "jupiter" -> new Vec3(.65, .73, .85);
            default -> Vec3.ZERO;
        };
        set(shader, "Direction", direction);
        set(shader, "SunDirection", sunlight);
        set(shader, "Atmosphere", atmosphere);
        shader.safeGetUniform("DistanceRadius").set((float) (distance / body.radius()));
        shader.safeGetUniform("Thickness").set(thickness);
        shader.safeGetUniform("Style").set(sun ? 2F : earth ? 1F : 0F);
        shader.safeGetUniform("Rotation").set(time * (name.equals("jupiter") ? 2.4F : .25F) + .13F);
        // Each body turns with its real period and tilt; the server's arrival boundary turns with it.
        shader.safeGetUniform("Orientation").set(new Matrix3f(
                (float) orientation.m00(), (float) orientation.m01(), (float) orientation.m02(),
                (float) orientation.m10(), (float) orientation.m11(), (float) orientation.m12(),
                (float) orientation.m20(), (float) orientation.m21(), (float) orientation.m22()));
        var textures = Minecraft.getInstance().getTextureManager();
        textures.getTexture(body.texture()).setFilter(true, false);
        RenderSystem.setShaderTexture(0, body.texture());
        textures.getTexture(CLOUDS).setFilter(true, false);
        textures.getTexture(NIGHT).setFilter(true, false);
        RenderSystem.setShaderTexture(1, CLOUDS);
        RenderSystem.setShaderTexture(2, NIGHT);
        renderCube(body, camera, sun ? 2.2 : 1, orientation);
    }

    private static void renderCube(SpaceTravel.Destination body, Vec3 camera, double extent, Matrix3d orientation) {
        // Camera position in the body's own frame, where the cube is axis-aligned.
        var offset = camera.subtract(body.center()).scale(1 / body.radius());
        var local = orientation.transformTranspose(new Vector3d(offset.x, offset.y, offset.z));
        var relative = new Vec3(local.x, local.y, local.z);
        boolean inside = extent > 1 && Math.max(Math.abs(relative.x), Math.max(Math.abs(relative.y), Math.abs(relative.z))) <= extent;
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (var face : Direction.values()) {
            var normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            // The Sun's expanded mesh also covers the corona when the camera flies inside it.
            if (!inside && normal.dot(relative) <= extent) continue;
            var right = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(normal.z, 0, -normal.x);
            var up = normal.cross(right);
            cubeVertex(buffer, normal.subtract(right).subtract(up).scale(extent), normal);
            cubeVertex(buffer, normal.add(right).subtract(up).scale(extent), normal);
            cubeVertex(buffer, normal.add(right).add(up).scale(extent), normal);
            cubeVertex(buffer, normal.subtract(right).add(up).scale(extent), normal);
        }
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void cubeVertex(BufferBuilder buffer, Vec3 point, Vec3 normal) {
        // RGB selects the face axis; alpha carries its sign without adding a custom vertex format.
        buffer.addVertex((float) point.x, (float) point.y, (float) point.z).setUv(0, 0)
                .setColor((int) Math.abs(normal.x) * 255, (int) Math.abs(normal.y) * 255,
                        (int) Math.abs(normal.z) * 255, normal.x + normal.y + normal.z > 0 ? 255 : 0);
    }

    private static void set(ShaderInstance shader, String uniform, Vec3 value) {
        shader.safeGetUniform(uniform).set((float) value.x, (float) value.y, (float) value.z);
    }

}