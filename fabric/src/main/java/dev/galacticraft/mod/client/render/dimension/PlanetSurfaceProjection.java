package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Re-expresses the approached cube in surface coordinates without changing its texture coordinates. */
record PlanetSurfaceProjection(Vec3 center, double radius, Matrix3d orientation, Vec3 sunlight, float time) {
    static PlanetSurfaceProjection fromFlight(SpaceTravel.Destination body, Vec3 flight, double days,
                                              Vec3 arrival, double ground, double altitude, float time) {
        return fromFlight(body, flight, days, arrival, ground, altitude, time, SpaceTravel.SUN_POSITION);
    }

    static PlanetSurfaceProjection fromFlight(SpaceTravel.Destination body, Vec3 flight, double days,
                                              Vec3 arrival, double ground, double altitude, float time, Vec3 star) {
        Vec3 point = body.approachPosition(flight, 0, days);
        Vec3 outward = body.approachPosition(flight, 1, days).subtract(point).normalize();
        var normal = new Vector3d(outward.x, outward.y, outward.z);
        var right = new Vector3d(normal).cross(Math.abs(normal.z) < .9
                ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0)).normalize();
        var forward = new Vector3d(right).cross(normal);
        var toSurface = new Matrix3d(right.x, normal.x, forward.x,
                right.y, normal.y, forward.y, right.z, normal.z, forward.z);
        // Preserve altitude / radius, hence texel magnification, at the end of the flight reveal.
        double scale = Math.max(1, arrival.y - ground) / Math.max(.001, altitude);
        Vec3 center = new Vec3(arrival.x, ground, arrival.z)
                .subtract(transform(toSurface, point.subtract(body.center())).scale(scale));
        Vec3 sunlight = transform(toSurface, star.subtract(body.center()).normalize());
        return new PlanetSurfaceProjection(center, body.radius() * scale,
                new Matrix3d(toSurface).mul(body.spin().orientation(days)), sunlight, time);
    }

    private static Vec3 transform(Matrix3d rotation, Vec3 vector) {
        var result = rotation.transform(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(result.x, result.y, result.z);
    }
}