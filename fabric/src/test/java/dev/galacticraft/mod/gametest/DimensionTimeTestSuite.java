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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.accessor.DimensionDayTimeAccessor;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.tag.GCDimensionTypeTags;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * The sleep skip and {@code /time set} both read how long a dimension's day is out of its celestial body,
 * behind the same two lookups the sky renderer uses. Neither would report a failure if a lookup started
 * coming back empty - they would simply fall back to the vanilla day and go quiet - so the inputs are
 * pinned here against the loaded registries.
 *
 * @see dev.galacticraft.mod.util.DimensionTime
 */
public final class DimensionTimeTestSuite implements GalacticraftGameTest {
    /** {@code GCCelestialBodies.MOON}: eight vanilla days to one lunar day. */
    private static final long MOON_DAY = 192000L;
    /** A quarter of the way through a lunar day, and distinctive enough to spot in a failure. */
    private static final long LUNAR_NOON = MOON_DAY / 4;

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonTurnsOnceEveryEightVanillaDays(GameTestHelper context) {
        RegistryAccess registries = context.getLevel().registryAccess();
        CelestialBody<?, ?> moon = registries.registryOrThrow(AddonRegistries.CELESTIAL_BODY).get(GCCelestialBodies.MOON);

        if (moon == null) {
            context.fail("the Moon is missing from the celestial body registry");
        } else if (moon.dayLength() != MOON_DAY) {
            context.fail("the Moon reported a " + moon.dayLength() + " tick day, expected " + MOON_DAY);
        } else {
            context.succeed();
        }
    }

    /**
     * A dimension other than the Overworld is handed a {@code DerivedLevelData}, which reads the
     * Overworld's clock and drops writes to its own. That is what stops sleeping and {@code /time set}
     * from doing anything out there, so it is exercised directly here - on an instance of its own, so
     * no dimension the other tests share is left running on a clock this one started.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void aDimensionCanKeepADayTimeOfItsOwn(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerLevelData overworld = (ServerLevelData) server.overworld().getLevelData();
        DerivedLevelData derived = new DerivedLevelData(server.getWorldData(), overworld);
        DimensionDayTimeAccessor clock = (DimensionDayTimeAccessor) derived;
        long shared = overworld.getDayTime();

        if (derived.getDayTime() != shared) {
            context.fail("a dimension without a clock of its own should read the Overworld's");
            return;
        }
        derived.setDayTime(shared + 5000L);
        if (derived.getDayTime() != shared) {
            context.fail("a dimension without a clock of its own should discard writes, as vanilla does");
            return;
        }

        clock.galacticraft$startOwnDayTime(LUNAR_NOON);
        if (derived.getDayTime() != LUNAR_NOON) {
            context.fail("a started clock should read back what it was started at, got " + derived.getDayTime());
            return;
        }
        clock.galacticraft$advanceOwnDayTime();
        if (derived.getDayTime() != LUNAR_NOON + 1L) {
            context.fail("a started clock should run on by a tick, got " + derived.getDayTime());
            return;
        }
        derived.setDayTime(LUNAR_NOON);
        if (derived.getDayTime() != LUNAR_NOON) {
            context.fail("a started clock should accept writes, got " + derived.getDayTime());
            return;
        }
        if (overworld.getDayTime() != shared) {
            context.fail("setting one dimension's clock moved the Overworld's");
            return;
        }
        context.succeed();
    }

    /** Without this tag the Moon's sky - and everything keyed to it - falls back to the vanilla day. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonIsTaggedAsSpace(GameTestHelper context) {
        ResourceKey<DimensionType> moon = ResourceKey.create(Registries.DIMENSION_TYPE, Constant.id("moon"));
        Holder<DimensionType> type = context.getLevel().registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE).getHolder(moon).orElse(null);

        if (type == null) {
            context.fail("the Moon dimension type is missing");
        } else if (!type.is(GCDimensionTypeTags.SPACE)) {
            context.fail("the Moon dimension type is not in the space tag");
        } else {
            context.succeed();
        }
    }
}
