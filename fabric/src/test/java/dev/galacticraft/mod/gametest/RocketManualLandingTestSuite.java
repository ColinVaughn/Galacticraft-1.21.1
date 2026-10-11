package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.network.c2s.ControlEntityPayload;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class RocketManualLandingTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void controlledDescentLandsAndCanTakeOffAgain(GameTestHelper context) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
        rocket.setData(RocketPrefabs.TIER_1);
        rocket.setFuel(FluidUtil.bucketsToDroplets(20));
        rocket.getVehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        var pos = context.absolutePos(new BlockPos(2, 8, 2));
        rocket.absMoveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        context.getLevel().addFreshEntity(rocket);
        var pilot = context.makeMockServerPlayerInLevel();
        pilot.startRiding(rocket, true);
        GCServerPlayer.get(pilot).setLaunchpadStack(new ItemStack(GCBlocks.ROCKET_LAUNCH_PAD, 9));
        long before = rocket.getFuel();
        rocket.toggleLanding(pilot);
        context.assertTrue(rocket.isLanding(), "pilot could not activate landing");
        context.runAtTickTime(80, () -> {
            context.assertTrue(rocket.isAlive(), "controlled touchdown destroyed the rocket");
            context.assertTrue(rocket.getLaunchStage() == LaunchStage.IDLE, "rocket did not return to idle after touchdown");
            context.assertTrue(!rocket.isLanding(), "landing mode stayed active on the ground");
            context.assertTrue(rocket.getFuel() > 0 && rocket.getFuel() < before, "landing must consume fuel without discarding the reserve");
            context.assertTrue(pilot.getVehicle() == rocket, "landing ejected the pilot");
            ItemStack cargo = rocket.getVehicleInventory().getItem(0);
            context.assertTrue(cargo.is(Items.DIAMOND) && cargo.getCount() == 7, "landing changed rocket cargo");
            context.assertTrue(pilot.getInventory().countItem(GCBlocks.ROCKET_LAUNCH_PAD.asItem()) == 9,
                    "landing did not return the consumed launch pad");
            context.assertTrue(GCServerPlayer.get(pilot).getLaunchpadStack().isEmpty(), "landing left a duplicate launch pad reserve");
            double y = rocket.getY();
            rocket.inputTick(0, 0, false, false, false, false, true, false, false);
            context.assertTrue(rocket.getLaunchStage() == LaunchStage.WARNING, "landed rocket did not accept launch confirmation");
            rocket.inputTick(0, 0, false, false, false, false, false, false, false);
            rocket.inputTick(0, 0, false, false, false, false, true, false, false);
            context.assertTrue(rocket.getLaunchStage() == LaunchStage.IGNITED, "landed rocket could not ignite again");
            context.runAtTickTime(80 + rocket.getPreLaunchWait() + 25, () -> {
                context.assertTrue(rocket.isAlive() && rocket.getLaunchStage() == LaunchStage.LAUNCHED,
                        "relaunch failed after the ignition countdown");
                context.assertTrue(rocket.getY() > y + .1, "grounded rocket could not lift off again");
                context.assertTrue(pilot.getInventory().countItem(GCBlocks.ROCKET_LAUNCH_PAD.asItem()) == 9,
                        "relaunch duplicated the returned launch pad");
                context.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void landingStateSurvivesReload(GameTestHelper context) {
        var rocket = new SavedRocket(context.getLevel());
        rocket.setData(RocketPrefabs.TIER_1);
        rocket.setFuel(FluidUtil.bucketsToDroplets(20));
        var pilot = context.makeMockServerPlayerInLevel();
        pilot.startRiding(rocket, true);
        rocket.toggleLanding(pilot);
        context.assertTrue(!rocket.isLanding(), "grounded rocket accepted landing mode");
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        rocket.toggleLanding(context.makeMockServerPlayerInLevel());
        context.assertTrue(!rocket.isLanding(), "a non-pilot changed landing mode");
        rocket.toggleLanding(pilot);
        context.assertTrue(rocket.isLanding(), "pilot could not activate landing");
        var tag = new CompoundTag();
        rocket.write(tag);
        var copy = new SavedRocket(context.getLevel());
        copy.read(tag);
        context.assertTrue(copy.isLanding(), "landing state was lost on reload");
        context.assertTrue(copy.getFuel() == rocket.getFuel(), "reloading landing state changed fuel");
        rocket.toggleLanding(pilot);
        context.assertTrue(!rocket.isLanding(), "pilot could not resume ascent");
        rocket.setFuel(0);
        rocket.toggleLanding(pilot);
        context.assertTrue(!rocket.isLanding(), "empty tank accepted powered landing");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 130)
    public void pilotCanCutThrustAndBrakeWithoutDismounting(GameTestHelper context) {
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
        rocket.setData(RocketPrefabs.TIER_1);
        rocket.setFuel(FluidUtil.bucketsToDroplets(20));
        var pos = context.absolutePos(new BlockPos(2, 80, 2));
        rocket.absMoveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        context.getLevel().addFreshEntity(rocket);
        var pilot = context.makeMockServerPlayerInLevel();
        pilot.startRiding(rocket, true);
        rocket.toggleLanding(pilot);
        context.onEachTick(() -> {
            pilot.setShiftKeyDown(context.getTick() <= 30 || context.getTick() > 85);
            if (context.getTick() <= 30)
                rocket.inputTick(0, 0, false, false, false, false, false, true, false);
            else if (context.getTick() > 85)
                rocket.inputTick(0, 0, false, false, false, false, true, true, false);
            // Between phases, missing input must restore automatic braking after ten ticks.
        });
        context.runAtTickTime(25, () -> {
            context.assertTrue(rocket.getThrust() == 0, "holding sneak did not cut thrust");
            context.assertTrue(rocket.getDeltaMovement().y < -.5, "cutting thrust did not speed descent");
            context.assertTrue(pilot.getVehicle() == rocket, "cutting thrust dismounted the pilot");
        });
        context.runAtTickTime(85, () -> {
            context.assertTrue(rocket.isAlive() && rocket.isLanding(), "thrust controls interrupted flight");
            context.assertTrue(rocket.getThrust() > 0, "stale thrust-cut input did not restore braking");
            context.assertTrue(Math.abs(rocket.getDeltaMovement().y + .18) < .001, "braking did not restore controlled descent");
        });
        context.runAtTickTime(110, () -> {
            context.assertTrue(rocket.getThrust() > 0, "hover did not override thrust cut");
            context.assertTrue(Math.abs(rocket.getDeltaMovement().y) < .001, "hover did not stop descent");
            context.assertTrue(pilot.getVehicle() == rocket, "thrust controls ejected the pilot");
            context.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void landingSteeringMovesWithWasdAndStopsWhenReleased(GameTestHelper context) {
        float[] strafe = {0, 1, 0, -1, 0};
        float[] forward = {1, 0, -1, 0, 1};
        String[] keys = {"W", "A", "S", "D", "W at 90 degree yaw"};
        Vec3[] directions = {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 0, -1),
                new Vec3(-1, 0, 0), new Vec3(-1, 0, 0)};
        var rockets = new RocketEntity[keys.length * 2];
        var starts = new Vec3[rockets.length];
        for (int i = 0; i < rockets.length; i++) {
            var rocket = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
            rocket.setData(RocketPrefabs.TIER_1);
            rocket.setFuel(FluidUtil.bucketsToDroplets(20));
            var pos = context.absolutePos(new BlockPos(2, 40 + i * 8, 2));
            rocket.absMoveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            rocket.setYRot(i % keys.length == 4 ? 90 : 0);
            rocket.setLaunchStage(LaunchStage.LAUNCHED);
            context.getLevel().addFreshEntity(rocket);
            var pilot = context.makeMockServerPlayerInLevel();
            pilot.startRiding(rocket, true);
            rocket.toggleLanding(pilot);
            rockets[i] = rocket;
            starts[i] = rocket.position();
            int direction = i % keys.length;
            boolean zeroImpulses = i >= keys.length;
            context.onEachTick(() -> {
                // Mounted movement impulses can be zero even while the WASD key states are held.
                if (context.getTick() <= 30) new ControlEntityPayload(zeroImpulses ? 0 : strafe[direction], zeroImpulses ? 0 : forward[direction],
                        forward[direction] > 0, forward[direction] < 0, strafe[direction] > 0, strafe[direction] < 0,
                        true, false, false).apply(pilot);
                else if (zeroImpulses) new ControlEntityPayload(1, 1, false, false, false, false,
                        true, false, false).apply(pilot);
            });
        }
        context.runAtTickTime(30, () -> {
            for (int i = 0; i < rockets.length; i++) {
                int direction = i % keys.length;
                Vec3 displacement = rockets[i].position().subtract(starts[i]);
                String input = keys[direction] + (i >= keys.length ? " with zero movement impulses" : "");
                context.assertTrue(displacement.dot(directions[direction]) > 2,
                        input + " did not move the rocket: displacement=" + displacement + ", ticks=" + rockets[i].tickCount);
                context.assertTrue(displacement.cross(directions[direction]).length() < .01, input + " moved in the wrong direction");
            }
        });
        context.runAtTickTime(40, () -> {
            for (int i = keys.length; i < rockets.length; i++)
                context.assertTrue(rockets[i].getDeltaMovement().horizontalDistanceSqr() < .000001,
                        "released WASD keys did not stop steering while movement impulses remained nonzero");
        });
        context.runAtTickTime(60, () -> {
            for (var rocket : rockets) {
                context.assertTrue(rocket.isAlive() && rocket.isLanding(), "steering interrupted landing");
                context.assertTrue(rocket.getDeltaMovement().horizontalDistanceSqr() < .000001,
                        "rocket kept steering after input stopped");
            }
            context.succeed();
        });
    }

    private static class SavedRocket extends RocketEntity {
        SavedRocket(ServerLevel level) { super(GCEntityTypes.ROCKET, level); }
        void write(CompoundTag tag) { addAdditionalSaveData(tag); }
        void read(CompoundTag tag) { readAdditionalSaveData(tag); }
    }
}
