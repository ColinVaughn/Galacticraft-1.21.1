package dev.galacticraft.impl.network.s2c;

import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class OxygenUpdatePayloadTest {
    @Test void invalidSectionIndexesCannotSilentlyWrapOnTheWire() {
        for (int section : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new OxygenUpdatePayload.OxygenData(section, new BitSet()));
        }
    }

    @Test void sectionIndexesUseTheFullUnsignedByteRangeWithoutChangingTheWireFormat() {
        BitSet bits = new BitSet();
        bits.set(0);
        bits.set(4095);
        for (int section : new int[]{0, 15, 16, 23, 127, 128, 255}) {
            var buffer = Unpooled.buffer();
            try {
                buffer.writeByte(section);
                ByteBufCodecs.BYTE_ARRAY.encode(buffer, bits.toByteArray());
                var decoded = OxygenUpdatePayload.OxygenData.CODEC.decode(buffer);
                assertEquals(section, decoded.section());
                assertEquals(bits, decoded.data());
                assertEquals(0, buffer.readableBytes());
                buffer.clear();
                OxygenUpdatePayload.OxygenData.CODEC.encode(buffer, decoded);
                assertEquals(section, buffer.readUnsignedByte());
                assertArrayEquals(bits.toByteArray(), ByteBufCodecs.BYTE_ARRAY.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}