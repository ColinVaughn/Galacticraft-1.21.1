/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.mixin;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the vanilla riding-input packet as the authoritative NeoForge launch input. Connector does
 * not consistently forward Galacticraft's Fabric client payload while riding, but Minecraft always
 * sends this packet with the jump-key state.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NeoServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void galacticraft$handleRocketLaunchInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (this.player.getVehicle() instanceof RocketEntity rocket
                && rocket.getLaunchStage().ordinal() < LaunchStage.IGNITED.ordinal()) {
            float leftImpulse = packet.getXxa();
            float forwardImpulse = packet.getZza();
            rocket.inputTick(
                    leftImpulse,
                    forwardImpulse,
                    forwardImpulse > 0.0F,
                    forwardImpulse < 0.0F,
                    leftImpulse > 0.0F,
                    leftImpulse < 0.0F,
                    packet.isJumping(),
                    packet.isShiftKeyDown(),
                    false
            );
        }
    }
}
