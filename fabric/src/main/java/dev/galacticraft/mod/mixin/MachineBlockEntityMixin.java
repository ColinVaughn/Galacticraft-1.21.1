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

import dev.galacticraft.machinelib.api.block.entity.ConfiguredBlockEntity;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.machine.MachineFaceDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every machine a face configuration that carries power out of the box.
 *
 * Applied on the machine's first tick rather than when it is built or loaded: changing a face tells
 * the neighbouring blocks about it, and at both of those earlier points the block entity has no level to
 * tell them through. By the first tick the configuration has been read back from the save, so a machine
 * somebody has already set up is left exactly as they set it.
 *
 * @see MachineFaceDefaults for what the defaults are and why
 */
@Mixin(ConfiguredBlockEntity.class)
public abstract class MachineBlockEntityMixin {
    @Unique
    private boolean galacticraft$facesDefaulted = false;

    // remap = false: the target is MachineLib's own method, which keeps its name in production and so
    // has no obfuscation mapping to look up. Selecting it by bare name leaves the descriptor - which is
    // made of Minecraft types - to be matched against whatever this handler is remapped to.
    @Inject(method = "tickBase", at = @At("HEAD"), remap = false)
    private void gc$applyDefaultFaces(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler, CallbackInfo ci) {
        if (this.galacticraft$facesDefaulted) return;
        this.galacticraft$facesDefaulted = true;

        if ((Object) this instanceof MachineBlockEntity machine) {
            MachineFaceDefaults.apply(machine);
        }
    }
}
