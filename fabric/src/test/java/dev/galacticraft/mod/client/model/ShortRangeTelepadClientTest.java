package dev.galacticraft.mod.client.model;

import dev.galacticraft.mod.client.gui.screen.ingame.ShortRangeTelepadScreen;
import dev.galacticraft.mod.client.render.block.entity.ShortRangeTelepadRenderer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import dev.galacticraft.mod.content.block.special.telepad.ShortRangeTelepadBlock;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in integrated-server check of GC4 geometry, GUI editing, control packets and player teleport synchronization. */
public final class ShortRangeTelepadClientTest implements ClientModInitializer {
    private static final BlockPos SOURCE = new BlockPos(0, 65, 0), TARGET = new BlockPos(6, 65, 0);
    private boolean opened, placed, cameraSet, arrived;
    private int ticks;
    private volatile ArmorStand camera;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.telepad.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                opened = true;
                client.options.renderDistance().set(4);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.options.tutorialStep = TutorialSteps.NONE;
                client.createWorldOpenFlows().createFreshLevel("telepad-" + System.currentTimeMillis(),
                        new LevelSettings("Telepad Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(23, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!placed) {
                if (client.screen != null) return;
                placed = true;
                client.getSingleplayerServer().execute(() -> place(client));
                return;
            }
            if (camera == null || client.level.getEntity(camera.getId()) == null || client.level.getBlockEntity(SOURCE) == null) return;
            if (!cameraSet) {
                require(GCModelLoader.INSTANCE.getModel(ShortRangeTelepadRenderer.MODEL) != GCModelLoader.MISSING_MODEL, "original OBJ model did not load");
                var item = client.getItemRenderer().getModel(GCBlocks.SHORT_RANGE_TELEPAD.asItem().getDefaultInstance(), client.level, client.player, 0);
                require(item.isCustomRenderer(), "inventory model does not use telepad renderer");
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = true;
                client.mouseHandler.releaseMouse();
                cameraSet = true;
            }
            ticks++;
            if (ticks == 40) capture(client, "telepad-models.png");
            if (ticks == 50) {
                client.options.hideGui = false;
                client.setCameraEntity(client.player);
                open(client);
            }
            if (ticks == 70) {
                require(client.screen instanceof ShortRangeTelepadScreen, "telepad GUI did not open");
                var menu = (ShortRangeTelepadMenu) client.player.containerMenu;
                require(menu.canEdit && !menu.addressValid, "owner or unset address did not synchronize");
                capture(client, "telepad-unset-gui.png");
                var fields = client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                require(fields.size() == 2, "address input widgets missing");
                fields.get(0).setValue("350000");
                fields.get(1).setValue("350001");
            }
            if (ticks == 110) {
                var menu = (ShortRangeTelepadMenu) client.player.containerMenu;
                require(menu.address == 350000 && menu.destination == 350001 && menu.addressValid && menu.targetResult == 0, "GUI address edits or target validation failed");
                capture(client, "telepad-paired-gui.png");
                client.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            if (ticks == 135) {
                var menu = (ShortRangeTelepadMenu) client.player.containerMenu;
                require(menu.disabled && menu.cooldown == 0, "disable control failed to synchronize");
                capture(client, "telepad-disabled-gui.png");
                client.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            if (ticks == 160) {
                require(!((ShortRangeTelepadMenu) client.player.containerMenu).disabled, "enable control failed");
                client.player.closeContainer();
                client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(0.5, 65.45, 0.5));
            }
            if (ticks == 200) {
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.hideGui = true;
            }
            if (ticks == 220) {
                var pad = (ShortRangeTelepadBlockEntity) client.level.getBlockEntity(SOURCE);
                require(pad.getTeleportTime() > 0, "charging progress never reached client renderer");
                capture(client, "telepad-charging.png");
            }
            if (ticks >= 300 && Math.abs(client.player.getX() - 6.5) < 0.1 && !arrived) {
                arrived = true;
                require(Math.abs(client.player.getY() - 65.45) < 0.01, "player arrived inside base collision");
                capture(client, "telepad-arrival.png");
                LoggerFactory.getLogger("ShortRangeTelepadClientTest").info("TELEPAD CLIENT CHECK PASSED: original model and inventory renderer, owner GUI address editing, disable/enable, synchronized charging and player teleport");
                client.stop();
            }
            if (ticks == 500) require(arrived, "player never arrived at destination");
        });
    }

    private void place(Minecraft client) {
        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        var level = player.serverLevel();
        player.teleportTo(3, 65, 3);
        level.setDayTime(6000);
        for (BlockPos pos : BlockPos.betweenClosed(-2, 64, -2, 8, 64, 3)) level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        for (BlockPos pos : new BlockPos[]{SOURCE, TARGET}) {
            level.setBlockAndUpdate(pos, GCBlocks.SHORT_RANGE_TELEPAD.defaultBlockState());
            ShortRangeTelepadBlock.createFrame(level, pos);
            var pad = (ShortRangeTelepadBlockEntity) level.getBlockEntity(pos);
            pad.getSecurity().tryUpdate(player.getUUID());
            pad.itemStorage().slot(0).set(GCItems.INFINITE_BATTERY, 1);
            pad.energyStorage().setEnergy(pad.energyStorage().getCapacity());
        }
        ((ShortRangeTelepadBlockEntity) level.getBlockEntity(TARGET)).configure(350001, 350000);
        player.getInventory().setItem(0, GCBlocks.SHORT_RANGE_TELEPAD.asItem().getDefaultInstance());
        camera = new ArmorStand(level, 4, 69, -7);
        camera.moveTo(4, 69, -7, 8, 23);
        camera.setYHeadRot(8);
        camera.setNoGravity(true);
        camera.setInvisible(true);
        level.addFreshEntity(camera);
    }

    private static void open(Minecraft client) {
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            var state = level.getBlockState(SOURCE);
            ((ShortRangeTelepadBlock) state.getBlock()).useWithoutItem(state, level, SOURCE, player,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(SOURCE), Direction.UP, SOURCE, false));
        });
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LoggerFactory.getLogger("ShortRangeTelepadClientTest").info("{}", message.getString()));
    }
}
