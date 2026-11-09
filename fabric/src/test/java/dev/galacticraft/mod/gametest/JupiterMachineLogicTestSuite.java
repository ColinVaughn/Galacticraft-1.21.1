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

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormRelayBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormRelayBlockEntity;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity;
import dev.galacticraft.mod.content.block.jupiter.machine.*;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterStorms;
import dev.galacticraft.mod.world.dimension.jupiter.PressureHabitats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;

/** Server-side behaviour of the Jupiter machines and the Stormheart encounter, each checked against a control. */
public final class JupiterMachineLogicTestSuite implements GalacticraftGameTest {
    private static <T> T place(GameTestHelper helper, BlockPos pos, Block block, Class<T> type) {
        helper.setBlock(pos, block);
        return type.cast(helper.getBlockEntity(pos));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void separatorSplitsChargedCondensateAndRefundsCharge(GameTestHelper helper) {
        var separator = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.CONDENSATE_SEPARATOR, CondensateSeparatorBlockEntity.class);
        var idle = place(helper, new BlockPos(3, 2, 1), JupiterBlocks.CONDENSATE_SEPARATOR, CondensateSeparatorBlockEntity.class);
        separator.energyStorage().setEnergy(10_000);
        idle.energyStorage().setEnergy(10_000);
        separator.itemStorage().slot(CondensateSeparatorBlockEntity.INPUT_SLOT).set(JupiterItems.CHARGED_CONDENSATE, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(separator.itemStorage().slot(CondensateSeparatorBlockEntity.INPUT_SLOT).isEmpty(), "Condensate not consumed");
            long water = separator.fluidStorage().slot(CondensateSeparatorBlockEntity.WATER_TANK).getAmount();
            helper.assertTrue(water == FluidUtil.bucketsToDroplets(1) / 2, "Wrong water yield: " + water);
            long energy = separator.energyStorage().getAmount();
            long expected = 10_000 - (long) CondensateSeparatorBlockEntity.PROCESS_TICKS * CondensateSeparatorBlockEntity.ENERGY_PER_TICK + CondensateSeparatorBlockEntity.CHARGE_REFUND;
            helper.assertTrue(energy == expected, "Charge was not refunded: " + energy + " != " + expected);
            // Control: with nothing to separate, the other machine idles and spends nothing.
            helper.assertTrue(idle.getState().getStatus() == JupiterMachineStatuses.NO_CONDENSATE, "Empty separator status: " + idle.getState().getStatus());
            helper.assertTrue(idle.energyStorage().getAmount() == 10_000, "Empty separator used energy");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void stabilizerSealsOnlyRatedEnclosures(GameTestHelper helper) {
        // A 3x3x3 room walled in reinforced hull, with the stabilizer on its floor.
        for (int x = 1; x <= 5; x++) for (int y = 1; y <= 5; y++) for (int z = 1; z <= 5; z++) {
            boolean shell = x == 1 || x == 5 || y == 1 || y == 5 || z == 1 || z == 5;
            helper.setBlock(new BlockPos(x, y, z), shell ? JupiterBlocks.REINFORCED_AEROSTAT_HULL : Blocks.AIR);
        }
        BlockPos stabilizerPos = new BlockPos(3, 2, 3), wall = new BlockPos(5, 3, 3);
        var stabilizer = place(helper, stabilizerPos, JupiterBlocks.PRESSURE_STABILIZER, PressureStabilizerBlockEntity.class);
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(stabilizerPos), inside = helper.absolutePos(new BlockPos(3, 3, 3)), outside = helper.absolutePos(new BlockPos(7, 3, 3));

        stabilizer.scan(level, origin);
        helper.assertTrue(stabilizer.result() == PressureStabilizerBlockEntity.Result.SEALED && stabilizer.volume() == 27, "Rated room did not seal: " + stabilizer.result() + " " + stabilizer.volume());

        helper.setBlock(wall, Blocks.STONE);
        stabilizer.scan(level, origin);
        helper.assertTrue(stabilizer.result() == PressureStabilizerBlockEntity.Result.UNRATED_WALL, "Stone wall accepted: " + stabilizer.result());
        var readout = stabilizer.readout();
        helper.assertTrue(new BlockPos(readout.get(4), readout.get(5), readout.get(6)).equals(helper.absolutePos(wall)), "Wrong unrated wall reported: " + readout);

        helper.setBlock(wall, Blocks.AIR);
        stabilizer.scan(level, origin);
        helper.assertTrue(stabilizer.result() == PressureStabilizerBlockEntity.Result.LEAK, "Hole did not leak: " + stabilizer.result());

        helper.setBlock(wall, JupiterBlocks.SEALED_HATCH.defaultBlockState().setValue(TrapDoorBlock.OPEN, true));
        stabilizer.scan(level, origin);
        helper.assertTrue(stabilizer.result() == PressureStabilizerBlockEntity.Result.LEAK, "Open hatch sealed");
        helper.setBlock(wall, JupiterBlocks.SEALED_HATCH.defaultBlockState());
        stabilizer.scan(level, origin);
        helper.assertTrue(stabilizer.result() == PressureStabilizerBlockEntity.Result.SEALED, "Closed hatch did not seal: " + stabilizer.result());

        // Powered, the sealed room protects whoever is inside it and nowhere else; unpowered, it protects no one.
        stabilizer.energyStorage().setEnergy(stabilizer.energyStorage().getCapacity());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(PressureHabitats.ratingAt(level, inside) == PressureHabitats.RATING, "Habitat not protecting its interior"))
                .thenExecute(() -> helper.assertTrue(PressureHabitats.ratingAt(level, outside) == 1, "Habitat leaked protection outside"))
                .thenExecute(() -> stabilizer.energyStorage().setEnergy(0))
                .thenWaitUntil(() -> helper.assertTrue(PressureHabitats.ratingAt(level, inside) == 1, "Unpowered habitat still protecting"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void dockChargesRefillsAndRepairsAParkedDiver(GameTestHelper helper) {
        var dock = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.VEHICLE_DOCK, VehicleDockBlockEntity.class);
        dock.energyStorage().setEnergy(50_000);
        dock.fluidStorage().slot(VehicleDockBlockEntity.OXYGEN_TANK).set(Gases.OXYGEN, FluidUtil.bucketsToDroplets(4));
        dock.itemStorage().slot(VehicleDockBlockEntity.SALVAGE_SLOT).set(JupiterItems.AEROSTAT_SALVAGE, 2);
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    // Control: nothing in range, so nothing is spent.
                    helper.assertTrue(dock.getState().getStatus() == JupiterMachineStatuses.NO_VEHICLE, "Dock serviced nothing: " + dock.getState().getStatus());
                    helper.assertTrue(dock.energyStorage().getAmount() == 50_000, "Dock spent energy with no vehicle");
                    CloudDiverEntity diver = GCEntityTypes.CLOUD_DIVER.create(helper.getLevel());
                    diver.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 1))));
                    helper.getLevel().addFreshEntity(diver);
                    diver.hurt(helper.getLevel().damageSources().generic(), 50);
                })
                .thenWaitUntil(() -> {
                    CloudDiverEntity diver = helper.getLevel().getEntitiesOfClass(CloudDiverEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 1))).inflate(2)).getFirst();
                    helper.assertTrue(diver.energy() > 0, "Diver not charged");
                    helper.assertTrue(diver.oxygen() > 0, "Diver not refilled");
                    helper.assertTrue(diver.hull() == 280, "Hull not patched once: " + diver.hull());
                    helper.assertTrue(dock.itemStorage().slot(VehicleDockBlockEntity.SALVAGE_SLOT).getAmount() == 1, "Salvage spent past the threshold");
                    diver.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void beaconsListOnlyBroadcastingNeighbours(GameTestHelper helper) {
        var alpha = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.SURVEY_BEACON, SurveyBeaconBlockEntity.class);
        var bravo = place(helper, new BlockPos(1, 2, 5), JupiterBlocks.SURVEY_BEACON, SurveyBeaconBlockEntity.class);
        var dark = place(helper, new BlockPos(5, 2, 1), JupiterBlocks.SURVEY_BEACON, SurveyBeaconBlockEntity.class);
        String suffix = Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        alpha.setBeaconName("Alpha " + suffix);
        bravo.setBeaconName("Bravo " + suffix);
        dark.setBeaconName("Dark " + suffix);
        alpha.energyStorage().setEnergy(5_000);
        bravo.energyStorage().setEnergy(5_000);
        helper.succeedWhen(() -> {
            var lines = alpha.readoutLines();
            helper.assertTrue(lines.getFirst().equals("Alpha " + suffix), "Own name missing: " + lines);
            helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("Bravo " + suffix + "|4|S")), "Neighbour missing or wrong bearing: " + lines);
            // Control: the unpowered beacon is on the network but not broadcasting.
            helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("Dark " + suffix)), "Unpowered beacon listed: " + lines);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void grounderTakesNearbyStrikesOnly(GameTestHelper helper) {
        var grounder = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.LIGHTNING_GROUNDER, LightningGrounderBlockEntity.class);
        var level = helper.getLevel();
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    // Control: a strike well beyond the grounder's radius, high in empty air, lands where it was aimed.
                    helper.assertTrue(JupiterStorms.strike(level, base.add(0, 40, JupiterStorms.GROUNDER_RADIUS + 24)) == null, "Far strike was grounded");
                    helper.assertTrue(grounder.strikes() == 0, "Far strike counted");
                    helper.assertTrue(JupiterStorms.strike(level, base.add(5, 0, 5)) == grounder, "Near strike missed the grounder");
                    helper.assertTrue(grounder.strikes() == 1, "Strike not counted");
                    helper.assertTrue(grounder.energyStorage().getAmount() == LightningGrounderBlockEntity.ENERGY_PER_STRIKE, "Strike energy not banked: " + grounder.energyStorage().getAmount());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void stormheartEncounterRunsAndResets(GameTestHelper helper) {
        var pedestal = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.STORMHEART_PEDESTAL, StormheartPedestalBlockEntity.class);
        var east = place(helper, new BlockPos(5, 2, 1), JupiterBlocks.STORM_RELAY, StormRelayBlockEntity.class);
        var south = place(helper, new BlockPos(1, 2, 5), JupiterBlocks.STORM_RELAY, StormRelayBlockEntity.class);
        var fighter = helper.makeMockServerPlayerInLevel();
        fighter.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));

        helper.assertTrue(pedestal.begin(fighter), "Encounter did not start");
        helper.assertTrue(pedestal.phase() == StormheartPedestalBlock.Phase.ACTIVE, "Not active");
        helper.assertTrue(east.mode() == StormRelayBlock.Mode.ENERGIZED && south.mode() == StormRelayBlock.Mode.ENERGIZED, "Relays not energized");
        helper.assertTrue(pedestal.relays().size() == 2, "Wrong relays linked: " + pedestal.relays());
        var leviathan = pedestal.leviathan();
        helper.assertTrue(leviathan != null, "Beginning the encounter did not summon the Leviathan");
        float full = leviathan.getHealth();
        helper.assertTrue(!leviathan.hurt(helper.getLevel().damageSources().playerAttack(fighter), 50) && leviathan.getHealth() == full,
                "The Leviathan took damage while the relays shield it");

        for (int i = 0; i < StormRelayBlockEntity.BREAKER_USES; i++) east.workBreaker(fighter);
        helper.assertTrue(east.mode() == StormRelayBlock.Mode.DISABLED, "Relay not disabled");
        helper.assertTrue(pedestal.phase() == StormheartPedestalBlock.Phase.ACTIVE, "Core exposed with a relay still up");
        for (int i = 0; i < StormRelayBlockEntity.BREAKER_USES; i++) south.workBreaker(fighter);
        helper.assertTrue(pedestal.phase() == StormheartPedestalBlock.Phase.CORE_EXPOSED, "Core not exposed");
        // The core stays guarded until the Leviathan falls.
        pedestal.claim(fighter);
        helper.assertTrue(fighter.getInventory().countItem(JupiterItems.STORMHEART_CORE) == 0, "Claimed a core past a living Leviathan");
        helper.assertTrue(leviathan.hurt(helper.getLevel().damageSources().playerAttack(fighter), 50) && leviathan.getHealth() < full,
                "The exposed Leviathan could not be hurt");
        leviathan.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(pedestal.isLeviathanSlain(), "The pedestal did not hear the Leviathan fall");

        // Control: someone who arrives after the fight gets nothing.
        var latecomer = helper.makeMockServerPlayerInLevel();
        pedestal.claim(latecomer);
        helper.assertTrue(!latecomer.getInventory().contains(JupiterItems.STORMHEART_CORE.getDefaultInstance()), "Latecomer got a core");
        pedestal.claim(fighter);
        pedestal.claim(fighter);
        int cores = fighter.getInventory().countItem(JupiterItems.STORMHEART_CORE);
        helper.assertTrue(cores == 1, "Participant got " + cores + " cores");

        pedestal.reset(true);
        helper.assertTrue(pedestal.phase() == StormheartPedestalBlock.Phase.IDLE, "Did not reset");
        helper.assertTrue(east.mode() == StormRelayBlock.Mode.IDLE && south.mode() == StormRelayBlock.Mode.IDLE, "Relays not released");
        helper.assertTrue(pedestal.begin(fighter), "Could not fight again after a reset");
        var second = pedestal.leviathan();
        helper.assertTrue(second != null && !pedestal.isLeviathanSlain(), "A new fight has no Leviathan");
        pedestal.reset(false);
        helper.assertTrue(second.isRemoved(), "Resetting left the Leviathan in the sky");
        helper.succeed();
    }

    /**
     * The game test server builds no Galacticraft dimensions, so collection on Jupiter itself is checked by
     * {@code JupiterClientTest}; here, the overworld refusal and the pressure-to-yield curve.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void collectorNeedsAGasGiantAndDenserAirYieldsMore(GameTestHelper helper) {
        var collector = place(helper, new BlockPos(1, 2, 1), JupiterBlocks.ATMOSPHERIC_COLLECTOR, AtmosphericCollectorBlockEntity.class);
        collector.energyStorage().setEnergy(collector.energyStorage().getCapacity());
        long[] thin = AtmosphericCollectorBlockEntity.yieldPerTick(1), dense = AtmosphericCollectorBlockEntity.yieldPerTick(16);
        helper.assertTrue(thin[0] > 0 && thin[1] > 0, "No yield at one bar");
        helper.assertTrue(dense[0] == thin[0] * 4 || Math.abs(dense[0] - thin[0] * 4) <= 2, "Yield does not scale with sqrt(pressure): " + thin[0] + " -> " + dense[0]);
        helper.assertTrue(thin[0] > thin[1] * 4, "Helium share too large: " + thin[0] + " / " + thin[1]);
        helper.succeedWhen(() -> {
            helper.assertTrue(collector.getState().getStatus() == JupiterMachineStatuses.NOT_ON_GAS_GIANT, "Overworld collector status: " + collector.getState().getStatus());
            helper.assertTrue(collector.fluidStorage().slot(AtmosphericCollectorBlockEntity.HYDROGEN_TANK).isEmpty(), "Overworld collector produced gas");
            helper.assertTrue(collector.energyStorage().getAmount() == collector.energyStorage().getCapacity(), "Overworld collector spent energy");
        });
    }
}
