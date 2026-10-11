package dev.galacticraft.mod.world.gen.feature;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.item.BatteryItem;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import dev.galacticraft.machinelib.api.component.MLDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.EnumSet;

public class LunarDiscoveryTestSuite implements GalacticraftGameTest {
    private static final String CAVE = "galacticraft-test:lunar_cave";
    private static final int FLOOR = 21;

    @GameTest(template = EMPTY_STRUCTURE)
    public void expeditionLootContainsFilledConfiguredSupplies(GameTestHelper helper) {
        int tanks = 0, batteries = 0;
        for (int seed = 1; seed <= 64; seed++) {
            ChestBlockEntity chest = new ChestBlockEntity(helper.absolutePos(BlockPos.ZERO), Blocks.CHEST.defaultBlockState());
            chest.setLevel(helper.getLevel());
            chest.setLootTable(GCLootTables.MOON_CAVE_EXPEDITION_CACHE, seed);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                var stack = chest.getItem(slot);
                if (stack.getItem() instanceof OxygenTankItem tank) {
                    long amount = stack.getOrDefault(MLDataComponents.AMOUNT.get(), 0L);
                    helper.assertTrue(amount >= tank.capacity * 0.39 && amount <= tank.capacity * 0.76,
                            "Cached oxygen is empty or exceeds the configured reserve range");
                    tanks++;
                } else if (stack.getItem() instanceof BatteryItem battery) {
                    long energy = battery.getStoredEnergy(stack), capacity = battery.getEnergyCapacity(stack);
                    helper.assertTrue(energy >= capacity * 0.39 && energy <= capacity * 0.76, "Cached battery is unusable");
                    batteries++;
                }
            }
        }
        helper.assertTrue(tanks > 0 && batteries > 0, "Supply loot entries never appeared");
        helper.succeed();
    }

    @GameTest(template = CAVE)
    public void trailsFollowBentPassagesWithoutCrossingWallsOrBlockingTheirLane(GameTestHelper helper) {
        cave(helper, false);
        BlockPos start = helper.absolutePos(new BlockPos(5, FLOOR, 5));
        BlockPos target = helper.absolutePos(new BlockPos(20, FLOOR, 13));
        var route = LunarCavePlacement.path(LunarCavePlacement.routes(helper.getLevel(), start), target);
        helper.assertTrue(route.size() > 20, "Trail did not follow the L-shaped passage");
        LunarCavePlacement.markRoute(helper.getLevel(), route, GCBlocks.HELION_NODULES);
        int markers = 0;
        for (BlockPos pos : route) helper.assertTrue(LunarCavePlacement.walkable(helper.getLevel(), pos), "Clue blocks obstruct the route");
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, FLOOR - 1, 0)), helper.absolutePos(new BlockPos(31, FLOOR + 3, 31)))) {
            if (helper.getLevel().getBlockState(pos).is(GCBlocks.HELION_NODULES)) markers++;
        }
        helper.assertTrue(markers >= 3, "No usable clue trail was generated");
        helper.succeed();
    }

    @GameTest(template = CAVE)
    public void surveySitesProvideANoteToAnActualReachableDeposit(GameTestHelper helper) {
        cave(helper, true);
        var feature = new LunarCaveLandmarkFeature(NoneFeatureConfiguration.CODEC);
        helper.assertTrue(feature.place(NoneFeatureConfiguration.INSTANCE, helper.getLevel(),
                helper.getLevel().getChunkSource().getGenerator(), RandomSource.create(5),
                helper.absolutePos(new BlockPos(12, FLOOR, 12))), "Survey site rejected a broad supported cave");
        int deposits = 0, notes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, FLOOR - 1, 0)), helper.absolutePos(new BlockPos(31, FLOOR + 4, 31)))) {
            if (helper.getLevel().getBlockState(pos).is(GCBlocks.LUNAR_SAPPHIRE_ORE)) deposits++;
            if (helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest
                    && chest.getItem(26).has(DataComponents.LORE)) {
                notes++;
                String coordinates = chest.getItem(26).get(DataComponents.LORE).lines().get(2).getString().substring("Survey: ".length());
                String[] values = coordinates.split(", ");
                BlockPos destination = new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
                helper.assertTrue(helper.getLevel().getBlockState(destination.below()).is(GCBlocks.LUNAR_SAPPHIRE_ORE),
                        "Survey coordinates miss their deposit");
                boolean reachable = false;
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos start = pos.relative(direction);
                    if (LunarCavePlacement.walkable(helper.getLevel(), start)
                            && LunarCavePlacement.routes(helper.getLevel(), start, 1536).containsKey(destination)) reachable = true;
                }
                helper.assertTrue(reachable, "Survey deposit cannot be reached from its cache");
            }
        }
        helper.assertTrue(deposits > 0 && notes == 1, "Survey points at no deposit or lacks its record: deposits=" + deposits + ", notes=" + notes);
        helper.succeed();
    }

    @GameTest(template = CAVE)
    public void densKeepTwoShelteredBypassesAndSupplyRecoverableSalvage(GameTestHelper helper) {
        cave(helper, true);
        BlockPos origin = helper.absolutePos(new BlockPos(12, FLOOR, 12));
        var feature = new LunarBurrowerDenFeature(NoneFeatureConfiguration.CODEC);
        helper.assertTrue(feature.place(NoneFeatureConfiguration.INSTANCE, helper.getLevel(),
                helper.getLevel().getChunkSource().getGenerator(), RandomSource.create(42), origin), "Den rejected a broad supported cave");
        int banks = 0, caches = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, FLOOR - 1, 0)), helper.absolutePos(new BlockPos(31, FLOOR + 4, 31)))) {
            if (helper.getLevel().getBlockState(pos).is(GCBlocks.COBBLED_MOON_ROCK)) banks++;
            if (helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                helper.assertTrue(chest.getItem(26).is(GCBlocks.ABANDONED_SURVEY_BEACON.asItem()), "Den salvage is missing");
                caches++;
            }
        }
        helper.assertTrue(banks >= 20 && caches == 1, "Den lacks shelter or salvage");
        helper.getLevel().getEntitiesOfClass(dev.galacticraft.mod.content.entity.LunarBurrowerEntity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(24)).forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    private static void cave(GameTestHelper helper, boolean broad) {
        var level = helper.getLevel();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < 38; y++) {
            boolean passage = broad || (x >= 4 && x <= 6 && z >= 4 && z <= 14)
                    || (x >= 4 && x <= 22 && z >= 12 && z <= 14);
            level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), passage && y >= FLOOR && y < FLOOR + 13
                    ? Blocks.AIR.defaultBlockState() : GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        }
        for (int x : new int[]{0, 15, 31}) for (int z : new int[]{0, 15, 31}) {
            Heightmap.primeHeightmaps(level.getChunkAt(helper.absolutePos(new BlockPos(x, 0, z))), EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
        }
    }
}
