/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.client.render.dimension.star;

import dev.galacticraft.mod.client.render.dimension.star.data.CelestialBody;
import dev.galacticraft.mod.client.render.dimension.star.data.CelestialBodyType;
import dev.galacticraft.mod.client.render.dimension.star.data.Planet3DData;
import dev.galacticraft.mod.client.render.dimension.star.data.PlanetData;
import dev.galacticraft.mod.client.render.dimension.star.data.StarData;
import dev.galacticraft.mod.client.render.dimension.star.display.CelestialBodyRenderer;
import dev.galacticraft.mod.client.render.dimension.star.display.PlanetRenderer2D;
import dev.galacticraft.mod.client.render.dimension.star.display.PlanetRenderer3D;
import dev.galacticraft.mod.client.render.dimension.star.display.StarRenderer;
import dev.galacticraft.mod.client.render.dimension.GCWorldRenderContext;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.*;

/** Renders stars and planets in a galaxy view. */
public class CelestialBodyRendererManager {
    private final CelestialBodyFactory factory;

    private final Map<CelestialBodyType, List<CelestialBody>> celestialBodies;

    private final Map<CelestialBodyType, CelestialBodyRenderer> renderers;

    private GeographicalSolarPosition solarPosition;

    // Fixed seed keeps the procedural background stars identical across clients.
    private static final long STAR_FIELD_SEED = 27893L;
    private static final int UNIFORM_STAR_COUNT = 5500;
    private static final int GALACTIC_STAR_COUNT = 3000;
    // Vanilla's sky projection is intentionally shallow. Keeping the stellar
    // sphere near the other sky bodies prevents the entire field from being
    // clipped at common render distances while pixel-sized billboards preserve
    // the illusion of astronomical distance.
    private static final double STAR_FIELD_RADIUS = 100.0;
    private static final double GALACTIC_BAND_SIGMA = Math.toRadians(6.5);
    private static final Vector3d GALACTIC_NORTH = new Vector3d(-0.868, 0.456, -0.198).normalize();
    private static final Vector3d GALACTIC_AXIS_U = new Vector3d(GALACTIC_NORTH).cross(0.0, 1.0, 0.0).normalize();
    private static final Vector3d GALACTIC_AXIS_V = new Vector3d(GALACTIC_NORTH).cross(GALACTIC_AXIS_U).normalize();

    /**
     * A compact naked-eye catalog. Procedural stars provide the depth while these
     * fixed equatorial positions preserve recognizable anchor constellations.
     */
    private static final CatalogStar[] BRIGHT_STAR_CATALOG = {
            // Orion
            new CatalogStar(5.9195, 7.407, 0.42, 0.02),
            new CatalogStar(5.4189, 6.349, 0.13, 0.96),
            new CatalogStar(5.6036, -1.202, 1.69, 0.72),
            new CatalogStar(5.6793, -1.943, 1.74, 0.88),
            new CatalogStar(5.5334, -0.299, 2.25, 0.82),
            new CatalogStar(5.2423, -8.202, 0.13, 0.98),
            new CatalogStar(5.7959, -9.670, 2.06, 0.86),
            // Big Dipper
            new CatalogStar(11.0621, 61.751, 1.79, 0.64),
            new CatalogStar(11.0307, 56.382, 2.37, 0.70),
            new CatalogStar(11.8972, 53.695, 2.44, 0.66),
            new CatalogStar(12.2570, 57.033, 3.31, 0.72),
            new CatalogStar(12.9004, 55.959, 1.76, 0.72),
            new CatalogStar(13.3987, 54.925, 2.23, 0.80),
            new CatalogStar(13.7923, 49.313, 1.85, 0.86),
            // Cassiopeia
            new CatalogStar(0.1529, 59.150, 2.27, 0.72),
            new CatalogStar(0.6751, 56.537, 2.24, 0.12),
            new CatalogStar(0.9451, 60.717, 2.47, 0.90),
            new CatalogStar(1.4302, 60.235, 2.68, 0.84),
            new CatalogStar(1.9066, 63.670, 3.35, 0.90),
            // Southern Cross
            new CatalogStar(12.4433, -63.099, 0.76, 0.90),
            new CatalogStar(12.7953, -59.689, 1.25, 0.96),
            new CatalogStar(12.5194, -57.113, 1.63, 0.08),
            new CatalogStar(12.2524, -58.749, 2.79, 0.84),
            // Bright all-sky anchors: Sirius through Regulus
            new CatalogStar(6.7525, -16.716, -1.46, 0.92),
            new CatalogStar(6.3992, -52.696, -0.74, 0.20),
            new CatalogStar(14.6601, -60.835, -0.27, 0.18),
            new CatalogStar(18.6156, 38.784, 0.03, 0.98),
            new CatalogStar(5.2782, 45.998, 0.08, 0.14),
            new CatalogStar(14.2610, 19.182, -0.05, 0.10),
            new CatalogStar(7.6550, 5.225, 0.34, 0.72),
            new CatalogStar(1.6286, -57.237, 0.46, 0.94),
            new CatalogStar(19.8464, 8.868, 0.77, 0.94),
            new CatalogStar(4.5987, 16.509, 0.85, 0.04),
            new CatalogStar(16.4901, -26.432, 0.96, 0.01),
            new CatalogStar(13.4199, -11.161, 0.97, 0.92),
            new CatalogStar(7.7553, 28.026, 1.14, 0.18),
            new CatalogStar(22.9608, -29.622, 1.16, 0.82),
            new CatalogStar(20.6905, 45.280, 1.25, 0.90),
            new CatalogStar(10.1395, 11.967, 1.35, 0.94)
    };

    private CelestialBodyRendererManager() {

        this.factory = new CelestialBodyFactory();
        this.celestialBodies = new HashMap<>();
        this.renderers = new HashMap<>();
        this.solarPosition = GeographicalSolarPosition.getInstance();

        for (CelestialBodyType type : CelestialBodyType.values()) {
            celestialBodies.put(type, new ArrayList<>());
        }

        renderers.put(CelestialBodyType.STAR, new StarRenderer());
        renderers.put(CelestialBodyType.PLANET2D, new PlanetRenderer2D());
        renderers.put(CelestialBodyType.PLANET3D, new PlanetRenderer3D());

        this.setStarPositions();
    }

    /** Rebuilds the deterministic background star field. */
    public void setStarPositions() {
        final Random random = new Random(STAR_FIELD_SEED);
        celestialBodies.get(CelestialBodyType.STAR).clear();

        for (int i = 0; i < UNIFORM_STAR_COUNT; i++) {
            this.addProceduralStar(random, randomSphereDirection(random));
        }

        for (int i = 0; i < GALACTIC_STAR_COUNT; i++) {
            this.addProceduralStar(random, galacticBandDirection(random));
        }

        for (CatalogStar catalogStar : BRIGHT_STAR_CATALOG) {
            Vector3d direction = equatorialDirection(catalogStar.rightAscensionHours, catalogStar.declinationDegrees);
            StarData star = this.addStar(direction, 0.90, catalogStar.colorHint * 360.0);
            double normalizedMagnitude = Math.max(0.0, Math.min(1.0, (4.0 - catalogStar.visualMagnitude) / 5.5));
            star.setBrightness(0.58 + Math.pow(normalizedMagnitude, 1.35) * 0.42);
        }
    }

    private void addProceduralStar(Random random, Vector3d direction) {
        // A power distribution produces many sub-pixel stars and very few visual
        // anchors, which is much closer to an apparent-magnitude sky.
        double brightness = 0.035 + Math.pow(random.nextDouble(), 6.5) * 0.965;
        StarData star = this.addStar(direction, 0.25 + brightness * 0.65, random.nextDouble(360.0));
        star.setBrightness(brightness);
    }

    private StarData addStar(Vector3d direction, double size, double colorHintDegrees) {
        CelestialBody body = this.addCelestialBody(
                CelestialBodyType.STAR,
                (int) Math.round(direction.x * STAR_FIELD_RADIUS),
                (int) Math.round(direction.y * STAR_FIELD_RADIUS),
                (int) Math.round(direction.z * STAR_FIELD_RADIUS),
                size,
                colorHintDegrees
        );
        return (StarData) body;
    }

    private static Vector3d randomSphereDirection(Random random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double longitude = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.sqrt(1.0 - y * y);
        return new Vector3d(horizontal * Math.cos(longitude), y, horizontal * Math.sin(longitude));
    }

    private static Vector3d galacticBandDirection(Random random) {
        double longitude = random.nextDouble() * Math.PI * 2.0;
        double latitude = Math.max(-0.35, Math.min(0.35, random.nextGaussian() * GALACTIC_BAND_SIGMA));
        double planar = Math.cos(latitude);
        return new Vector3d(GALACTIC_AXIS_U).mul(Math.cos(longitude) * planar)
                .add(new Vector3d(GALACTIC_AXIS_V).mul(Math.sin(longitude) * planar))
                .add(new Vector3d(GALACTIC_NORTH).mul(Math.sin(latitude)))
                .normalize();
    }

    private static Vector3d equatorialDirection(double rightAscensionHours, double declinationDegrees) {
        double longitude = Math.toRadians(rightAscensionHours * 15.0);
        double latitude = Math.toRadians(declinationDegrees);
        double planar = Math.cos(latitude);
        return new Vector3d(planar * Math.cos(longitude), Math.sin(latitude), planar * Math.sin(longitude));
    }

    private record CatalogStar(double rightAscensionHours, double declinationDegrees, double visualMagnitude,
                               double colorHint) {
    }

    public CelestialBody addCelestialBody(CelestialBodyType type, int x, int y, int z, double size, double rotation) {
        CelestialBody body = factory.createCelestialBody(type, x, y, z, size, rotation);
        celestialBodies.get(type).add(body);
        return body;
    }

    public PlanetData add2DPlanet(int x, int y, int z, double size, double rotation, ResourceLocation texture) {
        PlanetData planet = factory.createPlanet(x, y, z, size, rotation, texture);
        celestialBodies.get(CelestialBodyType.PLANET2D).add(planet);
        return planet;
    }

    public Planet3DData add3DPlanet(int x, int y, int z, double size, double rotation, ResourceLocation texture, float opacity) {
        Planet3DData planet = factory.create3DPlanet(x, y, z, size, rotation, texture, opacity);
        celestialBodies.get(CelestialBodyType.PLANET3D).add(planet);
        return planet;
    }

    public Planet3DData add3DPlanet(int x, int y, int z, double size, double rotation,
                                   java.util.Map<Planet3DData.Face, ResourceLocation> textures, float opacity) {
        Planet3DData planet = factory.create3DPlanet(x, y, z, size, rotation, textures, opacity);
        celestialBodies.get(CelestialBodyType.PLANET3D).add(planet);
        return planet;
    }

    public boolean removeCelestialBody(CelestialBody body) {
        return celestialBodies.get(body.getType()).remove(body);
    }

    public void registerRenderer(CelestialBodyType type, CelestialBodyRenderer renderer) {
        renderers.put(type, renderer);
    }

    public List<CelestialBody> getCelestialBodiesByType(CelestialBodyType type) {
        return new ArrayList<>(celestialBodies.get(type));
    }

    public List<CelestialBody> getAllCelestialBodies() {
        List<CelestialBody> allBodies = new ArrayList<>();
        for (List<CelestialBody> bodies : celestialBodies.values()) {
            allBodies.addAll(bodies);
        }
        return allBodies;
    }

    public void render(GCWorldRenderContext worldRenderContext) {
        this.render(worldRenderContext, null);
    }

    /** Renders the celestial sphere with an optional dimension-specific rotation. */
    public void render(GCWorldRenderContext worldRenderContext, Matrix4f celestialMatrix) {
        for (CelestialBodyType type : CelestialBodyType.values()) {
            CelestialBodyRenderer renderer = renderers.get(type);
            if (renderer != null) {
                List<CelestialBody> bodies = celestialBodies.get(type);
                if (celestialMatrix != null && renderer instanceof StarRenderer starRenderer) {
                    starRenderer.renderAll(bodies, worldRenderContext, celestialMatrix);
                } else {
                    renderer.renderAll(bodies, worldRenderContext);
                }
            }
        }
    }

    public GeographicalSolarPosition getSolarPosition() {
        return solarPosition;
    }

    public void setSolarPosition(GeographicalSolarPosition solarPosition) {
        this.solarPosition = solarPosition;
    }

    public void updateSolarPosition(double x, double y, double z) {
        this.solarPosition.setCameraPositions(x, y, z);
    }

    private static final CelestialBodyRendererManager INSTANCE = new CelestialBodyRendererManager();

    // TODO: support multiple world spaces for add-on solar systems.
    public static CelestialBodyRendererManager getInstance() {
        return INSTANCE;
    }
}
