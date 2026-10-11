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

package dev.galacticraft.mod.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.content.entity.BurrowerState;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class LunarBurrowerModelTest {
    @Test void tractionReachIsRemovedFromTheSharedRootTransform() {
        var motion = new BurrowerAnimations.Pose(new BurrowerAnimations.Vector(2, 3, -8),
                new BurrowerAnimations.Vector(0, 2, 0), BurrowerAnimations.Vector.ONE);
        var routed = LunarBurrowerModel.withoutReach(motion);
        assertEquals(new BurrowerAnimations.Vector(2, 3, 0), routed.position());
        assertEquals(motion.rotation(), routed.rotation());
        assertEquals(motion.scale(), routed.scale());
    }
    @Test void turningTheHeadKeepsItsRearHingeAttachedToTheNeck() {
        float angle = (float)(Math.PI / 2);
        Vec3 worldNeck = new Vec3(0, 0, 13.0 / 16);
        Vec3 offset = LunarBurrowerModel.headOffset(worldNeck, angle);
        Vec3 hinge = offset.add(0, 0, 13.0 / 16).yRot(angle);
        assertEquals(worldNeck.x, hinge.x, 0.0001);
        assertEquals(worldNeck.z, hinge.z, 0.0001);
    }
    @Test void bodyAnchorKeepsItsFloorHeightAndFollowsSlopes() {
        PoseStack pose = new PoseStack();
        Vec3 pivot = new Vec3(0, 0, -30);
        pose.translate(0, 0, 52.0 / 16);
        pose.translate(0, 0, pivot.z / 16);
        LunarBurrowerModel.applyBodyAnchor(pose, pivot, new Vec3(0, 1, 1.375),
                new Vec3(0, -0.7, -0.7), BurrowerAnimations.Pose.REST);
        var center = pose.last().pose().transformPosition(new Vector3f());
        assertEquals(1, center.y, 0.0001F);
        assertEquals(1.375, center.z, 0.0001F);
        var rear = pose.last().pose().transformPosition(new Vector3f(0, 0, 1));
        assertTrue(rear.y > center.y + 0.6, "Segment did not slope toward the higher rear floor");
    }
    @Test void animatedLateralOffsetTurnsWithTheBodyWithoutApplyingReachTwice() {
        PoseStack pose = new PoseStack();
        Vec3 pivot = new Vec3(0, 0, -30);
        pose.translate(0, 0, 52.0 / 16);
        pose.translate(0, 0, pivot.z / 16);
        var motion = new BurrowerAnimations.Pose(new BurrowerAnimations.Vector(16, 0, 16),
                BurrowerAnimations.Vector.ZERO, BurrowerAnimations.Vector.ONE);
        LunarBurrowerModel.applyBodyAnchor(pose, pivot, new Vec3(2, 0, 3), new Vec3(-1, 0, 0), motion);
        var center = pose.last().pose().transformPosition(new Vector3f());
        assertEquals(2, center.x, 0.0001F);
        assertEquals(2, center.z, 0.0001F);
    }
    @Test void warningRaisesTheMouthInJavaCoordinates() {
        var clips = BurrowerAnimations.read(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/galacticraft/animations/lunar_burrower.animation.json")), StandardCharsets.UTF_8));
        var motion = clips.clip(BurrowerState.WARNING).sample("head", 0.6F);
        PoseStack pose = new PoseStack();
        LunarBurrowerModel.applyMotion(pose, motion);
        var mouth = pose.last().pose().transformPosition(new Vector3f(0, 0, -1));
        assertTrue(mouth.y > motion.position().y() / 16 + 0.2, "Warning mouth bent into the floor");
    }
    @Test void exportedTranslationScaleAndRollKeepTheirHandedness() {
        PoseStack pose = new PoseStack();
        var motion = new BurrowerAnimations.Pose(new BurrowerAnimations.Vector(16, 32, 48),
                new BurrowerAnimations.Vector(0, 0, 90), new BurrowerAnimations.Vector(2, 1, 1));
        LunarBurrowerModel.applyMotion(pose, motion);
        var point = pose.last().pose().transformPosition(new Vector3f(1, 0, 0));
        assertEquals(1, point.x, 0.0001F);
        assertEquals(0, point.y, 0.0001F);
        assertEquals(3, point.z, 0.0001F);
    }
}
