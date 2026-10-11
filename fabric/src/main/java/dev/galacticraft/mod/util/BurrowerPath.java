package dev.galacticraft.mod.util;

import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** One continuous centerline shared by the armor, connectors and damage targets. */
public final class BurrowerPath {
    public static final double RIDE_STRIDE = 8;
    public static final float RIDE_CYCLE = 2.4F;
    private BurrowerPath() {}

    public static Vec3 riddenMotion(float yaw, float pitch, float forward, float strafe, float speed) {
        double thrust = Math.clamp(forward, -1, 1);
        if (thrust < 0) thrust *= 0.25;
        double angle = Math.toRadians(Math.clamp(pitch, -60, 60));
        Vec3 local = new Vec3(Math.clamp(strafe, -1, 1) * 0.5, -Math.sin(angle) * thrust, Math.cos(angle) * thrust);
        if (local.lengthSqr() > 1) local = local.normalize();
        return local.yRot(-(float)Math.toRadians(yaw)).scale(speed);
    }

    /** Model-space wave; the live head stays anchored and the crest travels toward the tail. */
    public static Vec3 rideOffset(double distance, double travel, float weight) {
        if (distance <= 0 || weight <= 0) return Vec3.ZERO;
        double phase = 2 * Math.PI * (travel / RIDE_STRIDE - distance / 5.8);
        double amplitude = weight * Math.clamp(distance / 1.375, 0, 1);
        return new Vec3(-0.25 * amplitude * Math.sin(phase), 0.12 * amplitude * (1 - Math.cos(phase)), 0);
    }

    public static float clampHeadYaw(float yaw, float neckYaw) {
        return neckYaw + Mth.clamp(Mth.wrapDegrees(yaw - neckYaw), -35, 35);
    }

    public static float steerYaw(float yaw, float wantedYaw, float neckYaw) {
        return clampHeadYaw(Mth.approachDegrees(yaw, clampHeadYaw(wantedYaw, neckYaw), 6), neckYaw);
    }

    public static Vec3[] smoothHorizontal(Vec3[] path) {
        Vec3[] smooth = new Vec3[path.length];
        for (int i = 0; i < path.length; i++) {
            // Round corners over a block of travel; taper at the live head to keep its anchor fixed.
            int radius = Math.min(4, Math.min(i, path.length - 1 - i));
            Vec3 sum = Vec3.ZERO;
            for (int offset = -radius; offset <= radius; offset++)
                sum = sum.add(path[i + offset].scale(radius + 1 - Math.abs(offset)));
            sum = sum.scale(1.0 / ((radius + 1) * (radius + 1)));
            smooth[i] = new Vec3(sum.x, path[i].y, sum.z);
        }
        return smooth;
    }

    public static Vec3 sample(Vec3[] path, double distance) {
        for (int i = 1; i < path.length; i++) {
            double length = path[i - 1].distanceTo(path[i]);
            if (distance <= length && length > 0.00001) return path[i - 1].lerp(path[i], distance / length);
            distance -= length;
        }
        return path[path.length - 1];
    }
}
