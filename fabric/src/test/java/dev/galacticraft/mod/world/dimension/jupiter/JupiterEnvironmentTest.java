package dev.galacticraft.mod.world.dimension.jupiter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JupiterEnvironmentTest {
    @Test void pressureMatchesProgressionAndChangesContinuously() {
        assertEquals(1, JupiterEnvironment.pressure(208));
        assertEquals(4, JupiterEnvironment.pressure(144));
        assertEquals(12, JupiterEnvironment.pressure(64));
        assertEquals(24, JupiterEnvironment.pressure(-32));
        assertTrue(JupiterEnvironment.pressure(-64) > 24);
        for (double y = -64; y <= 319; y += 0.25) {
            assertTrue(JupiterEnvironment.pressure(y) >= JupiterEnvironment.pressure(y + 0.25));
            assertTrue(Math.abs(JupiterEnvironment.pressure(y) - JupiterEnvironment.pressure(y + 0.001)) < 0.01);
            assertTrue(JupiterEnvironment.visualDensity(y) >= 0 && JupiterEnvironment.visualDensity(y) <= 1);
        }
        assertTrue(JupiterEnvironment.temperature(-32) > JupiterEnvironment.temperature(208));
        assertEquals(0, JupiterEnvironment.wind(240, 0));
        assertTrue(JupiterEnvironment.wind(64, 0) > JupiterEnvironment.wind(64, 1024));
    }
}