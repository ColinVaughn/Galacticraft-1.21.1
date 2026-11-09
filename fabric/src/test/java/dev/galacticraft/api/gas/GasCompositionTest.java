/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api.gas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasCompositionTest {
    @Test
    void oxygenMustHaveSafePartialPressure() {
        assertTrue(GasComposition.breathable(210000, 1));
        assertFalse(GasComposition.breathable(210000, 0.01));
        assertFalse(GasComposition.breathable(210000, 2));
        assertTrue(GasComposition.breathable(420000, 0.5));
        assertFalse(GasComposition.breathable(210000, 0));
        assertFalse(GasComposition.breathable(210000, Double.NaN));
        assertFalse(GasComposition.breathable(-210000, -1));
        assertFalse(GasComposition.breathable(2000000, 0.105));
    }
}