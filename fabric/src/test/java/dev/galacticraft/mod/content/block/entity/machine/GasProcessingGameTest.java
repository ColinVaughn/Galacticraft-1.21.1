package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.item.FluidCanisterItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import static dev.galacticraft.mod.content.block.entity.machine.GasProcessingBlockEntity.MILLIBUCKET;

public final class GasProcessingGameTest implements GalacticraftGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private GasLiquefierBlockEntity liquefier(GameTestHelper context) {
        context.setBlock(POS, GCBlocks.GAS_LIQUEFIER);
        return context.getBlockEntity(POS);
    }

    private MethaneSynthesizerBlockEntity synthesizer(GameTestHelper context) {
        context.setBlock(POS, GCBlocks.METHANE_SYNTHESIZER);
        return context.getBlockEntity(POS);
    }

    private void tick(GameTestHelper context, GasProcessingBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) machine.tickBase(context.getLevel(), context.absolutePos(POS),
                machine.getBlockState(), context.getLevel().getProfiler());
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquefierUsesTheRegisteredAtmosphericArgon(GameTestHelper context) {
        context.assertTrue(GCFluids.ARGON == Gases.ARGON, "Argon has two fluid instances");
        context.assertTrue(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(Gases.ARGON_ID) == GCFluids.ARGON,
                "Liquefier argon does not match the registered gas");
        var machine = liquefier(context);
        machine.fluidStorage().slot(0).set(Gases.ARGON, 6 * MILLIBUCKET);
        machine.doLiquefaction();
        context.assertTrue(machine.fluidStorage().slot(2).getResource() == GCFluids.LIQUID_ARGON, "Atmospheric argon did not liquefy");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 3 * MILLIBUCKET, "Argon liquefaction ratio");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void methaneLiquefactionMatchesGC4TimingAndSecondTankPriority(GameTestHelper context) {
        var machine = liquefier(context);
        machine.fluidStorage().slot(0).set(Gases.METHANE, 100 * MILLIBUCKET);
        machine.energyStorage().setEnergy(100000);
        tick(context, machine, 3);
        context.assertTrue(machine.fluidStorage().slot(2).isEmpty(), "initial countdown has not completed");
        tick(context, machine, 1);
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 94 * MILLIBUCKET, "6 mB gas consumed");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 3 * MILLIBUCKET, "3 mB fuel in second tank");
        context.assertTrue(machine.fluidStorage().slot(1).isEmpty(), "second tank fills first");
        context.assertValueEqual(machine.energyStorage().getAmount(), 100000 - 4 * 60L, "60 energy per working tick");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void incompatibleAndFullOutputsDoNotConsumeInput(GameTestHelper context) {
        var machine = liquefier(context);
        machine.fluidStorage().slot(0).set(Gases.METHANE, 100 * MILLIBUCKET);
        machine.fluidStorage().slot(1).set(GCFluids.LIQUID_OXYGEN, MILLIBUCKET);
        machine.fluidStorage().slot(2).set(GCFluids.FUEL, machine.fluidStorage().slot(2).getCapacity());
        machine.energyStorage().setEnergy(100000);
        tick(context, machine, 20);
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 100 * MILLIBUCKET, "blocked outputs preserve gas");
        context.assertValueEqual(machine.energyStorage().getAmount(), 100000L, "blocked machine preserves energy");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pureOxygenNitrogenAndArgonProduceTheirLiquids(GameTestHelper context) {
        var machine = liquefier(context);
        var inputs = new net.minecraft.world.level.material.Fluid[]{Gases.OXYGEN, Gases.NITROGEN, GCFluids.ARGON};
        var outputs = new net.minecraft.world.level.material.Fluid[]{GCFluids.LIQUID_OXYGEN, GCFluids.LIQUID_NITROGEN, GCFluids.LIQUID_ARGON};
        for (int i = 0; i < inputs.length; i++) {
            machine.fluidStorage().slot(0).set(inputs[i], 6 * MILLIBUCKET);
            machine.fluidStorage().slot(2).set(null, 0);
            machine.doLiquefaction();
            context.assertTrue(machine.fluidStorage().slot(2).getResource() == outputs[i], "correct liquefied gas");
            context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 3 * MILLIBUCKET, "2:1 gas ratio");
        }
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void finalSingleMillibucketMatchesLegacyRemainder(GameTestHelper context) {
        var machine = liquefier(context);
        machine.fluidStorage().slot(0).set(Gases.METHANE, MILLIBUCKET);
        machine.doLiquefaction();
        context.assertTrue(machine.fluidStorage().slot(0).isEmpty(), "final mB consumed");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), MILLIBUCKET, "legacy last mB converts 1:1");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCarbonFragmentSupportsFortySynthesisCycles(GameTestHelper context) {
        var machine = synthesizer(context);
        machine.fluidStorage().slot(0).set(Gases.HYDROGEN, 1000 * MILLIBUCKET);
        machine.itemStorage().slot(3).set(GCItems.CARBON_FRAGMENTS, 1);
        for (int i = 0; i < 40; i++) machine.doLiquefaction();
        context.assertTrue(machine.itemStorage().slot(3).isEmpty(), "one carbon fragment consumed");
        context.assertValueEqual(machine.getCoalPartial(), 0, "carbon credit exhausted after 40 cycles");
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 360 * MILLIBUCKET, "16 mB hydrogen per cycle");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 80 * MILLIBUCKET, "2 mB methane per cycle");
        machine.doLiquefaction();
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 80 * MILLIBUCKET, "no carbon means no further output");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void carbonCreditAndManualStopSurviveReload(GameTestHelper context) {
        var machine = synthesizer(context);
        machine.fluidStorage().slot(0).set(Gases.HYDROGEN, 100 * MILLIBUCKET);
        machine.itemStorage().slot(3).set(GCItems.CARBON_FRAGMENTS, 1);
        machine.doLiquefaction();
        machine.toggleDisabled();
        var tag = machine.saveWithoutMetadata(context.getLevel().registryAccess());
        var restored = new MethaneSynthesizerBlockEntity(context.absolutePos(POS), machine.getBlockState());
        restored.loadAdditional(tag, context.getLevel().registryAccess());
        context.assertValueEqual(restored.getCoalPartial(), 1, "remaining carbon credit persisted");
        context.assertTrue(restored.isManuallyDisabled(), "manual stop persisted");
        context.assertValueEqual(restored.fluidStorage().slot(2).getAmount(), 2 * MILLIBUCKET, "methane tank persisted");
        tick(context, machine, 8);
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 2 * MILLIBUCKET, "stopped machine consumes no resources");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void co2RouteConsumesOneMillibucketPerCycle(GameTestHelper context) {
        var machine = synthesizer(context);
        machine.fluidStorage().slot(0).set(Gases.HYDROGEN, 16 * MILLIBUCKET);
        machine.fluidStorage().slot(1).set(Gases.CARBON_DIOXIDE, 10 * MILLIBUCKET);
        machine.doLiquefaction();
        context.assertValueEqual(machine.fluidStorage().slot(1).getAmount(), 9 * MILLIBUCKET, "one mB CO2 consumed");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 2 * MILLIBUCKET, "two mB methane produced");
        context.assertTrue(machine.fluidStorage().slot(0).isEmpty(), "sixteen mB hydrogen consumed");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void synthesisNeedsPowerAndStopsAtCapacity(GameTestHelper context) {
        var machine = synthesizer(context);
        machine.fluidStorage().slot(0).set(Gases.HYDROGEN, 100 * MILLIBUCKET);
        machine.itemStorage().slot(3).set(GCItems.CARBON_FRAGMENTS, 2);
        tick(context, machine, 10);
        context.assertValueEqual(machine.itemStorage().slot(3).getAmount(), 2L, "no power preserves carbon");
        machine.energyStorage().setEnergy(100000);
        machine.fluidStorage().slot(2).set(Gases.METHANE, machine.fluidStorage().slot(2).getCapacity());
        tick(context, machine, 10);
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 100 * MILLIBUCKET, "full output preserves hydrogen");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquidOxygenContainerExpandsIntoTwiceAsMuchGas(GameTestHelper context) {
        var machine = liquefier(context);
        var filled = FluidCanisterItem.getFilledCanister(GCItems.FLUID_CANISTER, GCFluids.LIQUID_OXYGEN);
        machine.itemStorage().slot(1).set(filled.getItem(), filled.getComponentsPatch(), 1);
        tick(context, machine, 1);
        context.assertTrue(machine.fluidStorage().slot(0).getResource() == Gases.OXYGEN, "liquid container vaporized into gas tank");
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 2000 * MILLIBUCKET, "one bucket LOX expands to two buckets gas");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void overworldAirSeparatesNitrogenBeforeOxygen(GameTestHelper context) {
        var machine = liquefier(context);
        machine.itemStorage().slot(1).set(GCItems.ATMOSPHERIC_VALVE, 1);
        tick(context, machine, 1);
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 4 * MILLIBUCKET, "valve collects four mB without power");
        machine.fluidStorage().slot(0).set(GCFluids.ATMOSPHERIC_GASES, 100 * MILLIBUCKET);
        machine.doLiquefaction();
        context.assertTrue(machine.fluidStorage().slot(2).getResource() == GCFluids.LIQUID_NITROGEN, "main gas fills second output");
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 2 * MILLIBUCKET, "two mB nitrogen");
        context.assertTrue(machine.fluidStorage().slot(1).getResource() == GCFluids.LIQUID_OXYGEN, "secondary gas fills first output");
        context.assertValueEqual(machine.fluidStorage().slot(1).getAmount(), MILLIBUCKET, "one mB oxygen");
        context.assertValueEqual(machine.fluidStorage().slot(0).getAmount(), 94 * MILLIBUCKET, "six mB air consumed");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockedValveCannotCollectAir(GameTestHelper context) {
        var machine = liquefier(context);
        machine.itemStorage().slot(1).set(GCItems.ATMOSPHERIC_VALVE, 1);
        context.setBlock(POS.above(), net.minecraft.world.level.block.Blocks.STONE);
        tick(context, machine, 1);
        context.assertTrue(machine.fluidStorage().slot(0).isEmpty(), "blocked air intake stays empty");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outputValveVentsFourMillibucketsWithoutPower(GameTestHelper context) {
        var machine = liquefier(context);
        machine.fluidStorage().slot(2).set(GCFluids.FUEL, 100 * MILLIBUCKET);
        machine.itemStorage().slot(3).set(GCItems.ATMOSPHERIC_VALVE, 1);
        tick(context, machine, 1);
        context.assertValueEqual(machine.fluidStorage().slot(2).getAmount(), 96 * MILLIBUCKET, "output valve vents four mB");
        context.succeed();
    }
}
