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
import dev.galacticraft.mod.client.render.dimension.star.display.CelestialBodyRenderer;
import dev.galacticraft.mod.client.render.dimension.star.display.PlanetRenderer2D;
import dev.galacticraft.mod.client.render.dimension.star.display.PlanetRenderer3D;
import dev.galacticraft.mod.client.render.dimension.star.display.StarRenderer;
import dev.galacticraft.mod.client.render.dimension.GCWorldRenderContext;
import net.minecraft.resources.ResourceLocation;
import org.joml.SimplexNoise;
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
    private static final int STAR_COUNT = 20000;
    private static final int STAR_FIELD_RADIUS = 850;
    private static final int WORLEY_POINT_COUNT = 32;
    private static final float STAR_NOISE_SCALE = 0.005F;
    private static final double WORLEY_MIN_DISTANCE = 100.0;
    private static final double STAR_NOISE_THRESHOLD = 0.4;

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
        final int starCount = STAR_COUNT;
        final int size = STAR_FIELD_RADIUS;

        int numPoints = WORLEY_POINT_COUNT;
        Vector3d[] points = new Vector3d[numPoints];
        for (int i = 0; i < numPoints; i++) {
            points[i] = new Vector3d(
                    random.nextInt(size * 2) - size,
                    random.nextInt(size * 2) - size,
                    random.nextInt(size * 2) - size
            );
        }

        for (int i = 0; i < starCount; i++) {
            int x = random.nextInt((size * 2) + 1) - size;
            int y = random.nextInt((size * 2) + 1) - size;
            int z = random.nextInt((size * 2) + 1) - size;

            double noise = (SimplexNoise.noise(x * STAR_NOISE_SCALE, y * STAR_NOISE_SCALE, z * STAR_NOISE_SCALE) + 1) * 0.5;

            double minDist = Double.MAX_VALUE;
            for (Vector3d p : points) {
                double dx = x - p.x;
                double dy = y - p.y;
                double dz = z - p.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                minDist = Math.min(minDist, dist);
            }

            if (minDist > WORLEY_MIN_DISTANCE && noise > STAR_NOISE_THRESHOLD) {
                this.addCelestialBody(
                        CelestialBodyType.STAR,
                        x, y, z,
                        random.nextFloat(0.3f) + 1,
                        random.nextDouble(360) + 1
                );
            }
        }
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
        for (CelestialBodyType type : CelestialBodyType.values()) {
            CelestialBodyRenderer renderer = renderers.get(type);
            if (renderer != null) {
                List<CelestialBody> bodies = celestialBodies.get(type);
                renderer.renderAll(bodies, worldRenderContext);
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
