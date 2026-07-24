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

package dev.galacticraft.mod.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.galacticraft.mod.content.GCStats;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SupabaseGlobalStatisticsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parsesKnownStatisticsInDisplayOrder() {
        var entries = SupabaseGlobalStatistics.parseEntries("""
                [
                  {"stat_id":"galacticraft:safe_landing","total":42},
                  {"stat_id":"other:ignored","total":9000},
                  {"stat_id":"galacticraft:launch_rocket","total":17}
                ]
                """);

        assertEquals(GCStats.ALL.size(), entries.size());
        assertEquals(GCStats.LAUNCH_ROCKET, entries.get(0).stat());
        assertEquals(17L, entries.get(0).total());
        assertEquals(GCStats.SAFE_LANDING, entries.get(1).stat());
        assertEquals(42L, entries.get(1).total());
        assertEquals(0L, entries.get(2).total());
    }

    @Test
    void clampsNegativeTotals() {
        var entries = SupabaseGlobalStatistics.parseEntries(
                "[{\"stat_id\":\"galacticraft:launch_rocket\",\"total\":-1}]"
        );

        assertEquals(0L, entries.get(0).total());
    }
}
