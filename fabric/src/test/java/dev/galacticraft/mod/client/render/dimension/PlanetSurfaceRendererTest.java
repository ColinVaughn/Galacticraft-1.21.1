package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.world.dimension.SpaceTravel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceRendererTest {
    @Test void coversPlanetarySurfacesWithoutFillingTheAsteroidField() {
        for (var body : SpaceTravel.DESTINATIONS) {
            String name = body.body().location().getPath();
            assertEquals(!name.equals("asteroid"), PlanetSurfaceRenderer.hasSurface(name));
        }
        assertFalse(PlanetSurfaceRenderer.hasSurface("solar_system"));
        assertFalse(PlanetSurfaceRenderer.hasSurface("unknown"));
    }

    @Test void backdropFadesInBeforeGroundLeavesViewAndOutOnDescent() {
        for (int chunks : new int[]{2, 4, 12, 32}) {
            double range = Math.max(128, chunks * 16);
            assertEquals(0, PlanetSurfaceRenderer.opacity(0, chunks));
            assertEquals(0, PlanetSurfaceRenderer.opacity(-100, chunks));
            assertEquals(0, PlanetSurfaceRenderer.opacity(range * .5, chunks));
            assertEquals(.5, PlanetSurfaceRenderer.opacity(range * .75, chunks), 1e-6);
            assertEquals(1, PlanetSurfaceRenderer.opacity(range, chunks));
            assertEquals(1, PlanetSurfaceRenderer.opacity(900 - 128, chunks));
            float previous = 0;
            for (int height = 0; height < 1000; height++) {
                float current = PlanetSurfaceRenderer.opacity(height, chunks);
                assertTrue(current >= previous && current <= 1);
                previous = current;
            }
        }
    }
}