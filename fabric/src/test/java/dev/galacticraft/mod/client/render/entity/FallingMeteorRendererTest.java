package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallingMeteorRendererTest {
    @Test void ablationShrinksVolumeAndNeverEnlargesTheEntryBody() {
        for (int size = MeteoroidShape.MIN_SIZE; size <= MeteoroidShape.MAX_SIZE; size++) {
            float original = FallingMeteorRenderer.bodyScale(size, 1);
            assertEquals(MeteoroidShape.radiusFor(size), original, 0.00001);
            assertEquals(original / 2, FallingMeteorRenderer.bodyScale(size, 0.125f), 0.00001);
            assertEquals(0, FallingMeteorRenderer.bodyScale(size, 0));
            assertEquals(0, FallingMeteorRenderer.bodyScale(size, -1));
            assertEquals(original, FallingMeteorRenderer.bodyScale(size, 2));
        }
    }

    @Test void flameTailPointsBehindTheBodyForVerticalAndObliqueFlight() {
        for (Vec3 velocity : List.of(new Vec3(0, -10, 0), new Vec3(0, 10, 0),
                new Vec3(10, 0, 0), new Vec3(0, 0, -10), new Vec3(3, -8, 4), Vec3.ZERO)) {
            PoseStack pose = new PoseStack();
            FallingMeteorRenderer.alignFlames(pose, velocity);
            Vector3f tail = pose.last().pose().transformDirection(new Vector3f(0, 1, 0));
            Vec3 expected = velocity == Vec3.ZERO ? new Vec3(0, 1, 0) : velocity.normalize().scale(-1);
            assertEquals(expected.x, tail.x, 0.00001);
            assertEquals(expected.y, tail.y, 0.00001);
            assertEquals(expected.z, tail.z, 0.00001);
        }
    }
}
