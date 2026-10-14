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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.block.entity.DungeonSpawnerBlockEntity;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.structure.GCStructurePieceTypes;
import dev.galacticraft.mod.structure.dungeon.DungeonConfiguration;
import dev.galacticraft.mod.structure.dungeon.RoomTreasure;
import dev.galacticraft.mod.structure.dungeon.RoomBoss;
import dev.galacticraft.mod.structure.dungeon.RoomEmpty;
import dev.galacticraft.mod.structure.dungeon.MarsRoomBoss;
import dev.galacticraft.mod.structure.dungeon.VenusRoomBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class ComplexityRefactorTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void emptyRoomsRespectConfiguredDoorwayHeightAndGenerationClipping(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var level = helper.getLevel();
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 8, origin.getY() + 6, origin.getZ() + 8);
        for (Direction entrance : Direction.Plane.HORIZONTAL) for (int height = 2; height <= 5; height++) {
            var config = new DungeonConfiguration(Blocks.STONE_BRICKS.defaultBlockState(), origin.getY(), 1, 2, height, 6,
                    GCStructurePieceTypes.ROOM_BOSS, GCStructurePieceTypes.ROOM_TREASURE);
            var room = new RoomEmpty(config, RandomSource.create(42), origin.getX(), origin.getZ(), 8, 6, 8, entrance, 0);
            BlockPos doorway = origin.offset(4, 0, 4).relative(entrance, 4);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(42), box, new ChunkPos(origin), origin);
            for (int y = 1; y < height; y++) {
                helper.assertTrue(level.getBlockState(doorway.above(y)).isAir(), "Configured doorway was sealed: " + entrance + ", height " + height);
            }
            helper.assertTrue(level.getBlockState(doorway).is(Blocks.STONE_BRICKS)
                    && level.getBlockState(doorway.above(height)).is(Blocks.STONE_BRICKS), "Doorway removed its floor or header");
            helper.assertTrue(level.getBlockState(origin.offset(1, 1, 1)).isAir(), "Empty room interior was filled");
            level.setBlock(doorway.above(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            BoundingBox clipped = new BoundingBox(origin.getX() + 2, origin.getY(), origin.getZ() + 2, origin.getX() + 6, origin.getY() + 6, origin.getZ() + 6);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(42), clipped, new ChunkPos(origin), origin);
            helper.assertTrue(level.getBlockState(doorway.above()).is(Blocks.DIAMOND_BLOCK), "Room generation changed blocks outside the chunk box");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchPadFormsWithEveryPossibleLastBlock(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        for (int lastX = -1; lastX <= 1; lastX++) for (int lastZ = -1; lastZ <= 1; lastZ++) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                helper.setBlock(center.offset(x, 0, z), Blocks.AIR);
                helper.setBlock(center.offset(x, -1, z), Blocks.STONE);
            }
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (x != lastX || z != lastZ) helper.setBlock(center.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD);
            }
            helper.assertTrue(helper.getBlockState(center.offset(lastX, 0, lastZ)).isAir(), "Missing pad piece was filled prematurely");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (x == lastX && z == lastZ) continue;
                helper.assertTrue(helper.getBlockState(center.offset(x, 0, z)).getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.NONE,
                        "Incomplete pad assembled prematurely");
            }
            helper.setBlock(center.offset(lastX, 0, lastZ), GCBlocks.ROCKET_LAUNCH_PAD);
            helper.assertTrue(helper.getBlockState(center).getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.CENTER,
                    "Pad failed when last piece was " + lastX + ", " + lastZ);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                helper.assertTrue(helper.getBlockState(center.offset(x, 0, z)).getValue(AbstractLaunchPad.PART) != AbstractLaunchPad.Part.NONE,
                        "Assembled pad left an unassigned piece");
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void treasureRoomKeepsEntrancesLightingAndTieredLoot(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var level = helper.getLevel();
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 8, origin.getY() + 6, origin.getZ() + 8);
        for (Direction entrance : Direction.Plane.HORIZONTAL) for (int tier = 1; tier <= 3; tier++) {
            var config = new DungeonConfiguration(Blocks.STONE_BRICKS.defaultBlockState(), origin.getY(), 1, 2, 3, 6,
                    GCStructurePieceTypes.ROOM_BOSS, GCStructurePieceTypes.ROOM_TREASURE, tier);
            var room = new RoomTreasure(config, RandomSource.create(42), origin.getX(), origin.getZ(), 8, 6, 8, entrance, 0);
            RandomSource lootRandom = RandomSource.create(42);
            room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), lootRandom, box, new ChunkPos(origin), origin);
            BlockPos doorway = origin.offset(4, 1, 4).relative(entrance, 4);
            helper.assertTrue(level.getBlockState(doorway).isAir() && level.getBlockState(doorway.above()).isAir(), "Treasure entrance was sealed");
            helper.assertTrue(level.getBlockState(doorway.above(2)).is(Blocks.STONE_BRICKS), "Entrance cut through the wall above the doorway");
            helper.assertTrue(level.getBlockState(origin.offset(1, 1, 1)).is(Blocks.GLOWSTONE), "Treasure room lost its corner lighting");
            BlockPos chestPos = origin.offset(4, 1, 4);
            var chestState = level.getBlockState(chestPos);
            helper.assertTrue(chestState.is(RoomTreasure.treasureChestFor(tier)) && chestState.getValue(ChestBlock.FACING) == entrance,
                    "Treasure chest tier or facing changed");
            var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            var saved = chest.saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(saved.getString("LootTable").equals(GCLootTables.dungeonChest(tier).location().toString()), "Treasure chest has the wrong loot table");
            helper.assertTrue(saved.getLong("LootTableSeed") == RandomSource.create(42).nextLong(), "Treasure generation changed the loot seed");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bossRoomKeepsEntrancesBarsLavaAndPlanetSpawner(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var level = helper.getLevel();
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 8, origin.getY() + 6, origin.getZ() + 8);
        var config = new DungeonConfiguration(Blocks.STONE_BRICKS.defaultBlockState(), origin.getY(), 1, 2, 3, 6,
                GCStructurePieceTypes.ROOM_BOSS, GCStructurePieceTypes.ROOM_TREASURE, 1);
        var bossTypes = new net.minecraft.world.entity.EntityType<?>[]{GCEntityTypes.SKELETON_BOSS, GCEntityTypes.CREEPER_BOSS, GCEntityTypes.SPIDER_BOSS};
        for (Direction entrance : Direction.Plane.HORIZONTAL) {
            RoomBoss[] rooms = {
                    new RoomBoss(config, RandomSource.create(42), origin.getX(), origin.getZ(), 8, 6, 8, entrance, 0),
                    new MarsRoomBoss(config, RandomSource.create(42), origin.getX(), origin.getZ(), 8, 6, 8, entrance, 0),
                    new VenusRoomBoss(config, RandomSource.create(42), origin.getX(), origin.getZ(), 8, 6, 8, entrance, 0)
            };
            for (int planet = 0; planet < rooms.length; planet++) {
                var room = rooms[planet];
                BlockPos chestPos = origin.offset(12, 1, 12);
                room.setChestPos(chestPos);
                room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(42), box, new ChunkPos(origin), origin);
                BlockPos doorway = origin.offset(4, 1, 4).relative(entrance, 4);
                helper.assertTrue(level.getBlockState(doorway).isAir() && level.getBlockState(doorway.above()).isAir(), "Boss entrance was sealed");
                helper.assertTrue(level.getBlockState(doorway.above(2)).is(Blocks.STONE_BRICKS), "Boss doorway cut through the wall above it");
                for (int x : new int[]{1, 7}) for (int z : new int[]{1, 7}) {
                    helper.assertTrue(level.getBlockState(origin.offset(x, 3, z)).is(Blocks.LAVA), "Bars replaced a lava column");
                    helper.assertTrue(level.getBlockState(origin.offset(x, 6, z)).is(Blocks.STONE_BRICKS), "Lava broke the roof");
                }
                helper.assertTrue(level.getBlockState(origin.offset(1, 3, 4)).is(Blocks.IRON_BARS), "Horizontal bars missing");
                helper.assertTrue(level.getBlockState(origin.offset(2, 3, 2)).is(Blocks.IRON_BARS), "Inset corner bars missing");
                helper.assertTrue(level.getBlockState(origin.offset(1, 2, 2)).is(Blocks.IRON_BARS), "Vertical bars missing");
                helper.assertTrue(level.getBlockState(origin.offset(2, 2, 2)).isAir(), "Bars filled an open corner");
                var spawner = (DungeonSpawnerBlockEntity) level.getBlockEntity(origin.offset(4, 1, 4));
                var saved = spawner.saveWithoutMetadata(level.registryAccess());
                helper.assertTrue(saved.getString("bossType").equals(BuiltInRegistries.ENTITY_TYPE.getKey(bossTypes[planet]).toString()), "Wrong planet boss");
                helper.assertTrue(spawner.getChestPos().equals(chestPos), "Spawner lost its reward chest position");
                helper.assertTrue(saved.getInt("roomCoordsX") == origin.getX() + 1 && saved.getInt("roomCoordsY") == origin.getY() + 1
                        && saved.getInt("roomCoordsZ") == origin.getZ() + 1 && saved.getInt("roomSizeX") == 7
                        && saved.getInt("roomSizeY") == 5 && saved.getInt("roomSizeZ") == 7, "Spawner room bounds changed");
            }
        }
        helper.succeed();
    }
}
