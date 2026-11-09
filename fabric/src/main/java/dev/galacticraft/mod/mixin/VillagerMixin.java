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

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.accessor.MoonVillagerMarker;
import dev.galacticraft.mod.content.entity.MoonVillagerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves Moon villager identity through carrying mods that serialize into vanilla villagers,
 * including repeated storage and retrieval through Easy Villagers blocks.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin implements MoonVillagerMarker {
    @Unique
    private boolean galacticraft$carriedMoonVillager;

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void gc$writeMoonVillagerMarker(CompoundTag tag, CallbackInfo ci) {
        if (this.galacticraft$isMoonVillager()) {
            tag.putBoolean(Constant.Nbt.MOON_VILLAGER, true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void gc$readMoonVillagerMarker(CompoundTag tag, CallbackInfo ci) {
        this.galacticraft$carriedMoonVillager = tag.getBoolean(Constant.Nbt.MOON_VILLAGER);
    }

    @Override
    public boolean galacticraft$isMoonVillager() {
        return this.galacticraft$carriedMoonVillager || (Object) this instanceof MoonVillagerEntity;
    }
}
