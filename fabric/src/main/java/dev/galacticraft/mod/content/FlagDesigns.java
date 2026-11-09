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

package dev.galacticraft.mod.content;

import dev.galacticraft.mod.Constant;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Preset full-canvas flag designs selected by {@code galacticraft:flag_design}. Matching textures
 * under {@code textures/entity/flag/} use the banner layout and replace the flag's patterns.
 */
public final class FlagDesigns {
    public static final ResourceLocation FUCK_TRUMP = Constant.id("fuck_trump");
    public static final ResourceLocation PALESTINE = Constant.id("palestine");
    public static final ResourceLocation UKRAINE = Constant.id("ukraine");
    public static final ResourceLocation UKRAINE_VICTORY = Constant.id("ukraine_victory");

    public static final List<ResourceLocation> ALL = List.of(FUCK_TRUMP, PALESTINE, UKRAINE, UKRAINE_VICTORY);

    private FlagDesigns() {
    }

    public static ResourceLocation texture(ResourceLocation design) {
        return design.withPath(path -> "textures/entity/flag/" + path + ".png");
    }

    public static String translationKey(ResourceLocation design) {
        return design.toLanguageKey("flag_design");
    }
}
