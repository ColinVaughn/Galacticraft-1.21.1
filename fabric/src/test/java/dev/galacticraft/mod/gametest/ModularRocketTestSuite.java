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
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import dev.galacticraft.mod.recipe.GCRecipes;
import dev.galacticraft.mod.recipe.ModularRocketRecipe;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;

import java.util.List;

public class ModularRocketTestSuite implements GalacticraftGameTest {
    private static final List<RocketPartTypes> TYPES = List.of(RocketPartTypes.CONE, RocketPartTypes.BODY, RocketPartTypes.FIN, RocketPartTypes.ENGINE);

    @GameTest(template = EMPTY_STRUCTURE)
    public void allTierCombinationsCraftAndConsumeExactlyFourModules(GameTestHelper context) {
        Fixture fixture = fixture(context, true);
        context.assertTrue(fixture.menu.ingredientSlots().size() == 4, "Modular page did not draw four module wells");
        int crafts = 0;
        for (int cone = 1; cone <= 3; cone++) for (int body = 1; body <= 3; body++)
            for (int fins = 1; fins <= 3; fins++) for (int engine = 1; engine <= 3; engine++) {
                fill(fixture, cone, body, fins, engine);
                ItemStack result = fixture.workbench.output.getItem(0);
                context.assertTrue(result.is(GCItems.ROCKET), "No result for " + List.of(cone, body, fins, engine));
                assertParts(context, result.get(GCDataComponents.ROCKET_DATA), cone, body, fins, engine);
                assertParts(context, fixture.menu.previewRocket(), cone, body, fins, engine);
                var slot = fixture.menu.resultSlot();
                ItemStack crafted = slot.remove(1);
                slot.onTake(fixture.player, crafted);
                context.assertTrue(fixture.workbench.ingredients.isEmpty(), "Crafting left module ingredients behind");
                context.assertTrue(fixture.workbench.output.isEmpty(), "Crafting left a duplicate output");
                crafts++;
            }
        fixture.menu.removed(fixture.player);
        context.assertTrue(crafts == 81, "Did not cover every tier combination");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wrongSlotMissingModuleAndForgedComponentsAreHandled(GameTestHelper context) {
        Fixture fixture = fixture(context, true);
        fill(fixture, 2, 3, 1, 1);
        fixture.workbench.ingredients.setItem(3, ItemStack.EMPTY);
        fixture.workbench.ingredients.setChanged();
        context.assertTrue(fixture.workbench.output.isEmpty(), "Incomplete assembly produced a rocket");
        context.assertTrue(fixture.menu.previewRocket().engine().isEmpty(), "Preview retained a missing engine");
        context.assertTrue(!fixture.menu.ingredientSlots().getFirst().mayPlace(module(RocketPartTypes.BODY, 3).getDefaultInstance()),
                "Cone well accepted a body module");
        fixture.workbench.ingredients.setItem(3, module(RocketPartTypes.CONE, 1).getDefaultInstance());
        fixture.workbench.ingredients.setChanged();
        context.assertTrue(fixture.workbench.output.isEmpty(), "Wrong module type produced a rocket");
        ItemStack body = module(RocketPartTypes.BODY, 3).getDefaultInstance();
        body.set(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1);
        fixture.workbench.ingredients.setItem(1, body);
        fixture.workbench.ingredients.setItem(3, module(RocketPartTypes.ENGINE, 1).getDefaultInstance());
        fixture.workbench.ingredients.setChanged();
        assertParts(context, fixture.workbench.output.getItem(0).get(GCDataComponents.ROCKET_DATA), 2, 3, 1, 1);
        fixture.menu.removed(fixture.player);
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void selectedPartResearchStillGatesMixedAssembly(GameTestHelper context) {
        Fixture fixture = fixture(context, false);
        ((ServerResearchAccessor) fixture.player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE), GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN), GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE));
        fill(fixture, 1, 3, 1, 1);
        context.assertTrue(fixture.workbench.output.isEmpty(), "Unresearched tier-3 body bypassed research");
        context.assertTrue(fixture.menu.getMissingResearch().size() == 1, "Workbench did not explain the missing body research");
        ((ServerResearchAccessor) fixture.player).galacticraft$unlockRocketPartRecipes(GCRocketParts.recipeId(GCRocketParts.TIER_3_BODY));
        fixture.menu.onItemChanged();
        assertParts(context, fixture.workbench.output.getItem(0).get(GCDataComponents.ROCKET_DATA), 1, 3, 1, 1);
        fixture.menu.removed(fixture.player);
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mixedRocketStorageAndNetworkSyncPreserveTheModules(GameTestHelper context) {
        Fixture fixture = fixture(context, true);
        fill(fixture, 2, 3, 3, 1);
        for (int i = 0; i < 3; i++) fixture.workbench.chests.setItem(i, new ItemStack(Items.CHEST));
        ItemStack result = fixture.workbench.output.getItem(0);
        RocketData data = result.get(GCDataComponents.ROCKET_DATA);
        assertParts(context, data, 2, 3, 3, 1);
        context.assertTrue(data.getUpgradeData(StorageRocketData.class).orElseThrow().chests() == 3, "Mixed rocket lost storage");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf, result);
            RocketData decoded = ItemStack.STREAM_CODEC.decode(buf).get(GCDataComponents.ROCKET_DATA);
            assertParts(context, decoded, 2, 3, 3, 1);
            context.assertTrue(decoded.getUpgradeData(StorageRocketData.class).orElseThrow().chests() == 3, "Client sync lost storage");
        } finally { buf.release(); }
        var slot = fixture.menu.resultSlot();
        slot.onTake(fixture.player, slot.remove(1));
        context.assertTrue(fixture.workbench.chests.isEmpty(), "Crafting did not consume the upgrade chests");
        fixture.menu.removed(fixture.player);
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mixedRocketEntitySaveAndCollisionSizePreserveTheModules(GameTestHelper context) {
        Fixture fixture = fixture(context, true);
        fill(fixture, 2, 3, 3, 1);
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
        context.assertTrue(Math.abs(rocket.getBbWidth() - 2.5F) < .001, "Default tier-1 spawn box does not cover its landing legs");
        rocket.setData(fixture.workbench.output.getItem(0).get(GCDataComponents.ROCKET_DATA));
        rocket.setPos(context.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        rocket.setFuel(rocket.getFuelTankCapacity() / 2);
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        CompoundTag saved = rocket.saveWithoutId(new CompoundTag());
        RocketEntity loaded = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
        loaded.load(saved);
        assertParts(context, loaded.getRocketData(), 2, 3, 3, 1);
        context.assertTrue(Math.abs(loaded.getBbHeight() - 91 / 16F) < .001, "Tall cone collision height was lost");
        context.assertTrue(Math.abs(loaded.getBbWidth() - 44.8F / 16F) < .001, "Wide fins collision width was lost");
        context.assertTrue(loaded.getFuel() == rocket.getFuel(), "Entity reload lost fuel");
        context.assertTrue(loaded.getLaunchStage() == LaunchStage.LAUNCHED, "Entity reload lost flight state");
        context.assertTrue(loaded.body().is(GCRocketParts.TIER_3_BODY) && loaded.engine().is(GCRocketParts.TIER_1_ENGINE),
                "Entity did not resolve the mixed part holders");
        fixture.menu.removed(fixture.player);
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyModuleHasAWorkingCraftingRecipe(GameTestHelper context) {
        for (RocketModuleItem module : GCItems.ROCKET_MODULES) {
            var holder = context.getLevel().getRecipeManager().byKey(BuiltInRegistries.ITEM.getKey(module)).orElseThrow();
            context.assertTrue(holder.value() instanceof ShapedRecipe, "Module has no shaped crafting recipe");
            ShapedRecipe recipe = (ShapedRecipe) holder.value();
            var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), recipe.getIngredients().stream()
                    .map(ingredient -> ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy()).toList());
            context.assertTrue(recipe.matches(input, context.getLevel()), "Module recipe cannot be crafted");
            context.assertTrue(recipe.assemble(input, context.getLevel().registryAccess()).is(module), "Module recipe produced the wrong item");
        }
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void modularRecipeSyncKeepsItsFourTypedWells(GameTestHelper context) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        try {
            var serializer = GCRecipes.MODULAR_ROCKET_SERIALIZER;
            serializer.streamCodec().encode(buf, new ModularRocketRecipe());
            var decoded = serializer.streamCodec().decode(buf);
            context.assertTrue(decoded.slots().size() == 4 && decoded.getIngredients().size() == 4, "Recipe sync changed the well count");
            for (int i = 0; i < 4; i++) for (int tier = 1; tier <= 3; tier++) {
                context.assertTrue(decoded.getIngredients().get(i).test(module(TYPES.get(i), tier).getDefaultInstance()),
                        "Synced recipe rejected a module tier");
            }
        } finally { buf.release(); }
        context.succeed();
    }

    private static RocketModuleItem module(RocketPartTypes type, int tier) {
        return GCItems.ROCKET_MODULES.stream().filter(module -> module.type() == type && module.tier() == tier).findFirst().orElseThrow();
    }

    private static void fill(Fixture fixture, int cone, int body, int fins, int engine) {
        int[] tiers = {cone, body, fins, engine};
        for (int i = 0; i < 4; i++) fixture.workbench.ingredients.setItem(i, module(TYPES.get(i), tiers[i]).getDefaultInstance());
        fixture.workbench.ingredients.setChanged();
    }

    private static void assertParts(GameTestHelper context, RocketData data, int cone, int body, int fins, int engine) {
        context.assertTrue(data != null && data.isValid(), "Rocket data is incomplete");
        context.assertTrue(data.cone().orElseThrow().key().equals(module(RocketPartTypes.CONE, cone).rocketData().cone().orElseThrow().key()), "Wrong cone");
        context.assertTrue(data.body().orElseThrow().key().equals(module(RocketPartTypes.BODY, body).rocketData().body().orElseThrow().key()), "Wrong body");
        context.assertTrue(data.fin().orElseThrow().key().equals(module(RocketPartTypes.FIN, fins).rocketData().fin().orElseThrow().key()), "Wrong fins");
        context.assertTrue(data.engine().orElseThrow().key().equals(module(RocketPartTypes.ENGINE, engine).rocketData().engine().orElseThrow().key()), "Wrong engine");
    }

    private record Fixture(RocketWorkbenchBlockEntity workbench, ServerPlayer player, RocketWorkbenchMenu menu) { }

    private Fixture fixture(GameTestHelper context, boolean creative) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlock(pos, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(pos);
        ServerPlayer player = creative ? context.makeMockServerPlayerInLevel() : this.makeSurvivalServerPlayer(context);
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        return new Fixture(workbench, player, new RocketWorkbenchMenu(1, workbench, player.getInventory(), Constant.id("rocket/modular")));
    }
}

