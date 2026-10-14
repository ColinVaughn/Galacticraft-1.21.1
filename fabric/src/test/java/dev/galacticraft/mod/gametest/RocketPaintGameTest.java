package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.screen.RocketPaintMenu;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class RocketPaintGameTest implements GalacticraftGameTest {
    private static final List<RocketPartTypes> PARTS = List.of(RocketPartTypes.CONE, RocketPartTypes.BODY, RocketPartTypes.FIN, RocketPartTypes.ENGINE);
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final RocketPaint PAINT = RocketPaint.NONE.withColor(RocketPartTypes.BODY, 0xFF426BA8).withColor(RocketPartTypes.FIN, 0xFFFFFFFF);

    @GameTest(template = EMPTY_STRUCTURE)
    public void paintedModulesRetainIdentityPaintStoragePreviewAndSave(GameTestHelper helper) {
        helper.setBlock(POS, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity workbench = helper.getBlockEntity(POS);
        var player = makeSurvivalServerPlayer(helper);
        ((dev.galacticraft.api.accessor.ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                dev.galacticraft.mod.content.GCRocketParts.recipeId(dev.galacticraft.mod.content.GCRocketParts.TIER_3_CONE),
                dev.galacticraft.mod.content.GCRocketParts.recipeId(dev.galacticraft.mod.content.GCRocketParts.TIER_3_BODY),
                dev.galacticraft.mod.content.GCRocketParts.recipeId(dev.galacticraft.mod.content.GCRocketParts.TIER_3_FIN),
                dev.galacticraft.mod.content.GCRocketParts.recipeId(dev.galacticraft.mod.content.GCRocketParts.TIER_3_ENGINE));
        var menu = new RocketWorkbenchMenu(1, workbench, player.getInventory(), Constant.id("rocket/modular"));
        for (int slot = 0; slot < PARTS.size(); slot++) {
            RocketModuleItem module = module(PARTS.get(slot));
            ItemStack input = module.getDefaultInstance();
            // Forged identities must still be ignored while genuine paint is retained.
            input.set(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1.withPaint(PAINT));
            workbench.ingredients.setItem(slot, input);
        }
        workbench.chests.setItem(0, new ItemStack(Items.CHEST));
        workbench.ingredients.setChanged();
        RocketData data = workbench.output.getItem(0).get(GCDataComponents.ROCKET_DATA);
        helper.assertTrue(data != null && data.paint().equals(PAINT), "Assembly lost painted module colors");
        helper.assertTrue(data.body().equals(module(RocketPartTypes.BODY).rocketData().body()), "Saved component forged the body identity");
        helper.assertTrue(data.paint().equals(menu.previewRocket().paint()), "Preview paint differs from output");
        helper.assertTrue(data.upgrade().isPresent(), "Paint removed the storage upgrade");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, workbench.output.getItem(0));
            assertData(helper, data, ItemStack.STREAM_CODEC.decode(buffer).get(GCDataComponents.ROCKET_DATA));
        } finally { buffer.release(); }
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, helper.getLevel());
        rocket.setData(data);
        RocketEntity loaded = new RocketEntity(GCEntityTypes.ROCKET, helper.getLevel());
        loaded.load(rocket.saveWithoutId(new CompoundTag()));
        assertData(helper, data, loaded.getRocketData());
        assertData(helper, data, loaded.getDropItem().get(GCDataComponents.ROCKET_DATA));
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var old = RocketData.CODEC.encodeStart(ops, data).getOrThrow();
        ((CompoundTag) old).remove("paint");
        helper.assertTrue(RocketData.CODEC.parse(ops, old).getOrThrow().paint().equals(RocketPaint.NONE), "Old saves require paint");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void paintMenuValidatesRevisionsPartsOwnershipAndReset(GameTestHelper helper) {
        helper.setBlock(POS, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity workbench = helper.getBlockEntity(POS);
        var player = makeSurvivalServerPlayer(helper);
        player.setPos(helper.absolutePos(POS).getCenter());
        var menu = new RocketPaintMenu(5, workbench, player.getInventory(), Constant.id("rocket/modular"));
        player.containerMenu = menu;
        ItemStack rocket = GCItems.ROCKET.getDefaultInstance();
        rocket.set(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1_STORAGE_UPGRADE);
        rocket.set(DataComponents.CUSTOM_NAME, Component.literal("Paint test"));
        workbench.paintInput.setItem(0, rocket.copy());
        int revision = menu.revision();
        helper.assertTrue(menu.apply(player, revision, PAINT), "Valid paint was rejected");
        helper.assertTrue(workbench.paintInput.getItem(0).get(DataComponents.CUSTOM_NAME).equals(rocket.get(DataComponents.CUSTOM_NAME)), "Painting stripped item components");
        helper.assertTrue(RocketPaintMenu.data(menu.input()).upgrade().equals(RocketPrefabs.TIER_1_STORAGE_UPGRADE.upgrade()), "Painting stripped the upgrade");
        helper.assertTrue(!menu.apply(player, revision, RocketPaint.NONE), "Stale paint overwrote a newer edit");
        helper.assertTrue(!menu.apply(player, menu.revision(), PAINT.withColor(RocketPartTypes.BOOSTER, 0xFF000000)), "Missing booster was painted");
        var other = makeSurvivalServerPlayer(helper);
        other.setPos(player.position());
        var second = new RocketPaintMenu(6, workbench, other.getInventory(), Constant.id("rocket/modular"));
        other.containerMenu = second;
        int otherRevision = second.revision();
        helper.assertTrue(!menu.apply(other, menu.revision(), RocketPaint.NONE), "Wrong player menu edited input");
        helper.assertTrue(menu.apply(player, menu.revision(), RocketPaint.NONE), "Reset was rejected");
        helper.assertTrue(!second.apply(other, otherRevision, PAINT), "Concurrent stale edit overwrote reset");
        int beforeReplacement = menu.revision();
        workbench.paintInput.setItem(0, module(RocketPartTypes.CONE).getDefaultInstance());
        helper.assertTrue(!menu.apply(player, beforeReplacement, PAINT), "Replaced input received old paint");
        helper.assertTrue(!menu.apply(player, menu.revision(), PAINT), "Cone module accepted body paint");
        player.setPos(player.position().add(20, 0, 0));
        helper.assertTrue(!menu.apply(player, menu.revision(), RocketPaint.NONE.withColor(RocketPartTypes.CONE, 0xFF426BA8)), "Out-of-range edit succeeded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void paintInputSurvivesSaveFullInventoryAndWorkbenchBreak(GameTestHelper helper) {
        helper.setBlock(POS, GCBlocks.ROCKET_WORKBENCH);
        RocketWorkbenchBlockEntity workbench = helper.getBlockEntity(POS);
        var player = makeSurvivalServerPlayer(helper);
        player.setPos(helper.absolutePos(POS).getCenter());
        var menu = new RocketPaintMenu(2, workbench, player.getInventory(), Constant.id("rocket/modular"));
        player.containerMenu = menu;
        ItemStack rocket = GCItems.ROCKET.getDefaultInstance();
        rocket.set(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1.withPaint(PAINT));
        workbench.paintInput.setItem(0, rocket.copy());
        var saved = workbench.saveWithoutMetadata(helper.getLevel().registryAccess());
        var loaded = new RocketWorkbenchBlockEntity(workbench.getBlockPos(), workbench.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.paintInput.getItem(0).is(GCItems.ROCKET) && loaded.paintInput.getItem(0).getCount() == 1, "Saved paint input was lost");
        assertData(helper, rocket.get(GCDataComponents.ROCKET_DATA), loaded.paintInput.getItem(0).get(GCDataComponents.ROCKET_DATA));
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.PAPER, 64));
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && ItemStack.matches(menu.input(), rocket), "Full inventory lost the input");
        player.getInventory().setItem(9, ItemStack.EMPTY);
        helper.assertTrue(!menu.quickMoveStack(player, 0).isEmpty() && menu.input().isEmpty(), "Painted input could not be retrieved");
        helper.assertTrue(!menu.quickMoveStack(player, 1).isEmpty() && ItemStack.matches(menu.input(), rocket), "Shift-click could not return the input");
        helper.setBlock(POS, Blocks.AIR);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(POS)).inflate(2));
        helper.assertTrue(drops.stream().filter(drop -> ItemStack.matches(drop.getItem(), rocket)).mapToInt(drop -> drop.getItem().getCount()).sum() == 1,
                "Breaking the workbench lost or duplicated the painted input");
        helper.succeed();
    }

    private static void assertData(GameTestHelper helper, RocketData expected, RocketData actual) {
        helper.assertTrue(actual != null && actual.paint().equals(expected.paint()), "Paint did not round-trip");
        helper.assertTrue(actual.color() == expected.color() && actual.upgradeData().equals(expected.upgradeData()), "Legacy color or upgrade changed");
        helper.assertTrue(actual.cone().orElseThrow().key().equals(expected.cone().orElseThrow().key())
                && actual.body().orElseThrow().key().equals(expected.body().orElseThrow().key())
                && actual.fin().orElseThrow().key().equals(expected.fin().orElseThrow().key())
                && actual.engine().orElseThrow().key().equals(expected.engine().orElseThrow().key()), "Part identity did not round-trip");
        helper.assertTrue(actual.upgrade().map(net.minecraft.world.item.EitherHolder::key).equals(expected.upgrade().map(net.minecraft.world.item.EitherHolder::key)), "Upgrade identity changed");
    }

    private static RocketModuleItem module(RocketPartTypes type) {
        return GCItems.ROCKET_MODULES.stream().filter(module -> module.type() == type && module.tier() == 3).findFirst().orElseThrow();
    }
}