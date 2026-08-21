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

package dev.galacticraft.mod.client.resources;

import dev.galacticraft.mod.Constant;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the fluid sprite ids handed to the block atlas against the texture files that actually
 * ship.
 *
 * Nothing fails loudly when these drift apart: the atlas silently substitutes the missing
 * texture, so a bad id only shows up as a magenta tank in-game. Liquid oxygen shipped that way,
 * because the sprite was requested as {@code block/fluid/liquid_oxygen} -- the fluid's registry
 * name -- while the file on disk has only ever been {@code oxygen.png}.
 */
class FluidSpriteTest {
    /**
     * Every texture name passed to {@link Constant.Fluid#fluidId(String)} by the client-side fluid
     * registration in {@code GCResourceReloadListener} (Fabric) and {@code GCNeoForgeFluidTypes}
     * (NeoForge). Keep this in step with those two call sites.
     */
    private static final String[] REGISTERED_SPRITES = {
            Constant.Fluid.CRUDE_OIL_STILL,
            Constant.Fluid.CRUDE_OIL_FLOWING,
            Constant.Fluid.FUEL_STILL,
            Constant.Fluid.FUEL_FLOWING,
            Constant.Fluid.SULFURIC_ACID_STILL,
            Constant.Fluid.SULFURIC_ACID_FLOWING,
            Constant.Fluid.OXYGEN_STILL
    };

    @Test
    void everyRegisteredFluidSpriteHasATextureFile() {
        for (String sprite : REGISTERED_SPRITES) {
            ResourceLocation id = Constant.Fluid.fluidId(sprite);
            String path = "/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png";
            assertNotNull(FluidSpriteTest.class.getResource(path),
                    "no texture backs fluid sprite '" + id + "'; expected a file at " + path);
        }
    }
}
