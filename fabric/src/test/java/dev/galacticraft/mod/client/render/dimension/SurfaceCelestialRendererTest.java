package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceCelestialRendererTest {
    @Test void sunFollowsLocalDaylightOnEverySurface() {
        for (var observer : SpaceTravel.DESTINATIONS) {
            for (float time : new float[]{0, .25F, .5F, .75F, 1}) {
                var frame = SurfaceCelestialRenderer.skyFrame(observer, time);
                var offset = SpaceTravel.SUN_POSITION.subtract(observer.center()).normalize();
                var direction = frame.transform(new Vector3d(offset.x, offset.y, offset.z));
                assertEquals(Math.cos(time * Math.PI * 2), direction.y, 1e-9);
                String name = observer.body().location().getPath();
                double east = name.equals("moon") || name.equals("asteroid") || name.equals("jupiter") ? -1 : 1;
                assertEquals(east * Math.sin(time * Math.PI * 2), direction.x, 1e-9);
                assertEquals(0, direction.z, 1e-9);
                assertEquals(1, direction.length(), 1e-9);
                assertEquals(1, frame.determinant(), 1e-9);
            }
        }
    }

    @Test void lunarEarthStaysFixedWhileItsIlluminationChanges() {
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var moon = SpaceTravel.DESTINATIONS.get(1);
        Vec3 first = SurfaceCelestialRenderer.apparentPosition(moon, earth, SurfaceCelestialRenderer.skyFrame(moon, 0));
        for (float time : new float[]{.25F, .5F, .75F, 1}) {
            assertEquals(first, SurfaceCelestialRenderer.apparentPosition(moon, earth, SurfaceCelestialRenderer.skyFrame(moon, time)));
        }
        assertEquals(earth.center().distanceTo(moon.center()), first.length(), 1e-9);
        assertTrue(first.y > 0);
        Vec3 view = first.normalize().scale(-1);
        Vec3 offset = SpaceTravel.SUN_POSITION.subtract(moon.center());
        Vector3d noon = SurfaceCelestialRenderer.skyFrame(moon, 0).transform(new Vector3d(offset.x, offset.y, offset.z));
        Vector3d midnight = SurfaceCelestialRenderer.skyFrame(moon, .5F).transform(new Vector3d(offset.x, offset.y, offset.z));
        double noonLight = new Vec3(noon.x, noon.y, noon.z).subtract(first).normalize().dot(view);
        double midnightLight = new Vec3(midnight.x, midnight.y, midnight.z).subtract(first).normalize().dot(view);
        assertTrue(noonLight < 0 && midnightLight > 0 && midnightLight - noonLight > 1, "Earth's near side must wax and wane with the moving Sun");
    }

    @Test void otherPlanetsKeepFlightAngularSizesAndSunShrinksWithDistance() {
        for (var observer : SpaceTravel.DESTINATIONS) {
            for (var body : SpaceTravel.DESTINATIONS) {
                if (observer == body) continue;
                var position = SurfaceCelestialRenderer.apparentPosition(observer, body,
                        SurfaceCelestialRenderer.skyFrame(observer, .17F));
                assertEquals(body.center().distanceTo(observer.center()), position.length(), 1e-8);
            }
        }
        double mercury = SpaceTravel.DESTINATIONS.get(4).center().distanceTo(SpaceTravel.SUN_POSITION);
        double jupiter = SpaceTravel.DESTINATIONS.getLast().center().distanceTo(SpaceTravel.SUN_POSITION);
        assertTrue(2 * Math.toDegrees(Math.atan(SurfaceCelestialRenderer.SUN_RADIUS / mercury)) < 2);
        assertTrue(SurfaceCelestialRenderer.SUN_RADIUS / mercury > SurfaceCelestialRenderer.SUN_RADIUS / jupiter);
    }
    @Test void addonNamesDoNotInheritStockLunarLocking() {
        var moon = new SpaceTravel.Destination(net.minecraft.resources.ResourceKey.create(
                dev.galacticraft.api.registry.AddonRegistries.CELESTIAL_BODY, net.minecraft.resources.ResourceLocation.parse("example:moon")),
                new Vec3(0, 1024, 2400), 26, net.minecraft.resources.ResourceLocation.parse("example:moon.png"));
        var earth = new SpaceTravel.Destination(net.minecraft.resources.ResourceKey.create(
                dev.galacticraft.api.registry.AddonRegistries.CELESTIAL_BODY, net.minecraft.resources.ResourceLocation.parse("example:earth")),
                new Vec3(0, 1024, 0), 96, net.minecraft.resources.ResourceLocation.parse("example:earth.png"));
        var first = SurfaceCelestialRenderer.apparentPosition(moon, earth, SurfaceCelestialRenderer.skyFrame(moon, 0));
        var later = SurfaceCelestialRenderer.apparentPosition(moon, earth, SurfaceCelestialRenderer.skyFrame(moon, .25F));
        assertTrue(first.distanceTo(later) > 1000, "addon body names inherited stock lunar locking");
    }

}