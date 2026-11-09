package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record CloudDiverActionPayload(byte action) implements C2SPayload {
    public static final Type<CloudDiverActionPayload> TYPE=new Type<>(Constant.id("cloud_diver_action"));
    public static final StreamCodec<ByteBuf,CloudDiverActionPayload> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.BYTE,CloudDiverActionPayload::action,CloudDiverActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @Override public void handle(NetworkManager.PacketContext context){
        if(!(context.getPlayer() instanceof ServerPlayer player) || !(player.getVehicle() instanceof CloudDiverEntity diver))return;
        switch(action){case 0->player.stopRiding();case 1->diver.collect(player);default->{}}
    }
}