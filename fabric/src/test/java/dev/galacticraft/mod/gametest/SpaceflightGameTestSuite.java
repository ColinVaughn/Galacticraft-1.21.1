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

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.entity.vehicle.AutomaticLanderEntity;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.network.c2s.ControlEntityPayload;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.GCDimensionTypes;
import dev.galacticraft.dynamicdimensions.impl.accessor.DynamicDimensionProvider;
import dev.galacticraft.dynamicdimensions.impl.mixin.MinecraftServerAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class SpaceflightGameTestSuite implements GalacticraftGameTest {
    private static final String BATCH = "galacticraft:spaceflight";
    private static boolean previousMode;

    @BeforeBatch(batch = BATCH)
    public void enableSpaceflight(net.minecraft.server.level.ServerLevel level) {
        var config = (ConfigImpl) Galacticraft.CONFIG;
        previousMode = config.seamlessSpaceTravel();
        config.setSeamlessSpaceTravel(true);
        // GameTest's flat preset boots only vanilla levels; load the route fixtures explicitly.
        var registries = level.registryAccess();
        for (var key : java.util.List.of(GCDimensionTypes.SOLAR_SYSTEM, GCDimensionTypes.MOON, GCDimensionTypes.MARS, GCDimensionTypes.VENUS, GCDimensionTypes.ASTEROID)) {
            if (level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, key.location())) != null) continue;
            var type = registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(key);
            var settings = new FlatLevelGeneratorSettings(java.util.Optional.empty(),
                    registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(dev.galacticraft.mod.world.biome.GCBiomes.SPACE), java.util.List.of());
            var server = level.getServer();
            var accessor = (MinecraftServerAccessor) server;
            // These IDs already have data-driven types, so only the missing live levels need registration.
            var fixture = new net.minecraft.server.level.ServerLevel(server, accessor.getExecutor(), accessor.getStorageSource(),
                    new net.minecraft.world.level.storage.DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()),
                    net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, key.location()),
                    new net.minecraft.world.level.dimension.LevelStem(type, new FlatLevelSource(settings)),
                    accessor.getProgressListenerFactory().create(0), false, 0, java.util.List.of(), false, null);
            ((DynamicDimensionProvider) server).dynamicdimensions$registerLevel(fixture);
            if (key.equals(GCDimensionTypes.SOLAR_SYSTEM)) {
                var spawn = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(
                        SpaceTravel.DESTINATIONS.getFirst().departurePosition()));
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) fixture.getChunk(spawn.x + x, spawn.z + z);
            }
        }
    }

    @AfterBatch(batch = BATCH)
    public void restoreSpaceflight(net.minecraft.server.level.ServerLevel level) {
        ((ConfigImpl) Galacticraft.CONFIG).setSeamlessSpaceTravel(previousMode);
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void earthToMoonPacksTheRocketCargoPaintPadAndFuelIntoTheLander(GameTestHelper test) {
        RocketEntity original = launch(test);
        test.assertTrue(original.getVehicleInventory().getContainerSize() >= 54, "fixture lacks a full cargo hold");
        original.getVehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 17));
        original.getVehicleInventory().setItem(53, new ItemStack(Items.EMERALD, 64));
        var data = original.getRocketData().withPaint(dev.galacticraft.api.rocket.RocketPaint.NONE.withColor(
                dev.galacticraft.api.rocket.part.RocketPartTypes.BODY, 0xFF167BB0));
        original.setData(data);
        var expectedRocket = new ItemStack(GCItems.ROCKET);
        expectedRocket.applyComponents(data.asPatch());
        ServerPlayer pilot = (ServerPlayer) original.getFirstPassenger();
        GCServerPlayer.get(pilot).setLaunchpadStack(new ItemStack(dev.galacticraft.mod.content.GCBlocks.ROCKET_LAUNCH_PAD, 9));
        long fuel = original.getFuel();
        boolean[] inSpace = {false};
        test.succeedWhen(() -> {
            if (!inSpace[0]) {
                test.assertTrue(pilot.getVehicle() instanceof RocketEntity, "pilot lost the rocket");
                RocketEntity rocket = (RocketEntity) pilot.getVehicle();
                test.assertTrue(rocket.isInSpaceflight(), "still waiting for launch into space");
                test.assertTrue(original.isRemoved(), "source rocket was duplicated");
                Constant.LOGGER.info("Cargo flight entered space with fuel {}", rocket.getFuel());
                test.assertTrue(!pilot.galacticraft$isCelestialScreenActive(), "physical launch opened the planet map");
                test.assertTrue(rocket.getUUID().equals(original.getUUID()), "rocket identity changed");
                var moon = SpaceTravel.DESTINATIONS.get(1);
                rocket.setPos(moon.center().add(0, 0, moon.radius() + 8));
                rocket.move(net.minecraft.world.entity.MoverType.SELF, Vec3.ZERO);
                rocket.setDeltaMovement(Vec3.ZERO);
                inSpace[0] = true;
            }
            test.assertTrue(pilot.getVehicle() instanceof LanderEntity, "waiting for Moon lander");
            LanderEntity lander = (LanderEntity) pilot.getVehicle();
            require(lander.getType() == GCEntityTypes.LANDER && pilot.level().dimension().equals(GCDimensions.MOON), "wrong arrival vehicle");
            require(lander.getItem(lander.getContainerSize() - 2).save(lander.registryAccess()).equals(expectedRocket.save(lander.registryAccess())), "rocket paint or parts were lost");
            require(lander.getItem(0).is(Items.DIAMOND) && lander.getItem(0).getCount() == 17
                    && lander.getItem(53).is(Items.EMERALD) && lander.getItem(53).getCount() == 64, "cargo was lost or duplicated");
            require(lander.getItem(lander.getContainerSize() - 3).getCount() == 9, "launch pad was lost");
            require(lander.getFuel() > 0 && lander.getFuel() <= fuel, "fuel was lost or created");
            require(GCServerPlayer.get(pilot).getFuel() == 0
                    && GCServerPlayer.get(pilot).getRocketStacks().stream().allMatch(ItemStack::isEmpty), "player attachment duplicated cargo");
            var saved = new CompoundTag();
            lander.saveWithoutId(saved);
            var restored = new LanderEntity(GCEntityTypes.LANDER, lander.level());
            restored.load(saved);
            require(restored.getFuel() == lander.getFuel() && restored.getItem(restored.getContainerSize() - 2).save(lander.registryAccess()).equals(expectedRocket.save(lander.registryAccess())), "reload lost rocket or fuel");
            require(restored.shouldMove(), "reload restarted the old 40 tick landing delay");
            var menu = lander.createMenu(1, pilot.getInventory(), pilot);
            var recovered = menu.quickMoveStack(pilot, 36 + lander.getContainerSize() - 2);
            require(recovered.is(GCItems.ROCKET) && lander.getItem(lander.getContainerSize() - 2).isEmpty(), "shift-click did not recover the rocket");
            pilot.stopRiding();
            lander.discard();
        });
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void tierOneCannotEnterMarsAndEmptyFuelDoesNotApplySurfaceGravity(GameTestHelper test) {
        RocketEntity original = launch(test);
        ServerPlayer pilot = (ServerPlayer) original.getFirstPassenger();
        test.succeedWhen(() -> {
            test.assertTrue(pilot.getVehicle() instanceof RocketEntity rocket && rocket.isInSpaceflight(), "waiting for space");
            RocketEntity rocket = (RocketEntity) pilot.getVehicle();
            var mars = SpaceTravel.DESTINATIONS.get(2);
            rocket.setPos(mars.center().add(0, 0, mars.radius() + 8));
            rocket.tick();
            test.assertTrue(rocket.isInSpaceflight() && mars.distance(rocket.position(), SpaceTravel.days(rocket.level())) > 16, "tier one entered Mars");
            rocket.setFuel(0);
            rocket.setDeltaMovement(new Vec3(0, 0, 1));
            var cruise = new ControlEntityPayload(0, 0, false, false, false, false, true, false, false);
            cruise.apply(pilot);
            rocket.tick();
            test.assertTrue(rocket.getLaunchStage() == LaunchStage.LAUNCHED, "empty space tank caused atmospheric failure");
            test.assertTrue(Math.abs(rocket.getDeltaMovement().y) < 1e-9 && rocket.getDeltaMovement().z == 1,
                    "empty tank did not coast without gravity");
            var brake = new ControlEntityPayload(0, 0, false, false, false, false, false, true, false);
            for (int i = 0; i < 20; i++) { brake.apply(pilot); rocket.tick(); }
            test.assertTrue(rocket.getDeltaMovement().length() < 1e-9, "emergency braking needs fuel");
            pilot.stopRiding();
            rocket.discard();
        });
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void crewArrivesTogetherAndOnlyPilotInputCanSteer(GameTestHelper test) {
        RocketEntity original = launch(test, false);
        var data = original.getRocketData();
        original.setData(new dev.galacticraft.api.rocket.RocketData(data.cone(),
                java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(dev.galacticraft.test.addon.ExampleAddon.TIER_4_BODY)),
                data.fin(), data.booster(), data.engine(), data.upgrade(), data.upgradeData(), data.color(), data.paint(), data.tints()));
        ServerPlayer pilot = (ServerPlayer) original.getFirstPassenger();
        var crew = test.makeMockServerPlayerInLevel();
        crew.setInvulnerable(true);
        crew.setPos(original.position().add(0, 3, 0));
        test.assertTrue(crew.startRiding(original, true), "multi-seat addon body did not accept crew");
        boolean[] approached = {false};
        int[] waiting = {0};
        test.succeedWhen(() -> {
            if (!approached[0]) {
            test.assertTrue(pilot.getVehicle() instanceof RocketEntity, "pilot lost vehicle: approached=" + approached[0]
                    + ", original removed=" + original.isRemoved() + ", stage=" + original.getLaunchStage() + ", fuel=" + original.getFuel()
                    + ", player level=" + pilot.level().dimension().location());
            var rocket = (RocketEntity) pilot.getVehicle();
            test.assertTrue(crew.getVehicle() == rocket && rocket.getFirstPassenger() == pilot,
                    "crew or pilot order was lost during dimension transfer");
                test.assertTrue(rocket.isInSpaceflight(), "waiting for shared space");
                Constant.LOGGER.info("Crew flight entered space with {} passengers", rocket.getPassengers().size());
                var spaceToken = rocket.pendingSpaceflightHandoff();
                rocket.acknowledgeSpaceflight(pilot, spaceToken);
                rocket.acknowledgeSpaceflight(crew, spaceToken);
                float yaw = rocket.getYRot();
                var turn = new ControlEntityPayload(0, 0, false, false, false, true, false, false, true);
                turn.apply(crew);
                test.assertTrue(rocket.getYRot() == yaw, "crew input steered the rocket");
                turn.apply(pilot);
                test.assertTrue(rocket.getYRot() < yaw, "first-person pilot input did not turn the nose right");
                var moon = SpaceTravel.DESTINATIONS.get(1);
                rocket.setPos(moon.center().add(0, 0, moon.radius() + 8));
                rocket.move(net.minecraft.world.entity.MoverType.SELF, Vec3.ZERO);
                rocket.setDeltaMovement(Vec3.ZERO);
                approached[0] = true;
            }
            if (pilot.level().dimension().equals(GCDimensions.MOON) && pilot.getVehicle() instanceof RocketEntity held) {
                var token = held.pendingSpaceflightHandoff();
                held.acknowledgeSpaceflight(pilot, token);
                held.acknowledgeSpaceflight(crew, java.util.UUID.randomUUID());
                test.assertTrue(crew.getVehicle() == held && held.getDeltaMovement().lengthSqr() == 0,
                        "first ready rider released the other rider before terrain was ready");
                if (++waiting[0] == 20) held.acknowledgeSpaceflight(crew, token);
            }
            test.assertTrue(pilot.getVehicle() instanceof LanderEntity && crew.getVehicle() instanceof LanderEntity, "waiting for crew landers");
            var pilotLander = (LanderEntity) pilot.getVehicle();
            var crewLander = (LanderEntity) crew.getVehicle();
            test.assertTrue(crew.level() == pilot.level() && pilot.level().dimension().equals(GCDimensions.MOON), "crew missed atmospheric entry");
            test.assertTrue(pilotLander != crewLander && pilotLander.getFirstPassenger() == pilot && crewLander.getFirstPassenger() == crew, "crew lost individual seats");
            test.assertTrue(crewLander.getFuel() == 0 && crewLander.getContainerSize() == 3, "crew vehicle duplicated rocket or cargo");
            pilot.stopRiding();
            crew.stopRiding();
            pilotLander.discard();
            crewLander.discard();
        });
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 2000)
    public void flightWaitsSafelyForTheMatchingClientReadiness(GameTestHelper test) {
        var original = launch(test, false);
        ServerPlayer pilot = (ServerPlayer) original.getFirstPassenger();
        int[] ticks = {0};
        Vec3[] heldPosition = {null};
        long[] heldFuel = {0};
        java.util.UUID[] token = {null};
        test.onEachTick(() -> {
            if (!(pilot.getVehicle() instanceof RocketEntity ship) || !ship.isInSpaceflight()) return;
            if (token[0] == null) {
                token[0] = ship.pendingSpaceflightHandoff();
                test.assertTrue(token[0] != null, "transfer did not enter protected staging");
                heldPosition[0] = ship.position();
                heldFuel[0] = ship.getFuel();
                ship.acknowledgeSpaceflight(pilot, java.util.UUID.randomUUID());
                ship.acknowledgeSpaceflight(pilot, token[0], false);
                new dev.galacticraft.mod.network.c2s.SpaceflightReadyPayload(ship.getUUID(), token[0], true, Float.NaN, 90).apply(pilot);
                new dev.galacticraft.mod.network.c2s.SpaceflightReadyPayload(java.util.UUID.randomUUID(), token[0], true, 25, 90).apply(pilot);
            }
            ticks[0]++;
            new ControlEntityPayload(0, 0, false, false, false, false, true, false, false).apply(pilot);
            if (ticks[0] <= 25) {
                test.assertTrue(ship.position().distanceTo(heldPosition[0]) < .001, "craft moved before its client was ready");
                test.assertTrue(ship.getFuel() == heldFuel[0], "waiting for rendering burned fuel");
                if (ticks[0] == 25) {
                    new dev.galacticraft.mod.network.c2s.SpaceflightReadyPayload(ship.getUUID(), token[0], true, 25, 90).apply(pilot);
                    test.assertTrue(ship.getYRot() == 25, "pilot heading was lost during the handoff");
                }
            } else if (ship.position().distanceTo(heldPosition[0]) > .05) {
                test.assertTrue(ship.pendingSpaceflightHandoff() == null, "ready handoff did not release the craft");
                pilot.stopRiding();
                ship.discard();
                test.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void marsDeploysBalloons(GameTestHelper test) { destinationRoute(test, 2, GCEntityTypes.LANDING_BALLOONS); }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void venusDeploysParachutePod(GameTestHelper test) { destinationRoute(test, 3, GCEntityTypes.VENUS_ENTRY_POD); }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void asteroidsDeployEntryPod(GameTestHelper test) { destinationRoute(test, 5, GCEntityTypes.ENTRY_POD); }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void earthDeploysEquippedParachuteAndOneCargoChest(GameTestHelper test) { destinationRoute(test, 0, null); }

    private void destinationRoute(GameTestHelper test, int destinationIndex, net.minecraft.world.entity.EntityType<?> expected) {
        var original = launch(test);
        var base = RocketPrefabs.TIER_3;
        var storage = original.getRocketData();
        original.setData(new dev.galacticraft.api.rocket.RocketData(base.cone(), base.body(), base.fin(), base.booster(),
                base.engine(), storage.upgrade(), storage.upgradeData(), base.color()));
        original.setFuel(original.getFuelTankCapacity());
        original.getVehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        var player = (ServerPlayer) original.getFirstPassenger();
        if (expected == null) player.galacticraft$getGearInv().setItem(0, new ItemStack(GCItems.PARACHUTE));
        boolean[] approached = {false};
        test.succeedWhen(() -> {
            if (!approached[0]) {
                test.assertTrue(player.getVehicle() instanceof RocketEntity ship && ship.isInSpaceflight(), "waiting for space");
                var ship = (RocketEntity) player.getVehicle();
                var destination = SpaceTravel.DESTINATIONS.get(destinationIndex);
                ship.setPos(destination.center().add(0, 0, destination.radius() + 8));
                ship.move(net.minecraft.world.entity.MoverType.SELF, Vec3.ZERO);
                ship.setDeltaMovement(Vec3.ZERO);
                approached[0] = true;
            }
            if (expected == null) {
                test.assertTrue(player.level() == test.getLevel() && !player.isPassenger(), "waiting for Earth parachute");
                require(player.getY() >= 240, "Earth entry lost GC4's Y=250 arrival height");
                require(player.galacticraft$isParachuteOpen(), "equipped parachute did not open");
                var chests = player.level().getEntitiesOfClass(dev.galacticraft.mod.content.entity.ParachestEntity.class, player.getBoundingBox().inflate(12));
                require(chests.size() == 1, "arrival did not create exactly one parachest");
                require(chests.getFirst().cargo.getFirst().getCount() == 7 && chests.getFirst().fuelLevel > 0, "parachest lost cargo or fuel");
                chests.getFirst().discard();
            } else {
                test.assertTrue(player.getVehicle() != null && player.getVehicle().getType() == expected, "waiting for destination vehicle");
                var lander = (AutomaticLanderEntity) player.getVehicle();
                require(!lander.hasManualControls() && lander.getFuel() > 0 && lander.getItem(0).getCount() == 7, "automatic arrival lost cargo or fuel");
                double expectedY = destinationIndex == 5 ? 310 : 900;
                require(lander.getY() >= expectedY - 40, "arrival deployed near the surface instead of GC4 entry height");
                double y = lander.getY();
                lander.tick();
                require(lander.getY() < y, "arrival resumed the old startup freeze");
                player.stopRiding();
                lander.discard();
            }
            require(GCServerPlayer.get(player).getFuel() == 0, "arrival duplicated fuel into player attachment");
        });
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void automaticDescentUsesGc4AltitudeStages(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        var balloons = GCEntityTypes.LANDING_BALLOONS.create(level);
        balloons.setPos(pos.getX(), 900, pos.getZ());
        balloons.startDescent();
        balloons.tickInAir();
        test.assertTrue(balloons.hasParachute() && balloons.getDeltaMovement().y == -18,
                "Mars must descend from Y=900 at GC4's height-dependent speed");
        balloons.setPos(pos.getX(), 499, pos.getZ());
        balloons.tickInAir();
        test.assertTrue(!balloons.hasParachute(), "Mars canopy must release below Y=500");
        var venus = GCEntityTypes.VENUS_ENTRY_POD.create(level);
        venus.setPos(pos.getX(), 900, pos.getZ());
        venus.startDescent();
        test.assertTrue(venus.getDeltaMovement().y == -2.5, "Venus lost its high-entry initial velocity");
        venus.tickInAir();
        test.assertTrue(venus.hasEntryFlames() && !venus.hasParachute(), "Venus high entry needs flames");
        venus.setPos(pos.getX(), 242, pos.getZ());
        venus.tickInAir();
        test.assertTrue(!venus.hasEntryFlames() && venus.hasParachute(), "Venus canopy must deploy at Y=242");
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        venus.setPos(pos.getX(), ground + 4, pos.getZ());
        venus.tickInAir();
        test.assertTrue(!venus.hasParachute() && !venus.hasEntryFlames(), "Venus canopy must release for touchdown");
        test.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 500)
    public void balloonsBounceAndSettleWithCargoIntact(GameTestHelper test) {
        var level = test.getLevel();
        var ground = test.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
            level.setBlockAndUpdate(ground.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var balloons = GCEntityTypes.LANDING_BALLOONS.create(level);
        balloons.setPos(Vec3.atBottomCenterOf(ground.above(12)));
        var cargo = net.minecraft.core.NonNullList.withSize(1, new ItemStack(Items.DIAMOND, 3));
        balloons.loadCargo(cargo, 1000);
        balloons.startDescent();
        level.addFreshEntity(balloons);
        test.succeedWhen(() -> {
            test.assertTrue(balloons.onGround() && balloons.bounces() == 14, "waiting for fourteen damped bounces");
            test.assertTrue(!balloons.isRemoved() && balloons.getItem(0).getCount() == 3 && balloons.getFuel() == 1000, "bouncing destroyed the cargo");
            test.assertTrue(!balloons.shouldIgnoreShiftExit(), "settled balloons trapped their passenger");
            var saved = new CompoundTag();
            balloons.saveWithoutId(saved);
            var restored = GCEntityTypes.LANDING_BALLOONS.create(level);
            restored.load(saved);
            test.assertTrue(restored.bounces() == 14, "reload restarted the bounce sequence");
            balloons.discard();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private RocketEntity launch(GameTestHelper test) {
        return launch(test, true);
    }

    private RocketEntity launch(GameTestHelper test, boolean acknowledge) {
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        var base = RocketPrefabs.TIER_1_STORAGE_UPGRADE;
        rocket.setData(new dev.galacticraft.api.rocket.RocketData(base.cone(), base.body(), base.fin(), base.booster(),
                base.engine(), base.upgrade(), java.util.Optional.of(new dev.galacticraft.mod.content.rocket.part.data.StorageRocketData(3)), base.color()));
        rocket.setFuel(rocket.getFuelTankCapacity());
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        var pos = test.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        rocket.setPos(pos.getX() + .5, Constant.ESCAPE_HEIGHT + 1, pos.getZ() + .5);
        test.getLevel().addFreshEntity(rocket);
        var pilot = test.makeMockServerPlayerInLevel();
        pilot.setInvulnerable(true);
        pilot.setPos(rocket.position().add(0, 2, 0));
        pilot.startRiding(rocket, true);
        // Mock clients do not send movement packets to update their destination chunk tickets.
        test.onEachTick(() -> {
            if (pilot.getVehicle() instanceof RocketEntity ship) {
                if (ship.isInSpaceflight()) SpaceTravel.prepareArrival((net.minecraft.server.level.ServerLevel) ship.level(), ship.position(), ship.getId());
                var token = ship.pendingSpaceflightHandoff();
                if ((acknowledge || ship.isPreparingSpaceflight()) && token != null) for (var passenger : ship.getPassengers())
                    if (passenger instanceof ServerPlayer player) ship.acknowledgeSpaceflight(player, token);
            }
        });
        return rocket;
    }
}