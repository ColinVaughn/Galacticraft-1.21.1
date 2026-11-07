/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api.rocket;

import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType.Result;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelPredicateResultTest {
    @Test
    void alternativesAllowRegardlessOfChildOrderButPartVetoesStillWin() {
        assertEquals(Result.ALLOW, Result.BLOCK.or(Result.ALLOW));
        assertEquals(Result.ALLOW, Result.ALLOW.or(Result.BLOCK));
        assertEquals(Result.BLOCK, Result.PASS.or(Result.BLOCK));
        assertEquals(Result.PASS, Result.PASS.or(Result.PASS));
        assertEquals(Result.BLOCK, Result.ALLOW.merge(Result.BLOCK));
        assertEquals(Result.PASS, Result.PASS.merge(Result.PASS));
    }
}