/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.network.s2c;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.s2c.S2CPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.meteor.ClientMeteorShowers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client snapshot of a dimension's meteor shower state, sent to players in that
 * dimension whenever the phase changes (and periodically as a keep-alive). The client feeds it into
 * {@link ClientMeteorShowers}, which drives the sky-streak layer.
 *
 * The radiant travels with the snapshot so the cosmetic streaks stream from the same point in
 * the sky as the real meteoroids the server is spawning.
 */
public record MeteorShowerSyncPayload(byte phase, float peakIntensity, int ticksIntoPhase, int phaseDuration,
                                      int remainingShowerTicks, float radiantYaw,
                                      float radiantPitch) implements S2CPayload {
    // Hand-written rather than StreamCodec.composite, which tops out at six components.
    public static final StreamCodec<ByteBuf, MeteorShowerSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull MeteorShowerSyncPayload decode(ByteBuf buf) {
            return new MeteorShowerSyncPayload(
                    buf.readByte(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, MeteorShowerSyncPayload payload) {
            buf.writeByte(payload.phase());
            buf.writeFloat(payload.peakIntensity());
            buf.writeInt(payload.ticksIntoPhase());
            buf.writeInt(payload.phaseDuration());
            buf.writeInt(payload.remainingShowerTicks());
            buf.writeFloat(payload.radiantYaw());
            buf.writeFloat(payload.radiantPitch());
        }
    };

    public static final ResourceLocation ID = Constant.id("meteor_shower_sync");
    public static final Type<MeteorShowerSyncPayload> TYPE = new Type<>(ID);

    @Override
    public Runnable handle(NetworkManager.@NotNull PacketContext context) {
        return () -> ClientMeteorShowers.accept(this);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
