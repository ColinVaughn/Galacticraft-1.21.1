package dev.galacticraft.mod.world.dimension.meteor;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeteorShowerPlacementTest {
    @Test void targetsStayInTheDistantRingAndCoverAllDirections() {
        RandomSource random = RandomSource.create(41021);
        int[] quadrants = new int[4];
        for (int i = 0; i < 20_000; i++) {
            Vec3 offset = MeteorShowerManager.targetOffset(random, 256);
            assertEquals(0, offset.y);
            assertTrue(offset.lengthSqr() >= 96 * 96 - 0.00001);
            assertTrue(offset.lengthSqr() <= 256 * 256 + 0.00001);
            quadrants[(offset.x < 0 ? 2 : 0) + (offset.z < 0 ? 1 : 0)]++;
        }
        for (int count : quadrants) assertEquals(5_000, count, 250);
    }

    @Test void equalAreasGetEqualNumbersOfTargetsInsteadOfFavoringTheInnerRing() {
        for (int maxOffset : new int[]{144, 256}) {
            RandomSource random = RandomSource.create(631);
            double midpointSquared = (96.0 * 96 + maxOffset * maxOffset) / 2;
            int inner = 0;
            for (int i = 0; i < 20_000; i++) {
                if (MeteorShowerManager.targetOffset(random, maxOffset).lengthSqr() < midpointSquared) inner++;
            }
            assertEquals(10_000, inner, 250);
        }
    }

    @Test void shortSimulationRangesCannotForceTargetsBackTowardBases() {
        int unavailable = MeteorShowerManager.targetMaxOffset(4);
        assertTrue(unavailable < 96);
        assertThrows(IllegalArgumentException.class,
                () -> MeteorShowerManager.targetOffset(RandomSource.create(1), unavailable));
        assertEquals(144, MeteorShowerManager.targetMaxOffset(8));
        assertEquals(256, MeteorShowerManager.targetMaxOffset(32));
    }

    @Test void aDistantTargetCannotBringTheFlightPathThroughABase() {
        assertFalse(MeteorShowerManager.pathClearsPlayers(new Vec3(-200, 500, 0),
                new Vec3(200, 64, 0), List.of(Vec3.ZERO)));
        assertTrue(MeteorShowerManager.pathClearsPlayers(new Vec3(-200, 500, 120),
                new Vec3(200, 64, 120), List.of(Vec3.ZERO)));
    }

    @Test void placementChecksOtherPlayersAndIgnoresTheirHeight() {
        Vec3 start = new Vec3(160, 500, 160), target = new Vec3(160, 70, 160);
        assertTrue(MeteorShowerManager.pathClearsPlayers(start, target, List.of(Vec3.ZERO)));
        assertFalse(MeteorShowerManager.pathClearsPlayers(start, target,
                List.of(Vec3.ZERO, new Vec3(170, -60, 160))));
        assertFalse(MeteorShowerManager.pathClearsPlayers(new Vec3(-160, 500, -160),
                new Vec3(-160, 70, -160), List.of(new Vec3(-170, 300, -160))));
    }

    @Test void clearanceUsesTheSegmentIncludingBothEndpoints() {
        Vec3 start = new Vec3(120, 500, 0), target = new Vec3(200, 64, 0);
        assertTrue(MeteorShowerManager.pathClearsPlayers(start, target, List.of(Vec3.ZERO)));
        assertFalse(MeteorShowerManager.pathClearsPlayers(start, target, List.of(new Vec3(50, 0, 0))));
        assertFalse(MeteorShowerManager.pathClearsPlayers(start, target, List.of(new Vec3(250, 0, 0))));
        assertTrue(MeteorShowerManager.pathClearsPlayers(start, target, List.of(new Vec3(350, 0, 0))));
    }
}
