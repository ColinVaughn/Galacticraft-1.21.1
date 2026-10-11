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

public class VenusSurfaceRules {
    private static final SurfaceRules.RuleSource BEDROCK = block(Blocks.BEDROCK);
    public static final SurfaceRules.RuleSource HARD_ROCK = block(GCBlocks.HARD_VENUS_ROCK);
    private static final SurfaceRules.RuleSource SOFT_ROCK = block(GCBlocks.SOFT_VENUS_ROCK);
    private static final SurfaceRules.RuleSource SCORCHED_ROCK = block(GCBlocks.SCORCHED_VENUS_ROCK);
    private static final SurfaceRules.RuleSource VOLCANIC_ROCK = block(GCBlocks.VOLCANIC_ROCK);
    private static final SurfaceRules.RuleSource PLAINS_BASALT = block(GCBlocks.VENUS_PLAINS_BASALT);
    private static final SurfaceRules.RuleSource TESSERA_ROCK = block(GCBlocks.VENUS_TESSERA_ROCK);
    private static final SurfaceRules.RuleSource FLOW_BASALT = block(GCBlocks.VENUS_FLOW_BASALT);
    private static final SurfaceRules.RuleSource STONY_REGOLITH = block(GCBlocks.VENUS_STONY_REGOLITH);
    private static final SurfaceRules.RuleSource MAGMA = block(Blocks.MAGMA_BLOCK);

    // Top layer per region.
    private static final SurfaceRules.RuleSource SURFACE_MATERIAL = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_LAVA_SEA), MAGMA),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_SHIELD_VOLCANO), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.steep(), VOLCANIC_ROCK),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_PATCHES, 0.4, 100.0), SCORCHED_ROCK),
                    FLOW_BASALT)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_SULFUR_FLATS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_PATCHES, 0.35, 100.0), SOFT_ROCK),
                    STONY_REGOLITH)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_LAVA_CHANNELS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_PATCHES, 0.45, 100.0), SCORCHED_ROCK),
                    FLOW_BASALT)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_VOLCANIC_PLAINS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_PATCHES, 0.5, 100.0), SCORCHED_ROCK),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_DETAIL, 0.35, 100.0), STONY_REGOLITH),
                    PLAINS_BASALT)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_HIGHLANDS), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(GCNoiseData.VENUS_SURFACE_PATCHES, 0.5, 100.0), HARD_ROCK),
                    TESSERA_ROCK)),
            HARD_ROCK
    );

    // Layer just beneath the surface.
    private static final SurfaceRules.RuleSource SUBSURFACE_MATERIAL = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_LAVA_SEA), SCORCHED_ROCK),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_SHIELD_VOLCANO, GCBiomes.Venus.VENUS_LAVA_CHANNELS), FLOW_BASALT),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Venus.VENUS_SULFUR_FLATS), SOFT_ROCK),
            HARD_ROCK
    );

    private static final SurfaceRules.RuleSource SURFACE_GENERATION = SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SURFACE_MATERIAL),
            SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SUBSURFACE_MATERIAL)
    );

    public static final SurfaceRules.RuleSource VENUS = createDefaultRule();

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
