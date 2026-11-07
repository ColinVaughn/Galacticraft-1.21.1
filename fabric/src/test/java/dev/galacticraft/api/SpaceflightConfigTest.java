/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.galacticraft.api.universe.celestialbody.SpaceflightConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpaceflightConfigTest {
    private static final String VALID = "{\"body\":\"examplepack:world\",\"center\":[60000,1024,0],\"radius\":60,\"texture\":\"examplepack:textures/world.png\"}";
    @Test void addonGeometryRoundTripsWithDefaults() {
        var value = SpaceflightConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(VALID)).getOrThrow();
        assertEquals("examplepack:world", value.body().location().toString());
        assertEquals(42, value.skyRadius());
        assertEquals(value, SpaceflightConfig.CODEC.parse(JsonOps.INSTANCE,
                SpaceflightConfig.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow());
    }
    @Test void invalidGeometryIsRejectedBeforeTravelUsesIt() {
        assertTrue(SpaceflightConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(VALID.replace("\"radius\":60", "\"radius\":0"))).error().isPresent());
        assertTrue(SpaceflightConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(VALID.replace("60000", "30000000"))).error().isPresent());
        assertTrue(SpaceflightConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(VALID.replace("\"radius\":60", "\"radius\":60,\"rotation_period\":-1"))).error().isPresent());
    }
}