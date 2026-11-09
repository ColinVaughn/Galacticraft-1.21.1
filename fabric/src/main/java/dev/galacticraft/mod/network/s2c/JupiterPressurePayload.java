package dev.galacticraft.mod.network.s2c;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.s2c.S2CPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.gui.overlay.JupiterPressureOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record JupiterPressurePayload(float ambient, float rating, float charge, int unsafeTicks) implements S2CPayload {
    public static final Type<JupiterPressurePayload> TYPE = new Type<>(Constant.id("jupiter_pressure"));
    public static final StreamCodec<ByteBuf, JupiterPressurePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, JupiterPressurePayload::ambient, ByteBufCodecs.FLOAT, JupiterPressurePayload::rating,
            ByteBufCodecs.FLOAT, JupiterPressurePayload::charge, ByteBufCodecs.VAR_INT, JupiterPressurePayload::unsafeTicks,
            JupiterPressurePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    @Override public Runnable handle(NetworkManager.PacketContext context) { return () -> JupiterPressureOverlay.accept(this); }
}