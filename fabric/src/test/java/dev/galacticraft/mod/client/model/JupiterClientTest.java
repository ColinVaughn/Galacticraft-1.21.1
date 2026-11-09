package dev.galacticraft.mod.client.model;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.client.render.dimension.JupiterCloudRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL13C.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in rendered check: ./gradlew :fabric:runClient -PjupiterClientTest -Pastrocraft=false */
public final class JupiterClientTest implements ClientModInitializer {
    private boolean opened, travelling, alarmCaptured;
    private net.minecraft.world.phys.Vec3 flightStart;
    private int ticks, waitTicks, returnTicks, collectorScreenTicks;
    private volatile BlockPos collectorPos;
    private volatile int bankX, bankZ;
    private volatile BlockPos editPoint;
    private dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity diver;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.jupiter.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                opened = true;
                client.options.renderDistance().set(8);
                client.options.pauseOnLostFocus = false;
                // The tutorial toast would cover the top-right of every capture.
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                client.createWorldOpenFlows().createFreshLevel("jupiter-" + System.currentTimeMillis(),
                        new LevelSettings("Jupiter Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(23, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!travelling) {
                if (client.screen != null) return;
                travelling = true;
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().getLevel(GCDimensions.JUPITER);
                    if (level == null) throw new AssertionError("Jupiter dimension did not load");
                    boolean foundBank = false;
                    outer: for (int x = -128; x <= 128; x += 32) {
                        for (int z = -128; z <= 128; z += 32) {
                            level.getChunk(x >> 4, z >> 4);
                            if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) >= 222) {
                                bankX = x;
                                bankZ = z;
                                foundBank = true;
                                break outer;
                            }
                        }
                    }
                    if (!foundBank) throw new AssertionError("No cloud bank found for the rendered check");
                    editPoint = new BlockPos(bankX, level.getHeight(Heightmap.Types.WORLD_SURFACE, bankX, bankZ) - 1, bankZ);
                    if (!JupiterCloudRenderer.isCloud(level.getBlockState(editPoint))) throw new AssertionError("Missing cloud edit fixture");
                    level.setDayTime(6000);
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, bankX + 25.0, 254, bankZ - 65.0, 20, 22);
                });
                return;
            }
            if (ticks >= 205 && client.level.dimension().equals(Level.OVERWORLD)) {
                if (++returnTicks < 10) return;
                if (returnTicks == 10) {
                    if (JupiterCloudRenderer.isActive() || GCBlocks.DENSE_CLOUD.defaultBlockState().getRenderShape() != RenderShape.MODEL) {
                        throw new AssertionError("Cloud fallback did not restore after leaving Jupiter");
                    }
                    LoggerFactory.getLogger("JupiterClientTest").info("JUPITER FALLBACK CHECK PASSED: volume disabled and normal cloud block rendering restored outside Jupiter");
                    client.getSingleplayerServer().execute(() -> {
                        var level = client.getSingleplayerServer().overworld();
                        level.setDayTime(6000);
                        diver = dev.galacticraft.mod.content.GCEntityTypes.CLOUD_DIVER.create(level);
                        if (diver == null) throw new AssertionError("Cloud Diver did not create");
                        diver.moveTo(0, 75, 0, 180, 0);
                        diver.acceptEnergy(96000);
                        diver.acceptOxygen(324000);
                        level.addFreshEntity(diver);
                        var viewer = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        viewer.teleportTo(level, 6, 79, -8, 37, 12);
                        hover(viewer);
                    });
                }
                if (returnTicks == 60) {
                    capture(client, "cloud-diver-front.png");
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                            .teleportTo(client.getSingleplayerServer().overworld(), 6, 79, 8, 143, 12));
                }
                if (returnTicks == 110) {
                    capture(client, "cloud-diver-rear.png");
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                            .teleportTo(client.getSingleplayerServer().overworld(), 3.5, 77, -6.5, 25, 10));
                }
                if (returnTicks == 185) capture(client, "cloud-diver-close.png");
                if (returnTicks == 186) clientDiver(client).handleEntityEvent(dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity.COLLECT_EVENT);
                if (returnTicks == 196) {
                    if (clientDiver(client).collectProgress(0) < 0) throw new AssertionError("Collector arm animation did not start");
                    capture(client, "cloud-diver-collect.png");
                }
                if (returnTicks == 200) {
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.startRiding(diver, true);
                        player.setYRot(180);
                        player.setXRot(0);
                    });
                }
                if (returnTicks >= 240 && returnTicks <= 250) {
                    // The server-side rotation is not sent to the client, so face the bow locally and hold it until the capture.
                    client.player.setYRot(180);
                    client.player.setXRot(0);
                    client.player.yRotO = 180;
                    client.player.xRotO = 0;
                    client.player.setYHeadRot(180);
                }
                if (returnTicks == 250) {
                    if (!(client.player.getVehicle() instanceof dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity)) {
                        throw new AssertionError("Cloud Diver passenger did not synchronize");
                    }
                    capture(client, "cloud-diver-cockpit.png");
                    LoggerFactory.getLogger("JupiterClientTest").info("CLOUD DIVER CLIENT CHECK PASSED: front/rear, close-up, cockpit and passenger synchronization");
                    flightStart = clientDiver(client).position();
                    client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                }
                if (returnTicks > 250 && returnTicks <= 300) {
                    // Fly with the real key bindings: full throttle while turning right.
                    client.options.keyUp.setDown(returnTicks < 300);
                    client.options.keyRight.setDown(returnTicks < 300);
                }
                if (returnTicks == 295) {
                    var shown = clientDiver(client);
                    float hullTurn = net.minecraft.util.Mth.wrapDegrees(shown.getYRot() - 180), riderTurn = net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - 180);
                    double travelled = shown.position().subtract(flightStart).horizontalDistance();
                    if (travelled < 5 || hullTurn < 40) throw new AssertionError("Cloud Diver did not fly: moved " + travelled + ", turned " + hullTurn);
                    if (Math.abs(hullTurn - riderTurn) > 3) throw new AssertionError("Pilot view did not turn with the hull: hull " + hullTurn + ", pilot " + riderTurn);
                    if (shown.bank(1) < 0.6F || shown.lean(1) > -0.15F) throw new AssertionError("Hull did not bank and lean: bank " + shown.bank(1) + ", lean " + shown.lean(1));
                    capture(client, "cloud-diver-banking.png");
                    LoggerFactory.getLogger("JupiterClientTest").info("CLOUD DIVER FLIGHT CHECK PASSED: moved {}, turned {}, pilot turned {}, bank {}, lean {}",
                            travelled, hullTurn, riderTurn, shown.bank(1), shown.lean(1));
                }
                if (returnTicks == 300) {
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.stopRiding();
                        diver.moveTo(0, 75, 0, 180, 0);
                        diver.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        player.teleportTo(client.getSingleplayerServer().overworld(), 3.5, 77, -6.5, 25, 10);
                        hover(player);
                        diver.hurt(client.getSingleplayerServer().overworld().damageSources().generic(), 215);
                    });
                }
                if (returnTicks >= 340 && !alarmCaptured) {
                    var shown = clientDiver(client);
                    // The framebuffer holds the frame drawn before this tick, so the strobe must be on for both ticks.
                    if (shown.alarm() && shown.tickCount / 3 % 2 == 0 && (shown.tickCount - 1) / 3 % 2 == 0) {
                        capture(client, "cloud-diver-alarm.png");
                        alarmCaptured = true;
                        LoggerFactory.getLogger("JupiterClientTest").info("CLOUD DIVER ANIMATION CHECK PASSED: collector arm event and low-hull alarm synchronized");
                    }
                }
                if (returnTicks == 370) {
                    if (!alarmCaptured) throw new AssertionError("Low-hull alarm did not synchronize");
                    client.getSingleplayerServer().execute(() -> {
                        var level = client.getSingleplayerServer().overworld();
                        // Showcase: the animated machines in front, the building blocks and clusters behind them.
                        for (int x = 18; x <= 40; x++) for (int z = 18; z <= 26; z++)
                            level.setBlockAndUpdate(new BlockPos(x, 74, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        var machines = dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.MACHINES;
                        for (int i = 0; i < machines.size(); i++) level.setBlockAndUpdate(new BlockPos(21 + i * 2, 75, 24),
                                machines.get(i).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                        var blocks = java.util.List.of(dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.AEROSTAT_HULL,
                                dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.REINFORCED_AEROSTAT_HULL, dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.AEROSTAT_GRATING,
                                dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.PRESSURE_GLASS, dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.SEALED_HATCH,
                                dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.HYDROSULFIDE_CRYSTAL_CLUSTER, dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.CHARGED_CONDENSATE_CLUSTER);
                        for (int i = 0; i < blocks.size(); i++) level.setBlockAndUpdate(new BlockPos(23 + i * 2, 75, 20), blocks.get(i).defaultBlockState());
                        var viewer = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        viewer.teleportTo(level, 29.5, 79, 33.5, 180, 22);
                        hover(viewer);
                    });
                }
                if (returnTicks == 430) capture(client, "jupiter-blocks.png");
                if (returnTicks == 440) capture(client, "jupiter-blocks-2.png");
                if (returnTicks == 445) {
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                            .teleportTo(client.getSingleplayerServer().overworld(), 25.5, 77.2, 28.5, 200, 18));
                }
                if (returnTicks == 470) {
                    capture(client, "jupiter-blocks-close.png");
                    client.getSingleplayerServer().execute(() -> {
                        var inventory = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getInventory();
                        var machines = dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.MACHINES;
                        for (int i = 0; i < machines.size(); i++) inventory.setItem(i, new net.minecraft.world.item.ItemStack(machines.get(i)));
                    });
                    client.options.hideGui = false;
                }
                if (returnTicks == 485) {
                    capture(client, "jupiter-machine-items.png");
                    LoggerFactory.getLogger("JupiterClientTest").info("JUPITER BLOCK SHOWCASE CAPTURED");
                }
                if (returnTicks == 486) client.getSingleplayerServer().execute(() -> stageMachines(client));
                // The dock reaches out to the parked diver: hose connected, hatches open, service pulses flowing.
                if (returnTicks == 490) client.getSingleplayerServer().execute(() -> {
                    var viewer = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    viewer.teleportTo(client.getSingleplayerServer().overworld(), 32, 77.2, 26.5, 252, 12);
                    hover(viewer);
                });
                if (returnTicks == 490) client.options.hideGui = true;
                if (returnTicks == 528) {
                    var shown = clientDiver(client);
                    if (shown.dock().isEmpty() || shown.docking(1) < 0.95F) throw new AssertionError("Diver never docked on the client: " + shown.dock() + " " + shown.docking(1));
                    capture(client, "jupiter-dock-connected.png");
                    LoggerFactory.getLogger("JupiterClientTest").info("VEHICLE DOCK CHECK PASSED: diver linked to {}", shown.dock().get());
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                            .teleportTo(client.getSingleplayerServer().overworld(), 35.5, 76.6, 28.5, 207, 13));
                }
                if (returnTicks == 538) capture(client, "jupiter-dock-connected-2.png");
                if (returnTicks == 540) client.options.hideGui = false;
                // Open each machine's screen in turn: open, let it sync, capture, close.
                if (returnTicks >= SCREENS_START && (returnTicks - SCREENS_START) % 20 == 0 && (returnTicks - SCREENS_START) / 20 < SCREENS.length) {
                    int index = SCREENS[(returnTicks - SCREENS_START) / 20];
                    client.getSingleplayerServer().execute(() -> {
                        var level = client.getSingleplayerServer().overworld();
                        var viewer = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        // Stand within reach, as a player would; menus close beyond eight blocks.
                        viewer.teleportTo(level, 21.5 + index * 2, 76, 27.5, 180, 30);
                        hover(viewer);
                        use(level, new BlockPos(21 + index * 2, 75, 24), viewer);
                    });
                }
                if (returnTicks >= SCREENS_START && (returnTicks - SCREENS_START) % 20 == 15 && (returnTicks - SCREENS_START) / 20 < SCREENS.length) {
                    int index = SCREENS[(returnTicks - SCREENS_START) / 20];
                    var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.MACHINES.get(index)).getPath();
                    if (client.screen == null) throw new AssertionError("No screen opened for " + id);
                    capture(client, "jupiter-screen-" + id + (index == 0 ? "-overworld" : "") + ".png");
                    client.player.closeContainer();
                }
                if (returnTicks == SCREENS_START + SCREENS.length * 20) {
                    LoggerFactory.getLogger("JupiterClientTest").info("JUPITER MACHINE SCREENS CAPTURED");
                    client.stop();
                }
                return;
            }
            if (collectorScreenTicks > 0) {
                if (++collectorScreenTicks == 25) {
                    capture(client, "jupiter-screen-atmospheric_collector.png");
                    client.player.closeContainer();
                    collectorScreenTicks = 0;
                }
                return;
            }
            if (!client.level.dimension().equals(GCDimensions.JUPITER) || client.screen != null) return;
            if (!JupiterCloudRenderer.isActive()) {
                if (++waitTicks > 600) throw new AssertionError("Volumetric cloud renderer never became ready");
                return;
            }
            client.options.hideGui = true;
            client.mouseHandler.releaseMouse();
            ticks++;
            if (ticks == 100) {
                for (var block : new net.minecraft.world.level.block.Block[]{GCBlocks.DENSE_CLOUD, GCBlocks.PACKED_CLOUD, GCBlocks.AMMONIA_FROST}) {
                    var model = client.getBlockRenderer().getBlockModel(block.defaultBlockState());
                    var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
                    if (!model.getParticleIcon().contents().name().equals(Constant.id("block/" + id + "_side"))) {
                        throw new AssertionError("Missing Jupiter block texture: " + id);
                    }
                    for (Direction face : Direction.values()) {
                        if (model.getQuads(block.defaultBlockState(), face, RandomSource.create(0)).size() != 1) {
                            throw new AssertionError("Missing or uncullable cloud face: " + id + "/" + face);
                        }
                    }
                }
                if (GCBlocks.DENSE_CLOUD.defaultBlockState().getRenderShape() != RenderShape.INVISIBLE) {
                    throw new AssertionError("Dense cloud cube mesh was not replaced by the volume");
                }
                capture(client, "jupiter-cloudlands.png");
                client.getSingleplayerServer().execute(() -> {
                    var jupiter = client.getSingleplayerServer().getLevel(GCDimensions.JUPITER);
                    // A powered collector above the cloud banks, open to the sky; checked and opened at tick 190.
                    collectorPos = new BlockPos(bankX + 25, 250, bankZ - 60);
                    jupiter.setBlockAndUpdate(collectorPos, dev.galacticraft.mod.content.block.jupiter.JupiterBlocks.ATMOSPHERIC_COLLECTOR.defaultBlockState());
                    var collector = (dev.galacticraft.mod.content.block.jupiter.machine.AtmosphericCollectorBlockEntity) jupiter.getBlockEntity(collectorPos);
                    collector.energyStorage().setEnergy(collector.energyStorage().getCapacity());
                    jupiter.setBlockAndUpdate(editPoint, Blocks.AIR.defaultBlockState());
                    client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                            .teleportTo(client.getSingleplayerServer().getLevel(GCDimensions.JUPITER), bankX + 70.0, 188, bankZ - 65.0, 47, -20);
                });
            }
            if (ticks == 150) {
                if (!client.level.getBlockState(editPoint).isAir() || cachedVoxel(editPoint) != 0) {
                    throw new AssertionError("Mined cloud did not update the GPU cache");
                }
                client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getLevel(GCDimensions.JUPITER)
                        .setBlockAndUpdate(editPoint, GCBlocks.DENSE_CLOUD.defaultBlockState()));
            }
            if (ticks == 160) {
                capture(client, "jupiter-cloud-underside.png");
                LoggerFactory.getLogger("JupiterClientTest").info("JUPITER CLIENT CHECK PASSED: dimension, terrain models, textures, face culling, upper and lower atmosphere");
            }
            if (ticks == 190) {
                client.getSingleplayerServer().execute(() -> {
                    var jupiter = client.getSingleplayerServer().getLevel(GCDimensions.JUPITER);
                    var collector = (dev.galacticraft.mod.content.block.jupiter.machine.AtmosphericCollectorBlockEntity) jupiter.getBlockEntity(collectorPos);
                    long hydrogen = collector.fluidStorage().slot(dev.galacticraft.mod.content.block.jupiter.machine.AtmosphericCollectorBlockEntity.HYDROGEN_TANK).getAmount();
                    long helium = collector.fluidStorage().slot(dev.galacticraft.mod.content.block.jupiter.machine.AtmosphericCollectorBlockEntity.HELIUM_TANK).getAmount();
                    if (hydrogen <= 0 || helium <= 0) throw new AssertionError("Collector drew no gas on Jupiter: " + collector.getState().getStatus());
                    LoggerFactory.getLogger("JupiterClientTest").info("JUPITER COLLECTOR CHECK PASSED: {} hydrogen, {} helium droplets in 90 ticks", hydrogen, helium);
                });
            }
            if (ticks == 200) {
                if (!client.level.getBlockState(editPoint).is(GCBlocks.DENSE_CLOUD) || cachedVoxel(editPoint) != 191) {
                    throw new AssertionError("Placed cloud did not update the GPU cache");
                }
                LoggerFactory.getLogger("JupiterClientTest").info("JUPITER CLOUD EDIT CHECK PASSED: mining and placement updated GPU voxel data");
                // Open the collector's screen from within reach; machine menus close beyond eight blocks.
                client.getSingleplayerServer().execute(() -> {
                    var jupiter = client.getSingleplayerServer().getLevel(GCDimensions.JUPITER);
                    var viewer = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    viewer.teleportTo(jupiter, collectorPos.getX() + 0.5, collectorPos.getY() + 1, collectorPos.getZ() + 3.5, 180, 30);
                    hover(viewer);
                    use(jupiter, collectorPos, viewer);
                });
                collectorScreenTicks = 1;
            }
            if (ticks == 205) {
                client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                        .teleportTo(client.getSingleplayerServer().overworld(), 0, 70, 0, 0, 0));
            }
        });
    }

    /** Right-clicks the block at {@code pos}, opening its screen the way a player would. */
    private static void use(net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer player) {
        level.getBlockState(pos).useWithoutItem(level, player, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** Showcase indices (into {@code JupiterBlocks.MACHINES}) of the machines with screens; the relay has none. */
    private static final int[] SCREENS = {0, 1, 2, 3, 4, 6, 7, 8};
    private static final int SCREENS_START = 560;

    /** Gives the showcase machines something to show: power, input, a docked diver, a beacon network and a running encounter. */
    private void stageMachines(Minecraft client) {
        var level = client.getSingleplayerServer().overworld();
        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        java.util.function.IntFunction<net.minecraft.world.level.block.entity.BlockEntity> at = i -> level.getBlockEntity(new BlockPos(21 + i * 2, 75, 24));
        for (int i = 0; i < 9; i++) {
            if (at.apply(i) instanceof dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity machine) {
                machine.energyStorage().setEnergy(machine.energyStorage().getCapacity() * 3 / 5);
            }
        }
        var separator = (dev.galacticraft.mod.content.block.jupiter.machine.CondensateSeparatorBlockEntity) at.apply(1);
        separator.itemStorage().slot(dev.galacticraft.mod.content.block.jupiter.machine.CondensateSeparatorBlockEntity.INPUT_SLOT)
                .set(dev.galacticraft.mod.content.item.jupiter.JupiterItems.CLOUD_CONDENSATE, 12);
        var grounder = (dev.galacticraft.mod.content.block.jupiter.machine.LightningGrounderBlockEntity) at.apply(2);
        for (int i = 0; i < 3; i++) grounder.ground();
        var beacon = (dev.galacticraft.mod.content.block.jupiter.machine.SurveyBeaconBlockEntity) at.apply(7);
        beacon.setBeaconName("Red Spot Camp");
        dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.get(level).update(new BlockPos(140, 75, -260), "Storm Harvester", true);
        dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.get(level).update(new BlockPos(-420, 90, 64), "Citadel Approach", true);
        var dock = (dev.galacticraft.mod.content.block.jupiter.machine.VehicleDockBlockEntity) at.apply(8);
        dock.fluidStorage().slot(dev.galacticraft.mod.content.block.jupiter.machine.VehicleDockBlockEntity.OXYGEN_TANK)
                .set(dev.galacticraft.api.gas.Gases.OXYGEN, dev.galacticraft.mod.util.FluidUtil.bucketsToDroplets(10));
        dock.itemStorage().slot(dev.galacticraft.mod.content.block.jupiter.machine.VehicleDockBlockEntity.SALVAGE_SLOT)
                .set(dev.galacticraft.mod.content.item.jupiter.JupiterItems.AEROSTAT_SALVAGE, 6);
        diver.moveTo(39, 75, 27, 180, 0);
        diver.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var pedestal = (dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity) at.apply(6);
        if (!pedestal.begin(player)) throw new AssertionError("Showcase encounter did not start");
    }

    private static int cachedVoxel(BlockPos pos) {
        java.nio.ByteBuffer pixels = MemoryUtil.memAlloc(4096 * 6144);
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            var field = JupiterCloudRenderer.class.getDeclaredField("texture");
            field.setAccessible(true);
            var texture = (AbstractTexture) field.get(null);
            RenderSystem.bindTexture(texture.getId());
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
            int x = (pos.getZ() & 15) * 256 + (pos.getX() & 255);
            int y = ((pos.getZ() & 255) >> 4) * 384 + pos.getY() + 64;
            return pixels.get(y * 4096 + x) & 255;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        } finally {
            RenderSystem.bindTexture(previousTexture);
            RenderSystem.activeTexture(previousUnit);
            MemoryUtil.memFree(pixels);
        }
    }

    private dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity clientDiver(Minecraft client) {
        if (!(client.level.getEntity(diver.getId()) instanceof dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity shown)) {
            throw new AssertionError("Cloud Diver is missing on the client");
        }
        return shown;
    }

    /** Creative flight, so the viewer stays at the camera height instead of falling to the flat ground before a capture. */
    private static void hover(net.minecraft.server.level.ServerPlayer player) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("JupiterClientTest").info("{}", message.getString()));
    }
}