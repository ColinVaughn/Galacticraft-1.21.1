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

package dev.galacticraft.mod.api.block;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.wire.Wire;
import dev.galacticraft.mod.api.block.entity.PipeColor;
import dev.galacticraft.mod.content.block.entity.networked.WireBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

public abstract class WireBlock extends PipeShapedBlock<WireBlockEntity> {
    public final PipeColor color;

    public WireBlock(float radius, Properties settings) {
        this(radius, settings, PipeColor.CLEAR);
    }

    public WireBlock(float radius, Properties settings, PipeColor color) {
        super(radius, settings.pushReaction(PushReaction.BLOCK));
        this.color = color;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof WireBlockEntity wire)) return;
        if (wire.getNetwork() == null || wire.getNetwork().markedForRemoval())
            wire.createNetwork();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (this.updateConnection(state, pos, direction, neighborPos, level))
                this.onConnectionChanged(level, pos, direction, neighborPos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && Galacticraft.CONFIG.isDebugLogEnabled() && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof Wire wire) {
                Constant.LOGGER.info("Network: {}", wire.getNetwork());
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onConnectionChanged(Level level, BlockPos thisPos, Direction direction, BlockPos neighborPos) {
        if (level.getBlockEntity(thisPos) instanceof WireBlockEntity wire) {
            wire.updateConnection(level.getBlockState(thisPos), thisPos, neighborPos, direction);
        }
    }

    @Override
    public boolean canConnectTo(Level level, BlockPos thisPos, Direction direction, BlockPos neighborPos, BlockState thisState) {
        if (level.getBlockState(neighborPos).getBlock() instanceof WireBlock wire
                && this.color != PipeColor.GRAY && wire.color != PipeColor.GRAY
                && !this.color.canConnectTo(wire.color)) {
            return false;
        }
        return EnergyStorage.SIDED.find(level, neighborPos, direction.getOpposite()) != null;
    }

    @Nullable
    @Override
    public abstract WireBlockEntity newBlockEntity(BlockPos pos, BlockState state);
}
