package dev.galacticraft.mod.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BurrowerPathTest {
    @Test void tunnelingFollowsPitchAndYawWithoutDiagonalSpeedBoosts() {
        var straight = BurrowerPath.riddenMotion(180, 0, 1, 0, 0.3F);
        var down = BurrowerPath.riddenMotion(180, 45, 1, 0, 0.3F);
        var up = BurrowerPath.riddenMotion(180, -45, 1, 0, 0.3F);
        assertTrue(straight.z < 0 && Math.abs(straight.y) < 0.000001);
        assertTrue(down.z < 0 && down.y < -0.2);
        assertTrue(up.z < 0 && up.y > 0.2);
        assertEquals(0.3, up.length(), 0.00001);
        assertEquals(0.3, BurrowerPath.riddenMotion(90, -90, 1, 1, 0.3F).length(), 0.00001);
        assertEquals(0.075, BurrowerPath.riddenMotion(180, 0, -1, 0, 0.3F).length(), 0.00001);
        assertEquals(0, BurrowerPath.riddenMotion(180, 45, 0, 0, 0.3F).length());
    }
    @Test void ridingWaveTravelsAlongTheBodyAndNeverLiftsTheHeadOrEntersTheFloor() {
        assertEquals(Vec3.ZERO, BurrowerPath.rideOffset(0, 1.5, 1));
        assertEquals(Vec3.ZERO, BurrowerPath.rideOffset(4, 1.5, 0));
        boolean bent = false;
        for (int frame = 0; frame < 48; frame++) {
            double travel = frame * BurrowerPath.RIDE_STRIDE / 48;
            var wave = BurrowerPath.rideOffset(3, travel, 1);
            var following = BurrowerPath.rideOffset(4, travel + BurrowerPath.RIDE_STRIDE / 5.8, 1);
            assertEquals(wave.x, following.x, 0.00001);
            assertEquals(wave.y, following.y, 0.00001);
            assertTrue(wave.y >= 0 && wave.y <= 0.24001);
            assertTrue(Math.abs(wave.x) <= 0.25001);
            bent |= Math.abs(wave.x) > 0.2;
        }
        assertTrue(bent, "Riding remained a straight slide");
    }
    @Test void aStationaryHeadCannotTurnBackThroughTheNeck() {
        float yaw = 180;
        for (int tick = 0; tick < 60; tick++) {
            float next = BurrowerPath.steerYaw(yaw, 0, 180);
            assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(next - yaw)) <= 6.001);
            assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(next - 180)) <= 35.001);
            yaw = next;
        }
        assertEquals(145, yaw, 0.001);
        assertEquals(145, BurrowerPath.clampHeadYaw(0, 180), 0.001);
    }

    @Test void steeringCrossesTheAngleWrapWithoutDoingAFullLoop() {
        float yaw = 358;
        for (int tick = 0; tick < 10; tick++) {
            float next = BurrowerPath.steerYaw(yaw, 5, 350);
            assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(next - yaw)) <= 6.001);
            yaw = next;
        }
        assertEquals(0, net.minecraft.util.Mth.wrapDegrees(yaw - 5), 0.001);
    }
    @Test void straightTravelAndFloorHeightsStayUnchanged() {
        Vec3[] path = new Vec3[41];
        for (int i = 0; i < path.length; i++) path[i] = new Vec3(2, i >= 12 ? 1 : 0, i * 0.25);
        Vec3[] smooth = BurrowerPath.smoothHorizontal(path);
        for (int i = 0; i < path.length; i++) assertEquals(path[i], smooth[i]);
    }

    @Test void aRightAngleBecomesAGradualBendWithEvenSpacing() {
        Vec3[] path = new Vec3[41];
        for (int i = 0; i < path.length; i++) path[i] = i <= 16
                ? new Vec3(i * 0.25, 0, 0) : new Vec3(4, 0, (i - 16) * 0.25);
        Vec3[] smooth = BurrowerPath.smoothHorizontal(path);
        assertEquals(path[0], smooth[0]);
        assertTrue(smooth[16].x < 4 && smooth[16].z > 0, "Corner was not rounded");
        Vec3 previous = BurrowerPath.sample(smooth, 0), previousDirection = null;
        for (double distance = 0.1; distance <= 8; distance += 0.1) {
            Vec3 point = BurrowerPath.sample(smooth, distance);
            Vec3 direction = point.subtract(previous).normalize();
            assertEquals(0.1, point.distanceTo(previous), 0.002, "Body spacing changed at the bend");
            if (previousDirection != null) assertTrue(direction.dot(previousDirection) > Math.cos(Math.toRadians(16)),
                    "A section abruptly pivoted at the corner");
            previous = point; previousDirection = direction;
        }
    }

    @Test void bendsKeepTheirLengthOnSlopedGround() {
        Vec3[] path = {Vec3.ZERO, new Vec3(0, 0.75, 1), new Vec3(1, 0.75, 1)};
        Vec3 point = BurrowerPath.sample(path, 1.75);
        assertEquals(new Vec3(0.5, 0.75, 1), point);
    }
}
