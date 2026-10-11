package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.oxygencompressor.OxygenTankInside;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.machine.OxygenCompressorBlock;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class OxygenCompressorGameTest implements GalacticraftGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = EMPTY_STRUCTURE)
    public void compressionConservesOxygenAndLimitsTransfer(GameTestHelper context) {
        context.setBlock(POS, GCBlocks.OXYGEN_COMPRESSOR);
        OxygenCompressorBlockEntity machine = context.getBlockEntity(POS);
        var tank = machine.fluidStorage().slot(OxygenCompressorBlockEntity.OXYGEN_TANK);
        var item = machine.itemStorage().slot(OxygenCompressorBlockEntity.OXYGEN_SLOT);
        tank.set(Gases.OXYGEN, 10000);
        item.set(GCItems.SMALL_OXYGEN_TANK, 1);
        long modifications = item.getModifications();
        machine.energyStorage().setEnergy(100000);
        tick(context, machine);
        context.assertValueEqual(tank.getAmount(), 9200L, "oxygen remaining in machine");
        context.assertValueEqual(OxygenTankItem.getStorage(new ItemStack(item.getResource().builtInRegistryHolder(), 1,
                item.getComponents())).getAmount(), 800L, "oxygen transferred into item");
        context.assertTrue(item.getModifications() > modifications, "item menu cache invalidated");
        context.assertTrue(context.getBlockState(POS).getValue(OxygenCompressorBlock.OXYGEN_TANK_INSIDE)
                == OxygenTankInside.SMALL, "small tank visible on block");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void decompressionConservesOxygenNearCapacity(GameTestHelper context) {
        context.setBlock(POS, GCBlocks.OXYGEN_COMPRESSOR);
        OxygenCompressorBlockEntity machine = context.getBlockEntity(POS);
        machine.changeCompressionMode();
        var tank = machine.fluidStorage().slot(OxygenCompressorBlockEntity.OXYGEN_TANK);
        var item = machine.itemStorage().slot(OxygenCompressorBlockEntity.OXYGEN_SLOT);
        ItemStack fullTank = OxygenTankItem.getFullTank(GCItems.SMALL_OXYGEN_TANK);
        long before = OxygenTankItem.getStorage(fullTank).getAmount();
        item.set(fullTank.getItem(), fullTank.getComponentsPatch(), 1);
        tank.set(Gases.OXYGEN, tank.getCapacity() - 300);
        machine.energyStorage().setEnergy(100000);
        tick(context, machine);
        context.assertValueEqual(tank.getAmount(), tank.getCapacity(), "machine tank filled without overflow");
        context.assertValueEqual(OxygenTankItem.getStorage(new ItemStack(item.getResource().builtInRegistryHolder(), 1,
                item.getComponents())).getAmount(), before - 300, "only available capacity removed from item");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void compressionModePersistsAndDefaultsForOldSaves(GameTestHelper context) {
        context.setBlock(POS, GCBlocks.OXYGEN_COMPRESSOR);
        OxygenCompressorBlockEntity machine = context.getBlockEntity(POS);
        machine.changeCompressionMode();
        CompoundTag saved = machine.saveWithoutMetadata(context.getLevel().registryAccess());
        machine.changeCompressionMode();
        machine.loadAdditional(saved, context.getLevel().registryAccess());
        context.assertTrue(!machine.getCompressionMode(), "decompression mode restored");
        saved.remove(Constant.Nbt.COMPRESSION_MODE);
        machine.loadAdditional(saved, context.getLevel().registryAccess());
        context.assertTrue(machine.getCompressionMode(), "old compressor saves default to compression");
        context.succeed();
    }

    private static void tick(GameTestHelper context, OxygenCompressorBlockEntity machine) {
        machine.tick(context.getLevel(), context.absolutePos(POS), machine.getBlockState(),
                context.getLevel().getProfiler());
    }
}
