/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 * Licensed under the MIT license. See LICENSE in the project root.
 */
package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.screen.LaunchControllerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LaunchControllerSettingsPayload(int containerId, int frequency, int destination, int condition, int options)
        implements C2SPayload {
    public static final Type<LaunchControllerSettingsPayload> TYPE = new Type<>(Constant.id("launch_controller_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchControllerSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LaunchControllerSettingsPayload::containerId,
            ByteBufCodecs.VAR_INT, LaunchControllerSettingsPayload::frequency,
            ByteBufCodecs.VAR_INT, LaunchControllerSettingsPayload::destination,
            ByteBufCodecs.VAR_INT, LaunchControllerSettingsPayload::condition,
            ByteBufCodecs.VAR_INT, LaunchControllerSettingsPayload::options,
            LaunchControllerSettingsPayload::new);

    @Override
    public void handle(NetworkManager.PacketContext context) {
        if (context.getPlayer().containerMenu instanceof LaunchControllerMenu menu && menu.containerId == this.containerId) {
            menu.configure(context.getPlayer(), this.frequency, this.destination, this.condition, this.options);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
