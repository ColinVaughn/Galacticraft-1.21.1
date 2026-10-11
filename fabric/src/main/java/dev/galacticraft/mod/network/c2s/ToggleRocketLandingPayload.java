package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ToggleRocketLandingPayload() implements C2SPayload {
    public static final ToggleRocketLandingPayload INSTANCE = new ToggleRocketLandingPayload();
    public static final Type<ToggleRocketLandingPayload> TYPE = new Type<>(Constant.id("toggle_rocket_landing"));
    public static final StreamCodec<ByteBuf, ToggleRocketLandingPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public void handle(NetworkManager.PacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && player.getVehicle() instanceof RocketEntity rocket)
            rocket.toggleLanding(player);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
