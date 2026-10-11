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

package dev.galacticraft.mod.world.gen.surfacerule;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.world.biome.GCBiomes;
import dev.galacticraft.mod.world.gen.GCNoiseData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class MercurySurfaceRules {
    private static final SurfaceRules.RuleSource BEDROCK = block(Blocks.BEDROCK);
    private static final SurfaceRules.RuleSource SUB_SURFACE_ROCK = block(GCBlocks.MERCURY_SUB_SURFACE_ROCK);
    private static final SurfaceRules.RuleSource SCARP_ROCK = block(GCBlocks.MERCURY_SCARP_ROCK);
    private static final SurfaceRules.RuleSource ROCKY_REGOLITH = block(GCBlocks.MERCURY_ROCKY_REGOLITH);
    private static final SurfaceRules.RuleSource PLAINS_ROCK = block(GCBlocks.MERCURY_PLAINS_ROCK);
    private static final SurfaceRules.RuleSource IMPACT_BRECCIA = block(GCBlocks.MERCURY_IMPACT_BRECCIA);
    private static final SurfaceRules.RuleSource DARK_REGOLITH = block(GCBlocks.MERCURY_DARK_REGOLITH);

    // Impact-fragmented highlands, volcanic plains, and dark excavated material in patches.
    private static final SurfaceRules.RuleSource SURFACE_MATERIAL = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Mercury.MERCURY_HIGHLANDS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.steep(), SCARP_ROCK),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_PATCHES, 0.3, 100.0), DARK_REGOLITH),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_DETAIL, 0.2, 100.0), ROCKY_REGOLITH),
                    IMPACT_BRECCIA)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Mercury.MERCURY_BASIN), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.steep(), IMPACT_BRECCIA),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_PATCHES, 0.25, 100.0), DARK_REGOLITH),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_DETAIL, 0.35, 100.0), IMPACT_BRECCIA),
                    PLAINS_ROCK)),
            SurfaceRules.ifTrue(SurfaceRules.steep(), SCARP_ROCK),
            SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_PATCHES, 0.25, 100.0), ROCKY_REGOLITH),
            SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.MERCURY_SURFACE_DETAIL, -100.0, -0.4), DARK_REGOLITH),
            PLAINS_ROCK
    );

    // Layer just beneath the surface.
    private static final SurfaceRules.RuleSource SUBSURFACE_MATERIAL = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Mercury.MERCURY_HIGHLANDS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.steep(), SCARP_ROCK),
                    IMPACT_BRECCIA)),
            SUB_SURFACE_ROCK
    );

    private static final SurfaceRules.RuleSource SURFACE_GENERATION = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SURFACE_MATERIAL),
            SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SUBSURFACE_MATERIAL)
    );

    public static final SurfaceRules.RuleSource MERCURY = createDefaultRule();

    public static @NotNull SurfaceRules.RuleSource createDefaultRule() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), BEDROCK),
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SURFACE_GENERATION)
        );
    }

    @Contract("_ -> new")
    private static @NotNull SurfaceRules.RuleSource block(@NotNull Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }
}
