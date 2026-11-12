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

package dev.galacticraft.test.addon;

import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.content.block.entity.machine.CompressorBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.ElectricArcFurnaceBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.ElectricCompressorBlockEntity;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.network.s2c.OpenCelestialScreenPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

final class ReportedIssuesCheck {
    static void check(MinecraftServer server) {
        var level = server.overworld();
        var access = server.registryAccess();
        var bodies = access.registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            for (var packet : List.of(new OpenCelestialScreenPayload(RocketPrefabs.TIER_1, null),
                    new OpenCelestialScreenPayload(RocketPrefabs.TIER_1, bodies.getHolderOrThrow(GCCelestialBodies.EARTH)))) {
                OpenCelestialScreenPayload.STREAM_CODEC.encode(buffer, packet);
                var decoded = OpenCelestialScreenPayload.STREAM_CODEC.decode(buffer);
                require(decoded.celestialBody() == packet.celestialBody() && decoded.data() != null && decoded.data().isValid()
                        && decoded.data().cone().orElseThrow().key().equals(packet.data().cone().orElseThrow().key())
                        && !buffer.isReadable(), "Celestial screen packet failed on NeoForge");
                buffer.clear();
            }
        } finally { buffer.release(); }
        require(RocketPrefabs.TIER_1.canTravel(access, null, bodies.getOrThrow(GCCelestialBodies.MOON))
                && !RocketPrefabs.TIER_1.canTravel(access, null, bodies.getOrThrow(GCCelestialBodies.MARS)),
                "Unknown departure dimension changed NeoForge rocket tier limits");

        var pos = new BlockPos(0, 150, 0);
        var pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (var set : GCBlocks.BLOCKS.getDecorations()) {
            for (var block : List.of(set.block(), set.stairs(), set.slab(), set.wall(),
                    set.detailedBlock(), set.detailedStairs(), set.detailedSlab(), set.detailedWall())) {
                var state = block.defaultBlockState();
                var name = BuiltInRegistries.BLOCK.getKey(block);
                require(pickaxe.isCorrectToolForDrops(state) && pickaxe.getDestroySpeed(state) > 1,
                        name + " cannot be mined with a pickaxe on NeoForge");
                var drops = Block.getDrops(state, level, pos, null, null, pickaxe);
                require(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                        name + " does not drop itself on NeoForge");
                if (block instanceof SlabBlock) {
                    drops = Block.getDrops(state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), level, pos, null);
                    require(drops.size() == 1 && drops.getFirst().getCount() == 2, name + " loses a double slab on NeoForge");
                }
            }
        }
        checkHoppers(level, pos, GCBlocks.ELECTRIC_ARC_FURNACE,
                ElectricArcFurnaceBlockEntity.INPUT_SLOT, ElectricArcFurnaceBlockEntity.OUTPUT_SLOTS);
        checkHoppers(level, pos, GCBlocks.COMPRESSOR, CompressorBlockEntity.INPUT_SLOTS, CompressorBlockEntity.OUTPUT_SLOT);
        checkHoppers(level, pos, GCBlocks.ELECTRIC_COMPRESSOR,
                ElectricCompressorBlockEntity.INPUT_SLOTS, ElectricCompressorBlockEntity.OUTPUT_SLOTS);
        var compressor = placeMachine(level, pos, GCBlocks.COMPRESSOR);
        ((HopperBlockEntity) level.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.COAL, 2));
        tickHoppers(level, pos);
        require(compressor.itemStorage().getItem(CompressorBlockEntity.FUEL_SLOT).getCount() == 2
                && ((HopperBlockEntity) level.getBlockEntity(pos.below())).isEmpty(),
                "NeoForge hopper fuel went missing or did not reach the compressor");
        Constant.LOGGER.info("NATIVE REPORTED ISSUES CHECK PASSED: rocket packet, decoration mining/drops, machine hoppers/fuel");
    }

    private static void checkHoppers(ServerLevel level, BlockPos pos, Block block, int inputSlot, int outputSlot) {
        var machine = placeMachine(level, pos, block);
        var top = Capabilities.ItemHandler.BLOCK.getCapability(level, pos, null, null, Direction.UP);
        var bottom = Capabilities.ItemHandler.BLOCK.getCapability(level, pos, null, null, Direction.DOWN);
        require(top != null && bottom != null, "NeoForge machine item capabilities are missing");
        machine.itemStorage().slot(outputSlot).set(Items.DIAMOND, 2);
        require(top.extractItem(outputSlot, 1, false).isEmpty()
                && !top.insertItem(outputSlot, new ItemStack(Items.DIAMOND), false).isEmpty(),
                "NeoForge input capability bypassed output slot restrictions");
        ((HopperBlockEntity) level.getBlockEntity(pos.above())).setItem(0, new ItemStack(Items.RAW_IRON, 4));
        tickHoppers(level, pos);
        var output = (HopperBlockEntity) level.getBlockEntity(pos.below());
        require(machine.itemStorage().getItem(inputSlot).getCount() == 4
                && bottom.extractItem(inputSlot, 1, false).isEmpty()
                && output.getItem(0).is(Items.DIAMOND) && output.getItem(0).getCount() == 2
                && machine.itemStorage().getItem(outputSlot).isEmpty(), "NeoForge hoppers failed for " + block);
    }

    private static MachineBlockEntity placeMachine(ServerLevel level, BlockPos pos, Block block) {
        for (var target : List.of(pos, pos.above(), pos.below())) level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(pos.below(), Blocks.HOPPER.defaultBlockState());
        var machine = (MachineBlockEntity) level.getBlockEntity(pos);
        machine.tickBase(level, pos, level.getBlockState(pos), level.getProfiler());
        return machine;
    }

    private static void tickHoppers(ServerLevel level, BlockPos pos) {
        for (int tick = 0; tick < 48; tick++) {
            for (var hopperPos : List.of(pos.above(), pos.below())) {
                HopperBlockEntity.pushItemsTick(level, hopperPos, level.getBlockState(hopperPos),
                        (HopperBlockEntity) level.getBlockEntity(hopperPos));
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}