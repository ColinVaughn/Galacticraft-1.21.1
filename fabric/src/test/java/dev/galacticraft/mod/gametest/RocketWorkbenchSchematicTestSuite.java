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

import dev.galacticraft.api.accessor.ResearchAccessor;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.workbench.WorkbenchPage;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Legacy Galacticraft unlocked a NASA workbench page by consuming its schematic in the bench's own
 * unlock slot. This port had no such slot at all, so a schematic could not be placed in the rocket
 * workbench and the tier-2/3, buggy, cargo rocket and astro miner pages were unreachable.
 */
public class RocketWorkbenchSchematicTestSuite implements GalacticraftGameTest {
    private static final ResourceLocation ROCKET_PAGE = WorkbenchPages.ROCKET_ID;
    private static final ResourceLocation BUGGY_PAGE = Constant.id("buggy");

    @GameTest(template = EMPTY_STRUCTURE)
    public void theAddSchematicPageAcceptsASchematic(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);

        Slot slot = fixture.schematicSlot();
        if (slot == null) {
            fixture.close();
            context.fail("The add-schematic page has no slot backed by the workbench's schematic container", fixture.pos);
            return;
        }
        if (!slot.mayPlace(new ItemStack(GCItems.TIER_2_ROCKET_SCHEMATIC))) {
            fixture.close();
            context.fail("The schematic slot rejected a tier-2 rocket schematic", fixture.pos);
            return;
        }
        if (slot.mayPlace(new ItemStack(Items.DIRT))) {
            fixture.close();
            context.fail("The schematic slot accepted a block of dirt", fixture.pos);
            return;
        }
        if (slot.mayPlace(new ItemStack(GCItems.BASIC_ROCKET_BODY_SCHEMATIC))) {
            fixture.close();
            context.fail("The page-unlock slot accepted a rocket-part schematic that unlocks no page", fixture.pos);
            return;
        }

        fixture.close();
        context.succeed();
    }

    /** Legacy's {@code S_UNLOCK_NEW_SCHEMATIC}: the schematic is spent, not merely read. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void unlockingConsumesTheTierSchematicAndUnlocksModules(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);

        if (WorkbenchPages.rocketPartsUnlockedBy(GCItems.TIER_2_ROCKET_SCHEMATIC).stream()
                .anyMatch(part -> ((ResearchAccessor) fixture.player).galacticraft$isUnlocked(dev.galacticraft.mod.content.GCRocketParts.recipeId(part)))) {
            fixture.close();
            context.fail("Tier-2 modules should require their schematic", fixture.pos);
            return;
        }

        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.TIER_2_ROCKET_SCHEMATIC));
        boolean handled = fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK);

        if (!handled) {
            fixture.close();
            context.fail("The unlock button did nothing with a tier-2 rocket schematic in the slot", fixture.pos);
            return;
        }
        if (!fixture.workbench.schematic.getItem(0).isEmpty()) {
            fixture.close();
            context.fail("Unlocking left the schematic in the slot instead of consuming it", fixture.pos);
            return;
        }
        if (!((ResearchAccessor) fixture.player).galacticraft$isUnlocked(unlockId(GCItems.TIER_2_ROCKET_SCHEMATIC))) {
            fixture.close();
            context.fail("Unlocking did not record the tier-2 schematic against the player", fixture.pos);
            return;
        }
        if (!WorkbenchPages.rocketPartsUnlockedBy(GCItems.TIER_2_ROCKET_SCHEMATIC).stream()
                .allMatch(part -> ((ResearchAccessor) fixture.player).galacticraft$isUnlocked(dev.galacticraft.mod.content.GCRocketParts.recipeId(part)))) {
            fixture.close();
            context.fail("Tier-2 module research is still locked after spending its schematic", fixture.pos);
            return;
        }

        fixture.close();
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unlockingRefusesAnItemThatIsNotASchematic(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);

        fixture.workbench.schematic.setItem(0, new ItemStack(Items.PAPER));
        boolean handled = fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK);

        boolean consumed = fixture.workbench.schematic.getItem(0).isEmpty();
        fixture.close();
        if (handled || consumed) {
            context.fail("The unlock button consumed a sheet of paper", fixture.pos);
        } else {
            context.succeed();
        }
    }

    /** A known schematic turns to its page but is not spent a second time. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void unlockingAnAlreadyKnownSchematicOpensItsPageAndKeepsTheItem(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);

        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.TIER_2_ROCKET_SCHEMATIC));
        fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK);

        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.TIER_2_ROCKET_SCHEMATIC));
        boolean handled = fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK);

        boolean kept = fixture.workbench.schematic.getItem(0).is(GCItems.TIER_2_ROCKET_SCHEMATIC);
        fixture.close();
        if (!handled) {
            context.fail("The unlock button ignored a schematic for an already-known page", fixture.pos);
        } else if (kept) {
            context.succeed();
        } else {
            context.fail("A duplicate tier-2 schematic was eaten by the unlock button", fixture.pos);
        }
    }

    /**
     * Legacy opened the NASA workbench on page 0 - the tier-1 rocket, which needs no schematic - and
     * always offered the unlock page last.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void aFreshWorkbenchOffersOneRocketPageAndTheUnlockPage(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, null);

        List<ResourceLocation> pages = fixture.pageIds();
        ResourceLocation opened = fixture.menu.page().id();
        fixture.close();

        if (!pages.equals(List.of(ROCKET_PAGE, WorkbenchPages.ADD_SCHEMATIC_ID))) {
            context.fail("A fresh workbench should offer one rocket page and the unlock page, got " + pages, fixture.pos);
        } else if (!opened.equals(ROCKET_PAGE)) {
            context.fail("The workbench should open on modular assembly, got " + opened, fixture.pos);
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonBuggyGetsAPageOfItsOwn(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);

        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.MOON_BUGGY_SCHEMATIC));
        fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK);

        List<ResourceLocation> pages = fixture.visiblePageIds();
        fixture.close();
        if (pages.contains(BUGGY_PAGE)) {
            context.succeed();
        } else {
            context.fail("Unlocking the moon buggy schematic revealed no buggy page, got " + pages, fixture.pos);
        }
    }

    /** Flipping must walk the player's own pages and stop at the ends rather than wrapping. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void flippingWalksTheUnlockedPagesAndStopsAtTheEnds(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, null);

        if (fixture.menu.adjacentPage(-1) != null) {
            fixture.close();
            context.fail("Paging back from the first page should not be possible", fixture.pos);
            return;
        }
        WorkbenchPage next = fixture.menu.adjacentPage(1);
        if (next == null || !next.id().equals(WorkbenchPages.ADD_SCHEMATIC_ID)) {
            fixture.close();
            context.fail("Paging forward from the rocket page should reach the unlock page, got " + next, fixture.pos);
            return;
        }

        fixture.close();

        Fixture unlockPage = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);
        WorkbenchPage beyond = unlockPage.menu.adjacentPage(1);
        WorkbenchPage back = unlockPage.menu.adjacentPage(-1);
        unlockPage.close();

        if (beyond != null) {
            context.fail("The unlock page is the last page, but paging forward returned " + beyond, fixture.pos);
        } else if (back == null || !back.id().equals(ROCKET_PAGE)) {
            context.fail("Paging back from the unlock page should reach modular assembly, got " + back, fixture.pos);
        } else {
            context.succeed();
        }
    }

    /** The moon buggy had no recipe at all before it got a page; check the page actually builds one. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theBuggyPageAssemblesABuggy(GameTestHelper context) {
        Fixture fixture = Fixture.unlocked(context, GCItems.MOON_BUGGY_SCHEMATIC, BUGGY_PAGE);

        fixture.fillBuggyParts();

        ItemStack result = fixture.workbench.output.getItem(0);
        fixture.close();
        if (result.is(GCItems.BUGGY)) {
            context.succeed();
        } else {
            context.fail("A fully assembled buggy page produced " + result + " instead of a buggy", fixture.pos);
        }
    }

    /**
     * Legacy's buggy bench turned its three addon wells into the buggy's storage tier. This port
     * carries that as the buggy variant rather than as extra recipe ingredients.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void buggyStorageWellsChooseTheBuggyVariant(GameTestHelper context) {
        Fixture fixture = Fixture.unlocked(context, GCItems.MOON_BUGGY_SCHEMATIC, BUGGY_PAGE);

        fixture.fillBuggyParts();
        if (fixture.workbench.output.getItem(0).getOrDefault(GCDataComponents.BUGGY_TYPE, -1) != 0) {
            fixture.close();
            context.fail("A buggy built with no storage should be the plain variant", fixture.pos);
            return;
        }

        fixture.workbench.chests.setItem(0, new ItemStack(GCItems.BUGGY_STORAGE));
        fixture.workbench.chests.setChanged();
        int oneChest = fixture.workbench.output.getItem(0).getOrDefault(GCDataComponents.BUGGY_TYPE, -1);

        fixture.workbench.chests.setItem(1, new ItemStack(GCItems.BUGGY_STORAGE));
        fixture.workbench.chests.setChanged();
        int twoChests = fixture.workbench.output.getItem(0).getOrDefault(GCDataComponents.BUGGY_TYPE, -1);

        fixture.close();
        if (oneChest != Buggy.BuggyType.STORAGE_18.getId()) {
            context.fail("One storage upgrade should give the 18-slot buggy, got variant " + oneChest, fixture.pos);
        } else if (twoChests != Buggy.BuggyType.STORAGE_36.getId()) {
            context.fail("Two storage upgrades should give the 36-slot buggy, got variant " + twoChests, fixture.pos);
        } else {
            context.succeed();
        }
    }

    /**
     * The server names the page when it opens the menu, and both sides build their slot list from
     * it. A client whose research has not arrived yet must still lay out the page it was told to
     * open, or its slot indices disagree with the server's and items land in the wrong wells.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void anUnlockedPageStillOpensWhenTheClientHasNotSeenTheUnlockYet(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, BUGGY_PAGE);

        ResourceLocation opened = fixture.menu.page().id();
        int wells = (int) fixture.menu.slots.stream().filter(slot -> slot.container == fixture.workbench.ingredients).count();
        fixture.close();

        if (!opened.equals(BUGGY_PAGE)) {
            context.fail("Opening the buggy page without the unlock fell back to " + opened, fixture.pos);
        } else if (wells != 16) {
            context.fail("The buggy page laid out " + wells + " ingredient wells instead of 16", fixture.pos);
        } else {
            context.succeed();
        }
    }

    /**
     * Recipe-viewer transfer handlers address the workbench by slot index, so the page's slot order
     * is a contract: ingredient wells first, then the upgrade wells, then the result, then exactly
     * the player's 36 slots. Reading the result slot by arithmetic is what made EMI aim at an
     * upgrade well instead.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theMenuNamesItsIngredientAndResultSlots(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, null);

        List<Slot> ingredients = fixture.menu.ingredientSlots();
        Slot result = fixture.menu.resultSlot();
        int total = fixture.menu.slots.size();
        fixture.close();

        if (ingredients.size() != fixture.menu.getRecipeSize()) {
            context.fail("The tier-1 page reports " + fixture.menu.getRecipeSize()
                    + " ingredients but names " + ingredients.size() + " ingredient slots", fixture.pos);
            return;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            int offset = fixture.menu.refitInputSlot() == null ? 0 : 1;
            if (ingredients.get(i).index != i + offset) {
                context.fail("Ingredient wells must be consecutive after the refit input; well " + i
                        + " is at index " + ingredients.get(i).index, fixture.pos);
                return;
            }
        }
        if (result == null) {
            context.fail("The tier-1 page has no result slot", fixture.pos);
        } else if (result.container != fixture.workbench.output) {
            context.fail("The named result slot is not backed by the workbench output", fixture.pos);
        } else if (result.index != total - 36 - 1) {
            context.fail("The result slot should sit just before the player's 36 slots, got index "
                    + result.index + " of " + total, fixture.pos);
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierThreeSchematicUnlocksModulesWithoutAddingAnotherPage(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);
        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.TIER_3_ROCKET_SCHEMATIC));
        context.assertTrue(fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK), "Tier-3 schematic was rejected");
        context.assertTrue(fixture.workbench.schematic.isEmpty(), "Tier-3 schematic was not consumed");
        for (var part : WorkbenchPages.rocketPartsUnlockedBy(GCItems.TIER_3_ROCKET_SCHEMATIC)) {
            context.assertTrue(((ResearchAccessor) fixture.player).galacticraft$isUnlocked(dev.galacticraft.mod.content.GCRocketParts.recipeId(part)), "Tier-3 module research was not granted");
        }
        context.assertTrue(fixture.visiblePageIds().equals(List.of(ROCKET_PAGE, WorkbenchPages.ADD_SCHEMATIC_ID)), "Tier-3 schematic added a duplicate rocket page");
        context.assertTrue(fixture.player.containerMenu instanceof RocketWorkbenchMenu menu && menu.page().id().equals(ROCKET_PAGE), "Unlock did not open the Rocket page");
        fixture.close();
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void savedTierPageUnlocksBackfillModuleResearch(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, null);
        var research = (dev.galacticraft.api.accessor.ServerResearchAccessor) fixture.player;
        research.galacticraft$unlockRocketPartRecipes(unlockId(GCItems.TIER_2_ROCKET_SCHEMATIC), unlockId(GCItems.TIER_3_ROCKET_SCHEMATIC));
        for (var schematic : List.of(GCItems.TIER_2_ROCKET_SCHEMATIC, GCItems.TIER_3_ROCKET_SCHEMATIC)) {
            for (var part : WorkbenchPages.rocketPartsUnlockedBy(schematic))
                context.assertTrue(!research.galacticraft$isUnlocked(dev.galacticraft.mod.content.GCRocketParts.recipeId(part)), "Fixture already had module research");
        }
        var saved = fixture.player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        fixture.player.load(saved);
        var granted = dev.galacticraft.api.accessor.ServerResearchAccessor.galacticraft$backfillFromAdvancements(fixture.player);
        for (var schematic : List.of(GCItems.TIER_2_ROCKET_SCHEMATIC, GCItems.TIER_3_ROCKET_SCHEMATIC)) {
            for (var part : WorkbenchPages.rocketPartsUnlockedBy(schematic)) {
                var id = dev.galacticraft.mod.content.GCRocketParts.recipeId(part);
                context.assertTrue(granted.contains(id) && research.galacticraft$isUnlocked(id), "Saved tier unlock did not restore module research");
            }
        }
        context.assertTrue(dev.galacticraft.api.accessor.ServerResearchAccessor.galacticraft$backfillFromAdvancements(fixture.player).isEmpty(), "Migration granted research twice");
        fixture.close();
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void olderTierPartResearchKeepsDuplicateSchematics(GameTestHelper context) {
        Fixture fixture = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);
        var research = (dev.galacticraft.api.accessor.ServerResearchAccessor) fixture.player;
        var ids = WorkbenchPages.rocketPartsUnlockedBy(GCItems.TIER_2_ROCKET_SCHEMATIC).stream()
                .map(dev.galacticraft.mod.content.GCRocketParts::recipeId).toArray(ResourceLocation[]::new);
        research.galacticraft$unlockRocketPartRecipes(ids);
        dev.galacticraft.api.accessor.ServerResearchAccessor.galacticraft$backfillFromAdvancements(fixture.player);
        fixture.workbench.schematic.setItem(0, new ItemStack(GCItems.TIER_2_ROCKET_SCHEMATIC));
        context.assertTrue(fixture.menu.clickMenuButton(fixture.player, RocketWorkbenchMenu.BUTTON_UNLOCK), "Known schematic did not open assembly");
        context.assertTrue(fixture.workbench.schematic.getItem(0).is(GCItems.TIER_2_ROCKET_SCHEMATIC), "Previously researched schematic was consumed again");
        fixture.close();
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void legacyRocketRecipesAreAbsentAndOldPageRequestsOpenAssembly(GameTestHelper context) {
        for (var id : List.of("rocket/rocket", "rocket/rocket_tier_2", "rocket/rocket_tier_3")) {
            context.assertTrue(WorkbenchPages.recipe(context.getLevel(), Constant.id(id)) == null, "Legacy rocket recipe remains loaded");
            Fixture fixture = Fixture.on(context, Constant.id(id));
            context.assertTrue(fixture.menu.page().id().equals(ROCKET_PAGE) && fixture.menu.ingredientSlots().size() == 4, "Old page request did not fall back to module assembly");
            fixture.close();
        }
        context.succeed();
    }

    private static ResourceLocation unlockId(net.minecraft.world.item.Item schematic) {
        return WorkbenchPages.unlockId(BuiltInRegistries.ITEM.getKey(schematic));
    }

    /** Only used to reach the default method that builds a genuinely non-creative mock player. */
    private static final GalacticraftGameTest SURVIVAL_PLAYER = new GalacticraftGameTest() { };

    private record Fixture(GameTestHelper context, BlockPos pos, RocketWorkbenchBlockEntity workbench,
                           ServerPlayer player, RocketWorkbenchMenu menu) {
        static Fixture on(GameTestHelper context, ResourceLocation page) {
            BlockPos pos = new BlockPos(1, 1, 1);
            context.setBlock(pos, GCBlocks.ROCKET_WORKBENCH);
            RocketWorkbenchBlockEntity workbench = context.getBlockEntity(pos);
            ServerPlayer player = SURVIVAL_PLAYER.makeSurvivalServerPlayer(context);
            RocketWorkbenchMenu menu = page == null
                    ? new RocketWorkbenchMenu(1, workbench, player.getInventory())
                    : new RocketWorkbenchMenu(1, workbench, player.getInventory(), page);
            return new Fixture(context, pos, workbench, player, menu);
        }

        /** Opens the workbench on {@code page} with {@code schematic} already spent. */
        static Fixture unlocked(GameTestHelper context, net.minecraft.world.item.Item schematic, ResourceLocation page) {
            Fixture unlockPage = Fixture.on(context, WorkbenchPages.ADD_SCHEMATIC_ID);
            unlockPage.workbench.schematic.setItem(0, new ItemStack(schematic));
            unlockPage.menu.clickMenuButton(unlockPage.player, RocketWorkbenchMenu.BUTTON_UNLOCK);
            unlockPage.menu.removed(unlockPage.player);

            return new Fixture(context, unlockPage.pos, unlockPage.workbench, unlockPage.player,
                    new RocketWorkbenchMenu(1, unlockPage.workbench, unlockPage.player.getInventory(), page));
        }

        /** Eleven plating, the seat in the middle column, and four wheels - legacy's buggy recipe. */
        void fillBuggyParts() {
            for (int slot = 0; slot < 12; slot++) {
                this.workbench.ingredients.setItem(slot, new ItemStack(slot == 5 ? GCItems.BUGGY_SEAT : GCItems.TIER_1_HEAVY_DUTY_PLATE));
            }
            for (int slot = 12; slot < 16; slot++) {
                this.workbench.ingredients.setItem(slot, new ItemStack(GCItems.BUGGY_WHEEL));
            }
            this.workbench.ingredients.setChanged();
        }

        Slot schematicSlot() {
            return this.menu.slots.stream()
                    .filter(slot -> slot.container == this.workbench.schematic)
                    .findFirst()
                    .orElse(null);
        }

        /** The pages the open menu was built with. */
        List<ResourceLocation> pageIds() {
            return this.menu.pages().stream().map(WorkbenchPage::id).toList();
        }

        /**
         * The pages the player would see now. Unlocking reopens the workbench in-game, so a menu
         * built earlier still holds the page list from before the unlock.
         */
        List<ResourceLocation> visiblePageIds() {
            return WorkbenchPages.visible(this.player.level(), this.player).stream().map(WorkbenchPage::id).toList();
        }

        void close() {
            this.menu.removed(this.player);
        }
    }
}
