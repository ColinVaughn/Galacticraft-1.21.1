/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TelepadAddressPayload(int containerId, boolean destination, int address) implements C2SPayload {
    public static final Type<TelepadAddressPayload> TYPE = new Type<>(Constant.id("telepad_address"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelepadAddressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelepadAddressPayload::containerId, ByteBufCodecs.BOOL, TelepadAddressPayload::destination,
            ByteBufCodecs.VAR_INT, TelepadAddressPayload::address, TelepadAddressPayload::new);
    @Override public void handle(NetworkManager.PacketContext context) {
        if (context.getPlayer().containerMenu instanceof ShortRangeTelepadMenu menu && menu.containerId == containerId) {
            menu.configure(context.getPlayer(), destination, address);
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
