package dev.galacticraft.mod.world.gen.carver;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LunarChamberTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void coldTrapsHaveADescendingApproachAndRaisedSideLedge() {
        var room = room(LunarChamber.Kind.COLD_TRAP);
        assertTrue(room.floorLimit(0, 0) < room.floorLimit(0, 0.22));
        double previous = -1;
        for (double along = 0; along < 0.9; along += 0.05) {
            double floor = room.floorLimit(along, 0);
            assertTrue(floor >= previous);
            previous = floor;
        }
    }

    @Test void collapsePillarsSurviveAtWalkingAndCeilingHeight() {
        var room = room(LunarChamber.Kind.COLLAPSE);
        assertFalse(room.skip(0.1, 0, 0));
        assertTrue(room.skip(-0.30, 0, -0.25));
        assertTrue(room.skip(-0.30, 0.5, -0.25));
        assertTrue(room.skip(1, 0, 0));
    }

    @Test void impactRoutesGoAroundARetainedCentralCore() {
        var room = room(LunarChamber.Kind.IMPACT_CORE);
        assertTrue(room.skip(0.06, 0, 0));
        assertFalse(room.skip(0.06, 0, 0.40));
        assertTrue(room.floorLimit(0.8, 0) - room.floorLimit(0, 0.40) > 0.35);
    }

    @Test void approachesCannotCutThroughPillarsAndBridges() {
        var impact = room(LunarChamber.Kind.IMPACT_CORE);
        assertTrue(LunarChamber.protectedStone(List.of(impact), impact.x() + 1, impact.y(), impact.z()));
        var helion = room(LunarChamber.Kind.HELION);
        assertTrue(helion.skip(0, -0.30, 0.35));
        assertFalse(helion.skip(0, -0.65, 0.35));
        assertFalse(helion.skip(0, 0.10, 0.35));
        assertFalse(LunarChamber.protectedStone(List.of(helion), helion.x(), helion.y() + 15, helion.z()));
    }

    @Test void roomsHaveDifferentSilhouettesWithoutAnyDecoration() throws java.io.IOException {
        var silhouettes = new java.util.HashSet<String>();
        java.nio.file.Path preview = java.nio.file.Path.of("build", "moon-chamber-shapes.csv");
        java.nio.file.Files.createDirectories(preview.getParent());
        try (var writer = java.nio.file.Files.newBufferedWriter(preview)) {
            writer.write("kind,x,y,z\n");
            for (var kind : LunarChamber.Kind.values()) {
                var room = room(kind);
                StringBuilder mask = new StringBuilder();
                int minimum = 40, maximum = -40;
                for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) {
                    int air = 0;
                    for (int y = -18; y <= 18; y++) if (!room.skip(x / room.radius(), y / room.height(), z / room.radius())) {
                        air++; minimum = Math.min(minimum, y); maximum = Math.max(maximum, y);
                        writer.write(kind + "," + x + "," + y + "," + z + "\n");
                    }
                    mask.append((char) (air + 32));
                }
                assertTrue(maximum - minimum >= (kind == LunarChamber.Kind.CHEESE ? 4 : 9), kind + " has insufficient headroom");
                assertTrue(silhouettes.add(mask.toString()), kind + " repeats another room's geometry");
            }
        }
    }

    @Test void everyRoomHasAWalkingRouteBetweenItsApproaches() {
        for (var kind : LunarChamber.Kind.values()) for (long seed : new long[]{0, 64, 128, 192}) {
            var sample = room(kind);
            var room = new LunarChamber(0, 0, 0, sample.radius(), sample.height(), 0, kind, seed);
            var walkable = new java.util.HashSet<net.minecraft.core.BlockPos>();
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = -17; y < 15; y++) {
                if (!room.skip(x / room.radius(), y / room.height(), z / room.radius())
                        && !room.skip(x / room.radius(), (y + 1) / room.height(), z / room.radius())
                        && !room.skip(x / room.radius(), (y + 2) / room.height(), z / room.radius())
                        && room.skip(x / room.radius(), (y - 1) / room.height(), z / room.radius())) {
                    walkable.add(new net.minecraft.core.BlockPos(x, y, z));
                }
            }
            var start = walkable.stream().filter(pos -> pos.getX() == -16)
                    .min(java.util.Comparator.comparingInt(pos -> Math.abs(pos.getZ()))).orElseThrow();
            var visited = new java.util.HashSet<net.minecraft.core.BlockPos>();
            var pending = new java.util.ArrayDeque<net.minecraft.core.BlockPos>();
            pending.add(start); visited.add(start);
            while (!pending.isEmpty()) {
                var current = pending.removeFirst();
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int rise = -1; rise <= 1; rise++) {
                    var next = current.relative(direction).above(rise);
                    if (walkable.contains(next) && visited.add(next)) pending.addLast(next);
                }
            }
            assertTrue(visited.stream().anyMatch(pos -> pos.getX() == 16), kind + " blocks its exit for seed " + seed);
        }
    }

    @Test void connectorsIncludeBothEndsAndNeverLeaveAGap() {
        List<double[]> points = new ArrayList<>();
        LunarImpactCavernCarver.connectorPoints(-20, 8, -16, 34, -3, 25, points::add);
        assertArrayEquals(new double[]{-20, 8, -16}, points.getFirst());
        assertArrayEquals(new double[]{34, -3, 25}, points.getLast());
        for (int index = 1; index < points.size(); index++) {
            double[] a = points.get(index - 1), b = points.get(index);
            assertTrue(Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2)) <= 1.401);
        }
    }

    @Test void bypassesReconnectAtTheExactEndpointsAcrossLevels() {
        var start = new LunarLavaTubeCarver.TubeNode(-16, 38, -1, 3, 2.5, 0);
        var end = new LunarLavaTubeCarver.TubeNode(22, 27, -1, 3, 2.5, 0);
        var path = LunarLavaTubeCarver.createBypass(RandomSource.create(42), start, end, 2.8, 1);
        assertEquals(start.x(), path.getFirst().x()); assertEquals(start.y(), path.getFirst().y());
        assertEquals(end.x(), path.getLast().x(), 1e-9); assertEquals(end.y(), path.getLast().y(), 1e-9);
        for (int index = 1; index < path.size(); index++) assertTrue(Math.abs(path.get(index).y() - path.get(index - 1).y()) < 0.6);
    }

    @Test void skylightsUseWorldTerrainAndRejectExcessiveOverburden() {
        var terrain = DensityFunctions.yClampedGradient(40, 64, 1, -1);
        assertEquals(52, LunarLavaTubeCarver.surfaceHeight(terrain, 15, -16, 25));
        assertEquals(52, LunarLavaTubeCarver.surfaceHeight(terrain, 16, -16, 25));
        assertEquals(-1, LunarLavaTubeCarver.surfaceHeight(terrain, 16, -16, -20));
        assertEquals(-1, LunarLavaTubeCarver.surfaceHeight(DensityFunctions.constant(-1), 16, -16, 25));
    }

    @Test void decorationSeedsDoNotDependOnOtherCandidatesOrChunkOrder() {
        long seed = LunarChamber.positionSeed(42, -16, 15);
        RandomSource random = RandomSource.create(seed);
        double expected = random.nextDouble();
        for (int index = 0; index < 200; index++) RandomSource.create(LunarChamber.positionSeed(42, index, 16)).nextLong();
        assertEquals(expected, RandomSource.create(LunarChamber.positionSeed(42, -16, 15)).nextDouble());
        assertNotEquals(seed, LunarChamber.positionSeed(42, -15, 15));
    }

    private static LunarChamber room(LunarChamber.Kind kind) {
        return new LunarChamber(16, 20, 16, 20, kind == LunarChamber.Kind.COLD_TRAP ? 18
                : kind == LunarChamber.Kind.CHEESE ? 9 : kind == LunarChamber.Kind.HELION ? 14 : 12, 0, kind, 0);
    }
}
