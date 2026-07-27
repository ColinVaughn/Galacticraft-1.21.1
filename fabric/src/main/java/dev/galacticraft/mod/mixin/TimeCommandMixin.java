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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.galacticraft.mod.util.DimensionTime;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code /time} speak each dimension's own day.
 *
 * <p>The presets are named for where they put the sun - {@code day}, {@code noon}, {@code midnight} -
 * but they are plain tick counts out of 24000. On a body that takes eight vanilla days to turn, every
 * one of them lands within the first hour after its sunrise, so none of them can bring the sun up:
 * lunar noon is 48000, not 6000. Treating the argument as a point in the day rather than as a raw tick
 * count makes each preset mean what it is named for in whichever sky it is aimed at.
 */
@Mixin(TimeCommand.class)
public class TimeCommandMixin {
    /**
     * {@code /time set} walks every dimension, so each one is given the moment in its own day that the
     * argument names, rather than all of them being given the same raw tick.
     */
    @WrapOperation(method = "setTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private static void gc$setEachDimensionsOwnTime(ServerLevel level, long vanillaDayTime, Operation<Void> original) {
        original.call(level, DimensionTime.sameTimeOfDay(vanillaDayTime, DimensionTime.dayLength(level)));
    }

    /** Reports the time back in the same units {@code /time set} accepts, so the pair line up. */
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private static void gc$queryInVanillaTicks(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
        long dayLength = DimensionTime.dayLength(level);
        if (dayLength != DimensionTime.VANILLA_DAY_LENGTH) {
            cir.setReturnValue((int) DimensionTime.vanillaTimeOfDay(level.getDayTime(), dayLength));
        }
    }
}
