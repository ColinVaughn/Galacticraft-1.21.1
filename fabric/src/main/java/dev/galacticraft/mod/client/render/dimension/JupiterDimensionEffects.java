package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.api.dimension.GalacticDimensionEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Jupiter sky and fog, with latitude bands and a heat glow that intensifies toward the core.
 */
public final class JupiterDimensionEffects extends GalacticDimensionEffects {
    public static final JupiterDimensionEffects INSTANCE = new JupiterDimensionEffects();
    private static final Vec3 ZONE_HAZE_COLOR = new Vec3(0.76, 0.71, 0.62);
    private static final Vec3 BELT_HAZE_COLOR = new Vec3(0.64, 0.49, 0.37);
    private static final Vec3 DEEP_COLOR = new Vec3(0.25, 0.19, 0.16);
    private static final Vec3 EMBER_COLOR = new Vec3(0.45, 0.09, 0.03);
    private static final Vec3 MOLTEN_COLOR = new Vec3(0.95, 0.42, 0.08);
    private static final Vec3 INCANDESCENT_COLOR = new Vec3(1.0, 0.82, 0.5);
    /** The heat glow begins below this height and is fully incandescent by {@link #INCANDESCENT_Y}. */
    public static final double CORE_TOP = -8, INCANDESCENT_Y = -118;

    private JupiterDimensionEffects() {
        super(Float.NaN, false, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getFogColor(ClientLevel level, float partialTicks, Vec3 cameraPos, CubicSampler.Vec3Fetcher fetcher) {
        // The fog sampler supplies quart coordinates, so use the actual camera for altitude.
        return atmosphereColor(level, partialTicks);
    }

    @Override
    public Vec3 getSkyColor(ClientLevel level, float partialTicks) {
        // Keep the dome darker than the core fog so the light appears to come from below.
        double heat = coreHeat(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y);
        Vec3 color = atmosphereColor(level, partialTicks);
        return color.multiply(1.0 - 0.55 * heat, 1.0 - 0.7 * heat, 1.0 - 0.8 * heat);
    }

    private Vec3 atmosphereColor(ClientLevel level, float partialTicks) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double depth = Mth.clamp((208.0 - camera.y) / 176.0, 0.0, 1.0);
        double latitude = Math.abs(bandOffset(camera.z) / 1024.0 - 1.0);
        double belt = Mth.smoothstep(Mth.clamp((latitude - 0.25) / 0.50, 0.0, 1.0));
        double daylight = 0.35 + 0.65 * (1.0 - getStarBrightness(level, partialTicks));
        Vec3 haze = ZONE_HAZE_COLOR.lerp(BELT_HAZE_COLOR, belt).lerp(DEEP_COLOR, depth);
        double heat = coreHeat(camera.y);
        // The core glows by its own light, so night stops dimming it as the heat takes over.
        return coreColor(haze, camera.y).scale(Mth.lerp(heat, daylight, 1.0));
    }

    /** 0 above {@link #CORE_TOP}, rising to 1 by the incandescent depths. */
    public static double coreHeat(double y) {
        return Mth.clamp((CORE_TOP - y) / (CORE_TOP - INCANDESCENT_Y), 0.0, 1.0);
    }

    /** Blends {@code haze} through ember, molten and incandescent as {@code y} sinks toward the core. */
    static Vec3 coreColor(Vec3 haze, double y) {
        Vec3 color = haze.lerp(EMBER_COLOR, Mth.smoothstep(Mth.clamp((CORE_TOP - y) / 40.0, 0.0, 1.0)));
        color = color.lerp(MOLTEN_COLOR, Mth.smoothstep(Mth.clamp((-48.0 - y) / 30.0, 0.0, 1.0)));
        return color.lerp(INCANDESCENT_COLOR, Mth.smoothstep(Mth.clamp((-78.0 - y) / 40.0, 0.0, 1.0)));
    }

    /** The fog colour the core paints over vanilla's void darkening. */
    public Vec3 coreFog(ClientLevel level, float partialTicks) {
        return this.atmosphereColor(level, partialTicks);
    }

    static float bandOffset(double worldZ) {
        return (float) (worldZ - Math.floor(worldZ / 2048.0) * 2048.0);
    }

    public static float fogDistance(double cameraY) {
        return (float) Mth.clampedLerp(24.0, 160.0, (cameraY - 96.0) / 128.0);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean tickRain(ClientLevel level, Camera camera, int ticks) {
        Vec3 eye = camera.getPosition();
        double heat = coreHeat(eye.y);
        if (heat <= 0) return true;
        var random = level.random;
        var options = Minecraft.getInstance().options.particles().get();
        if (options == net.minecraft.client.ParticleStatus.MINIMAL) return true;
        int count = (int) (2 + heat * 16) / (options == net.minecraft.client.ParticleStatus.DECREASED ? 2 : 1);
        for (int i = 0; i < count; i++) {
            double x = eye.x + random.nextGaussian() * 7, y = eye.y + random.nextGaussian() * 6 - 3, z = eye.z + random.nextGaussian() * 7;
            if (random.nextFloat() < 0.6F) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x, y, z,
                        random.nextGaussian() * 0.01, 0.06 + random.nextFloat() * 0.12 * heat, random.nextGaussian() * 0.01);
            } else {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, x, y, z, 0, 0.02, 0);
            }
        }
        if (heat > 0.5 && random.nextFloat() < heat * 0.08F) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, eye.x + random.nextGaussian() * 5, eye.y - 2, eye.z + random.nextGaussian() * 5, 0, 0, 0);
        }
        if (ticks % 40 == 0 && random.nextFloat() < 0.3F + heat * 0.5F) {
            level.playLocalSound(eye.x, eye.y, eye.z, net.minecraft.sounds.SoundEvents.LAVA_AMBIENT, net.minecraft.sounds.SoundSource.AMBIENT,
                    (float) (0.3 + heat * 0.7), 0.5F + random.nextFloat() * 0.2F, false);
        }
        if (ticks % 80 == 0 && heat > 0.3) {
            level.playLocalSound(eye.x, eye.y, eye.z, net.minecraft.sounds.SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value(), net.minecraft.sounds.SoundSource.AMBIENT,
                    (float) heat, 0.6F, false);
        }
        return true;
    }
}