package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceProjectionTest {
    @Test void preservesTextureCoordinatesScaleAndLightAcrossEveryFaceAndCorners() {
        for (var body : SpaceTravel.DESTINATIONS) {
            if (body.body().location().getPath().equals("asteroid")) continue;
            for (double days : new double[]{0, .37, 8.5}) {
                for (var local : new Vector3d[]{new Vector3d(3, .2, -.3), new Vector3d(-3, .2, .3),
                        new Vector3d(.2, 3, -.3), new Vector3d(.2, -3, .3),
                        new Vector3d(.2, -.3, 3), new Vector3d(-.2, .3, -3),
                        new Vector3d(3, 3, .2), new Vector3d(-3, -3, -3)}) {
                    var offset = body.spin().orientation(days).transform(new Vector3d(local).mul(body.radius()));
                    Vec3 flight = body.center().add(offset.x, offset.y, offset.z);
                    double altitude = body.radius() * .02;
                    Vec3 arrival = new Vec3(170, 900, -340);
                    var projection = PlanetSurfaceProjection.fromFlight(body, flight, days, arrival, 100, altitude, 3);
                    Vec3 endpoint = body.approachPosition(flight, altitude, days);
                    var expectedCamera = body.spin().orientation(days).transformTranspose(vector(endpoint.subtract(body.center()))).div(body.radius());
                    var actualCamera = projection.orientation().transformTranspose(vector(arrival.subtract(projection.center()))).div(projection.radius());
                    assertVector(expectedCamera, actualCamera);
                    var expectedSun = body.spin().orientation(days).transformTranspose(vector(SpaceTravel.SUN_POSITION.subtract(body.center()).normalize()));
                    var actualSun = projection.orientation().transformTranspose(vector(projection.sunlight()));
                    assertVector(expectedSun, actualSun);
                    assertEquals(1, projection.orientation().determinant(), 1e-9);
                    assertEquals(3, projection.time());
                    // Descending changes distance to the very same atlas location, rather than resetting its UVs.
                    var foot = new Vec3(arrival.x, 100, arrival.z);
                    var localFoot = projection.orientation().transformTranspose(vector(foot.subtract(projection.center()))).div(projection.radius());
                    var expectedFoot = body.spin().orientation(days).transformTranspose(
                            vector(body.approachPosition(flight, 0, days).subtract(body.center()))).div(body.radius());
                    assertVector(expectedFoot, localFoot);
                    assertTrue(Double.isFinite(projection.radius()) && projection.radius() > 0);
                }
            }
        }
    }

    private static Vector3d vector(Vec3 value) { return new Vector3d(value.x, value.y, value.z); }
    private static void assertVector(Vector3d expected, Vector3d actual) {
        assertEquals(expected.x, actual.x, 1e-8);
        assertEquals(expected.y, actual.y, 1e-8);
        assertEquals(expected.z, actual.z, 1e-8);
    }
}