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

package dev.galacticraft.mod.world.gen.feature;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/** Registry of Galacticraft's custom worldgen {@link Feature} types. */
public class GCFeatures {
    public static final Feature<BlockStateConfiguration> MARS_BOULDER = new PlanetBoulderFeature(GCBlockTags.MARS_CARVER_REPLACEABLES);
    public static final Feature<BlockStateConfiguration> MOON_BOULDER = new PlanetBoulderFeature(GCBlockTags.MOON_CRATER_CARVER_REPLACEABLES);
    public static final Feature<NoneFeatureConfiguration> VOLCANO = new VolcanoFeature(NoneFeatureConfiguration.CODEC);
    public static final Feature<NoneFeatureConfiguration> OLIVINE_SPIRE = new OlivineSpireFeature(NoneFeatureConfiguration.CODEC);
    public static final Feature<NoneFeatureConfiguration> CHEESE_TREE = new CheeseTreeFeature(NoneFeatureConfiguration.CODEC);
    public static final Feature<NoneFeatureConfiguration> LUNAR_CAVE_LANDMARK = new LunarCaveLandmarkFeature(NoneFeatureConfiguration.CODEC);

    public static final Feature<NoneFeatureConfiguration> MOON_CAVE_DECORATIONS = new LunarCaveDecorationFeature(NoneFeatureConfiguration.CODEC);

    public static final Feature<NoneFeatureConfiguration> MOON_BURROWER_DEN = new LunarBurrowerDenFeature(NoneFeatureConfiguration.CODEC);

    public static void register() {
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("moon_burrower_den"), MOON_BURROWER_DEN);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("moon_cave_decorations"), MOON_CAVE_DECORATIONS);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("mars_boulder"), MARS_BOULDER);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("moon_boulder"), MOON_BOULDER);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("volcano"), VOLCANO);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("olivine_spire"), OLIVINE_SPIRE);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("cheese_tree"), CHEESE_TREE);
        Registry.register(BuiltInRegistries.FEATURE, Constant.id("lunar_cave_landmark"), LUNAR_CAVE_LANDMARK);
    }
}
