package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.machinelib.api.machine.configuration.RedstoneMode;
import dev.galacticraft.mod.content.block.special.telepad.ShortRangeTelepadBlock;
import dev.galacticraft.mod.content.block.special.telepad.TelepadRegistry;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import dev.galacticraft.mod.network.c2s.TelepadAddressPayload;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import team.reborn.energy.api.EnergyStorage;

import java.util.concurrent.atomic.AtomicInteger;
import static dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity.*;

public final class ShortRangeTelepadGameTest implements GalacticraftGameTest {
    private static final AtomicInteger ADDRESSES = new AtomicInteger(800_000);
    private static final BlockPos SOURCE = new BlockPos(2, 2, 2), TARGET = new BlockPos(6, 2, 2);
    private int address(GameTestHelper helper) {
        var registry = TelepadRegistry.get(helper.getLevel().getServer());
        int address;
        do { address = ADDRESSES.getAndIncrement(); } while (registry.get(address) != null);
        return address;
    }
    private ShortRangeTelepadBlockEntity pad(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, GCBlocks.SHORT_RANGE_TELEPAD);
        ShortRangeTelepadBlock.createFrame(helper.getLevel(), helper.absolutePos(relative));
        return helper.getBlockEntity(relative);
    }
    private void tick(GameTestHelper helper, ShortRangeTelepadBlockEntity pad, int count, boolean powered) {
        for (int i = 0; i < count; i++) {
            if (powered) pad.energyStorage().setEnergy(pad.energyStorage().getCapacity());
            pad.tickBase(helper.getLevel(), pad.getBlockPos(), pad.getBlockState(), helper.getLevel().getProfiler());
        }
    }
    private ShortRangeTelepadBlockEntity[] pair(GameTestHelper helper) {
        var source = pad(helper, SOURCE);
        var target = pad(helper, TARGET);
        int from = address(helper), to = address(helper);
        target.configure(to, from);
        source.configure(from, to);
        target.energyStorage().setEnergy(9000);
        return new ShortRangeTelepadBlockEntity[]{source, target};
    }
    private Pig pig(GameTestHelper helper, ShortRangeTelepadBlockEntity source) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.moveTo(source.getBlockPos().getX() + 0.5, source.getBlockPos().getY() + 0.45, source.getBlockPos().getZ() + 0.5, 37, 0);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void uniqueAddressesSurviveReloadAndCannotBeStolen(GameTestHelper helper) {
        var pair = pair(helper);
        int address = pair[0].getAddress();
        pair[1].configure(address, -1);
        helper.assertTrue(!pair[1].hasValidAddress(), "duplicate address is invalid");
        var registry = TelepadRegistry.get(helper.getLevel().getServer());
        helper.assertTrue(registry.get(address).location().pos().equals(pair[0].getBlockPos()), "duplicate cannot steal existing address");
        pair[1].unregister();
        helper.assertTrue(registry.get(address) != null, "removing a duplicate cannot unregister the owner");
        var restored = TelepadRegistry.load(registry.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.get(address).equals(registry.get(address)), "address ownership survives saved-data reload");
        var saved = pair[0].saveWithoutMetadata(helper.getLevel().registryAccess());
        var reloaded = new ShortRangeTelepadBlockEntity(pair[0].getBlockPos(), pair[0].getBlockState());
        reloaded.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.getAddress() == address && reloaded.getDestination() == pair[0].getDestination(), "pad addresses survive reload");
        pair[0].configure(-1, -1);
        pair[1].configure(address, -1);
        helper.assertTrue(pair[1].hasValidAddress(), "released address can be claimed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enforcesSameDimensionStrictRangeAndTargetEnabledState(GameTestHelper helper) {
        var pair = pair(helper);
        var source = pair[0];
        var registry = TelepadRegistry.get(helper.getLevel().getServer());
        int address = address(helper);
        GlobalPos target = GlobalPos.of(Level.NETHER, source.getBlockPos());
        registry.claim(address, target, true);
        source.configure(source.getAddress(), address);
        helper.assertTrue(source.getTargetResult() == TargetResult.WRONG_DIMENSION, "cross-dimension teleporting is rejected");
        registry.remove(address, target);
        target = GlobalPos.of(helper.getLevel().dimension(), source.getBlockPos().east(256));
        registry.claim(address, target, true);
        source.configure(source.getAddress(), address);
        helper.assertTrue(source.getTargetResult() == TargetResult.TOO_FAR, "exactly 256 blocks is outside GC4 range");
        registry.remove(address, target);
        target = GlobalPos.of(helper.getLevel().dimension(), source.getBlockPos().east(255));
        registry.claim(address, target, true);
        source.configure(source.getAddress(), address);
        helper.assertTrue(source.getTargetResult() == TargetResult.VALID, "255 blocks is within range");
        source.configure(source.getAddress(), pair[1].getAddress());
        pair[1].toggleDisabled();
        source.configure(source.getAddress(), pair[1].getAddress());
        helper.assertTrue(source.getTargetResult() == TargetResult.DISABLED, "disabled targets cannot receive");
        source.configure(source.getAddress(), source.getAddress());
        helper.assertTrue(source.getTargetResult() == TargetResult.SELF, "pad cannot target itself");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void teleportsLivingEntitiesAfter150TicksAndDebitsEachBuffer(GameTestHelper helper) {
        var pair = pair(helper);
        var first = pig(helper, pair[0]);
        var second = pig(helper, pair[0]);
        var item = new ItemEntity(helper.getLevel(), first.getX(), first.getY(), first.getZ(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        helper.getLevel().addFreshEntity(item);
        first.fallDistance = 10;
        tick(helper, pair[0], 153, true);
        helper.assertTrue(pair[0].getTeleportTime() == 149 && first.getX() < pair[1].getBlockPos().getX(), "cannot teleport before 150 charging ticks");
        tick(helper, pair[0], 1, true);
        helper.assertTrue(Math.abs(first.getX() - (pair[1].getBlockPos().getX() + 0.5)) < 0.001 && first.position().equals(second.position()), "all living occupants arrive together");
        helper.assertTrue(first.fallDistance == 0 && first.getYRot() == 37, "arrival clears fall damage and preserves facing");
        helper.assertTrue(item.getX() < pair[1].getBlockPos().getX(), "dropped items are not living occupants");
        helper.assertTrue(pair[0].energyStorage().getAmount() == pair[0].energyStorage().getCapacity() - 2550, "source pays teleport plus enabled tick cost");
        helper.assertTrue(pair[1].energyStorage().getAmount() == 6500, "destination pays from its own buffer");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void interruptedChargingDecaysAndDisabledPadsStopConsumingPower(GameTestHelper helper) {
        var pair = pair(helper);
        var pig = pig(helper, pair[0]);
        tick(helper, pair[0], 24, true);
        helper.assertTrue(pair[0].getTeleportTime() == 20, "charge starts on fifth tick");
        pig.moveTo(pair[0].getBlockPos().getX() + 2, pig.getY(), pig.getZ());
        tick(helper, pair[0], 3, true);
        helper.assertTrue(pair[0].getTeleportTime() == 17, "leaving central column decays one tick at a time");
        pair[0].toggleDisabled();
        long energy = pair[0].energyStorage().getAmount();
        tick(helper, pair[0], 1, false);
        helper.assertTrue(pair[0].getTeleportTime() == 16 && pair[0].energyStorage().getAmount() == energy, "disabled pad decays without passive drain");
        pair[0].configure(pair[0].getAddress(), -1);
        helper.assertTrue(pair[0].getTeleportTime() == 0, "changing route resets charge");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destinationNeedsItsOwnEnergy(GameTestHelper helper) {
        var pair = pair(helper);
        pair[1].energyStorage().setEnergy(2499);
        var pig = pig(helper, pair[0]);
        tick(helper, pair[0], 154, true);
        helper.assertTrue(pair[0].getTargetResult() == TargetResult.NO_ENERGY, "insufficient target energy is reported");
        helper.assertTrue(pig.getX() < pair[1].getBlockPos().getX(), "occupant stays at source");
        helper.assertTrue(pair[1].energyStorage().getAmount() == 2499 && pair[0].energyStorage().getAmount() == pair[0].energyStorage().getCapacity() - 50, "failed transfer charges no teleport energy");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockedArrivalRejectsTeleportWithoutCharging(GameTestHelper helper) {
        var pair = pair(helper);
        var pig = pig(helper, pair[0]);
        helper.setBlock(TARGET.above(), Blocks.GLASS);
        tick(helper, pair[0], 154, true);
        helper.assertTrue(pair[0].getTargetResult() == TargetResult.BLOCKED && pig.getX() < pair[1].getBlockPos().getX(), "solid landing obstruction prevents teleport");
        helper.assertTrue(pair[1].energyStorage().getAmount() == 9000, "blocked transfer leaves target energy untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destinationIsRecheckedAtCompletion(GameTestHelper helper) {
        var pair = pair(helper);
        var pig = pig(helper, pair[0]);
        tick(helper, pair[0], 153, true);
        pair[1].toggleDisabled();
        tick(helper, pair[0], 1, true);
        helper.assertTrue(pair[0].getTargetResult() == TargetResult.DISABLED && pig.getX() < pair[1].getBlockPos().getX(), "disabled target is rejected even when cached route was valid");
        helper.assertTrue(pair[1].energyStorage().getAmount() == 9000, "rejected destination pays no transfer cost");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void topCenterAcceptsEnergyIntoMasterBuffer(GameTestHelper helper) {
        var pad = pad(helper, SOURCE);
        tick(helper, pad, 1, false);
        var input = EnergyStorage.SIDED.find(helper.getLevel(), pad.getBlockPos().above(2), Direction.UP);
        helper.assertTrue(input != null, "upper center exposes native energy input");
        try (var transaction = Transaction.openOuter()) {
            helper.assertTrue(input.insert(120, transaction) == 120, "power reaches master buffer");
            helper.assertTrue(input.extract(120, transaction) == 0, "input cannot extract power");
            transaction.commit();
        }
        helper.assertTrue(pad.energyStorage().getAmount() == 120, "frame forwards energy to master");
        helper.assertTrue(EnergyStorage.SIDED.find(helper.getLevel(), pad.getBlockPos().above(2), Direction.NORTH) == null, "upper connector only accepts from above");
        helper.assertTrue(EnergyStorage.SIDED.find(helper.getLevel(), pad.getBlockPos().above(2).east(), Direction.UP) == null, "decorative frame has no power storage");
        var face = pad.getIOConfig().get(dev.galacticraft.machinelib.api.util.BlockFace.TOP);
        face.setOption(dev.galacticraft.machinelib.api.transfer.ResourceType.NONE, dev.galacticraft.machinelib.api.transfer.ResourceFlow.BOTH);
        try (var transaction = Transaction.openOuter()) {
            helper.assertTrue(!input.supportsInsertion() && input.insert(120, transaction) == 0, "cached connector respects disabled top input");
            transaction.commit();
        }
        face.setOption(dev.galacticraft.machinelib.api.transfer.ResourceType.ENERGY, dev.galacticraft.machinelib.api.transfer.ResourceFlow.INPUT);
        helper.assertTrue(input.supportsInsertion(), "cached connector resumes after input reconfiguration");
        helper.getLevel().destroyBlock(pad.getBlockPos(), false);
        try (var transaction = Transaction.openOuter()) {
            helper.assertTrue(!input.supportsInsertion() && input.insert(120, transaction) == 0, "cached connector cannot power a removed pad");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reservesFullFrameAndBreakingAnyPartRemovesIt(GameTestHelper helper) {
        var pos = helper.absolutePos(SOURCE);
        helper.assertTrue(ShortRangeTelepadBlock.hasRoom(helper.getLevel(), pos), "empty volume can fit frame");
        helper.setBlock(SOURCE.above(), Blocks.GLASS);
        helper.assertTrue(!ShortRangeTelepadBlock.hasRoom(helper.getLevel(), pos), "obstructed center prevents placement");
        helper.setBlock(SOURCE.above(), Blocks.AIR);
        var pad = pad(helper, SOURCE);
        int address = address(helper);
        pad.configure(address, -1);
        int count = 0;
        for (int y : new int[]{0, 2}) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (helper.getLevel().getBlockState(pos.offset(x, y, z)).is(GCBlocks.TELEPAD_FRAME)) count++;
        helper.assertTrue(count == 17, "frame has 17 linked parts");
        helper.getLevel().destroyBlock(pos.offset(1, 2, 1), false);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "breaking upper corner removes master");
        for (int y : new int[]{0, 2}) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            helper.assertTrue(helper.getLevel().getBlockState(pos.offset(x, y, z)).isAir(), "no orphaned frame remains");
        helper.assertTrue(TelepadRegistry.get(helper.getLevel().getServer()).get(address) == null, "broken pad releases address");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), item -> item.getItem().is(GCBlocks.SHORT_RANGE_TELEPAD.asItem()));
        helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "whole structure drops exactly one pad");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addressEditsRequireOwnerLiveMenuAndValidBounds(GameTestHelper helper) {
        var pad = pad(helper, SOURCE);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.moveTo(pad.getBlockPos().getCenter());
        pad.getSecurity().tryUpdate(owner.getUUID());
        var menu = new ShortRangeTelepadMenu(1, owner, pad);
        int address = address(helper);
        menu.configure(owner, false, address);
        helper.assertTrue(pad.getAddress() == address, "owner can edit nearby pad");
        var visitor = helper.makeMockServerPlayerInLevel();
        visitor.moveTo(pad.getBlockPos().getCenter());
        menu.configure(visitor, false, address + 1);
        menu.configure(owner, false, 1_000_000);
        menu.configure(owner, true, -2);
        helper.assertTrue(pad.getAddress() == address && pad.getDestination() == -1, "visitor and invalid addresses cannot edit");
        owner.moveTo(pad.getBlockPos().getCenter().add(20, 0, 0));
        menu.configure(owner, false, address + 1);
        helper.assertTrue(pad.getAddress() == address, "distant menu cannot edit");
        owner.moveTo(pad.getBlockPos().getCenter());
        helper.setBlock(SOURCE, Blocks.AIR);
        menu.configure(owner, false, address + 1);
        helper.assertTrue(pad.getAddress() == address, "removed pad cannot be edited through stale menu");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadAndDisabledStateRoundTrip(GameTestHelper helper) {
        var pad = pad(helper, SOURCE);
        pad.configure(address(helper), 999999);
        pad.toggleDisabled();
        pad.energyStorage().setEnergy(1000);
        var saved = pad.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = new ShortRangeTelepadBlockEntity(pad.getBlockPos(), pad.getBlockState());
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.isManuallyDisabled() && restored.getDestination() == 999999 && restored.energyStorage().getAmount() == 1000, "disable flag, destination and energy survive save");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var payload = new TelepadAddressPayload(23, true, -1);
            TelepadAddressPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(TelepadAddressPayload.STREAM_CODEC.decode(buffer)), "clear-address payload round trips");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void redstoneStopsChargingAndPassiveDrain(GameTestHelper helper) {
        var pair = pair(helper);
        pig(helper, pair[0]);
        tick(helper, pair[0], 24, true);
        pair[0].setRedstoneMode(RedstoneMode.HIGH);
        long energy = pair[0].energyStorage().getAmount();
        tick(helper, pair[0], 1, false);
        helper.assertTrue(pair[0].getTeleportTime() == 19 && pair[0].energyStorage().getAmount() == energy, "unpowered redstone mode stops charging and drain");
        helper.setBlock(SOURCE.below(), Blocks.REDSTONE_BLOCK);
        tick(helper, pair[0], 10, true);
        helper.assertTrue(pair[0].getTeleportTime() > 19, "redstone signal resumes charging");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void staleLoadedOwnerDoesNotPermanentlyReserveAddress(GameTestHelper helper) {
        var pad = pad(helper, SOURCE);
        int address = address(helper);
        var stale = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TARGET));
        TelepadRegistry.get(helper.getLevel().getServer()).claim(address, stale, true);
        pad.configure(address, -1);
        helper.assertTrue(pad.hasValidAddress(), "loaded missing owner releases stale reservation");
        helper.assertTrue(TelepadRegistry.get(helper.getLevel().getServer()).get(address).location().pos().equals(pad.getBlockPos()), "new owner claims cleaned address");
        helper.succeed();
    }
}
