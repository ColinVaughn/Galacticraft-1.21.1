package dev.galacticraft.mod.meteor;

import dev.galacticraft.mod.world.dimension.meteor.AtmosphereProfile;
import dev.galacticraft.mod.world.dimension.meteor.MeteorPhysics;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidState;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeteorFlightTimingTest {
    private static final AtmosphereProfile MOON = new AtmosphereProfile(0, 1, 1.62, 64);

    /** Mirrors the entity's physics step and movement from its 480-block entry to ground at 64. */
    private static int[] descent(double speed) {
        double y = 480;
        double mass = 20_000;
        MeteoroidState state = new MeteoroidState(mass, 0, -speed, 0, MOON.altitudeOf(y));
        int ticks = 0, final128Ticks = 0;
        while (y > 64 && ticks < 1200) {
            if (y <= 64 + 128) final128Ticks++;
            var step = MeteorPhysics.step(state.withAltitude(MOON.altitudeOf(y)), MeteoroidClass.IRON, MOON, mass);
            assertFalse(step.burnedOut());
            state = step.state();
            y += MeteorPhysics.toBlocksPerTick(state.vy());
            ticks++;
        }
        assertTrue(y <= 64, "meteor never reached the ground");
        assertEquals(mass, state.mass(), "slower flight must not consume a vacuum meteor");
        double entryEnergy = 0.5 * mass * speed * speed;
        assertEquals(entryEnergy, state.kineticEnergy(), entryEnergy * 0.001,
                "slowing visible movement must preserve the body's impact strength");
        return new int[]{ticks, final128Ticks};
    }

    @Test void fastestMeteorLeavesTimeToSeeItsDescentAndApproach() {
        int[] flight = descent(30_000);
        System.out.printf("Fast meteor: %.2fs full descent, %.2fs in final 128 blocks%n", flight[0] / 20.0, flight[1] / 20.0);
        assertTrue(flight[0] >= 140, "full descent was only " + flight[0] / 20.0 + " seconds");
        assertTrue(flight[1] >= 40, "final approach was only " + flight[1] / 20.0 + " seconds");
    }

    @Test void typicalMeteorHasSeveralSecondsOfVisibleApproach() {
        int[] flight = descent(20_000);
        System.out.printf("Typical meteor: %.2fs full descent, %.2fs in final 128 blocks%n", flight[0] / 20.0, flight[1] / 20.0);
        assertTrue(flight[0] >= 200, "full descent was only " + flight[0] / 20.0 + " seconds");
        assertTrue(flight[1] >= 60, "final approach was only " + flight[1] / 20.0 + " seconds");
    }

    @Test void physicsAltitudeAndEntityMovementAdvanceOnTheSameClock() {
        double mass = 20_000;
        var state = new MeteoroidState(mass, 3000, -20_000, 4000, MOON.altitudeOf(480));
        var next = MeteorPhysics.step(state, MeteoroidClass.IRON, MOON, mass).state();
        assertEquals((next.altitude() - state.altitude()) / AtmosphereProfile.ALTITUDE_COMPRESSION,
                MeteorPhysics.toBlocksPerTick(next.vy()), 0.00001);
        assertEquals(-20_000 - MOON.gravity() * MeteorPhysics.TICK_SECONDS * MeteorPhysics.FLIGHT_TIME_SCALE,
                next.vy(), 0.000001);
    }

    @Test void fastestEntryAndFragmentMotionSurviveTheVanillaVelocityPacket() {
        Vec3 motion = new Vec3(MeteorPhysics.toBlocksPerTick(30_200),
                MeteorPhysics.toBlocksPerTick(-30_200), MeteorPhysics.toBlocksPerTick(4000));
        var packet = new ClientboundSetEntityMotionPacket(1, motion);
        assertEquals(motion.x, packet.getXa(), 1.0 / 8000);
        assertEquals(motion.y, packet.getYa(), 1.0 / 8000);
        assertEquals(motion.z, packet.getZa(), 1.0 / 8000);
    }
}
