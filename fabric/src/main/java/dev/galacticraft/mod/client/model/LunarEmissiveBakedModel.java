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

import dev.galacticraft.mod.Constant;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Keeps stone normally lit while the helion material is full bright in world and item rendering. */
public class LunarEmissiveBakedModel implements BakedModel {
    protected final BakedModel delegate;
    public LunarEmissiveBakedModel(BakedModel delegate) { this.delegate = delegate; }
    public static boolean matches(ResourceLocation id) {
        if (!id.getNamespace().equals(Constant.MOD_ID)) return false;
        String path = id.getPath();
        return path.endsWith("helion_nodules") || path.endsWith("helion_wall_crust") || path.endsWith("helium_3_vent");
    }
    public static boolean glows(BakedQuad quad) {
        var sprite = quad.getSprite().contents().name();
        return sprite.equals(Constant.id("block/helion_core")) || sprite.equals(Constant.id("block/helium_3_surface"));
    }
    private static final class GlowQuad extends BakedQuad {
        GlowQuad(BakedQuad source) { super(bright(source.getVertices()), source.getTintIndex(), source.getDirection(), source.getSprite(), false); }
        // NeoForge reads per-quad AO; Fabric uses an explicit emissive render material.
        public boolean hasAmbientOcclusion() { return false; }
        private static int[] bright(int[] source) {
            int[] vertices = source.clone();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) vertices[i * stride + 6] = LightTexture.FULL_BRIGHT;
            return vertices;
        }
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return delegate.getQuads(state, side, random).stream().map(q -> glows(q) ? new GlowQuad(q) : q).toList();
    }
    @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return delegate.isGui3d(); }
    @Override public boolean usesBlockLight() { return delegate.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return delegate.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return delegate.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return delegate.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return delegate.getOverrides(); }
}
