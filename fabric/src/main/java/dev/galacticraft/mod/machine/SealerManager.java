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

package dev.galacticraft.mod.machine;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity.SEAL_CHECK_TIME;

public class SealerManager {

    private static class SpaceToSeal {

        private final List<OxygenSealerBlockEntity> sealers = new ArrayList<>();
        private final Set<BlockPos> blocksToSeal = new HashSet<>();
        private final Deque<BlockPos> floodFillQueue = new ArrayDeque<>();

        public SpaceToSeal(OxygenSealerBlockEntity sealer) {
            sealers.add(sealer);
            floodFillQueue.add(sealer.getBlockPos().offset(0, 1, 0));
        }

        public boolean willSealSucceed() {
            return floodFillQueue.isEmpty();
        }

    }

    private final Level level;
    private final Map<BlockPos, OxygenSealerBlockEntity> sealers = new HashMap<>();
    private final Set<BlockPos> sealedBlocks = new HashSet<>();
    private boolean sealUpdateQueued = false;

    public SealerManager(Level level) {
        this.level = level;
    }

    private static final double MAX_SEALER_VOLUME = 1024;

    public void tick() {
        // Update sealing status periodically
        if (this.sealUpdateQueued || this.level.getGameTime() % SEAL_CHECK_TIME == 0) {
            updateSealedBlocks();
        }
    }

    public void updateSealedBlocks() {
        if (level.isClientSide) return;

        // If the level is breathable oxygen sealers don't change anything
        if (level.getDefaultBreathable()) return;

        if (level.getServer() != null && !level.getServer().isReady()) {
            Constant.LOGGER.info("World is not fully loaded, skipping sealing calculation");
            return;
        }

        this.sealUpdateQueued = false;
        this.clearSealedBlocks();

        Set<SpaceToSeal> spacesToSeal = new HashSet<>();
        for (Map.Entry<BlockPos, OxygenSealerBlockEntity> entry : this.sealers.entrySet()) {
            OxygenSealerBlockEntity sealer = entry.getValue();
            if (!sealer.hasEnergy()) continue;
            if (!sealer.hasOxygen()) continue;
            if (sealer.isBlocked()) continue;

            // Flood fill to find all blocks this sealer is trying to seal
            SpaceToSeal spaceToSeal = new SpaceToSeal(sealer);
            while (!spaceToSeal.floodFillQueue.isEmpty()) {
                BlockPos pos = spaceToSeal.floodFillQueue.pollFirst();
                if (spaceToSeal.blocksToSeal.contains(pos)) continue;
                BlockState blockState = this.level.getBlockState(pos);
                if (this.isFullySealedBlock(blockState, pos)) continue;

                spaceToSeal.blocksToSeal.add(pos);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = pos.relative(direction);
                    if (!this.isSealedBetween(pos, blockState, adjacent, this.level.getBlockState(adjacent), direction)) {
                        spaceToSeal.floodFillQueue.add(adjacent);
                    }
                }

                boolean willAlreadySeal = false;
                for (SpaceToSeal otherSpace : spacesToSeal) {
                    if (spaceToSeal == otherSpace) continue;
                    if (!otherSpace.blocksToSeal.contains(pos)) continue;
                    // We have encountered a block that another sealer is trying to seal,
                    // so both sealers must be within the same space, so we combine them.
                    spaceToSeal.sealers.addAll(otherSpace.sealers);
                    spaceToSeal.blocksToSeal.addAll(otherSpace.blocksToSeal);
                    spaceToSeal.floodFillQueue.addAll(otherSpace.floodFillQueue);
                    willAlreadySeal = otherSpace.willSealSucceed();
                    spacesToSeal.remove(otherSpace);
                    break;
                }
                // If we just combined with a space that was already filled,
                // we must be in the same space, so we don't need to continue flood fill
                if (willAlreadySeal) break;

                // If the space has become too large to fill, stop performing flood fill
                if (spaceToSeal.blocksToSeal.size() > spaceToSeal.sealers.size() * MAX_SEALER_VOLUME) break;
            }
            spacesToSeal.add(spaceToSeal);
        }

        for (SpaceToSeal spaceToSeal : spacesToSeal) {
            for (OxygenSealerBlockEntity sealer : spaceToSeal.sealers) sealer.setSealed(spaceToSeal.willSealSucceed());
            if (!spaceToSeal.willSealSucceed()) continue;
            for (BlockPos pos : spaceToSeal.blocksToSeal) {
                sealedBlocks.add(pos);
                level.setBreathable(pos, true);
            }
        }
    }

    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.level.isClientSide || this.level.getDefaultBreathable() || this.sealers.isEmpty()) return;
        if (oldState.equals(newState)) return;

        this.sealUpdateQueued = true;
        this.clearSealedBlocks();
    }

    private void clearSealedBlocks() {
        for (BlockPos pos : this.sealedBlocks) this.level.setBreathable(pos, false);
        this.sealedBlocks.clear();
        for (OxygenSealerBlockEntity sealer : this.sealers.values()) {
            sealer.setSealed(false);
        }
    }

    private boolean isFullySealedBlock(BlockState state, BlockPos pos) {
        if (state.is(GCBlockTags.UNSEALABLE)) return false;
        if (state.is(GCBlockTags.SEALABLE)) return true;

        for (Direction direction : Direction.values()) {
            if (!state.isFaceSturdy(this.level, pos, direction)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSealedBetween(BlockPos pos, BlockState state, BlockPos adjacent, BlockState adjacentState, Direction direction) {
        return this.isSealedFace(state, pos, direction) || this.isSealedFace(adjacentState, adjacent, direction.getOpposite());
    }

    private boolean isSealedFace(BlockState state, BlockPos pos, Direction direction) {
        if (state.is(GCBlockTags.UNSEALABLE)) return false;
        return state.is(GCBlockTags.SEALABLE) || state.isFaceSturdy(this.level, pos, direction);
    }

    public void addSealer(OxygenSealerBlockEntity sealer) {
        BlockPos pos = sealer.getBlockPos();
        Constant.LOGGER.info("Adding sealer at {} in dimension {}", pos, level.dimension().location());
        this.sealers.put(pos, sealer);
    }

    public void removeSealer(OxygenSealerBlockEntity sealer) {
        BlockPos pos = sealer.getBlockPos();
        Constant.LOGGER.info("Removing sealer at {} in dimension {}", pos, level.dimension().location());
        this.sealers.remove(pos);
    }

}
