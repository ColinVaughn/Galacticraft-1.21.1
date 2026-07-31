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

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import dev.galacticraft.mod.network.s2c.SensorGlassesLeakPayload;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.tag.GCItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity.SEAL_CHECK_TIME;

public class SealerManager {

    private static class SpaceToSeal {

        private final List<OxygenSealerBlockEntity> sealers = new ArrayList<>();
        private final Set<BlockPos> blocksToSeal = new HashSet<>();
        private final Deque<BlockPos> floodFillQueue = new ArrayDeque<>();
        private final Map<BlockPos, BlockPos> routeParents = new HashMap<>();
        private BlockPos leakEnd;
        /**
         * Whether the flood fill found a boundary in every direction within the sealers' volume budget.
         * An empty queue is not a substitute for this: a fill that stops early because it has joined a
         * space that is already known to be enclosed still has queued positions, and every one of them
         * lies inside that space.
         */
        private boolean enclosed = false;

        public SpaceToSeal(OxygenSealerBlockEntity sealer) {
            sealers.add(sealer);
            BlockPos start = sealer.getBlockPos().offset(0, 1, 0);
            floodFillQueue.add(start);
            routeParents.put(start, null);
        }

        public boolean willSealSucceed() {
            return enclosed;
        }

        public List<BlockPos> leakTrace() {
            if (this.leakEnd == null) return List.of();
            LinkedList<BlockPos> trace = new LinkedList<>();
            BlockPos cursor = this.leakEnd;
            while (cursor != null) {
                trace.addFirst(cursor);
                cursor = this.routeParents.get(cursor);
            }
            // Legacy only followed 90 parent links. Keep the packet and particle count bounded the same way.
            return trace.size() > 91 ? List.copyOf(trace.subList(0, 91)) : List.copyOf(trace);
        }

    }

    private final Level level;
    private final Map<BlockPos, OxygenSealerBlockEntity> sealers = new HashMap<>();
    private final Map<BlockPos, SealerState> sealerStates = new HashMap<>();
    private final Set<BlockPos> sealedBlocks = new HashSet<>();
    private final Set<BlockPos> observedBlocks = new HashSet<>();
    private boolean sealUpdateQueued = false;
    private boolean hasUnsealedActiveSealers = false;
    private boolean changingBreathability = false;

    public SealerManager(Level level) {
        this.level = level;
    }

    private static final double MAX_SEALER_VOLUME = 1024;

    public void tick() {
        for (Map.Entry<BlockPos, OxygenSealerBlockEntity> entry : this.sealers.entrySet()) {
            SealerState state = this.sealerStates.get(entry.getKey());
            if (state == null) {
                state = new SealerState();
                this.sealerStates.put(entry.getKey(), state);
            }
            if (state.update(entry.getValue())) {
                this.sealUpdateQueued = true;
            }
        }

        // Successful spaces are event-driven. Open spaces retry periodically so a newly
        // completed boundary outside the previous bounded flood fill is still discovered.
        if (this.sealUpdateQueued
                || (this.hasUnsealedActiveSealers && this.level.getGameTime() % SEAL_CHECK_TIME == 0)) {
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
        Set<SpaceToSeal> spacesToSeal = new HashSet<>();
        Set<BlockPos> nextObservedBlocks = new HashSet<>();
        Set<OxygenSealerBlockEntity> activeSealers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<BlockPos, OxygenSealerBlockEntity> entry : this.sealers.entrySet()) {
            OxygenSealerBlockEntity sealer = entry.getValue();
            if (!sealer.canSeal()) continue;
            activeSealers.add(sealer);

            // Flood fill to find all blocks this sealer is trying to seal
            SpaceToSeal spaceToSeal = new SpaceToSeal(sealer);
            // Draining the queue means every direction ran into a boundary; only the volume budget
            // running out leaves the space genuinely open.
            boolean withinBudget = true;
            while (!spaceToSeal.floodFillQueue.isEmpty()) {
                BlockPos pos = spaceToSeal.floodFillQueue.pollFirst();
                if (spaceToSeal.blocksToSeal.contains(pos)) continue;
                nextObservedBlocks.add(pos);
                BlockState blockState = this.level.getBlockState(pos);
                if (this.isFullySealedBlock(blockState, pos)) continue;

                spaceToSeal.blocksToSeal.add(pos);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = pos.relative(direction);
                    nextObservedBlocks.add(adjacent);
                    BlockState adjacentState = this.level.getBlockState(adjacent);
                    if (!this.isSealedBetween(pos, blockState, adjacent, adjacentState, direction)) {
                        if (!spaceToSeal.routeParents.containsKey(adjacent)) {
                            spaceToSeal.routeParents.put(adjacent, pos);
                            spaceToSeal.floodFillQueue.add(adjacent);
                        }
                    }
                }

                boolean willAlreadySeal = false;
                for (Iterator<SpaceToSeal> iterator = spacesToSeal.iterator(); iterator.hasNext();) {
                    SpaceToSeal otherSpace = iterator.next();
                    if (spaceToSeal == otherSpace) continue;
                    if (!otherSpace.blocksToSeal.contains(pos)) continue;
                    // We have encountered a block that another sealer is trying to seal,
                    // so both sealers must be within the same space, so we combine them.
                    spaceToSeal.sealers.addAll(otherSpace.sealers);
                    spaceToSeal.blocksToSeal.addAll(otherSpace.blocksToSeal);
                    spaceToSeal.floodFillQueue.addAll(otherSpace.floodFillQueue);
                    otherSpace.routeParents.forEach((routePos, parent) -> {
                        if (!spaceToSeal.routeParents.containsKey(routePos)) {
                            spaceToSeal.routeParents.put(routePos, parent);
                        }
                    });
                    willAlreadySeal = otherSpace.willSealSucceed();
                    iterator.remove();
                    break;
                }
                // If we just combined with a space that was already filled,
                // we must be in the same space, so we don't need to continue flood fill
                if (willAlreadySeal) break;

                // If the space has become too large to fill, stop performing flood fill
                if (spaceToSeal.blocksToSeal.size() > spaceToSeal.sealers.size() * MAX_SEALER_VOLUME) {
                    withinBudget = false;
                    spaceToSeal.leakEnd = pos;
                    break;
                }
            }
            spaceToSeal.enclosed = withinBudget;
            spacesToSeal.add(spaceToSeal);
        }

        Set<BlockPos> nextSealedBlocks = new HashSet<>();
        Set<OxygenSealerBlockEntity> sealedSealers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SpaceToSeal spaceToSeal : spacesToSeal) {
            if (!spaceToSeal.willSealSucceed()) continue;
            sealedSealers.addAll(spaceToSeal.sealers);
            nextSealedBlocks.addAll(spaceToSeal.blocksToSeal);
        }

        for (OxygenSealerBlockEntity sealer : this.sealers.values()) {
            sealer.setSealed(sealedSealers.contains(sealer));
        }
        this.changingBreathability = true;
        try {
            for (BlockPos pos : this.sealedBlocks) {
                if (!nextSealedBlocks.contains(pos)) this.level.setBreathable(pos, false);
            }
            for (BlockPos pos : nextSealedBlocks) {
                if (!this.sealedBlocks.contains(pos)) this.level.setBreathable(pos, true);
            }
        } finally {
            this.changingBreathability = false;
        }

        this.sealedBlocks.clear();
        this.sealedBlocks.addAll(nextSealedBlocks);
        this.observedBlocks.clear();
        this.observedBlocks.addAll(nextObservedBlocks);
        this.hasUnsealedActiveSealers = sealedSealers.size() < activeSealers.size();
        this.syncLeakTraces(spacesToSeal);
    }

    private void syncLeakTraces(Set<SpaceToSeal> spaces) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        List<SpaceToSeal> failedSpaces = spaces.stream().filter(space -> !space.willSealSucceed()).toList();
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.getItemBySlot(EquipmentSlot.HEAD).is(GCItemTags.SENSOR_GLASSES)) continue;
            SpaceToSeal nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (SpaceToSeal space : failedSpaces) {
                for (OxygenSealerBlockEntity sealer : space.sealers) {
                    double distance = player.distanceToSqr(
                            sealer.getBlockPos().getX() + 0.5D,
                            sealer.getBlockPos().getY() + 0.5D,
                            sealer.getBlockPos().getZ() + 0.5D);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = space;
                    }
                }
            }
            NetworkManager.sendToPlayer(player, new SensorGlassesLeakPayload(
                    nearest == null ? List.of() : nearest.leakTrace()));
        }
    }

    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.level.isClientSide || this.level.getDefaultBreathable() || this.sealers.isEmpty()) return;
        if (oldState.equals(newState)) return;

        if (this.observedBlocks.isEmpty() || this.observedBlocks.contains(pos)) {
            this.sealUpdateQueued = true;
            if (!this.changingBreathability) {
                this.clearSealedBlocks();
            }
        }
    }

    private void clearSealedBlocks() {
        this.changingBreathability = true;
        try {
            for (BlockPos pos : this.sealedBlocks) this.level.setBreathable(pos, false);
            this.sealedBlocks.clear();
            for (OxygenSealerBlockEntity sealer : this.sealers.values()) {
                sealer.setSealed(false);
            }
        } finally {
            this.changingBreathability = false;
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
        this.sealerStates.remove(pos);
        this.sealUpdateQueued = true;
    }

    public void removeSealer(OxygenSealerBlockEntity sealer) {
        BlockPos pos = sealer.getBlockPos();
        Constant.LOGGER.info("Removing sealer at {} in dimension {}", pos, level.dimension().location());
        this.sealers.remove(pos);
        this.sealerStates.remove(pos);
        this.sealUpdateQueued = true;
    }

    /** Watches one sealer for the moment it starts or stops being able to hold a space. */
    private static final class SealerState {
        private boolean initialized;
        private boolean canSeal;

        private boolean update(OxygenSealerBlockEntity sealer) {
            boolean canSeal = sealer.canSeal();
            boolean changed = !this.initialized || this.canSeal != canSeal;
            this.initialized = true;
            this.canSeal = canSeal;
            return changed;
        }
    }

}
