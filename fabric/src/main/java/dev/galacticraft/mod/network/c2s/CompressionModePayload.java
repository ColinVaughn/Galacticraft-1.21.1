
package dev.galacticraft.mod.network.c2s;

import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.screen.OxygenCompressorMenu;
import dev.galacticraft.mod.content.block.entity.machine.OxygenCompressorBlockEntity;
import io.netty.buffer.ByteBuf;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record CompressionModePayload() implements C2SPayload {
    public static final CompressionModePayload STATIC_INSTANCE = new CompressionModePayload();
    public static final StreamCodec<ByteBuf, CompressionModePayload> STREAM_CODEC = StreamCodec.unit(STATIC_INSTANCE);
    public static final ResourceLocation ID = Constant.id("compression_mode");
    public static final CustomPacketPayload.Type<CompressionModePayload> TYPE = new CustomPacketPayload.Type<>(ID);

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void handle(NetworkManager.@NotNull PacketContext context) {
        ServerPlayer serverPlayer = (ServerPlayer) context.getPlayer();
        if ((serverPlayer).containerMenu instanceof OxygenCompressorMenu sHandler) {
            OxygenCompressorBlockEntity machine = sHandler.be;
            if (machine.getSecurity().hasAccess(serverPlayer))
                machine.changeCompressionMode();
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
