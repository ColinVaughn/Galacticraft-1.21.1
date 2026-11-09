package dev.galacticraft.mod.world.dimension.jupiter;

import net.minecraft.util.Mth;

/** Compressed gameplay layers; P is a relative rating rather than physical atmospheres. */
public final class JupiterEnvironment {
    private JupiterEnvironment() { }

    public static float pressure(double y) {
        if (y >= 208) return 1;
        if (y >= 144) return interpolate(y, 208, 144, 1, 4);
        if (y >= 64) return interpolate(y, 144, 64, 4, 12);
        if (y >= -32) return interpolate(y, 64, -32, 12, 24);
        return interpolate(y, -32, -64, 24, 40);
    }

    private static float interpolate(double y, double top, double bottom, float low, float high) {
        double t = Mth.clamp((top - y) / (top - bottom), 0, 1);
        return (float) Mth.lerp(Mth.smoothstep(t), low, high);
    }

    public static int temperature(double y) {
        return (int) Mth.clampedLerp(120, -145, (y + 64) / 384.0);
    }

    public static float wind(double y, double z) {
        float storm = (float) (0.5 + 0.5 * Math.cos(z * Math.PI / 1024));
        return (float) (storm * Mth.clamp((208 - y) / 144.0, 0, 1));
    }

    public static float visualDensity(double y) {
        return (float) Mth.clamp((240 - y) / 200.0, 0.15, 1);
    }
}