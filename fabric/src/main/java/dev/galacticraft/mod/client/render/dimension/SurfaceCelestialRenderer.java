package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.ShaderPackCompat;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;

/** The flight bodies viewed from a surface, with the local daylight clock and atmospheric extinction. */
public final class SurfaceCelestialRenderer {
    // The flight Sun is enlarged for navigation. Surface skies use a modest disk, scaled by orbital distance.
    static final double SUN_RADIUS = 42;
    private static final SpaceTravel.Destination SUN = new SpaceTravel.Destination(
            Constant.key(AddonRegistries.CELESTIAL_BODY, "sol"), SpaceTravel.SUN_POSITION, SUN_RADIUS,
            Constant.id("textures/environment/solar_system/sun.png"), SpaceTravel.SUN_SPIN);

    private SurfaceCelestialRenderer() { }

    static boolean available() {
        return GCRenderTypes.getSpacePlanetShader() != null && !ShaderPackCompat.isActive();
    }

    /** Returns false when the caller should use its existing shader-pack/legacy sun path. */
    public static boolean render(GCWorldRenderContext context) {
        var observer = SpaceTravel.fromLevel(context.world());
        if (observer == null || !available()) return false;
        var configuredSun = SpaceTravel.sun(context.world().registryAccess(), observer, true);
        double sunScale = SpaceDimensionEffects.sky(context.world()).sunSize() / 2.0;
        var sunBody = new SpaceTravel.Destination(configuredSun.body(), configuredSun.center(),
                Math.max(0.001, configuredSun.radius() * sunScale), configuredSun.texture(), configuredSun.spin());
        var shader = GCRenderTypes.getSpacePlanetShader();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        double days = SpaceTravel.days(context.world(), partialTick);
        float time = (float) (days % 10);
        String home = observer.body().location().getNamespace().equals(Constant.MOD_ID) ? observer.body().location().getPath() : "addon";
        Matrix3d frame = skyFrame(observer, context.world().getTimeOfDay(partialTick), sunBody);
        Vec3 sunPosition = transform(frame, sunBody.center().subtract(observer.center()));
        float night = Math.clamp(context.world().getStarBrightness(partialTick) * 2F, 0F, 1F);
        float transmission = switch (home) {
            case "mars" -> 1F - ClientDustStorms.intensity(partialTick) * .9F;
            case "venus" -> .12F;
            case "jupiter" -> (float) Math.clamp((context.camera().getPosition().y - 128) / 192, 0, .35);
            default -> 1F;
        };
        float planets = switch (home) {
            case "mars" -> .08F + .82F * night;
            case "venus", "jupiter" -> .08F * night;
            default -> 1F;
        };
        var bodies = new ArrayList<>(SpaceTravel.systemBodies(context.world().registryAccess(), observer));
        bodies.removeIf(body -> body.body().equals(sunBody.body()));
        bodies.remove(observer);
        if (sunScale > 0) bodies.add(sunBody);
        bodies.sort(Comparator.comparingDouble((SpaceTravel.Destination body) ->
                body.center().distanceToSqr(observer.center())).reversed());
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        FogRenderer.setupNoFog();
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("ViewMat").set(context.positionMatrix());
        shader.safeGetUniform("HorizonFade").set(1F);
        try {
            for (var body : bodies) {
                boolean sun = body == sunBody;
                if (!sun && body.resolve(context.world().registryAccess()) == null) continue;
                String name = sun ? "sun" : body.body().location().getNamespace().equals(Constant.MOD_ID) ? body.body().location().getPath() : "addon";
                Vec3 position = apparentPosition(observer, body, frame);
                var apparent = new SpaceTravel.Destination(body.body(), position, body.radius(), body.texture(), body.spin());
                Matrix3d orientation = home.equals("moon") && name.equals("earth")
                        ? body.spin().orientation(days)
                        : new Matrix3d(frame).mul(body.spin().orientation(days));
                Vec3 light = sunPosition.subtract(position).normalize();
                shader.safeGetUniform("Opacity").set(transmission * (sun ? 1F : planets));
                SolarSystemSkyRenderer.renderBody(shader, apparent, Vec3.ZERO, name, time, orientation, light);
            }
        } finally {
            // These uniforms are shared with flight, including the retained world during a seamless handoff.
            shader.safeGetUniform("HorizonFade").set(0F);
            shader.safeGetUniform("Opacity").set(1F);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
        return true;
    }

    static Matrix3d skyFrame(SpaceTravel.Destination observer, float timeOfDay) {
        return skyFrame(observer, timeOfDay, SUN);
    }

    static Matrix3d skyFrame(SpaceTravel.Destination observer, float timeOfDay, SpaceTravel.Destination star) {
        Vec3 sun = star.center().subtract(observer.center()).normalize();
        Matrix3d align = new Matrix3d().rotation(new Quaterniond().rotationTo(sun.x, sun.y, sun.z, 0, 1, 0));
        String name = observer.body().location().getNamespace().equals(Constant.MOD_ID) ? observer.body().location().getPath() : "addon";
        // Match each existing sky's sun path, so lighting, dust halos and lunar exposure remain aligned.
        double angle = timeOfDay * Math.PI * 2;
        Matrix3d daily = name.equals("moon") || name.equals("asteroid") || name.equals("jupiter")
                ? new Matrix3d().rotateZ(angle)
                : new Matrix3d().rotateY(-Math.PI / 2).rotateX(-angle);
        return daily.mul(align);
    }

    static Vec3 apparentPosition(SpaceTravel.Destination observer, SpaceTravel.Destination body, Matrix3d frame) {
        Vec3 offset = body.center().subtract(observer.center());
        if (observer.body().location().equals(Constant.id("moon")) && body.body().location().equals(Constant.id("earth"))) {
            // The lunar surface is tidally locked; Earth's phase changes, but its position does not.
            return transform(new Matrix3d().rotateY(Math.toRadians(32)).rotateZ(Math.toRadians(46)),
                    new Vec3(0, offset.length(), 0));
        }
        return transform(frame, offset);
    }

    private static Vec3 transform(Matrix3d matrix, Vec3 value) {
        Vector3d result = matrix.transform(new Vector3d(value.x, value.y, value.z));
        return new Vec3(result.x, result.y, result.z);
    }
}