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

package dev.galacticraft.mod.meteor;

import dev.galacticraft.mod.world.dimension.meteor.MeteorImpactRules;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the three layers that can decide whether an impact breaks blocks combine: the global
 * config default, the per-dimension exception list, and an op's per-world command override.
 */
class MeteorImpactRulesTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.parse("galacticraft:moon");

    @Test
    void withNoExceptionsEveryDimensionFollowsTheGlobalDefault() {
        assertTrue(resolve(true, List.of(), OVERWORLD));
        assertTrue(resolve(true, List.of(), MOON));
        assertFalse(resolve(false, List.of(), OVERWORLD));
        assertFalse(resolve(false, List.of(), MOON));
    }

    @Test
    void aListedDimensionDoesTheOppositeOfTheDefault() {
        assertFalse(resolve(true, List.of("minecraft:overworld"), OVERWORLD),
                "the overworld was exempted from a damaging default");
        assertTrue(resolve(true, List.of("minecraft:overworld"), MOON),
                "unlisted dimensions are untouched");
        assertTrue(resolve(false, List.of("galacticraft:moon"), MOON),
                "the list works in the other direction too");
    }

    @Test
    void listEntriesMayOmitTheNamespaceOrCarryStrayWhitespace() {
        assertFalse(resolve(true, List.of("overworld"), OVERWORLD));
        assertFalse(resolve(true, List.of("  minecraft:overworld  "), OVERWORLD));
        assertFalse(resolve(true, List.of("Minecraft:Overworld"), OVERWORLD));
    }

    @Test
    void anUnparseableEntryIsIgnoredRatherThanThrowing() {
        assertTrue(resolve(true, List.of("not a dimension id!", "minecraft:overworld", ""), MOON),
                "a bad entry must not take the good ones down with it");
        assertFalse(resolve(true, List.of("not a dimension id!", "minecraft:overworld", ""), OVERWORLD));
    }

    @Test
    void aCommandOverrideBeatsBothConfigLayers() {
        assertTrue(MeteorImpactRules.resolve(false, List.of("galacticraft:moon"), MOON,
                MeteorImpactRules.Override.ALWAYS));
        assertFalse(MeteorImpactRules.resolve(true, List.of(), MOON,
                MeteorImpactRules.Override.NEVER));
        assertFalse(MeteorImpactRules.resolve(true, List.of("galacticraft:moon"), MOON,
                MeteorImpactRules.Override.DEFAULT), "DEFAULT hands the decision back to the config");
    }

    @Test
    void aNullListIsTreatedAsEmpty() {
        assertTrue(MeteorImpactRules.resolve(true, null, MOON, MeteorImpactRules.Override.DEFAULT),
                "a config written before this field existed deserialises the list as null");
    }

    @Test
    void overrideIdsSurviveTheNbtRoundTrip() {
        for (MeteorImpactRules.Override override : MeteorImpactRules.Override.values()) {
            assertEquals(override, MeteorImpactRules.Override.byId(override.id()));
        }
        assertEquals(MeteorImpactRules.Override.DEFAULT, MeteorImpactRules.Override.byId((byte) -1),
                "out-of-range ids fall back to following the config");
        assertEquals(MeteorImpactRules.Override.DEFAULT, MeteorImpactRules.Override.byId((byte) 99));
    }

    private static boolean resolve(boolean globalDefault, List<String> exceptions, ResourceLocation dimension) {
        return MeteorImpactRules.resolve(globalDefault, exceptions, dimension, MeteorImpactRules.Override.DEFAULT);
    }
}
