package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadarBlockEntityRendererTest {
    @Test void authoredNorthFacesTheConsoleFrontInEveryPlacement() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            PoseStack stack = new PoseStack();
            RadarBlockEntityRenderer.face(stack, facing);
            Vector3f normal = stack.last().pose().transformDirection(new Vector3f(0, 0, -1));
            assertEquals(facing.getStepX(), normal.x, 0.00001);
            assertEquals(facing.getStepZ(), normal.z, 0.00001);
        }
    }
}
