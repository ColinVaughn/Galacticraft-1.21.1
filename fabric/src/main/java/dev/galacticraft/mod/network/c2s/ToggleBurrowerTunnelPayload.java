package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ToggleBurrowerTunnelPayload() implements C2SPayload {
    public static final ToggleBurrowerTunnelPayload INSTANCE = new ToggleBurrowerTunnelPayload();
    public static final Type<ToggleBurrowerTunnelPayload> TYPE = new Type<>(Constant.id("toggle_burrower_tunnel"));
    public static final StreamCodec<ByteBuf, ToggleBurrowerTunnelPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public void handle(NetworkManager.PacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) apply(player);
    }
    public void apply(ServerPlayer player) {
        if (player.getVehicle() instanceof LunarBurrowerEntity worm) worm.toggleTunnelMode(player);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
