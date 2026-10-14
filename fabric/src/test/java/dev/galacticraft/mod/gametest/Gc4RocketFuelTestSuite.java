package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public class Gc4RocketFuelTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void allTiersLaunchWithGc4TanksAndRetainAReturnReserve(GameTestHelper test) {
        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        try {
            config.setGc4RocketFuelMode(true);
            RocketData[] tiers = {RocketPrefabs.TIER_1, RocketPrefabs.TIER_2, RocketPrefabs.TIER_3};
            double[] expectedCosts = {.40, .28, .21};
            for (int tier = 0; tier < tiers.length; tier++) {
                var rocket = new SavedRocket(test.getLevel());
                try {
                    rocket.setData(tiers[tier]);
                    long capacity = FluidUtil.bucketsToDroplets(1) * (tier == 0 ? 1000 : 1500) / 1000;
                    test.assertTrue(rocket.getFuelTankCapacity() == capacity && rocket.getTank().getCapacity() == capacity,
                            "wrong tank or synchronized capacity for tier " + (tier + 1));
                    rocket.setFuel(capacity);
                    var pos = test.absolutePos(new BlockPos(2, 10, 2));
                    rocket.absMoveTo(pos.getX() + .5, 64, pos.getZ() + .5);
                    rocket.setLaunchStage(LaunchStage.IGNITED);
                    for (int tick = 0; tick < rocket.getPreLaunchWait(); tick++) rocket.tick();
                    test.assertTrue(rocket.getFuel() == capacity, "countdown burned fuel");
                    int flightTicks = 0;
                    while (!rocket.isRemoved() && flightTicks++ < 2200) rocket.tick();
                    double cost = (capacity - rocket.getFuel()) / (double) FluidUtil.bucketsToDroplets(1);
                    test.assertTrue(rocket.getY() >= Constant.ESCAPE_HEIGHT && rocket.getLaunchStage() == LaunchStage.LAUNCHED,
                            "GC4 tier " + (tier + 1) + " failed to reach space");
                    test.assertTrue(Math.abs(cost - expectedCosts[tier]) < .02, "GC4 tier " + (tier + 1) + " used " + cost + " buckets");
                    test.assertTrue(rocket.getFuel() >= capacity / 2, "launch left too little return reserve");
                    Constant.LOGGER.info("GC4 fuel mode: tier={}, capacity={}B, Earth launch={}B, reserve={}B",
                            tier + 1, capacity / (double) FluidUtil.bucketsToDroplets(1), cost,
                            rocket.getFuel() / (double) FluidUtil.bucketsToDroplets(1));
                } finally {
                    rocket.discard();
                }
            }
            var cargo = new CargoRocketEntity(GCEntityTypes.CARGO_ROCKET, test.getLevel());
            test.assertTrue(cargo.getFuelTankCapacity() == FluidUtil.bucketsToDroplets(2), "cargo must retain its GC4-sized tank");
            cargo.discard();
            test.succeed();
        } finally {
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void savedModeCapacityAndBurnRateSurviveAConfigChange(GameTestHelper test) {
        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        var rockets = new java.util.ArrayList<SavedRocket>();
        try {
            config.setGc4RocketFuelMode(true);
            var original = new SavedRocket(test.getLevel());
            rockets.add(original);
            original.setData(RocketPrefabs.TIER_2);
            original.setFuel(original.getFuelTankCapacity());
            var tag = new CompoundTag();
            original.write(tag);
            config.setGc4RocketFuelMode(false);
            var loaded = new SavedRocket(test.getLevel());
            rockets.add(loaded);
            loaded.read(tag);
            test.assertTrue(loaded.getFuel() == original.getFuel() && loaded.getFuelTankCapacity() == original.getFuelTankCapacity(),
                    "changing the config lost stored GC4 fuel or capacity");
            loaded.setLaunchStage(LaunchStage.LAUNCHED);
            loaded.tick();
            test.assertTrue(original.getFuel() - loaded.getFuel() == FluidUtil.bucketsToDroplets(1) / 5600,
                    "a saved GC4 rocket switched to the configurable burn rate");

            var modern = new SavedRocket(test.getLevel());
            rockets.add(modern);
            modern.setData(RocketPrefabs.TIER_1);
            modern.setFuel(modern.getFuelTankCapacity());
            var legacyTag = new CompoundTag();
            modern.write(legacyTag);
            legacyTag.remove("Gc4FuelMode");
            legacyTag.remove("FuelCapacity");
            config.setGc4RocketFuelMode(true);
            var legacy = new SavedRocket(test.getLevel());
            rockets.add(legacy);
            legacy.read(legacyTag);
            test.assertTrue(legacy.getFuel() == modern.getFuel() && legacy.getFuelTankCapacity() >= legacy.getFuel(),
                    "loading a pre-mode rocket discarded fuel");
            legacy.setLaunchStage(LaunchStage.LAUNCHED);
            legacy.tick();
            test.assertTrue(modern.getFuel() - legacy.getFuel() == FluidUtil.bucketsToDroplets(1) / config.rocketBurnTicksPerBucket(),
                    "a pre-mode save silently adopted GC4 fuel consumption");
            test.succeed();
        } finally {
            rockets.forEach(RocketEntity::discard);
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fuelCapacitySyncOverridesADifferentLocalConfig(GameTestHelper test) {
        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        var rockets = new java.util.ArrayList<RocketEntity>();
        try {
            config.setGc4RocketFuelMode(true);
            var server = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
            rockets.add(server);
            server.setData(RocketPrefabs.TIER_1);
            server.setFuel(server.getFuelTankCapacity());
            config.setGc4RocketFuelMode(false);
            var replica = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
            rockets.add(replica);
            // Vanilla spawn packets send only values which differ from constant entity-data defaults.
            replica.getEntityData().assignValues(server.getEntityData().getNonDefaultValues());
            test.assertTrue(replica.getFuelTankCapacity() == FluidUtil.bucketsToDroplets(1),
                    "a different local config displayed the wrong capacity after synchronization");
            test.assertTrue(replica.getScaledFuelLevel(100) == 100, "a full GC4 tank did not display as full");
            test.succeed();
        } finally {
            rockets.forEach(RocketEntity::discard);
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void changingModulesDoesNotDiscardFuel(GameTestHelper test) {
        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        config.setGc4RocketFuelMode(true);
        var rocket = new SavedRocket(test.getLevel());
        try {
            rocket.setData(RocketPrefabs.TIER_3);
            rocket.setFuel(rocket.getFuelTankCapacity());
            long before = rocket.getFuel();
            rocket.setData(RocketPrefabs.TIER_1);
            test.assertTrue(rocket.getFuel() == before && rocket.getFuelTankCapacity() >= before, "a module change spilled fuel");
            rocket.setFuel(0);
            rocket.setData(RocketPrefabs.TIER_1);
            test.assertTrue(rocket.getFuelTankCapacity() == FluidUtil.bucketsToDroplets(1), "empty tank did not pick up the new engine profile");
            test.succeed();
        } finally {
            rocket.discard();
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    private static class SavedRocket extends RocketEntity {
        SavedRocket(ServerLevel level) { super(GCEntityTypes.ROCKET, level); }
        void write(CompoundTag tag) { addAdditionalSaveData(tag); }
        void read(CompoundTag tag) { readAdditionalSaveData(tag); }
    }
}