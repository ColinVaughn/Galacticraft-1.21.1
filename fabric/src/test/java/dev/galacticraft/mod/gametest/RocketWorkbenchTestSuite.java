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
import dev.galacticraft.mod.recipe.GCRecipes;
import dev.galacticraft.mod.recipe.RocketRecipe;
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
import net.minecraft.world.level.GameType;

import java.util.List;

public class RocketWorkbenchTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void mixedTierRocketPartsAreRejected(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());

        for (int slot = 1; slot <= 8; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.TIER_1_HEAVY_DUTY_PLATE));
        }
        for (int slot = 9; slot <= 12; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.ROCKET_FIN));
        }
        workbench.ingredients.setItem(13, new ItemStack(GCItems.ROCKET_ENGINE));
        workbench.ingredients.setChanged();

        Slot coneSlot = menu.slots.getFirst();
        if (coneSlot.mayPlace(new ItemStack(GCItems.HEAVY_NOSE_CONE))) {
            menu.removed(player);
            context.fail("Expected the tier-1 assembly to reject a tier-3 heavy nose cone", workbenchPos);
            return;
        }

        // Reproduce a legacy/persisted mixed-tier inventory that bypassed slot validation.
        workbench.ingredients.setItem(0, new ItemStack(GCItems.HEAVY_NOSE_CONE));
        workbench.ingredients.setChanged();
        if (!workbench.output.getItem(0).isEmpty()) {
            menu.removed(player);
            context.fail("Expected no output for mixed-tier rocket parts", workbenchPos);
            return;
        }
        if (menu.previewRocket().cone().isPresent()) {
            menu.removed(player);
            context.fail("Expected the preview to mark the incompatible heavy nose cone as incomplete", workbenchPos);
            return;
        }

        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        workbench.ingredients.setChanged();
        menu.removed(player);
        if (workbench.output.getItem(0).is(GCItems.ROCKET)) {
            context.succeed();
        } else {
            context.fail("Expected replacing the heavy nose cone with a tier-1 nose cone to craft the rocket", workbenchPos);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierOneRocketCanBeCrafted(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE)
        );

        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        for (int slot = 1; slot <= 8; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.TIER_1_HEAVY_DUTY_PLATE));
        }
        for (int slot = 9; slot <= 12; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.ROCKET_FIN));
        }
        workbench.ingredients.setItem(13, new ItemStack(GCItems.ROCKET_ENGINE));
        workbench.ingredients.setChanged();

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
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE)
        );

        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        for (int slot = 1; slot <= 8; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.TIER_1_HEAVY_DUTY_PLATE));
        }
        for (int slot = 9; slot <= 12; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.ROCKET_FIN));
        }
        workbench.ingredients.setItem(13, new ItemStack(GCItems.ROCKET_ENGINE));
        workbench.ingredients.setChanged();
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
     * Every rocket recipe is offered to the player through JEI/EMI, so every rocket recipe has to
     * be satisfiable with the slots the workbench actually has.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void everyRocketRecipeIsReachableInTheWorkbench(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        int slots = workbench.ingredients.getContainerSize();
        menu.removed(player);

        for (RecipeHolder<RocketRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(GCRecipes.ROCKET_TYPE)) {
            int required = holder.value().getIngredients().size();
            if (required != slots) {
                context.fail("Recipe " + holder.id() + " needs " + required
                        + " ingredient slots but the workbench only ever has " + slots
                        + ", so it can never be crafted", workbenchPos);
                return;
            }
        }

        context.succeed();
    }
}
