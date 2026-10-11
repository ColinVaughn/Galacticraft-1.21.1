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

import com.mojang.blaze3d.platform.NativeImage;
import dev.galacticraft.mod.Constant;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LunarEmissiveBakedModelTest {
    private static class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents) { super(Constant.id("textures/atlas/test.png"), contents, 16, 16, 0, 0); }
    }
    @Test void onlyCoreQuadsReceiveFullBrightAndTheSourceModelIsUnchanged() {
        try (var core = contents("block/helion_core"); var stone = contents("block/vesicular_lunar_basalt")) {
            var glowing = new BakedQuad(new int[32], -1, Direction.UP, new Sprite(core), true);
            var armor = new BakedQuad(new int[32], -1, Direction.UP, new Sprite(stone), true);
            var source = List.of(glowing, armor);
            BakedModel delegate = (BakedModel)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{BakedModel.class},
                    (proxy, method, args) -> method.getName().equals("getQuads") ? source : null);
            var quads = new LunarEmissiveBakedModel(delegate).getQuads(null, null, RandomSource.create());
            assertSame(armor, quads.get(1));
            assertNotSame(glowing, quads.getFirst());
            assertFalse(quads.getFirst().isShade());
            for (int vertex = 0; vertex < 4; vertex++) {
                assertEquals(LightTexture.FULL_BRIGHT, quads.getFirst().getVertices()[vertex * 8 + 6]);
                assertEquals(0, glowing.getVertices()[vertex * 8 + 6]);
                assertEquals(0, armor.getVertices()[vertex * 8 + 6]);
            }
        }
    }
    private SpriteContents contents(String path) {
        return new SpriteContents(Constant.id(path), new FrameSize(16, 16), new NativeImage(16, 16, true), ResourceMetadata.EMPTY);
    }
    @Test void onlyTheAuthoredLuminousModelsAreWrapped() {
        for (String name : List.of("helion_nodules", "helion_wall_crust", "helium_3_vent")) {
            assertTrue(LunarEmissiveBakedModel.matches(Constant.id(name)));
            assertTrue(LunarEmissiveBakedModel.matches(Constant.id("block/" + name)));
        }
        assertFalse(LunarEmissiveBakedModel.matches(Constant.id("burrower_den_scraps")));
        assertFalse(LunarEmissiveBakedModel.matches(ResourceLocation.withDefaultNamespace("helion_nodules")));
    }
}
