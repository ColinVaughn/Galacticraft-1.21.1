package dev.galacticraft.mod.client.model;

import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.gui.screen.ingame.GeothermalGeneratorScreen;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.GeothermalGeneratorBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.GeothermalGeneratorMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in client check of the original GC4 textures, animated GUI and synchronized generator control. */
public final class GeothermalClientTest implements ClientModInitializer {
    private static final BlockPos ACTIVE = new BlockPos(0, 65, 0), INVALID = new BlockPos(2, 65, 0);
    private boolean opened, placed, cameraSet;
    private int ticks;
    private volatile ArmorStand camera;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.geothermal.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                opened = true;
                client.options.renderDistance().set(4);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.createWorldOpenFlows().createFreshLevel("geothermal-" + System.currentTimeMillis(),
                        new LevelSettings("Geothermal Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
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
            if (camera == null || client.level.getEntity(camera.getId()) == null || client.level.getBlockEntity(ACTIVE) == null) return;
            if (!cameraSet) {
                checkModels(client);
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = true;
                client.mouseHandler.releaseMouse();
                cameraSet = true;
            }
            ticks++;
            if (ticks == 40) {
                require(client.level.getBlockState(ACTIVE).getValue(MachineBlock.ACTIVE), "valid generator never activated");
                require(!client.level.getBlockState(INVALID).getValue(MachineBlock.ACTIVE), "invalid generator activated");
                capture(client, "geothermal-models.png");
            }
            if (ticks == 50) {
                client.options.hideGui = false;
                client.setCameraEntity(client.player);
                open(client, ACTIVE);
            }
            if (ticks == 70) {
                require(client.screen instanceof GeothermalGeneratorScreen, "generator GUI did not open");
                var menu = (GeothermalGeneratorMenu) client.player.containerMenu;
                require(menu.validSpout && menu.generation >= 30 && !menu.disabled, "generator readings did not synchronize");
                capture(client, "geothermal-gui.png");
                client.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            if (ticks == 95) {
                var menu = (GeothermalGeneratorMenu) client.player.containerMenu;
                require(menu.disabled && menu.generation == 0 && menu.cooldown == 0, "disable state or cooldown failed to synchronize");
                capture(client, "geothermal-disabled-gui.png");
                client.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            if (ticks == 120) {
                var menu = (GeothermalGeneratorMenu) client.player.containerMenu;
                require(!menu.disabled && menu.generation >= 30, "enable button did not resume generation");
            }
            if (ticks == 140) open(client, INVALID);
            if (ticks == 160) {
                require(client.screen instanceof GeothermalGeneratorScreen, "invalid-spout GUI did not open");
                var menu = (GeothermalGeneratorMenu) client.player.containerMenu;
                require(!menu.validSpout && menu.generation == 0, "invalid spout was not synchronized");
                capture(client, "geothermal-invalid-gui.png");
                LoggerFactory.getLogger("GeothermalClientTest").info("GEOTHERMAL CLIENT CHECK PASSED: active/inactive models, facing, inventory model, GUI readings, disable/enable and invalid-spout status");
            }
            if (ticks == 170) client.stop();
        });
    }

    private void place(Minecraft client) {
        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        var level = player.serverLevel();
        player.teleportTo(1, 65, 3);
        level.setDayTime(6000);
        for (BlockPos pos : BlockPos.betweenClosed(-2, 64, -2, 6, 64, 3)) level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(ACTIVE, GCBlocks.GEOTHERMAL_GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(ACTIVE.below(), GCBlocks.VAPOR_SPOUT.defaultBlockState());
        level.setBlockAndUpdate(ACTIVE.below(5), GCBlocks.SULFURIC_ACID.defaultBlockState());
        level.setBlockAndUpdate(INVALID, GCBlocks.GEOTHERMAL_GENERATOR.defaultBlockState());
        level.setBlockAndUpdate(INVALID.below(), GCBlocks.VAPOR_SPOUT.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(4, 65, 0), GCBlocks.COAL_GENERATOR.defaultBlockState());
        var machine = (GeothermalGeneratorBlockEntity) level.getBlockEntity(ACTIVE);
        machine.itemStorage().slot(0).set(GCItems.BATTERY, 1);
        machine.energyStorage().setEnergy(machine.energyStorage().getCapacity());
        player.getInventory().setItem(0, GCBlocks.GEOTHERMAL_GENERATOR.asItem().getDefaultInstance());
        camera = new ArmorStand(level, 3.5, 67, -5);
        camera.moveTo(3.5, 67, -5, 20, 22);
        camera.setYHeadRot(20);
        camera.setNoGravity(true);
        camera.setInvisible(true);
        level.addFreshEntity(camera);
    }

    private static void checkModels(Minecraft client) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean active : new boolean[]{false, true}) {
                var state = GCBlocks.GEOTHERMAL_GENERATOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(MachineBlock.ACTIVE, active);
                var model = client.getBlockRenderer().getBlockModel(state);
                var front = model.getQuads(state, facing, RandomSource.create(0));
                require(front.size() == 1 && front.getFirst().getSprite().contents().name()
                        .equals(Constant.id("block/gc4/" + (active ? "geothermal" : "geothermal_inactive"))), "GC4 front texture or facing incorrect");
                var output = model.getQuads(state, facing.getClockWise(), RandomSource.create(0));
                require(output.size() == 1 && output.getFirst().getSprite().contents().name()
                        .equals(Constant.id("block/gc4/machine_output")), "GC4 output face is incorrectly rotated");
            }
        }
        var item = client.getItemRenderer().getModel(GCBlocks.GEOTHERMAL_GENERATOR.asItem().getDefaultInstance(), client.level, client.player, 0);
        require(item.getParticleIcon().contents().name().equals(Constant.id("block/gc4/machine")), "inventory model is missing");
    }

    private static void open(Minecraft client, BlockPos pos) {
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            var state = level.getBlockState(pos);
            ((dev.galacticraft.machinelib.api.block.BaseBlock) state.getBlock()).useWithoutItem(state, level, pos, player,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
        });
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("GeothermalClientTest").info("{}", message.getString()));
    }
}
