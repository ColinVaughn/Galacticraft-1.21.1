/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.machine.workbench.RocketRefit;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class RocketRefitGameTest implements GalacticraftGameTest {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final int COLOR = 0xFF426BA8;

    @GameTest(template = EMPTY_STRUCTURE)
    public void allModulesExtractSwapAndRetainPaintAndItemComponents(GameTestHelper h) {
        var f = fixture(h, true);
        for (RocketModuleItem module : GCItems.ROCKET_MODULES) {
            ItemStack rocket = rocket(RocketPrefabs.TIER_1_STORAGE_UPGRADE);
            RocketData data = RocketRefit.replace(RocketRefit.data(rocket), module.type(), module.getDefaultInstance());
            rocket.set(GCDataComponents.ROCKET_DATA, data.withPaint(RocketPaint.NONE.withColor(module.type(), COLOR)));
            f.bench.refitInput.setItem(0, rocket.copy());
            Slot part = partSlot(f.menu, module.type());
            f.menu.clicked(part.index, 0, ClickType.PICKUP, f.player);
            h.assertTrue(f.menu.getCarried().is(module) && f.menu.getCarried().getCount() == 1, "Wrong extracted module");
            h.assertTrue(((RocketModuleItem) f.menu.getCarried().getItem()).rocketData(f.menu.getCarried()).paint().color(module.type()).orElseThrow() == COLOR, "Extracted paint changed");
            h.assertTrue(!RocketRefit.hasPart(RocketRefit.data(f.menu.refitRocket()), module.type()) && f.menu.resultSlot().getItem().isEmpty(), "Removal left an intact rocket result");
            f.menu.clicked(part.index, 0, ClickType.PICKUP, f.player);
            h.assertTrue(f.menu.getCarried().isEmpty() && RocketRefit.data(f.menu.refitRocket()).isValid(), "Module did not reinstall");
            ItemStack replacement = module(module.type(), module.tier() == 3 ? 1 : 3).getDefaultInstance();
            f.menu.setCarried(replacement);
            f.menu.clicked(part.index, 1, ClickType.PICKUP, f.player);
            h.assertTrue(f.menu.getCarried().is(module), "Swap destroyed the old module");
            RocketData updated = RocketRefit.data(f.menu.refitRocket());
            h.assertTrue(updated.paint().color(module.type()).isEmpty(), "New module inherited old paint");
            h.assertTrue(updated.upgrade().equals(data.upgrade()) && updated.upgradeData().equals(data.upgradeData()), "Refit stripped storage");
            h.assertTrue(f.menu.refitRocket().get(DataComponents.CUSTOM_NAME).equals(rocket.get(DataComponents.CUSTOM_NAME)), "Refit stripped custom name");
            f.menu.setCarried(ItemStack.EMPTY);
            f.menu.clicked(f.menu.resultSlot().index, 0, ClickType.PICKUP, f.player);
            h.assertTrue(f.menu.getCarried().is(GCItems.ROCKET) && f.bench.refitInput.isEmpty() && f.bench.output.isEmpty(), "Returning rocket duplicated the result");
            f.menu.setCarried(ItemStack.EMPTY);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullInventoryShiftClicksAndHotbarSwapsConserveItems(GameTestHelper h) {
        var f = fixture(h, true);
        f.bench.refitInput.setItem(0, rocket(RocketPrefabs.TIER_1));
        for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.PAPER, 64));
        Slot engine = partSlot(f.menu, RocketPartTypes.ENGINE);
        ItemStack original = f.menu.refitRocket().copy();
        h.assertTrue(f.menu.quickMoveStack(f.player, engine.index).isEmpty() && ItemStack.matches(original, f.menu.refitRocket()), "Full inventory lost a removed engine");
        f.player.getInventory().setItem(9, module(RocketPartTypes.ENGINE, 3).getDefaultInstance());
        Slot incoming = inventorySlot(f.menu, 9);
        h.assertTrue(f.menu.quickMoveStack(f.player, incoming.index).isEmpty(), "Full inventory accepted a shift-swap without return space");
        h.assertTrue(f.menu.quickMoveStack(f.player, f.menu.resultSlot().index).isEmpty(), "Full inventory lost the result");
        f.player.getInventory().setItem(10, ItemStack.EMPTY);
        h.assertTrue(!f.menu.quickMoveStack(f.player, incoming.index).isEmpty(), "Shift-swap failed");
        h.assertTrue(f.player.getInventory().countItem(module(RocketPartTypes.ENGINE, 1)) == 1, "Shift-swap lost or duplicated the old engine");
        h.assertTrue(RocketRefit.key(RocketRefit.data(f.menu.refitRocket()), RocketPartTypes.ENGINE).equals(RocketRefit.key(module(RocketPartTypes.ENGINE, 3).rocketData(), RocketPartTypes.ENGINE)), "Shift-swap installed the wrong engine");
        f.player.getInventory().setItem(0, module(RocketPartTypes.ENGINE, 2).getDefaultInstance());
        f.menu.clicked(engine.index, 0, ClickType.SWAP, f.player);
        h.assertTrue(f.player.getInventory().getItem(0).is(module(RocketPartTypes.ENGINE, 3)), "Hotbar swap lost the old engine");
        f.player.getInventory().setItem(1, ItemStack.EMPTY);
        f.menu.clicked(f.menu.resultSlot().index, 1, ClickType.SWAP, f.player);
        h.assertTrue(f.player.getInventory().getItem(1).is(GCItems.ROCKET) && f.bench.refitInput.isEmpty(), "Hotbar result swap left the input rocket behind");
        f.menu.clicked(f.menu.resultSlot().index, 2, ClickType.SWAP, f.player);
        h.assertTrue(f.player.getInventory().countItem(GCItems.ROCKET) == 1, "Repeated result swap duplicated a rocket");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void researchWrongTypesUnsupportedPartsAndOwnershipAreValidated(GameTestHelper h) {
        var f = fixture(h, false);
        f.bench.refitInput.setItem(0, rocket(RocketPrefabs.TIER_3));
        Slot engine = partSlot(f.menu, RocketPartTypes.ENGINE);
        ItemStack original = f.menu.refitRocket().copy();
        f.menu.setCarried(module(RocketPartTypes.BODY, 1).getDefaultInstance());
        f.menu.clicked(engine.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(ItemStack.matches(original, f.menu.refitRocket()) && f.menu.getCarried().is(module(RocketPartTypes.BODY, 1)), "Wrong type replaced an engine");
        f.menu.setCarried(module(RocketPartTypes.ENGINE, 2).getDefaultInstance());
        f.menu.clicked(engine.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(ItemStack.matches(original, f.menu.refitRocket()), "Unresearched replacement installed");
        ((ServerResearchAccessor) f.player).galacticraft$unlockRocketPartRecipes(GCRocketParts.recipeId(GCRocketParts.TIER_2_ENGINE));
        f.menu.clicked(engine.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(module(RocketPartTypes.ENGINE, 3)) && !ItemStack.matches(original, f.menu.refitRocket()), "Research blocked unchanged higher-tier parts");
        ItemStack updated = f.menu.refitRocket().copy();
        f.player.setPos(f.player.position().add(30, 0, 0));
        f.menu.setCarried(ItemStack.EMPTY);
        f.menu.clicked(engine.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(ItemStack.matches(updated, f.menu.refitRocket()), "Out-of-range removal succeeded");
        f.player.setPos(h.absolutePos(POS).getCenter());
        f.player.containerMenu = f.player.inventoryMenu;
        f.menu.clicked(engine.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(ItemStack.matches(updated, f.menu.refitRocket()), "A closed menu edited the rocket");
        f.player.containerMenu = f.menu;
        RocketData base = RocketPrefabs.TIER_1;
        RocketData unknown = new RocketData(java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(
                net.minecraft.resources.ResourceKey.create(net.minecraft.resources.ResourceKey.createRegistryKey(GCRocketParts.TIER_1_CONE.registry()), Constant.id("unknown_cone")))),
                base.body(), base.fin(), base.booster(), base.engine(), base.upgrade(), base.upgradeData(), base.color());
        f.bench.refitInput.setItem(0, rocket(unknown));
        Slot cone = partSlot(f.menu, RocketPartTypes.CONE);
        ItemStack cargo = f.menu.refitRocket().copy();
        f.menu.setCarried(module(RocketPartTypes.CONE, 1).getDefaultInstance());
        f.menu.clicked(cone.index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(ItemStack.matches(cargo, f.menu.refitRocket()), "Unsupported cone was destroyed");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void partialRocketsPersistAcrossPagesSaveAndBlockBreak(GameTestHelper h) {
        var f = fixture(h, true);
        ItemStack rocket = rocket(RocketPrefabs.TIER_1_STORAGE_UPGRADE.withPaint(RocketPaint.NONE.withColor(RocketPartTypes.BODY, COLOR)));
        f.bench.refitInput.setItem(0, rocket);
        f.menu.clicked(partSlot(f.menu, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, f.player);
        ItemStack partial = f.menu.refitRocket().copy();
        f.menu.setCarried(ItemStack.EMPTY);
        f.menu.removed(f.player);
        var otherPage = new RocketWorkbenchMenu(2, f.bench, f.player.getInventory(), Constant.id("add_schematic"));
        otherPage.removed(f.player);
        var saved = f.bench.saveWithoutMetadata(h.getLevel().registryAccess());
        var loaded = new RocketWorkbenchBlockEntity(f.bench.getBlockPos(), f.bench.getBlockState());
        loaded.loadWithComponents(saved, h.getLevel().registryAccess());
        var ops = h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var expected = ItemStack.CODEC.encodeStart(ops, partial).getOrThrow();
        var actual = ItemStack.CODEC.encodeStart(ops, loaded.refitInput.getItem(0)).getOrThrow();
        h.assertTrue(expected.equals(actual), "Partial rocket changed on save: " + expected + " != " + actual);
        var reopened = new RocketWorkbenchMenu(3, f.bench, f.player.getInventory(), Constant.id("rocket/modular"));
        f.player.containerMenu = reopened;
        h.assertTrue(!reopened.previewRocket().isValid() && reopened.resultSlot().getItem().isEmpty(), "Reopening regenerated a missing engine");
        h.setBlock(POS, Blocks.AIR);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(2));
        h.assertTrue(drops.stream().filter(drop -> ItemStack.matches(partial, drop.getItem())).mapToInt(drop -> drop.getItem().getCount()).sum() == 1, "Breaking the workbench lost or duplicated the partial rocket");
        h.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().getItem() instanceof RocketModuleItem), "Breaking the workbench dropped attached module copies");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void multiplePlayersCannotExtractTheSamePartOrResultTwice(GameTestHelper h) {
        var f = fixture(h, true);
        f.bench.refitInput.setItem(0, rocket(RocketPrefabs.TIER_1));
        var other = h.makeMockServerPlayerInLevel();
        other.setPos(f.player.position());
        var second = new RocketWorkbenchMenu(2, f.bench, other.getInventory(), Constant.id("rocket/modular"));
        other.containerMenu = second;
        f.menu.clicked(partSlot(f.menu, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, f.player);
        second.clicked(partSlot(second, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, other);
        h.assertTrue(f.menu.getCarried().is(module(RocketPartTypes.ENGINE, 1)) && second.getCarried().isEmpty(), "Concurrent extraction duplicated an engine");
        f.menu.clicked(partSlot(f.menu, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, f.player);
        second.clicked(second.resultSlot().index, 0, ClickType.PICKUP, other);
        f.menu.clicked(f.menu.resultSlot().index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(second.getCarried().is(GCItems.ROCKET) && f.menu.getCarried().isEmpty() && f.bench.refitInput.isEmpty(), "Concurrent result pickup duplicated a rocket");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void insertionRespectsAssemblyAndIncompleteRocketsCannotLaunch(GameTestHelper h) {
        var f = fixture(h, true);
        ItemStack original = rocket(RocketPrefabs.TIER_1);
        f.bench.ingredients.setItem(0, module(RocketPartTypes.CONE, 1).getDefaultInstance());
        h.assertTrue(!f.menu.refitInputSlot().mayPlace(original), "Refit replaced assembly ingredients");
        f.bench.ingredients.clearContent();
        f.bench.chests.setItem(0, new ItemStack(Items.CHEST));
        h.assertTrue(!f.menu.refitInputSlot().mayPlace(original), "Refit hid existing upgrades");
        f.bench.chests.clearContent();
        f.player.getInventory().setItem(9, original);
        h.assertTrue(!f.menu.quickMoveStack(f.player, inventorySlot(f.menu, 9).index).isEmpty() && f.menu.isRefitting(), "Rocket shift-click did not enter the refit input");
        f.menu.clicked(partSlot(f.menu, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, f.player);
        f.menu.setCarried(ItemStack.EMPTY);
        f.menu.clicked(f.menu.refitInputSlot().index, 0, ClickType.PICKUP, f.player);
        ItemStack incomplete = f.menu.getCarried().copy();
        h.assertTrue(!RocketRefit.data(incomplete).isValid() && f.bench.refitInput.isEmpty(), "Incomplete project could not be retrieved");
        BlockPos padPos = new BlockPos(4, 1, 4);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) h.setBlock(padPos.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD);
        h.assertTrue(h.getBlockState(padPos).getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.CENTER, "Launch pad did not form");
        LaunchPadBlockEntity pad = h.getBlockEntity(padPos);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, incomplete);
        var hit = new BlockHitResult(h.absolutePos(padPos).getCenter(), Direction.UP, h.absolutePos(padPos), false);
        InteractionResult result = GCItems.ROCKET.useOn(new UseOnContext(f.player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result == InteractionResult.FAIL && !pad.hasDockedEntity() && f.player.getMainHandItem().getCount() == 1, "Incomplete rocket was placed or consumed");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void legacyTintSurvivesExtractionAndDoesNotTintReplacements(GameTestHelper h) {
        var f = fixture(h, true);
        RocketData base = RocketPrefabs.TIER_1;
        RocketData tinted = new RocketData(base.cone(), base.body(), base.fin(), base.booster(), base.engine(), base.upgrade(), base.upgradeData(), COLOR);
        f.bench.refitInput.setItem(0, rocket(tinted));
        f.menu.setCarried(module(RocketPartTypes.ENGINE, 3).getDefaultInstance());
        f.menu.clicked(partSlot(f.menu, RocketPartTypes.ENGINE).index, 0, ClickType.PICKUP, f.player);
        h.assertTrue(((RocketModuleItem) f.menu.getCarried().getItem()).rocketData(f.menu.getCarried()).color() == COLOR, "Extracted legacy tint changed");
        RocketData data = RocketRefit.data(f.menu.refitRocket());
        h.assertTrue(data.color() == COLOR && data.paint().body().isEmpty() && data.paint().engine().isEmpty() && data.tints().engine().orElseThrow() == 0xFFFFFFFF, "Legacy tint leaked onto the new engine");
        h.succeed();
    }

    private Fixture fixture(GameTestHelper h, boolean creative) {
        h.setBlock(POS, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity bench = h.getBlockEntity(POS);
        ServerPlayer player = creative ? h.makeMockServerPlayerInLevel() : makeSurvivalServerPlayer(h);
        player.setPos(h.absolutePos(POS).getCenter());
        var menu = new RocketWorkbenchMenu(1, bench, player.getInventory(), Constant.id("rocket/modular"));
        player.containerMenu = menu;
        return new Fixture(bench, player, menu);
    }
    private static ItemStack rocket(RocketData data) {
        ItemStack rocket = GCItems.ROCKET.getDefaultInstance();
        if (data.upgrade().isPresent() && data.upgradeData().isEmpty())
            data = new RocketData(data.cone(), data.body(), data.fin(), data.booster(), data.engine(), data.upgrade(),
                    java.util.Optional.of(new dev.galacticraft.mod.content.rocket.part.data.StorageRocketData(3)), data.color(), data.paint());
        rocket.set(GCDataComponents.ROCKET_DATA, data);
        rocket.set(DataComponents.CUSTOM_NAME, Component.literal("Refit test"));
        return rocket;
    }
    private static RocketModuleItem module(RocketPartTypes type, int tier) {
        return GCItems.ROCKET_MODULES.stream().filter(module -> module.type() == type && module.tier() == tier).findFirst().orElseThrow();
    }
    private static Slot partSlot(RocketWorkbenchMenu menu, RocketPartTypes type) {
        int index = dev.galacticraft.mod.recipe.ModularRocketRecipe.SLOTS.stream().map(slot -> slot.partType()).toList().indexOf(type);
        return menu.slots.stream().filter(slot -> slot.container == menu.workbench.ingredients && slot.getContainerSlot() == index).findFirst().orElseThrow();
    }
    private static Slot inventorySlot(RocketWorkbenchMenu menu, int index) {
        return menu.slots.stream().filter(slot -> slot.container == menu.playerInventory && slot.getContainerSlot() == index).findFirst().orElseThrow();
    }
    private record Fixture(RocketWorkbenchBlockEntity bench, ServerPlayer player, RocketWorkbenchMenu menu) { }
}