package dev.galacticraft.mod.content.entity.vehicle;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AstroMinerDockingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void dockUsesTheMultiblockCenterAtTheSameHeightInEveryFacing() {
        BlockPos master = new BlockPos(-20, 64, 37);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var dock = AstroMinerEntity.dockPosition(master, facing);
            assertEquals(master.getY(), dock.y);
            assertEquals(1, dock.x - master.getX() - facing.getStepX());
            assertEquals(1, dock.z - master.getZ() - facing.getStepZ());
            Vector3f drill = new Matrix4f().rotateY((float) Math.toRadians(180 - AstroMinerEntity.yawForFacing(facing)))
                    .transformDirection(new Vector3f(0, 0, -1));
            assertEquals(facing.getStepX(), drill.x, 0.00001);
            assertEquals(facing.getStepZ(), drill.z, 0.00001);
        }
    }

    @Test void theAuthoredMinerFitsBetweenTheDockSidesAndAboveItsFloor() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/galacticraft/models/misc/astro_miner.obj")) {
            assertNotNull(input);
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.startsWith("v ")) continue;
                String[] coordinates = line.split("\\s+");
                double x = Double.parseDouble(coordinates[1]) * 0.0495;
                double y = Double.parseDouble(coordinates[2]) * 0.0495 + 1.4 - 0.42;
                assertTrue(Math.abs(x) < 1, "Miner extends outside the two-block dock width");
                assertTrue(y >= 0 && y <= 2, "Miner clips through the dock floor or roof");
            }
        }
    }
}
