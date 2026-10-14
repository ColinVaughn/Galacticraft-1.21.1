package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.machine.configuration.RedstoneMode;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.item.BatteryItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import dev.galacticraft.mod.world.gen.feature.GCFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import team.reborn.energy.api.EnergyStorage;

public final class GeothermalGeneratorGameTest implements GalacticraftGameTest {
    // The GameTest world starts near its minimum build height; leave room for the full shaft.
    private static final BlockPos POS = new BlockPos(2, 24, 2);

    private GeothermalGeneratorBlockEntity generator(GameTestHelper context, int acidDepth) {
        context.setBlock(POS, GCBlocks.GEOTHERMAL_GENERATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        context.setBlock(POS.below(), GCBlocks.VAPOR_SPOUT);
        for (int depth = 2; depth <= 20; depth++) context.setBlock(POS.below(depth), Blocks.AIR);
        if (acidDepth > 0) context.setBlock(POS.below(acidDepth), GCBlocks.SULFURIC_ACID);
        return context.getBlockEntity(POS);
    }

    private void tick(GameTestHelper context, GeothermalGeneratorBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) machine.tickBase(context.getLevel(), context.absolutePos(POS),
                context.getBlockState(POS), context.getLevel().getProfiler());
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void generatedSpoutsHaveUsableAcidShafts(GameTestHelper context) {
        BlockPos spout = POS.below();
        for (int seed = 0; seed < 32; seed++) {
            context.setBlock(spout, Blocks.AIR);
            context.setBlock(spout.below(), GCBlocks.SOFT_VENUS_ROCK);
            boolean placed = GCFeatures.VAPOR_SPOUT.place(NoneFeatureConfiguration.INSTANCE, context.getLevel(),
                    context.getLevel().getChunkSource().getGenerator(), RandomSource.create(seed), context.absolutePos(spout));
            context.assertTrue(placed, "Venus rock accepts an acid pool and vapor spout");
            context.assertTrue(GeothermalGeneratorBlockEntity.hasValidSpoutBelow(context.getLevel(), context.absolutePos(POS)),
                    "generated shaft is usable by a generator directly above the spout, seed " + seed);
        }
        context.setBlock(spout, Blocks.AIR);
        context.setBlock(spout.below(), Blocks.STONE);
        context.assertTrue(!GCFeatures.VAPOR_SPOUT.place(NoneFeatureConfiguration.INSTANCE, context.getLevel(),
                context.getLevel().getChunkSource().getGenerator(), RandomSource.create(0), context.absolutePos(spout)),
                "spouts reject non-Venus ground");
        context.assertTrue(context.getBlockState(spout).isAir(), "rejected placement leaves the surface untouched");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requiresSpoutAndSulfuricAcid(GameTestHelper context) {
        var machine = generator(context, 3);
        context.setBlock(POS.below(), Blocks.STONE);
        tick(context, machine, 20);
        context.assertTrue(!machine.hasValidSpout() && machine.energyStorage().isEmpty(), "ordinary stone is not a vapor spout");
        context.setBlock(POS.below(), GCBlocks.VAPOR_SPOUT);
        context.setBlock(POS.below(3), Blocks.LAVA);
        tick(context, machine, 20);
        context.assertTrue(!machine.hasValidSpout() && machine.getGeneration() == 0, "lava cannot replace sulfuric acid");
        context.setBlock(POS.below(3), GCBlocks.SULFURIC_ACID);
        tick(context, machine, 20);
        context.assertTrue(machine.hasValidSpout() && machine.getGeneration() > 0, "spout over acid generates without fuel");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void acceptsNineteenBlockDepthButRejectsTwenty(GameTestHelper context) {
        var machine = generator(context, 19);
        tick(context, machine, 20);
        context.assertTrue(machine.hasValidSpout(), "acid 19 blocks below is inside the GC4 scan range");
        context.setBlock(POS.below(19), Blocks.AIR);
        context.setBlock(POS.below(20), GCBlocks.SULFURIC_ACID);
        tick(context, machine, 20);
        context.assertTrue(!machine.hasValidSpout(), "acid 20 blocks below is outside the scan range");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockedShaftStopsAtTheNextTwentyTickScan(GameTestHelper context) {
        var machine = generator(context, 5);
        tick(context, machine, 20);
        context.setBlock(POS.below(3), Blocks.GLASS);
        tick(context, machine, 19);
        context.assertTrue(machine.hasValidSpout(), "spout validity is cached until the next scan");
        tick(context, machine, 1);
        context.assertTrue(!machine.hasValidSpout() && machine.getGeneration() == 0, "even transparent solids obstruct the shaft");
        context.assertTrue(!context.getBlockState(POS).getValue(MachineBlock.ACTIVE), "invalid spout selects the inactive texture");
        long stored = machine.energyStorage().getAmount();
        tick(context, machine, 1);
        context.assertValueEqual(machine.energyStorage().getAmount(), stored, "invalid spout generates no further energy");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outputMatchesGC4WaveAndOneTickStorageDelay(GameTestHelper context) {
        var machine = generator(context, 2);
        tick(context, machine, 19);
        context.assertValueEqual(machine.getGeneration(), 0, "first scan has not occurred");
        tick(context, machine, 1);
        context.assertValueEqual(machine.getGeneration(), 148, "GC4 wave at tick 20");
        context.assertTrue(machine.energyStorage().isEmpty(), "new output enters storage on the next tick");
        tick(context, machine, 1);
        context.assertValueEqual(machine.energyStorage().getAmount(), 148L, "previous tick's output is stored");
        tick(context, machine, 58);
        context.assertValueEqual(machine.getGeneration(), 199, "GC4 peak at tick 79");
        tick(context, machine, 157);
        context.assertValueEqual(machine.getGeneration(), 30, "GC4 trough at tick 236");
        context.assertTrue(context.getBlockState(POS).getValue(MachineBlock.ACTIVE), "generator remains active even with a full buffer");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void manualDisableHasTwentyTickCooldownAndPersists(GameTestHelper context) {
        var machine = generator(context, 2);
        tick(context, machine, 20);
        machine.toggleDisabled();
        machine.toggleDisabled();
        context.assertTrue(machine.isManuallyDisabled(), "cooldown rejects an immediate second toggle");
        tick(context, machine, 1);
        context.assertValueEqual(machine.getGeneration(), 0, "manual disable stops generation");
        context.assertTrue(!context.getBlockState(POS).getValue(MachineBlock.ACTIVE), "disabled generator selects inactive texture");
        var tag = machine.saveWithoutMetadata(context.getLevel().registryAccess());
        var restored = new GeothermalGeneratorBlockEntity(context.absolutePos(POS), machine.getBlockState());
        restored.loadAdditional(tag, context.getLevel().registryAccess());
        context.assertTrue(restored.isManuallyDisabled(), "disabled flag survives reload");
        context.assertValueEqual(restored.energyStorage().getAmount(), machine.energyStorage().getAmount(), "stored energy survives reload");
        tick(context, machine, 18);
        machine.toggleDisabled();
        context.assertTrue(machine.isManuallyDisabled(), "cooldown still rejects a toggle at tick 19");
        tick(context, machine, 1);
        machine.toggleDisabled();
        tick(context, machine, 1);
        context.assertTrue(!machine.isManuallyDisabled() && machine.getGeneration() > 0, "generation resumes after cooldown");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void chargesBatteryFromStoredOutput(GameTestHelper context) {
        var machine = generator(context, 2);
        machine.itemStorage().slot(0).set(GCItems.BATTERY, 1);
        tick(context, machine, 21);
        var slot = machine.itemStorage().slot(0);
        ItemStack battery = new ItemStack(slot.getResource().builtInRegistryHolder(), 1, slot.getComponents());
        context.assertTrue(((BatteryItem) GCItems.BATTERY).getStoredEnergy(battery) > 0, "generated energy charges the battery");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exportsStoredPowerWhileDisabledThroughOriginalRightPort(GameTestHelper context) {
        var machine = generator(context, 0);
        BlockPos recipientPos = POS.east();
        context.setBlock(recipientPos, GCBlocks.ENERGY_STORAGE_MODULE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        EnergyStorageModuleBlockEntity recipient = context.getBlockEntity(recipientPos);
        recipient.tickBase(context.getLevel(), context.absolutePos(recipientPos), context.getBlockState(recipientPos), context.getLevel().getProfiler());
        machine.energyStorage().setEnergy(1000);
        machine.toggleDisabled();
        tick(context, machine, 1);
        context.assertValueEqual(recipient.energyStorage().getAmount(), 200L, "right port exports up to 200 energy per tick while generation is disabled");
        context.assertValueEqual(machine.energyStorage().getAmount(), 800L, "exported energy is removed from the buffer");
        for (Direction side : Direction.values()) {
            var exposed = EnergyStorage.SIDED.find(context.getLevel(), context.absolutePos(POS), side);
            if (side == Direction.EAST) {
                context.assertTrue(exposed != null && exposed.supportsExtraction() && !exposed.supportsInsertion(), "right side exposes output-only energy");
            } else {
                context.assertTrue(exposed == null, "other sides do not expose energy by default");
            }
        }
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void redstoneControlStopsAndResumesGeneration(GameTestHelper context) {
        var machine = generator(context, 2);
        tick(context, machine, 20);
        machine.setRedstoneMode(RedstoneMode.HIGH);
        tick(context, machine, 1);
        context.assertValueEqual(machine.getGeneration(), 0, "high mode without power stops generation");
        machine.getState().setPowered(true);
        tick(context, machine, 1);
        context.assertTrue(machine.getGeneration() > 0, "redstone signal resumes generation");
        context.succeed();
    }
}
