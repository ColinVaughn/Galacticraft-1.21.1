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

package dev.galacticraft.mod.client.render.entity.model;

import dev.galacticraft.mod.Constant;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class GCEntityModelLayer {
    public static final ModelLayerLocation GAZER = layer("gazer");
    public static final ModelLayerLocation RUMBLER = layer("rumbler");
    public static final ModelLayerLocation COMET_CUBE = layer("comet_cube");
    public static final ModelLayerLocation OLI_GRUB = layer("oli_grub");
    public static final ModelLayerLocation GREY = layer("grey");
    public static final ModelLayerLocation ARCH_GREY = layer("arch_grey");
    public static final ModelLayerLocation LANDER = layer("lander");
    public static final ModelLayerLocation PARACHEST = layer("parachest");
    public static final ModelLayerLocation MOON_VILLAGER = layer("moon_villager");
    public static final ModelLayerLocation SKELETON_BOSS = layer("skeleton_boss");
    public static final ModelLayerLocation FLAG = layer("flag");
    public static final ModelLayerLocation SOLAR_PANEL = layer("solar_panel");
    public static final ModelLayerLocation ROCKET_WORKBENCH = layer("rocket_workbench");

    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(Constant.id(id), "main");
    }

    private GCEntityModelLayer() {}
}
