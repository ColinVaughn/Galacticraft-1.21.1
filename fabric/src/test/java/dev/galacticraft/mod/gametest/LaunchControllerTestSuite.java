/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 * Licensed under the MIT license. See LICENSE in the project root.
 */
package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.block.entity.machine.LaunchControllerBlockEntity;
import dev.galacticraft.mod.content.block.special.launchpad.CargoPadRegistry;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.ContainerVehicle;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.network.c2s.LaunchControllerSettingsPayload;
import dev.galacticraft.mod.screen.LaunchControllerMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import static dev.galacticraft.mod.content.block.entity.machine.LaunchControllerBlockEntity.*;

public class LaunchControllerTestSuite implements GalacticraftGameTest {
    private static final String BATCH = "galacticraft:launch_controller";
    private static final java.util.List<LaunchControllerBlockEntity> CONTROLLERS = new java.util.ArrayList<>();
    private static int nextFrequency = 800_000;

    private static int frequency(ServerLevel level) {
        while (CargoPadRegistry.get(level.getServer()).contains(nextFrequency)) nextFrequency++;
        return nextFrequency++;
    }

    @AfterBatch(batch = BATCH)
    public void removeTestControllers(ServerLevel unused) {
        for (LaunchControllerBlockEntity controller : CONTROLLERS) {
            ServerLevel level = (ServerLevel) controller.getLevel();
            CargoPadRegistry.get(level.getServer()).unregister(CargoPadRegistry.PadTarget.of(level, controller.getBlockPos()));
            if (level.getBlockEntity(controller.getBlockPos()) == controller) level.removeBlock(controller.getBlockPos(), false);
        }
        CONTROLLERS.clear();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void unpoweredControllerOpensItsMenuWithoutAPad(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos pos = context.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, GCBlocks.LAUNCH_CONTROLLER.defaultBlockState());
        LaunchControllerBlockEntity controller = (LaunchControllerBlockEntity) level.getBlockEntity(pos);
        CONTROLLERS.add(controller);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.moveTo(pos.getCenter());
        context.assertTrue(controller.getConnectedPad() == null && controller.energyStorage().getAmount() == 0,
                "Controller should start without a pad or power");
        var result = level.getBlockState(pos).useWithoutItem(level, player,
                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
        context.assertTrue(result.consumesAction() && player.containerMenu instanceof LaunchControllerMenu,
                "Right-clicking an unpowered Launch Controller did not open its menu");
        context.assertTrue(controller.canEdit(player), "First interaction did not claim the controller");
        player.closeContainer();
        context.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void allLegacyLaunchConditions(GameTestHelper context) {
        SimpleContainer hold = new SimpleContainer(4);
        ContainerVehicle vehicle = new ContainerVehicle() {
            public Container getVehicleInventory() { return hold; }
            public int getCargoSlotCount() { return 2; }
        };
        hold.setItem(3, new ItemStack(Items.DIAMOND)); // reserved return slots are not cargo
        context.assertTrue(LaunchCondition.CARGO_UNLOADED.isMet(vehicle, 0, 100, 0, false), "Reserved items blocked unload detection");
        hold.setItem(0, new ItemStack(Items.STONE, 64));
        hold.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
        context.assertTrue(LaunchCondition.CARGO_FULL.isMet(vehicle, 0, 100, 0, false), "Full stacks with different limits were not full");
        hold.getItem(1).shrink(1);
        context.assertTrue(!LaunchCondition.CARGO_FULL.isMet(vehicle, 0, 100, 0, false), "Partial stack was full");
        context.assertTrue(!LaunchCondition.CARGO_UNLOADED.isMet(vehicle, 0, 100, 0, false), "Loaded cargo was empty");
        context.assertTrue(!LaunchCondition.FULLY_FUELED.isMet(vehicle, 99, 100, 0, false), "Partial fuel triggered launch");
        context.assertTrue(LaunchCondition.FULLY_FUELED.isMet(vehicle, 100, 100, 0, false), "Full fuel did not trigger launch");
        LaunchCondition[] timers = {LaunchCondition.INSTANT, LaunchCondition.TEN_SECONDS, LaunchCondition.THIRTY_SECONDS, LaunchCondition.ONE_MINUTE};
        int[] delays = {12, 200, 600, 1200};
        for (int i = 0; i < timers.length; i++) {
            context.assertTrue(!timers[i].isMet(vehicle, 100, 100, delays[i] - 1, false), "Timer launched early");
            context.assertTrue(timers[i].isMet(vehicle, 100, 100, delays[i], false), "Timer failed to launch");
        }
        context.assertTrue(!LaunchCondition.REDSTONE.isMet(vehicle, 100, 100, 0, false), "Unpowered redstone launched");
        context.assertTrue(LaunchCondition.REDSTONE.isMet(vehicle, 100, 100, 0, true), "Redstone did not launch");
        context.assertTrue(!validSettings(1, 1, 0, 15) && !validSettings(1_000_000, 2, 0, 15)
                && !validSettings(1, -2, 0, 15) && !validSettings(1, 2, 8, 15) && !validSettings(1, 2, 0, 16), "Invalid settings accepted");
        context.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void frequenciesPersistAndCannotBeStolen(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchControllerBlockEntity source = controller(level, context.absolutePos(new BlockPos(2, 1, 2)));
        LaunchControllerBlockEntity target = controller(level, context.absolutePos(new BlockPos(6, 1, 2)));
        int sourceFrequency = frequency(level);
        context.assertTrue(source.configure(sourceFrequency, 999_999, 7, 15), "Source settings rejected");
        context.assertTrue(target.configure(999_999, sourceFrequency, 0, ENABLED), "High frequency rejected");
        context.assertTrue(source.getConnectedPad().getDestinationAddress() == 999_999, "Pad ignored its controller");
        context.assertTrue(!target.configure(sourceFrequency, -1, 0, ENABLED), "Duplicate frequency was stolen");
        context.assertTrue(target.getFrequency() == 999_999, "Rejected update changed the old frequency");
        LaunchPadBlockEntity occupied = target.getConnectedPad();
        CompoundTag occupiedTag = occupied.saveWithFullMetadata(level.registryAccess());
        occupiedTag.putUUID(Constant.Nbt.DOCKED_UUID, java.util.UUID.randomUUID());
        occupied.loadWithComponents(occupiedTag, level.registryAccess());
        context.assertTrue(occupied.hasDockedEntity(), "Pad was available before its saved rocket finished loading");
        CargoRocketEntity sourceRocket = cargo(level, source.getConnectedPad());
        long fuel = sourceRocket.getFuelTankAmount();
        context.assertTrue(!sourceRocket.ignite() && sourceRocket.getFuelTankAmount() == fuel, "Occupied destination consumed launch fuel");
        occupied.setDockedEntity(null);

        CompoundTag saved = source.saveWithFullMetadata(level.registryAccess());
        source.loadWithComponents(saved, level.registryAccess());
        context.assertTrue(source.getFrequency() == sourceFrequency && source.getDestination() == 999_999
                && source.getCondition() == LaunchCondition.REDSTONE && source.getOptions() == 15, "Settings did not persist");
        CargoPadRegistry restored = CargoPadRegistry.load(CargoPadRegistry.get(level.getServer()).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        context.assertTrue(restored.resolve(level.getServer(), 999_999).orElseThrow().pad() == target.getConnectedPad(), "Address book did not persist");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        LaunchControllerSettingsPayload packet = new LaunchControllerSettingsPayload(3, sourceFrequency, 999_999, 7, 15);
        LaunchControllerSettingsPayload.STREAM_CODEC.encode(buf, packet);
        context.assertTrue(packet.equals(LaunchControllerSettingsPayload.STREAM_CODEC.decode(buf)), "Frequency truncated by network codec");
        buf.release();
        BlockPos targetPos = target.getBlockPos();
        level.removeBlock(targetPos, false);
        context.assertTrue(restored.resolve(level.getServer(), 999_999).isEmpty(), "Broken controller remained a destination");
        context.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void onlyTheNearbyOwnerCanChangeSettings(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchControllerBlockEntity controller = controller(level, context.absolutePos(new BlockPos(2, 1, 2)));
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        owner.moveTo(controller.getBlockPos().getCenter());
        controller.getSecurity().tryUpdate(owner.getUUID());
        LaunchControllerMenu menu = new LaunchControllerMenu(1, owner.getInventory(), controller);
        int frequency = frequency(level);
        menu.configure(owner, frequency, -1, 0, ENABLED);
        context.assertTrue(controller.getFrequency() == frequency, "Owner could not configure controller");
        ServerPlayer visitor = context.makeMockServerPlayerInLevel();
        visitor.moveTo(controller.getBlockPos().getCenter());
        menu.configure(visitor, frequency(level), -1, 0, ENABLED);
        context.assertTrue(controller.getFrequency() == frequency, "Visitor changed settings");
        owner.moveTo(controller.getBlockPos().getCenter().add(20, 0, 0));
        menu.configure(owner, frequency(level), -1, 0, ENABLED);
        context.assertTrue(controller.getFrequency() == frequency, "Out-of-range owner changed settings");
        context.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void scheduledCargoRequiresPowerAndAValidRoute(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchControllerBlockEntity source = controller(level, context.absolutePos(new BlockPos(2, 1, 2)));
        LaunchControllerBlockEntity target = controller(level, context.absolutePos(new BlockPos(6, 1, 2)));
        int frequency = frequency(level);
        int destination = frequency(level);
        source.configure(frequency, destination, LaunchCondition.INSTANT.ordinal(), ENABLED | SCHEDULING | KEEP_PAD);
        CargoRocketEntity rocket = cargo(level, source.getConnectedPad());
        for (int i = 0; i < 15; i++) tick(source);
        context.assertTrue(!rocket.inFlight(), "Rocket launched without a destination");
        target.configure(destination, frequency, 0, ENABLED);
        source.energyStorage().extract(source.energyStorage().getAmount());
        for (int i = 0; i < 15; i++) tick(source);
        context.assertTrue(!rocket.inFlight(), "Rocket launched without controller power");
        source.energyStorage().insert(100);
        for (int i = 0; i < 11; i++) tick(source);
        context.assertTrue(!rocket.inFlight(), "Instant launch skipped its 12-tick grace period");
        tick(source);
        context.assertTrue(rocket.inFlight(), "Powered configured controller did not launch cargo");
        context.assertTrue(!source.getConnectedPad().hasDockedEntity(), "Departed rocket remained on pad");
        context.succeed();
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE, timeoutTicks = 450)
    public void cargoDocksTheNewInstanceAcrossDimensions(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        LaunchControllerBlockEntity source = controller(level, context.absolutePos(new BlockPos(2, 1, 2)));
        BlockPos destinationPos = new BlockPos(source.getBlockPos().getX(), 100, source.getBlockPos().getZ() + 32);
        LaunchControllerBlockEntity target = controller(nether, destinationPos);
        int frequency = frequency(level);
        int destination = frequency(level);
        source.configure(frequency, destination, 0, ENABLED | KEEP_PAD);
        target.configure(destination, frequency, 0, ENABLED | KEEP_PAD);
        CargoRocketEntity rocket = cargo(level, source.getConnectedPad());
        for (int slot = 0; slot < 27; slot++) {
            rocket.getVehicleInventory().setItem(slot, new ItemStack(Items.DIAMOND, 64));
        }
        context.assertTrue(rocket.ignite(), "Cargo could not ignite");
        rocket.setPos(rocket.getX(), rocket.getY() + 301, rocket.getZ());
        rocket.tick();
        LaunchPadBlockEntity pad = target.getConnectedPad();
        context.assertTrue(pad.getDockedEntity() instanceof CargoRocketEntity, "Destination has no cargo rocket");
        CargoRocketEntity arrived = (CargoRocketEntity) pad.getDockedEntity();
        context.assertTrue(arrived != rocket && !arrived.isRemoved() && rocket.isRemoved()
                && arrived.level() == nether && arrived.getLandingPad() == pad
                && arrived.getFlightState() == CargoRocketEntity.FlightState.IDLE, "Dock points at a removed or flying rocket");
        context.assertTrue(arrived.getVehicleInventory().getItem(26).getCount() == 64
                && arrived.getCargoLoad() == 1 && arrived.getBbHeight() == 6,
                "Cross-dimension cargo or full fairing size was lost");
        long energyAtArrival = target.energyStorage().getAmount();
        // Also cross vanilla's 300-tick cutoff for a dimension with no players in it.
        context.runAfterDelay(350, () -> context.succeedWhen(() -> {
            context.assertTrue(energyAtArrival - target.energyStorage().getAmount() >= 325,
                    "Controller stopped ticking in an empty dimension");
            context.assertTrue(nether.getEntity(arrived.getUUID()) == arrived, "Arrived rocket is not available by UUID yet");
            CompoundTag savedPad = pad.saveWithFullMetadata(nether.registryAccess());
            pad.loadWithComponents(savedPad, nether.registryAccess());
            context.assertTrue(pad.getDockedEntity() == arrived, "Reload lost the arrived rocket");
            arrived.discard();
            nether.removeBlock(target.getBlockPos(), false);
            nether.destroyBlock(pad.getBlockPos(), false);
        }));
    }

    @GameTest(batch = BATCH, template = EMPTY_STRUCTURE)
    public void crewedRocketKeepsPadAndLandsAtItsFrequency(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchControllerBlockEntity source = controller(level, context.absolutePos(new BlockPos(2, 1, 2)));
        LaunchControllerBlockEntity target = controller(level, context.absolutePos(new BlockPos(6, 1, 2)));
        int frequency = frequency(level);
        int destination = frequency(level);
        source.configure(frequency, destination, 0, ENABLED | KEEP_PAD);
        target.configure(destination, frequency, 0, ENABLED | KEEP_PAD);
        LaunchPadBlockEntity pad = source.getConnectedPad();
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, level);
        rocket.setPad(pad);
        rocket.setPos(pad.getBlockPos().getCenter());
        rocket.setFuel(rocket.getFuelTankCapacity());
        level.addFreshEntity(rocket);
        pad.setDockedEntity(rocket);
        context.assertTrue(!rocket.igniteFromController(), "Empty crewed rocket launched");
        ServerPlayer passenger = context.makeMockServerPlayerInLevel();
        passenger.startRiding(rocket, true);
        context.assertTrue(rocket.igniteFromController(), "Mounted rocket could not use controller");
        rocket.setTimeAsState(rocket.getPreLaunchWait());
        rocket.tick();
        context.assertTrue(level.getBlockEntity(pad.getBlockPos()) == pad && !pad.hasDockedEntity(), "Kept pad was destroyed or remained occupied");
        context.assertTrue(GCServerPlayer.get(passenger).getLaunchpadStack().isEmpty(), "Kept pad was duplicated as a return item");
        rocket.setPos(rocket.getX(), Constant.ESCAPE_HEIGHT + 1, rocket.getZ());
        rocket.tick();
        context.assertTrue(target.getConnectedPad().getDockedEntity() == rocket
                && rocket.getLaunchStage() == LaunchStage.IDLE && passenger.getVehicle() == rocket, "Crewed rocket did not arrive mounted on destination pad");
        BlockPos removablePad = target.getConnectedPad().getBlockPos();
        target.configure(destination, frequency, 0, ENABLED);
        context.assertTrue(rocket.igniteFromController(), "Return route could not ignite");
        rocket.setTimeAsState(rocket.getPreLaunchWait());
        rocket.tick();
        context.assertTrue(level.getBlockEntity(removablePad) == null, "Pad removal setting was ignored");
        context.assertTrue(GCServerPlayer.get(passenger).getLaunchpadStack().getCount() == 9, "Removed pad was not returned exactly once");
        context.succeed();
    }

    private static LaunchControllerBlockEntity controller(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 7, 1))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(center.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD.defaultBlockState());
        }
        BlockPos pos = center.south(2);
        level.setBlockAndUpdate(pos, GCBlocks.LAUNCH_CONTROLLER.defaultBlockState());
        LaunchControllerBlockEntity controller = (LaunchControllerBlockEntity) level.getBlockEntity(pos);
        CONTROLLERS.add(controller);
        controller.energyStorage().insert(10_000);
        return controller;
    }

    private static CargoRocketEntity cargo(ServerLevel level, LaunchPadBlockEntity pad) {
        CargoRocketEntity rocket = new CargoRocketEntity(GCEntityTypes.CARGO_ROCKET, level);
        rocket.getFuelTank().insert(GCFluids.FUEL, rocket.getFuelTankCapacity());
        rocket.placeOnPad(pad);
        level.addFreshEntity(rocket);
        pad.setDockedEntity(rocket);
        return rocket;
    }

    private static void tick(LaunchControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.tickBase(level, controller.getBlockPos(), controller.getBlockState(), level.getProfiler());
    }
}
