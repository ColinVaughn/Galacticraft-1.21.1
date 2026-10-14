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

package dev.galacticraft.mod.machine.workbench;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Modular assembly is always available; vehicle pages require their schematics, and the
 * add-schematic page is always last.
 */
class WorkbenchPagesTest {
    private static final ResourceLocation ROCKET = ResourceLocation.parse("galacticraft:rocket/modular");
    private static final ResourceLocation CARGO = ResourceLocation.parse("galacticraft:cargo_rocket");
    private static final ResourceLocation BUGGY = ResourceLocation.parse("galacticraft:buggy");

    private static final ResourceLocation CARGO_SCHEMATIC = ResourceLocation.parse("galacticraft:cargo_rocket_schematic");
    private static final ResourceLocation BUGGY_SCHEMATIC = ResourceLocation.parse("galacticraft:moon_buggy_schematic");

    /**
     * The unlock id is persisted in player NBT, so its shape is a compatibility promise: changing it
     * silently revokes every schematic every player has unlocked.
     */
    @Test
    void unlockIdNamespacesTheSchematicItemId() {
        assertEquals(
                ResourceLocation.parse("galacticraft:schematic/cargo_rocket_schematic"),
                WorkbenchPages.unlockId(CARGO_SCHEMATIC)
        );
    }

    @Test
    void unlockIdKeepsAddonNamespacesDistinct() {
        assertEquals(
                ResourceLocation.parse("othermod:schematic/cool_rocket"),
                WorkbenchPages.unlockId(ResourceLocation.parse("othermod:cool_rocket"))
        );
    }

    @Test
    void aPageWithNoSchematicIsAlwaysVisible() {
        List<WorkbenchPage> pages = WorkbenchPages.order(List.of(rocket()), Set.of()::contains);

        assertEquals(List.of(ROCKET, WorkbenchPages.ADD_SCHEMATIC_ID), ids(pages));
    }

    @Test
    void aPageIsHiddenUntilItsSchematicIsUnlocked() {
        List<WorkbenchPage> candidates = List.of(rocket(), cargo());

        assertEquals(List.of(ROCKET, WorkbenchPages.ADD_SCHEMATIC_ID), ids(WorkbenchPages.order(candidates, Set.of()::contains)));
        assertEquals(
                List.of(ROCKET, CARGO, WorkbenchPages.ADD_SCHEMATIC_ID),
                ids(WorkbenchPages.order(candidates, Set.of(WorkbenchPages.unlockId(CARGO_SCHEMATIC))::contains))
        );
    }

    @Test
    void pagesAreOrderedBySortOrderThenId() {
        List<WorkbenchPage> candidates = List.of(buggy(), cargo(), rocket());
        Set<ResourceLocation> unlocked = Set.of(
                WorkbenchPages.unlockId(CARGO_SCHEMATIC),
                WorkbenchPages.unlockId(BUGGY_SCHEMATIC)
        );

        assertEquals(List.of(ROCKET, BUGGY, CARGO, WorkbenchPages.ADD_SCHEMATIC_ID), ids(WorkbenchPages.order(candidates, unlocked::contains)));
    }

    /**
     * Legacy always offered the unlock page, however little the player had unlocked - it is the only
     * way to unlock anything, so hiding it would dead-end progression.
     */
    @Test
    void theAddSchematicPageIsAlwaysLastAndAlwaysPresent() {
        assertEquals(List.of(WorkbenchPages.ADD_SCHEMATIC_ID), ids(WorkbenchPages.order(List.of(), Set.of()::contains)));

        List<WorkbenchPage> pages = WorkbenchPages.order(List.of(rocket(), cargo()), Set.of(WorkbenchPages.unlockId(CARGO_SCHEMATIC))::contains);
        assertSame(WorkbenchPages.ADD_SCHEMATIC, pages.getLast());
        assertTrue(pages.getLast().schematic().isEmpty());
    }

    private static List<ResourceLocation> ids(List<WorkbenchPage> pages) {
        return pages.stream().map(WorkbenchPage::id).toList();
    }

    private static WorkbenchPage rocket() {
        return new WorkbenchPage(ROCKET, 0, Optional.empty());
    }

    private static WorkbenchPage cargo() {
        return new WorkbenchPage(CARGO, 40, Optional.of(CARGO_SCHEMATIC));
    }

    private static WorkbenchPage buggy() {
        return new WorkbenchPage(BUGGY, 10, Optional.of(BUGGY_SCHEMATIC));
    }
}
