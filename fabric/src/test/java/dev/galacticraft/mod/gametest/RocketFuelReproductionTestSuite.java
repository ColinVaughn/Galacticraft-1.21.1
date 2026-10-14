package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class RocketFuelReproductionTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void defaultFullTankReachesSpace(GameTestHelper test) {
        simulate(test, 100, 50, 100, true);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void threeBucketsInDefaultTankRunOutAndFall(GameTestHelper test) {
        simulate(test, 100, 50, 3, false);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fullThreeBucketTankStillRunsOutAtDefaultEfficiency(GameTestHelper test) {
        simulate(test, 3, 50, 3, false);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void threeBucketsReachSpaceWithHigherEfficiency(GameTestHelper test) {
        simulate(test, 3, 550, 3, true);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gc4LikeTierOneTankReachesSpace(GameTestHelper test) {
        simulate(test, 1, 4000, 1, true);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void holdingForwardTiltsAFullRocketUntilItFalls(GameTestHelper test) {
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        try {
            rocket.setData(RocketPrefabs.TIER_1);
            rocket.setFuel(rocket.getFuelTankCapacity());
            var pos = test.absolutePos(new BlockPos(2, 10, 2));
            double startY = Math.max(64, pos.getY());
            rocket.absMoveTo(pos.getX() + .5, startY, pos.getZ() + .5);
            rocket.setLaunchStage(LaunchStage.LAUNCHED);
            rocket.setThrust((float) (Math.sqrt(2) / 2));
            double peakY = startY;
            int ticks = 0;
            while (ticks < 100) {
                // W pitches the rocket instead of increasing engine power.
                rocket.inputTick(0, 1, true, false, false, false, false, false, false);
                rocket.tick();
                ticks++;
                peakY = Math.max(peakY, rocket.getY());
                if (rocket.getDeltaMovement().y < 0) break;
            }
            Constant.LOGGER.info("Full rocket with W held: flightTicks={}, climb={} blocks, pitch={}, remaining={}B, stage={}",
                    ticks, peakY - startY, rocket.getXRot(), rocket.getFuel() / (double) FluidUtil.bucketsToDroplets(1), rocket.getLaunchStage());
            test.assertTrue(peakY > startY && peakY < startY + 5, "expected only a few blocks of climb");
            test.assertTrue(rocket.getDeltaMovement().y < 0, "steering did not reproduce falling");
            test.assertTrue(rocket.getFuel() > 0 && rocket.getLaunchStage() == LaunchStage.LAUNCHED,
                    "steering reproduction must fall while the engine still has fuel");
            test.succeed();
        } finally {
            rocket.discard();
        }
    }

    private static void simulate(GameTestHelper test, int capacity, int efficiency, int buckets, boolean reachesSpace) {
        var config = (ConfigImpl) Galacticraft.CONFIG;
        int oldCapacity = config.rocketFuelTankCapacity();
        int oldEfficiency = config.rocketBurnTicksPerBucket();
        boolean oldMode = config.gc4RocketFuelMode();
        RocketEntity rocket = null;
        try {
            config.setGc4RocketFuelMode(false);
            config.setRocketFuelTankCapacity(capacity);
            config.setRocketBurnTicksPerBucket(efficiency);
            rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
            rocket.setData(RocketPrefabs.TIER_1);
            rocket.setFuel(FluidUtil.bucketsToDroplets(buckets));
            var pos = test.absolutePos(new BlockPos(2, 10, 2));
            double startY = Math.max(64, pos.getY());
            rocket.absMoveTo(pos.getX() + .5, startY, pos.getZ() + .5);
            test.assertTrue(!rocket.isCreative(), "creative fuel bypass would invalidate this reproduction");
            test.assertTrue(rocket.getFuelTankCapacity() == FluidUtil.bucketsToDroplets(capacity), "wrong tank capacity");
            long initialFuel = rocket.getFuel();
            rocket.setLaunchStage(LaunchStage.IGNITED);
            // Tick directly so the real entity logic runs even above the world's normal ticking height.
            // No pilot is needed: reaching escape height discards an unmanned rocket.
            for (int tick = 0; tick < rocket.getPreLaunchWait(); tick++) rocket.tick();
            test.assertTrue(rocket.getLaunchStage() == LaunchStage.LAUNCHED, "countdown did not launch");
            test.assertTrue(rocket.getFuel() == initialFuel, "countdown consumed fuel");
            int ticks = 0;
            double peakY = rocket.getY();
            while (ticks < 2200 && !rocket.isRemoved()) {
                rocket.tick();
                ticks++;
                peakY = Math.max(peakY, rocket.getY());
                if (rocket.getLaunchStage() == LaunchStage.FAILED && rocket.getDeltaMovement().y < 0) break;
            }
            Constant.LOGGER.info("Rocket fuel reproduction: capacity={}B, efficiency={} ticks/B, fuel={}B, startY={}, powered/flightTicks={}, peakY={}, remaining={}B, stage={}, removed={}",
                    capacity, efficiency, buckets, startY, ticks, peakY,
                    rocket.getFuel() / (double) FluidUtil.bucketsToDroplets(1), rocket.getLaunchStage(), rocket.isRemoved());
            if (reachesSpace) {
                test.assertTrue(rocket.getY() >= Constant.ESCAPE_HEIGHT && rocket.isRemoved(), "rocket did not reach space");
                test.assertTrue(rocket.getLaunchStage() == LaunchStage.LAUNCHED, "full tank failed before space");
                test.assertTrue(rocket.getFuel() > 0, "launch exhausted the tank");
            } else {
                test.assertTrue(rocket.getLaunchStage() == LaunchStage.FAILED, "low fuel did not fail flight");
                test.assertTrue(rocket.getFuel() == 0, "flight failed with fuel remaining");
                test.assertTrue(peakY > startY && peakY < Constant.ESCAPE_HEIGHT, "expected a short climb below space");
                test.assertTrue(rocket.getDeltaMovement().y < 0, "rocket did not fall after fuel exhaustion");
            }
            test.succeed();
        } finally {
            if (rocket != null && !rocket.isRemoved()) rocket.discard();
            config.setRocketFuelTankCapacity(oldCapacity);
            config.setRocketBurnTicksPerBucket(oldEfficiency);
            config.setGc4RocketFuelMode(oldMode);
        }
    }
}