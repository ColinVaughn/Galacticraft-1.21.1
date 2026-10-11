package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.util.BuggyAnimationState;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuggyAnimationsTest {
    private BuggyAnimations animations() {
        var resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> new Resource(null,
                        () -> Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/"
                                + ((net.minecraft.resources.ResourceLocation) args[0]).getPath()))));
        return new BuggyAnimations(resources);
    }

    @Test void exportedClipsUseKnownBonesAndFiniteLinearTracks() {
        var bones = Set.of("Climb", "Chassis", "SteeringWheel", "WheelRight", "WheelLeft", "RearWheelRight",
                "RearWheelLeft", "RightFender", "LeftFender", "RadarDish");
        var json = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/galacticraft/animations/buggy.animation.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations");
        assertEquals(Set.of("animation.buggy.roll", "animation.buggy.steer", "animation.buggy.radar",
                "animation.buggy.suspension", "animation.buggy.climb"), json.keySet());
        json.entrySet().forEach(entry -> {
            var clip = BurrowerAnimations.readClip(entry.getValue().getAsJsonObject());
            if (entry.getKey().equals("animation.buggy.climb")) {
                assertFalse(clip.loop);
                assertEquals(BuggyAnimationState.CLIMB_DURATION, clip.length);
            }
            assertTrue(bones.containsAll(clip.boneNames()));
            for (String bone : clip.boneNames()) for (float time = 0; time <= clip.length; time += 0.025F) {
                var pose = clip.sample(bone, time);
                for (var v : new BurrowerAnimations.Vector[]{pose.position(), pose.rotation(), pose.scale()})
                    assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z()));
            }
        });
    }

    @Test void exportedAxesMatchBlockbenchAndSteeringPartsStayCoupled() {
        var animation = animations();
        assertEquals(-90, animation.sample("roll", "WheelRight", 0.25).rotation().x());
        assertEquals(-90, animation.sample("roll", "WheelLeft", 0.25 + 100_000_000).rotation().x());
        for (float steering : new float[]{-30, 0, 30}) for (String bone : new String[]{
                "WheelRight", "WheelLeft", "RightFender", "LeftFender", "SteeringWheel"})
            assertEquals(steering, animation.steering(bone, steering).rotation().y(), 0.00001);
        for (double time = 0; time < 8; time += 0.05) {
            var matrices = new PoseStack();
            BuggyAnimations.apply(matrices, animation.sample("radar", "RadarDish", time), 1);
            assertTrue(matrices.last().pose().transformDirection(new Vector3f(0, 1, 0)).y > 0.95,
                    "Radar tipped too far to stay mounted");
        }
    }

    @Test void suspensionUsesModelUnitsAndWeightZeroIsRest() {
        var pose = animations().sample("suspension", "Chassis", 0.25);
        var matrices = new PoseStack();
        matrices.scale(0.41F, 0.41F, 0.41F);
        BuggyAnimations.apply(matrices, pose, 1);
        assertEquals(-0.18 / 16, matrices.last().pose().transformPosition(new Vector3f()).y, 0.000001);
        matrices = new PoseStack();
        BuggyAnimations.apply(matrices, pose, 0);
        assertEquals(new org.joml.Matrix4f(), matrices.last().pose());
        assertEquals(pose, animations().sample("suspension", "Chassis", 1.25));
    }

    @Test void observedMotionRollsForwardAndReverseAndIgnoresTeleports() {
        var state = new BuggyAnimationState();
        state.tick(0, 0, 0.5, 0, 30, true, false);
        double turns = 0.5 / BuggyAnimationState.WHEEL_CIRCUMFERENCE;
        assertEquals(0, state.wheelTurns(0));
        assertEquals(turns / 2, state.wheelTurns(0.5F), 0.000001);
        assertEquals(turns, state.wheelTurns(1), 0.000001);
        assertTrue(state.steering(1) > 0 && state.steering(1) < 30);
        assertTrue(state.drive(1) > 0);
        state.tick(0, 0, -0.5, 0, 0, true, false);
        assertEquals(0, state.wheelTurns(1), 0.000001);
        state.tick(-0.5, 0, 0, 90, -30, true, false);
        assertEquals(turns, state.wheelTurns(1), 0.000001);
        state.tick(100, 0, 100, 0, 0, true, false);
        assertEquals(turns, state.wheelTurns(1), 0.000001);
        for (int i = 0; i < 60; i++) state.tick(0, 0, 0, 0, 0, true, false);
        assertEquals(0, state.drive(1), 0.000001);
        assertEquals(0, state.steering(1), 0.000001);
    }

    @Test void climbingBracesWhileRisingThenSettlesAndKeepsTheTrailingAxleAnchored() {
        var animation = animations();
        for (int direction : new int[]{1, -1}) {
            var state = new BuggyAnimationState();
            state.tick(0, 0, 0.2 * direction, 0, 0, true, false);
            state.tick(0, 0.1, 0.1 * direction, 0, 0, false, true);
            assertEquals(0, state.climbTime(0));
            assertEquals(0.025, state.climbTime(0.5F), 0.000001);
            assertEquals(direction, state.climbDirection());
            // Observers can infer the rest of the ascent without local collision flags.
            for (int i = 0; i < 9; i++) state.tick(0, 0.1, 0.1 * direction, 0, 0, false, false);
            assertEquals(BuggyAnimationState.CLIMB_HOLD_TIME, state.climbTime(1));
            var pose = animation.sample("climb", "Climb", state.climbTime(1));
            assertEquals(14, pose.rotation().x());
            var matrices = new PoseStack();
            matrices.scale(0.41F, 0.41F, 0.41F);
            float axleY = dev.galacticraft.mod.client.render.entity.BuggyRenderer.AXLE_Y;
            float axleZ = dev.galacticraft.mod.client.render.entity.BuggyRenderer.AXLE_Z * direction;
            matrices.translate(0, axleY, axleZ);
            BuggyAnimations.apply(matrices, pose, direction);
            matrices.translate(0, -axleY, -axleZ);
            var trailing = matrices.last().pose().transformPosition(new Vector3f(0, axleY, axleZ));
            assertEquals(axleY * 0.41F, trailing.y, 0.000001);
            assertEquals(axleZ * 0.41F, trailing.z, 0.000001);
            var leading = matrices.last().pose().transformPosition(new Vector3f(0, axleY, -axleZ));
            assertTrue(leading.y > trailing.y + 0.5, "Leading wheels must lift in the direction of travel");
            for (int i = 0; i < 3; i++) state.tick(0, 0, 0.1 * direction, 0, 0, true, false);
            assertEquals(7, animation.sample("climb", "Climb", state.climbTime(1)).rotation().x(), 0.00001);
            for (int i = 0; i < 3; i++) state.tick(0, 0, 0.1 * direction, 0, 0, true, false);
            assertEquals(-2, animation.sample("climb", "Climb", state.climbTime(1)).rotation().x(), 0.00001);
            for (int i = 0; i < 4; i++) state.tick(0, 0, 0.1 * direction, 0, 0, true, false);
            assertEquals(BuggyAnimationState.CLIMB_DURATION, state.climbTime(1));
            assertEquals(0, animation.sample("climb", "Climb", state.climbTime(1)).rotation().x(), 0.000001);
        }
    }

    @Test void flatDrivingBlockedWheelsFallsAndTeleportsDoNotStartClimbing() {
        var state = new BuggyAnimationState();
        state.tick(0, 0, 0.5, 0, 0, true, false);
        state.tick(0, 0, 0, 0, 0, true, true);
        state.tick(0, -0.3, 0.2, 0, 0, false, false);
        state.tick(0, 0.1, 0.2, 0, 0, false, false);
        assertEquals(BuggyAnimationState.CLIMB_DURATION, state.climbTime(1));
        state.tick(0, 3, 0.2, 0, 0, true, true);
        state.tick(10, 0.1, 10, 0, 0, true, true);
        assertEquals(BuggyAnimationState.CLIMB_DURATION, state.climbTime(1));
        // A fresh observer still detects a one-block step from a grounded position.
        state.tick(0, 1, 0.2, 0, 0, true, false);
        assertEquals(0.05, state.climbTime(1), 0.000001);
    }
}
