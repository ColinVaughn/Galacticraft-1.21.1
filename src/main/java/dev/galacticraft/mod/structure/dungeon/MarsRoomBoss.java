/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.structure.dungeon;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.boss.AbstractBossEntity;
import dev.galacticraft.mod.structure.GCStructurePieceTypes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * The Mars dungeon boss room. Identical to {@link RoomBoss} except that it
 * spawns the Mars {@link dev.galacticraft.mod.content.entity.boss.CreeperBoss}.
 */
public class MarsRoomBoss extends RoomBoss {
    public MarsRoomBoss(CompoundTag tag) {
        super(GCStructurePieceTypes.MARS_ROOM_BOSS, tag);
    }

    public MarsRoomBoss(DungeonConfiguration configuration, RandomSource rand, int blockPosX, int blockPosZ, Direction entranceDir, int genDepth) {
        super(GCStructurePieceTypes.MARS_ROOM_BOSS, configuration, rand, blockPosX, blockPosZ, entranceDir, genDepth);
    }

    public MarsRoomBoss(DungeonConfiguration configuration, RandomSource rand, int blockPosX, int blockPosZ, int sizeX, int sizeY, int sizeZ, Direction entranceDir, int genDepth) {
        super(GCStructurePieceTypes.MARS_ROOM_BOSS, configuration, rand, blockPosX, blockPosZ, sizeX, sizeY, sizeZ, entranceDir, genDepth);
    }

    @Override
    protected EntityType<? extends AbstractBossEntity> getBossType() {
        return GCEntityTypes.CREEPER_BOSS;
    }
}
