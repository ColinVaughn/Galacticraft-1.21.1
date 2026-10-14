/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.screen.RocketPaintMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RocketPaintPayload(int containerId, int revision, RocketPaint paint) implements C2SPayload {
    public static final Type<RocketPaintPayload> TYPE = new Type<>(Constant.id("rocket_paint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RocketPaintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RocketPaintPayload::containerId, ByteBufCodecs.INT, RocketPaintPayload::revision,
            RocketPaint.STREAM_CODEC, RocketPaintPayload::paint, RocketPaintPayload::new);

    @Override public void handle(NetworkManager.PacketContext context) {
        if (context.getPlayer().containerMenu instanceof RocketPaintMenu menu && menu.containerId == containerId)
            menu.apply(context.getPlayer(), revision, paint);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}