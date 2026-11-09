package dev.galacticraft.mod.mixin;

import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CloudDiverMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleMoveVehicle",at=@At("HEAD"),cancellable=true)
    private void gc$serverOwnedDiver(ServerboundMoveVehiclePacket packet,CallbackInfo ci) {
        if(player.getVehicle() instanceof CloudDiverEntity)ci.cancel();
    }
}