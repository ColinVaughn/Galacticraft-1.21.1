package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.ShaderPackCompat;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Matrix4f;

/** Distant ground/clouds below the camera while real terrain is outside the view distance. */
public final class PlanetSurfaceRenderer {
    private static ResourceKey<Level> approachLevel;
    private static PlanetSurfaceProjection approach;
    private static long approachTick;

    private PlanetSurfaceRenderer() { }

    public static void beginApproach(ClientLevel level, SpaceTravel.Destination body, Vec3 flight, Vec3 arrival,
                                     double days, double altitude, float time) {
        if (!hasSurface(level, body)) return;
        approach = PlanetSurfaceProjection.fromFlight(body, flight, days, arrival,
                ground(level, arrival, body.body().location().getPath()), altitude, time,
                SpaceTravel.sun(level.registryAccess(), body, false).center());
        approachLevel = level.dimension();
        approachTick = level.getGameTime();
    }

    public static void clearApproach() {
        approach = null;
        approachLevel = null;
    }

    public static boolean hasApproach(ResourceKey<Level> level) {
        return approach != null && level.equals(approachLevel);
    }

    private static double ground(ClientLevel level, Vec3 position, String name) {
        var chunk = level.getChunkSource().getChunk((int) Math.floor(position.x / 16),
                (int) Math.floor(position.z / 16), ChunkStatus.FULL, false);
        return chunk == null || name.equals("jupiter") ? level.getMaxBuildHeight()
                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(position.x) & 15, (int) Math.floor(position.z) & 15);
    }

    public static void render(ClientLevel level, Matrix4f view, Camera camera, float partialTick) {
        if (camera.getFluidInCamera() != FogType.NONE || ShaderPackCompat.isActive()) return;
        var body = SpaceTravel.fromLevel(level);
        if (body == null || !hasSurface(level, body)) return;
        var shader = GCRenderTypes.getSpacePlanetShader();
        if (shader == null) return;
        String name = body.body().location().getNamespace().equals(dev.galacticraft.mod.Constant.MOD_ID) ? body.body().location().getPath() : "addon";
        var position = camera.getPosition();
        double altitude = position.y - ground(level, position, name);
        float opacity = opacity(altitude, Minecraft.getInstance().options.getEffectiveRenderDistance());
        if (opacity <= 0) {
            // Once terrain takes over, a later launch must not restore stale arrival lighting.
            if (hasApproach(level.dimension())) clearApproach();
            return;
        }
        // This atlas does not match generated terrain; geographic continuity would require terrain LODs.
        double radius = Math.max(2048, body.radius() * 32);
        Vec3 center = position.add(0, -radius - altitude, 0);
        Matrix3d orientation = new Matrix3d().rotateZ(Math.PI / 2);
        float time = (float) (SpaceTravel.days(level, partialTick) % 10);
        float ambient = .1F;
        double angle = level.getTimeOfDay(partialTick) * Math.PI * 2;
        Vec3 sun = new Vec3(Math.sin(angle) * (name.equals("moon") || name.equals("jupiter") ? -1 : 1),
                Math.cos(angle), 0);
        if (hasApproach(level.dimension())) {
            radius = approach.radius();
            center = approach.center();
            orientation = approach.orientation();
            time = approach.time() + (level.getGameTime() - approachTick + partialTick) / 24000F;
            // Keep orbital shading while distant, then converge on local daylight as real terrain appears.
            sun = approach.sunlight().lerp(sun, 1 - opacity).normalize();
            ambient = .009F + .091F * (1 - opacity);
        }
        var backdrop = new SpaceTravel.Destination(body.body(), center, radius, body.texture(), SpaceTravel.Spin.NONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        FogRenderer.setupNoFog();
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("ViewMat").set(view);
        shader.safeGetUniform("HorizonFade").set(0F);
        shader.safeGetUniform("Opacity").set(opacity);
        shader.safeGetUniform("AmbientLight").set(ambient);
        try {
            SolarSystemSkyRenderer.renderBody(shader, backdrop, position, name, time, orientation, sun);
        } finally {
            shader.safeGetUniform("Opacity").set(1F);
            shader.safeGetUniform("AmbientLight").set(.009F);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static boolean hasSurface(ClientLevel level, SpaceTravel.Destination body) {
        var value = body.resolve(level.registryAccess());
        return value != null && value.type() instanceof dev.galacticraft.api.universe.celestialbody.landable.Landable
                && (body.body().equals(dev.galacticraft.mod.content.GCCelestialBodies.JUPITER)
                    || !level.registryAccess().registryOrThrow(dev.galacticraft.api.registry.AddonRegistries.CELESTIAL_BODY)
                        .getHolderOrThrow(body.body()).is(dev.galacticraft.api.universe.celestialbody.CelestialBodyTags.GAS_GIANT))
                && !body.body().equals(dev.galacticraft.mod.content.GCCelestialBodies.ASTEROID);
    }

    static boolean hasSurface(String name) {
        return switch (name) {
            case "earth", "moon", "mars", "mercury", "venus", "jupiter" -> true;
            default -> false;
        };
    }

    static float opacity(double altitude, int renderDistanceChunks) {
        double distance = Math.max(128, renderDistanceChunks * 16.0);
        double t = Math.clamp((altitude - distance * .5) / (distance * .5), 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }
}