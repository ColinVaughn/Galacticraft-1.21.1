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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.galacticraft.mod.accessor.DimensionDayTimeAccessor;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a dimension keep its own day time.
 *
 * The server builds one of these per dimension other than the Overworld and hands it to the level
 * as its level data. Vanilla reads the Overworld's clock through it and drops writes on the floor,
 * which is exactly why sleeping and {@code /time set} do nothing outside the Overworld, and why a
 * clock jump in the Overworld drags every other dimension along with it.
 *
 * A dimension that starts a clock here keeps its own instead, ticked by {@code ServerLevelMixin}
 * and saved by {@code DimensionDayTimeState}. Dimensions that never start one - the Nether, the End,
 * anything another mod adds - behave exactly as they did.
 */
@Mixin(DerivedLevelData.class)
public abstract class DerivedLevelDataMixin implements DimensionDayTimeAccessor {
    @Unique
    private boolean galacticraft$ownClock = false;
    @Unique
    private long galacticraft$dayTime = 0L;

    @ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
    private long gc$readOwnDayTime(long shared) {
        return this.galacticraft$ownClock ? this.galacticraft$dayTime : shared;
    }

    @Inject(method = "setDayTime", at = @At("HEAD"))
    private void gc$writeOwnDayTime(long dayTime, CallbackInfo ci) {
        if (this.galacticraft$ownClock) this.galacticraft$dayTime = dayTime;
    }

    @Override
    public boolean galacticraft$hasOwnDayTime() {
        return this.galacticraft$ownClock;
    }

    @Override
    public void galacticraft$startOwnDayTime(long dayTime) {
        this.galacticraft$ownClock = true;
        this.galacticraft$dayTime = dayTime;
    }

    @Override
    public void galacticraft$advanceOwnDayTime() {
        if (this.galacticraft$ownClock) this.galacticraft$dayTime++;
    }
}
