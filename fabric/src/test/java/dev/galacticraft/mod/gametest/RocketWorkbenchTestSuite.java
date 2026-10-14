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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.recipe.WorkbenchRecipe;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import dev.galacticraft.mod.Constant;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

public class RocketWorkbenchTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void legacyLoosePartsCannotReplaceRocketModules(GameTestHelper context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlock(pos, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(pos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        fillTierOneModules(workbench);
        context.assertTrue(!menu.ingredientSlots().getFirst().mayPlace(new ItemStack(GCItems.NOSE_CONE)), "Rocket page accepted a loose nose cone");
        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        workbench.ingredients.setChanged();
        context.assertTrue(workbench.output.isEmpty() && menu.previewRocket().cone().isEmpty(), "Legacy parts produced a modular rocket");
        fillTierOneModules(workbench);
        context.assertTrue(workbench.output.getItem(0).is(GCItems.ROCKET), "Replacing the loose part with a module did not craft");
        menu.removed(player);
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierOneRocketCanBeCrafted(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = this.makeSurvivalServerPlayer(context);
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE)
        );

        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        fillTierOneModules(workbench);

        if (!workbench.output.getItem(0).is(GCItems.ROCKET)) {
            context.fail("Expected a tier-1 rocket in the workbench result slot", workbenchPos);
            return;
        }

        Slot resultSlot = menu.slots.stream()
                .filter(slot -> slot.container == workbench.output)
                .findFirst()
                .orElseThrow();
        ItemStack crafted = resultSlot.remove(1);
        resultSlot.onTake(player, crafted);
        menu.removed(player);

        if (!crafted.is(GCItems.ROCKET)) {
            context.fail("Expected to take the crafted tier-1 rocket", workbenchPos);
        } else if (!workbench.ingredients.isEmpty()) {
            context.fail("Expected crafting to consume all tier-1 rocket parts", workbenchPos);
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierOneRocketWithStorageUpgradeCanBeCrafted(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = this.makeSurvivalServerPlayer(context);
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE)
        );

        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        fillTierOneModules(workbench);
        workbench.chests.setItem(0, new ItemStack(Items.CHEST));
        workbench.chests.setChanged();

        ItemStack output = workbench.output.getItem(0);
        if (!output.is(GCItems.ROCKET)) {
            menu.removed(player);
            context.fail("Expected a tier-1 rocket in the result slot when a chest is installed", workbenchPos);
            return;
        }

        RocketData data = output.get(GCDataComponents.ROCKET_DATA);
        menu.removed(player);
        if (data == null || data.upgrade().isEmpty()) {
            context.fail("Expected the crafted rocket to carry the storage upgrade", workbenchPos);
            return;
        }

        // The result slot is synced to the client with this codec; if it cannot round-trip, the
        // client sees an empty result slot even though the server built the rocket.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf, output);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buf);
            RocketData decodedData = decoded.get(GCDataComponents.ROCKET_DATA);
            if (decodedData == null || decodedData.upgrade().isEmpty()) {
                context.fail("Storage upgrade was lost syncing the result slot to the client", workbenchPos);
                return;
            }
        } catch (Exception e) {
            context.fail("Failed to sync a storage-upgrade rocket to the client: " + e, workbenchPos);
            return;
        }

        context.succeed();
    }

    /**
     * Advancement rewards only fire the moment an advancement completes, so a save whose
     * {@code rocket_workbench} advancement predates that reward can never craft a rocket in
     * survival. Joining has to re-apply the rewards of already-completed advancements.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void completedAdvancementsBackfillRocketPartResearch(GameTestHelper context) {
        BlockPos origin = new BlockPos(1, 1, 1);
        ServerPlayer player = context.makeMockServerPlayerInLevel();

        if (!ServerResearchAccessor.galacticraft$backfillFromAdvancements(player).isEmpty()) {
            context.fail("A player with no completed advancements should be granted nothing", origin);
            return;
        }

        AdvancementHolder advancement = context.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.withDefaultNamespace(Constant.MOD_ID + "/rocket_workbench"));
        if (advancement == null) {
            context.fail("Could not find the rocket_workbench advancement", origin);
            return;
        }
        for (String criterion : advancement.value().criteria().keySet()) {
            player.getAdvancements().award(advancement, criterion);
        }

        List<ResourceLocation> granted = ServerResearchAccessor.galacticraft$backfillFromAdvancements(player);
        for (ResourceKey<?> part : List.of(
                GCRocketParts.TIER_1_CONE, GCRocketParts.TIER_1_BODY,
                GCRocketParts.TIER_1_FIN, GCRocketParts.TIER_1_ENGINE)) {
            if (!granted.contains(GCRocketParts.recipeId(part))) {
                context.fail("Backfill missed " + GCRocketParts.recipeId(part)
                        + " for a completed rocket_workbench advancement", origin);
                return;
            }
        }

        context.succeed();
    }

    /**
     * Every workbench recipe is offered to the player through JEI/EMI, so every one of them has to
     * be satisfiable with the wells its page actually draws. A recipe with more ingredients than
     * wells can never be completed, and the extra ingredients are invisible.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void everyWorkbenchRecipeFillsExactlyTheWellsItsPageDraws(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);

        for (RecipeHolder<? extends WorkbenchRecipe> holder : WorkbenchPages.recipes(context.getLevel())) {
            int ingredients = holder.value().getIngredients().size();
            int wells = holder.value().ingredientSlots().size();
            if (ingredients != wells) {
                context.fail("Recipe " + holder.id() + " lists " + ingredients
                        + " ingredients but its page draws " + wells + " wells, so it can never be crafted", workbenchPos);
                return;
            }
        }

        context.succeed();
    }

    /** Modular assembly is visible immediately; selected parts still require their own research. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void rocketPageIsTheOnlySchematicFreeAssemblyPage(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);

        List<ResourceLocation> free = new java.util.ArrayList<>();
        for (RecipeHolder<? extends WorkbenchRecipe> holder : WorkbenchPages.recipes(context.getLevel())) {
            if (holder.value().schematic().isEmpty()) {
                free.add(holder.id());
            }
        }

        if (java.util.Set.copyOf(free).equals(java.util.Set.of(WorkbenchPages.ROCKET_ID))) {
            context.succeed();
        } else {
            context.fail("Expected one schematic-free rocket assembly page, got " + free, workbenchPos);
        }
    }

    private static void fillTierOneModules(RocketWorkbenchBlockEntity workbench) {
        var types = List.of(dev.galacticraft.api.rocket.part.RocketPartTypes.CONE, dev.galacticraft.api.rocket.part.RocketPartTypes.BODY,
                dev.galacticraft.api.rocket.part.RocketPartTypes.FIN, dev.galacticraft.api.rocket.part.RocketPartTypes.ENGINE);
        for (int slot = 0; slot < types.size(); slot++) {
            var type = types.get(slot);
            var module = GCItems.ROCKET_MODULES.stream().filter(item -> item.type() == type && item.tier() == 1).findFirst().orElseThrow();
            workbench.ingredients.setItem(slot, module.getDefaultInstance());
        }
        workbench.ingredients.setChanged();
    }
}
