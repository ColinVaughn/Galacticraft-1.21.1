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

import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class BurrowerMiningMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Unique private BlockState galacticraft$burrowerMined;
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void remember(BlockPos pos, CallbackInfoReturnable<Boolean> callback) { galacticraft$burrowerMined = level.getBlockState(pos); }
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void disturb(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && galacticraft$burrowerMined != null
                && (galacticraft$burrowerMined.is(GCBlockTags.MOON_CAVE_SUBSTRATE) || galacticraft$burrowerMined.is(GCBlockTags.MOON_CAVE_FORMATIONS)))
            LunarBurrowerEntity.miningDisturbance(level, pos, player);
    }
}
