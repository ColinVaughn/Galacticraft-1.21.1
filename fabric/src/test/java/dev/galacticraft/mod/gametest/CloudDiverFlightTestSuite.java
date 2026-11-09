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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntConsumer;

/**
 * Flies the Cloud Diver through its server control path: inertia, eased turning, riders turning with the hull, buoyancy.
 * Inputs and checks follow the diver's own tick count so phase lengths are exact.
 */
public final class CloudDiverFlightTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void thrustBuildsCruisesAndCoasts(GameTestHelper helper) {
        var diver = launch(helper, 40, CloudDiverEntity.ENERGY_CAPACITY);
        var pilot = pilot(helper, diver);
        Vec3 start = diver.position();
        script(helper, diver, tick -> fly(diver, tick < 60, false), Map.of(5, () -> {
            double speed = diver.getDeltaMovement().length();
            helper.assertTrue(speed > 0.03 && speed < 0.25, "Thrust was not gradual: " + speed);
        }, 60, () -> {
            Vec3 velocity = diver.getDeltaMovement();
            helper.assertTrue(velocity.z > CloudDiverEntity.MAX_SPEED * 0.9 && Math.abs(velocity.x) < 0.01,
                    "Did not reach cruise along its heading: " + velocity);
            helper.assertTrue(diver.position().z - start.z > 10, "Did not travel forward");
        }, 66, () -> {
            double speed = diver.getDeltaMovement().length();
            helper.assertTrue(speed > 0.15 && speed < CloudDiverEntity.MAX_SPEED * 0.92, "Did not coast after release: " + speed);
        }, 150, () -> {
            helper.assertTrue(diver.getDeltaMovement().length() < 0.01, "Did not come to a hover: " + diver.getDeltaMovement());
            helper.assertTrue(Math.abs(diver.getY() - start.y) < 0.01, "Hover drifted vertically: " + (diver.getY() - start.y));
            land(helper, diver, pilot);
        }));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void turningEasesInAndOutAndRidersTurnWithTheHull(GameTestHelper helper) {
        var diver = launch(helper, 55, CloudDiverEntity.ENERGY_CAPACITY);
        var pilot = pilot(helper, diver);
        float hullStart = diver.getYRot(), riderStart = pilot.getYRot();
        script(helper, diver, tick -> fly(diver, false, tick < 50), Map.of(4, () -> {
            float turned = Mth.wrapDegrees(diver.getYRot() - hullStart);
            helper.assertTrue(turned > 0.5F && turned < 4 * CloudDiverEntity.TURN_SPEED * 0.8F, "Turn did not ease in: " + turned);
        }, 48, () -> {
            float rate = Mth.wrapDegrees(diver.getYRot() - diver.yRotO);
            helper.assertTrue(Math.abs(rate - CloudDiverEntity.TURN_SPEED) < 0.15F, "Did not settle at the turn rate: " + rate);
        }, 53, () -> {
            float rate = Mth.wrapDegrees(diver.getYRot() - diver.yRotO);
            helper.assertTrue(rate > 0.1F && rate < CloudDiverEntity.TURN_SPEED * 0.9F, "Turn stopped instead of easing out: " + rate);
        }, 110, () -> {
            helper.assertTrue(diver.getYRot() == diver.yRotO, "Turn never settled");
            float hull = Mth.wrapDegrees(diver.getYRot() - hullStart), rider = Mth.wrapDegrees(pilot.getYRot() - riderStart);
            helper.assertTrue(hull > 90, "Barely turned: " + hull);
            helper.assertTrue(Math.abs(hull - rider) < 0.5F, "Rider did not turn with the hull: hull " + hull + ", rider " + rider);
            land(helper, diver, pilot);
        }));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void emergencyAscentOverridesTheThrottle(GameTestHelper helper) {
        var diver = launch(helper, 70, 500);
        var pilot = pilot(helper, diver);
        script(helper, diver, tick -> fly(diver, true, false), Map.of(40, () -> {
            Vec3 velocity = diver.getDeltaMovement();
            helper.assertTrue(diver.alarm(), "Low power did not raise the alarm");
            helper.assertTrue(velocity.y > 0.25 && velocity.horizontalDistance() < 0.05, "Emergency ascent did not take over: " + velocity);
            land(helper, diver, pilot);
        }));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void emptyDiverHoldsStation(GameTestHelper helper) {
        var diver = launch(helper, 100, CloudDiverEntity.ENERGY_CAPACITY);
        diver.setDeltaMovement(0.3, 0, 0);
        double y = diver.getY();
        script(helper, diver, tick -> { }, Map.of(70, () -> {
            helper.assertTrue(diver.getDeltaMovement().length() < 0.01, "Empty diver kept drifting: " + diver.getDeltaMovement());
            helper.assertTrue(Math.abs(diver.getY() - y) < 0.01, "Empty diver sank or rose");
            release(helper, diver);
        }));
    }

    /**
     * Feeds input every tick and runs each check once the diver has lived that many ticks. Keyed to the diver's own age,
     * because its chunk can start entity-ticking a few ticks after the test does.
     */
    private static void script(GameTestHelper helper, CloudDiverEntity diver, IntConsumer input, Map<Integer, Runnable> checks) {
        var due = new TreeMap<>(checks);
        helper.onEachTick(() -> {
            if (diver.isRemoved()) return;
            input.accept(diver.tickCount);
            while (!due.isEmpty() && due.firstKey() <= diver.tickCount) due.pollFirstEntry().getValue().run();
        });
    }

    private static CloudDiverEntity launch(GameTestHelper helper, int height, int energy) {
        var diver = GCEntityTypes.CLOUD_DIVER.create(helper.getLevel());
        helper.assertTrue(diver != null, "Cloud Diver did not create");
        // Fly well above the test grid so neighbouring tests and their structures stay out of the way.
        Vec3 at = helper.absoluteVec(new Vec3(1.5, height, 1.5));
        // Test chunks only entity-tick near the structure; a cruising diver would freeze once it flew out of them.
        forceChunks(helper, at);
        diver.moveTo(at.x, at.y, at.z, 0, 0);
        diver.acceptEnergy(energy);
        diver.acceptOxygen(CloudDiverEntity.OXYGEN_CAPACITY);
        helper.getLevel().addFreshEntity(diver);
        return diver;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer pilot(GameTestHelper helper, CloudDiverEntity diver) {
        var pilot = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(pilot.startRiding(diver, true), "Pilot could not board");
        return pilot;
    }

    private static void fly(CloudDiverEntity diver, boolean forward, boolean right) {
        if (!diver.isRemoved()) diver.inputTick(0, 0, forward, false, false, right, false, false, false);
    }

    private static void land(GameTestHelper helper, CloudDiverEntity diver, ServerPlayer pilot) {
        pilot.stopRiding();
        pilot.discard();
        release(helper, diver);
    }

    private static void release(GameTestHelper helper, CloudDiverEntity diver) {
        diver.discard();
        helper.succeed();
    }

    /**
     * Never unforced: the framework forces its own test chunks the same way, so clearing ours would freeze neighbouring
     * tests. The GameTest world is discarded after the run.
     */
    private static void forceChunks(GameTestHelper helper, Vec3 around) {
        int cx = Mth.floor(around.x) >> 4, cz = Mth.floor(around.z) >> 4;
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) helper.getLevel().setChunkForced(x, z, true);
    }
}
