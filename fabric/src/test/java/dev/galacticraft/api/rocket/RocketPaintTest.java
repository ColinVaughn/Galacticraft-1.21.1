package dev.galacticraft.api.rocket;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RocketPaintTest {
    @Test void missingColorsAndExplicitWhiteRemainDistinct() {
        var original = RocketPaint.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(RocketPaint.NONE, original);
        var white = original.withColor(RocketPartTypes.BODY, 0xFFFFFFFF);
        var encoded = RocketPaint.CODEC.encodeStart(JsonOps.INSTANCE, white).getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("body"));
        assertFalse(encoded.getAsJsonObject().has("cone"));
        assertEquals(white, RocketPaint.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(original, white.withColor(RocketPartTypes.BODY, null));
    }

    @Test void networkRoundTripPreservesIndependentColors() {
        var paint = RocketPaint.NONE.withColor(RocketPartTypes.BODY, 0xFF426BA8).withColor(RocketPartTypes.FIN, 0xFFDF762B);
        var buffer = Unpooled.buffer();
        try {
            RocketPaint.STREAM_CODEC.encode(buffer, paint);
            assertEquals(paint, RocketPaint.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertTrue(paint.cone().isEmpty());
    }

    @Test void rejectsTransparentPaintAtBothSerializationBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> RocketPaint.NONE.withColor(RocketPartTypes.CONE, 0x00426BA8));
        assertTrue(RocketPaint.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"body\":4352936}")).error().isPresent());
        var buffer = Unpooled.buffer();
        try {
            buffer.writeBoolean(true).writeInt(0x00426BA8);
            for (int i = 0; i < 4; i++) buffer.writeBoolean(false);
            assertThrows(IllegalArgumentException.class, () -> RocketPaint.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}