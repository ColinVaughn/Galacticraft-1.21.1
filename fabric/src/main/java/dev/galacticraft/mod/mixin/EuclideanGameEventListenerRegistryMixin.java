/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

import dev.galacticraft.mod.world.atmosphere.VibrationAcoustics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Only vibration listeners hear sound; others (e.g. the sculk catalyst's death listener) keep vanilla range.
@Mixin(EuclideanGameEventListenerRegistry.class)
public abstract class EuclideanGameEventListenerRegistryMixin {
    @Inject(method = "getPostableListenerPosition", at = @At("HEAD"), cancellable = true)
    private static void galacticraft$hearThroughTheAir(ServerLevel level, Vec3 eventPos, GameEventListener listener, CallbackInfoReturnable<Optional<Vec3>> cir) {
        if (!(listener instanceof VibrationSystem.Listener) || level.galacticraft$getCelestialBody() == null) return;

        Optional<Vec3> listenerPos = listener.getListenerSource().getPosition(level);
        if (listenerPos.isEmpty()) {
            cir.setReturnValue(listenerPos);
            return;
        }
        BlockPos source = BlockPos.containing(eventPos);
        BlockPos at = BlockPos.containing(listenerPos.get());
        BlockPos anchor = VibrationAcoustics.anchor(listener.getListenerSource(), listenerPos.get());
        cir.setReturnValue(VibrationAcoustics.reaches(level, source, at, anchor, listener.getListenerRadius()) ? listenerPos : Optional.empty());
    }
}
