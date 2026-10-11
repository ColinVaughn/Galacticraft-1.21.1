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

package dev.galacticraft.mod.client.model;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

public final class FabricLunarEmissiveBakedModel extends LunarEmissiveBakedModel implements FabricBakedModel {
    public FabricLunarEmissiveBakedModel(BakedModel delegate) { super(delegate); }
    @Override public boolean isVanillaAdapter() { return false; }
    @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                                        Supplier<RandomSource> random, RenderContext context) { emit(state, random, context); }
    @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) { emit(null, random, context); }
    private void emit(BlockState state, Supplier<RandomSource> random, RenderContext context) {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        var stone = renderer.materialFinder().find();
        var glow = renderer.materialFinder().emissive(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
        var emitter = context.getEmitter();
        for (int face = 0; face <= 6; face++) {
            Direction side = face == 6 ? null : Direction.values()[face];
            for (var quad : getQuads(state, side, random.get())) emitter.fromVanilla(quad, glows(quad) ? glow : stone, side).emit();
        }
    }
}
