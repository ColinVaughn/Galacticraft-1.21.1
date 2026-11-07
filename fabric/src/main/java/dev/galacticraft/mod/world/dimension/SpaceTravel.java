/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.world.dimension;

import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.landable.Landable;
import dev.galacticraft.mod.Constant;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import java.util.Comparator;

/** Shared coordinates for physical destinations, rendering and server arrival checks. */
public final class SpaceTravel {
    private static final TicketType<Integer> FLIGHT_TICKET = TicketType.create("galacticraft_spaceflight", Integer::compareTo, 100);

    public static final double PRELOAD_DISTANCE = 768;
    public static final double ENTRY_DISTANCE = 128;
    public static final int LANDING_TERRAIN_RADIUS = 6;
    public static final double CRUISE_SPEED = 1.5;
    public static final double BOOST_SPEED = 8;
    public static final double ACCELERATION = .15;

    public static final Vec3 SUN_POSITION = new Vec3(-8000, 1024, 0);
    /** Slightly larger than Jupiter; the true Sun (109 Earths) would swallow the inner orbits at these distances. */
    public static final double SUN_RADIUS = 1200;
    /** The Sun's equatorial rotation (25.4 days) and the tilt of its axis (7.25 degrees). */
    public static final Spin SUN_SPIN = new Spin(25.38, 7.25, 0);

    /** Legacy nominal stock geometry for binary compatibility and pure geometry tests. Gameplay uses destinations(registries). */
    @Deprecated
    public static final List<Destination> DESTINATIONS = dev.galacticraft.mod.content.GCSpaceflight.DEFAULTS.stream()
            .filter(config -> !config.body().location().getPath().equals("sol")).map(SpaceTravel::destination).toList();

    private static Destination destination(dev.galacticraft.api.universe.celestialbody.SpaceflightConfig config) {
        return new Destination(config.body(), config.center(), config.radius(), config.texture(),
                new Spin(config.period(), config.tilt(), config.phase()));
    }

    /** Rebuilt from registry values so new entries and replacement values cannot leave a stale route cache. */
    public static List<Destination> bodies(RegistryAccess registries) {
        var configs = registries.registry(AddonRegistries.SPACEFLIGHT);
        if (configs.isEmpty()) return DESTINATIONS;
        return configs.get().entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().location()))
                .map(entry -> destination(entry.getValue())).filter(body -> body.resolve(registries) != null).toList();
    }

    public static List<Destination> destinations(RegistryAccess registries) {
        return bodies(registries).stream().filter(body -> body.resolve(registries).type() instanceof Landable).toList();
    }

    public static net.minecraft.resources.ResourceKey<CelestialBody<?, ?>> rootStar(RegistryAccess registries, net.minecraft.resources.ResourceKey<CelestialBody<?, ?>> key) {
        var registry = registries.registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        var visited = new java.util.HashSet<net.minecraft.resources.ResourceKey<CelestialBody<?, ?>>>();
        while (visited.add(key)) {
            var body = registry.get(key);
            if (body == null || body.parent().isEmpty()) return key;
            key = body.<CelestialBody<?, ?>>parent().get();
        }
        return key;
    }

    public static List<Destination> systemBodies(RegistryAccess registries, Destination observer) {
        var root = rootStar(registries, observer.body());
        return bodies(registries).stream().filter(body -> rootStar(registries, body.body()).equals(root)).toList();
    }

    public static Destination sun(RegistryAccess registries, Destination observer, boolean surface) {
        var root = rootStar(registries, observer.body());
        var star = bodies(registries).stream().filter(body -> body.body().equals(root)).findFirst();
        if (star.isPresent()) {
            var value = star.get();
            double radius = surface ? registries.registryOrThrow(AddonRegistries.SPACEFLIGHT).stream()
                    .filter(config -> config.body().equals(root)).findFirst().map(config -> config.skyRadius()).orElse(42.0) : value.radius();
            return new Destination(value.body(), value.center(), radius, value.texture(), value.spin());
        }
        return new Destination(root, SUN_POSITION, surface ? 42 : SUN_RADIUS,
                Constant.id("textures/environment/solar_system/sun.png"), SUN_SPIN);
    }

    private static Vec3 orbit(double radius, double phase) {
        double angle = Math.toRadians(phase);
        return SUN_POSITION.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
    }

    private SpaceTravel() { }

    /** Solar time in Earth days: one Earth day passes in one Minecraft day. Server and clients share game time. */
    public static double days(Level level) {
        return level.getGameTime() / 24000.0;
    }

    public static double days(Level level, float partialTick) {
        return (level.getGameTime() + partialTick) / 24000.0;
    }

    /**
     * How a body turns: sidereal rotation period in Earth days (0 for none), axial tilt and starting phase in degrees.
     * The spin runs about the body's own north axis (its cube net's top face), which is then tilted about world Z.
     */
    public record Spin(double period, double tilt, double phase) {
        public static final Spin NONE = new Spin(0, 0, 0);

        /** Body-to-world rotation at a time: positive spin turns the surface eastward (prograde). */
        public Matrix3d orientation(double days) {
            double angle = (phase + (period == 0 ? 0 : 360 * (days / period % 1))) % 360;
            return new Matrix3d().rotateZ(Math.toRadians(tilt)).rotateY(Math.toRadians(angle));
        }
    }

    public record Destination(ResourceKey<CelestialBody<?, ?>> body, Vec3 center, double radius, ResourceLocation texture, Spin spin) {
        public Destination(ResourceKey<CelestialBody<?, ?>> body, Vec3 center, double radius, ResourceLocation texture) {
            this(body, center, radius, texture, Spin.NONE);
        }

        public @Nullable CelestialBody<?, ?> resolve(RegistryAccess registries) {
            return registries.registryOrThrow(AddonRegistries.CELESTIAL_BODY).get(body);
        }

        public @Nullable ServerLevel level(ServerLevel source) {
            CelestialBody<?, ?> value = resolve(source.registryAccess());
            return value != null && value.type() instanceof Landable landable
                    ? source.getServer().getLevel(landable.world(value.config())) : null;
        }

        /** Signed distance to the cube as it is turned at that time: radius is the half-width of each face. */
        public double distance(Vec3 position, double days) {
            return cubeDistance(local(position, days));
        }

        public Vec3 approachPosition(Vec3 position, double altitude, double days) {
            var offset = local(position, days);
            var surface = new Vector3d(Math.clamp(offset.x, -radius, radius), Math.clamp(offset.y, -radius, radius),
                    Math.clamp(offset.z, -radius, radius));
            var outward = new Vector3d(offset).sub(surface);
            if (outward.lengthSquared() < 1e-12) {
                // Saved spherical routes can start inside the new cube; use the closest face.
                double x = Math.abs(offset.x), y = Math.abs(offset.y), z = Math.abs(offset.z);
                outward = x >= y && x >= z ? new Vector3d(Math.copySign(1, offset.x), 0, 0)
                        : y >= z ? new Vector3d(0, Math.copySign(1, offset.y), 0) : new Vector3d(0, 0, Math.copySign(1, offset.z));
                surface = new Vector3d(offset).fma(-cubeDistance(offset), outward);
            }
            var world = spin.orientation(days).transform(surface.fma(altitude, outward.normalize()));
            return center.add(world.x, world.y, world.z);
        }

        /** Outside even the cube's corners, so no turn of the body can sweep over it. */
        public Vec3 departurePosition() {
            return center.add(0, 0, radius * Math.sqrt(3) + 160);
        }

        /** A world position in the body's own frame, relative to its centre. */
        private Vector3d local(Vec3 position, double days) {
            var offset = position.subtract(center);
            return spin.orientation(days).transformTranspose(new Vector3d(offset.x, offset.y, offset.z));
        }

        private double cubeDistance(Vector3d offset) {
            double x = Math.abs(offset.x) - radius, y = Math.abs(offset.y) - radius, z = Math.abs(offset.z) - radius;
            double dx = Math.max(x, 0), dy = Math.max(y, 0), dz = Math.max(z, 0);
            return Math.sqrt(dx * dx + dy * dy + dz * dz) + Math.min(Math.max(x, Math.max(y, z)), 0);
        }
    }

    public static @Nullable Destination fromLevel(Level level) {
        var holder = level.galacticraft$getCelestialBody();
        if (holder == null) return null;
        return destinations(level.registryAccess()).stream().filter(d -> holder.is(d.body())).findFirst().orElse(null);
    }

    public static @Nullable Destination nearest(RegistryAccess registries, Vec3 position, double days) {
        return destinations(registries).stream().min(Comparator.comparingDouble(d -> d.distance(position, days))).orElse(null);
    }

    public static @Nullable Destination preloadTarget(RegistryAccess registries, Vec3 position, Vec3 velocity) {
        return preloadTarget(destinations(registries), position, velocity);
    }

    public static Destination nearest(Vec3 position, double days) {
        return DESTINATIONS.stream().min(Comparator.comparingDouble(d -> d.distance(position, days))).orElseThrow();
    }

    /** Start warming the body on the flight path while there is still cruise time to generate its landing area. */
    public static @Nullable Destination preloadTarget(Vec3 position, Vec3 velocity) {
        return preloadTarget(DESTINATIONS, position, velocity);
    }

    private static @Nullable Destination preloadTarget(List<Destination> destinations, Vec3 position, Vec3 velocity) {
        if (velocity.lengthSqr() < .01) return null;
        Vec3 direction = velocity.normalize();
        Destination target = null;
        double nearest = PRELOAD_DISTANCE * 4;
        for (var body : destinations) {
            Vec3 offset = body.center().subtract(position);
            double along = offset.dot(direction);
            double corridor = body.radius() * Math.sqrt(3) + ENTRY_DISTANCE;
            if (along > 0 && along < nearest && offset.lengthSqr() - along * along <= corridor * corridor) {
                target = body;
                nearest = along;
            }
        }
        return target;
    }

    /** Short-lived tickets start generation without blocking the server thread or retaining old routes. */
    public static boolean prepareArrival(ServerLevel level, Vec3 position, int rocketId) {
        ChunkPos center = new ChunkPos(BlockPos.containing(position));
        int radius = level.dimension().equals(GCDimensions.SOLAR_SYSTEM) ? 2 : LANDING_TERRAIN_RADIUS + 1;
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, center, radius + 1, rocketId);
        // Meshes need neighbor chunks, and FULL alone does not mean vanilla can send their lighting yet.
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (level.getChunkSource().chunkMap.getChunkToSend(ChunkPos.asLong(center.x + x, center.z + z)) == null) return false;
        }
        return true;
    }

    /** GC4 player arrival heights, with clearance for terrain taller than the old worlds. */
    public static double landingHeight(ResourceKey<Level> destination, int ground) {
        int entry = destination.equals(Level.OVERWORLD) ? 250 : destination.equals(GCDimensions.ASTEROID) ? 310 : 900;
        return Math.max(entry, ground + 64.0);
    }

    /** The rocket model points up at pitch zero, unlike Minecraft's horizontal look vector. */
    public static Vec3 forward(float pitch, float yaw) {
        double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
        return new Vec3(-Math.sin(p) * Math.sin(y), Math.cos(p), Math.sin(p) * Math.cos(y));
    }

    public static Vec3 accelerate(Vec3 velocity, Vec3 target) {
        return accelerate(velocity, target, ACCELERATION);
    }

    public static Vec3 accelerate(Vec3 velocity, Vec3 target, double acceleration) {
        Vec3 change = target.subtract(velocity);
        return velocity.add(change.length() <= acceleration ? change : change.normalize().scale(acceleration));
    }

    /**
     * Boost speed by engine tier: a quarter faster per tier above one, capped at tier three.
     * A tick of flight stays shorter than the atmospheric entry band.
     */
    public static double boostSpeed(int tier) {
        return BOOST_SPEED * (1 + .25 * (Math.clamp(tier, 1, 3) - 1));
    }

    /** Acceleration scaled with the tier's top speed, so every tier reaches full boost in the same time. */
    public static double acceleration(int tier) {
        return ACCELERATION * boostSpeed(tier) / BOOST_SPEED;
    }
}