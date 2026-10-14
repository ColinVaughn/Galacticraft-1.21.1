package dev.galacticraft.mod.client.model;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.gui.screen.ingame.GasProcessingScreen;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.block.entity.machine.GasLiquefierBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.MethaneSynthesizerBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.GasProcessingMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in real-client check of the original GC4 models, GUIs and menu packets. */
public final class GasProcessingClientTest implements ClientModInitializer {
    private static final BlockPos LIQUEFIER = new BlockPos(0, 65, 0), SYNTHESIZER = new BlockPos(2, 65, 0);
    private boolean opened, placed, cameraSet;
    private int ticks;
    private volatile ArmorStand camera;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.gas-machines.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                opened = true;
                client.options.renderDistance().set(4);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.createWorldOpenFlows().createFreshLevel("gas-machines-" + System.currentTimeMillis(),
                        new LevelSettings("Gas Machine Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
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
            if (camera == null || client.level.getEntity(camera.getId()) == null || client.level.getBlockEntity(LIQUEFIER) == null) return;
            if (!cameraSet) {
                checkModel(client, GCBlocks.GAS_LIQUEFIER, "gas_liquefier");
                checkModel(client, GCBlocks.METHANE_SYNTHESIZER, "methane_synthesizer");
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = true;
                client.mouseHandler.releaseMouse();
                cameraSet = true;
            }
            ticks++;
            if (ticks == 30) capture(client, "gas-machines-comparison.png");
            if (ticks == 34) client.getCameraEntity().moveTo(3.5, 66, -4, 28, 22);
            if (ticks == 38) capture(client, "gc4-gas-machines.png");
            if (ticks == 40) {
                client.options.hideGui = false;
                client.setCameraEntity(client.player);
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    openMachine(player, LIQUEFIER);
                });
            }
            if (ticks == 60) {
                require(client.screen instanceof GasProcessingScreen<?>, "Liquefier screen did not open");
                capture(client, "gas-liquefier-gui.png");
                client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId, 0);
            }
            if (ticks == 75) {
                require(client.player.containerMenu instanceof GasProcessingMenu<?> menu && menu.disabled, "Stop button failed to synchronize");
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    openMachine(player, SYNTHESIZER);
                });
            }
            if (ticks == 95) {
                require(client.screen instanceof GasProcessingScreen<?>, "Synthesizer screen did not open");
                capture(client, "methane-synthesizer-gui.png");
                LoggerFactory.getLogger("GasProcessingClientTest").info("GAS MACHINE CLIENT CHECK PASSED: GC4 models, facing, items, textures, GUIs, synchronized stop button");
            }
            if (ticks == 105) client.stop();
        });
    }

    private void place(Minecraft client) {
        var server = client.getSingleplayerServer();
        var player = server.getPlayerList().getPlayers().getFirst();
        var level = player.serverLevel();
        player.teleportTo(3, 65, 4);
        level.setDayTime(6000);
        for (BlockPos pos : BlockPos.betweenClosed(-2, 64, -2, 10, 64, 3)) level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(LIQUEFIER, GCBlocks.GAS_LIQUEFIER.defaultBlockState());
        level.setBlockAndUpdate(SYNTHESIZER, GCBlocks.METHANE_SYNTHESIZER.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(4, 65, 0), GCBlocks.REFINERY.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(6, 65, 0), GCBlocks.OXYGEN_COMPRESSOR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(8, 65, 0), GCBlocks.ELECTRIC_COMPRESSOR.defaultBlockState());
        var liquefier = (GasLiquefierBlockEntity) level.getBlockEntity(LIQUEFIER);
        liquefier.energyStorage().setEnergy(liquefier.energyStorage().getCapacity());
        liquefier.fluidStorage().slot(0).set(Gases.METHANE, 100000);
        liquefier.fluidStorage().slot(1).set(GCFluids.LIQUID_OXYGEN, 50000);
        var synthesizer = (MethaneSynthesizerBlockEntity) level.getBlockEntity(SYNTHESIZER);
        synthesizer.energyStorage().setEnergy(synthesizer.energyStorage().getCapacity());
        synthesizer.fluidStorage().slot(0).set(Gases.HYDROGEN, 100000);
        synthesizer.itemStorage().slot(3).set(GCItems.CARBON_FRAGMENTS, 32);
        synthesizer.itemStorage().slot(0).set(GCItems.INFINITE_BATTERY, 1);
        liquefier.itemStorage().slot(0).set(GCItems.INFINITE_BATTERY, 1);
        player.getInventory().setItem(0, GCBlocks.GAS_LIQUEFIER.asItem().getDefaultInstance());
        player.getInventory().setItem(1, GCBlocks.METHANE_SYNTHESIZER.asItem().getDefaultInstance());
        camera = new ArmorStand(level, 7, 68, -8);
        camera.moveTo(7, 68, -8, 18, 20);
        camera.setYHeadRot(18);
        camera.setNoGravity(true);
        camera.setInvisible(true);
        level.addFreshEntity(camera);
    }

    private static void openMachine(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var state = level.getBlockState(pos);
        ((dev.galacticraft.machinelib.api.block.BaseBlock) state.getBlock()).useWithoutItem(state, level, pos, player,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }

    private static void checkModel(Minecraft client, net.minecraft.world.level.block.Block block, String name) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            require(state.getRenderShape() == RenderShape.MODEL, name + " does not use its block model");
            var model = client.getBlockRenderer().getBlockModel(state);
            var front = model.getQuads(state, facing, RandomSource.create(0));
            require(front.size() == 1 && front.getFirst().getSprite().contents().name().equals(Constant.id("block/gc4/" + name)),
                    name + " front texture missing or facing incorrectly: " + facing);
            for (Direction side : Direction.values()) {
                require(model.getQuads(state, side, RandomSource.create(0)).size() == 1, name + " cube face missing: " + side);
            }
        }
        var itemModel = client.getItemRenderer().getModel(block.asItem().getDefaultInstance(), client.level, client.player, 0);
        require(itemModel.getParticleIcon().contents().name().equals(Constant.id("block/gc4/advanced/machine")),
                name + " inventory model missing");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("GasProcessingClientTest").info("{}", message.getString()));
    }
}
