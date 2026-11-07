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

package dev.galacticraft.mod.content.block.jupiter.encounter;

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class StormRelayBlockEntity extends BlockEntity implements JupiterAnimated {
    public static final int ZAP_RANGE = 6;
    public static final int ZAP_INTERVAL = 40;
    public static final float ZAP_DAMAGE = 4;
    /** Breaker uses needed to shut the relay down. */
    public static final int BREAKER_USES = 15;
    /** Ticks without a use before one point of breaker progress bleeds away. */
    public static final int RECHARGE_TICKS = 20;
    private @Nullable BlockPos pedestal;
    private int breaker, sinceUse;

    public StormRelayBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.STORM_RELAY, pos, state);
    }

    public StormRelayBlock.Mode mode() {
        return this.getBlockState().getValue(StormRelayBlock.MODE);
    }

    public int breakerProgress() {
        return this.breaker;
    }

    /** Puts the relay under a pedestal's control in {@code mode}; {@code pedestal} may be null when releasing it. */
    public void link(@Nullable BlockPos pedestal, StormRelayBlock.Mode mode) {
        this.pedestal = pedestal == null ? null : pedestal.immutable();
        this.breaker = 0;
        this.setChanged();
        if (this.level != null && this.mode() != mode) {
            this.level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(StormRelayBlock.MODE, mode));
        }
    }

    public void workBreaker(Player player) {
        if (!(this.level instanceof ServerLevel level)) return;
        this.sinceUse = 0;
        this.breaker++;
        level.playSound(null, this.worldPosition, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 0.8F, 0.6F + this.breaker * 0.05F);
        if (this.breaker >= BREAKER_USES) {
            this.link(this.pedestal, StormRelayBlock.Mode.DISABLED);
            level.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1, 1.4F);
            player.displayClientMessage(Component.translatable(JupiterText.RELAY_DISABLED), true);
            if (this.pedestal != null && level.getBlockEntity(this.pedestal) instanceof StormheartPedestalBlockEntity pedestal) pedestal.relayDisabled();
        } else {
            player.displayClientMessage(Component.translatable(JupiterText.RELAY_DISARMING, this.breaker * 100 / BREAKER_USES), true);
        }
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StormRelayBlockEntity relay) {
        if (state.getValue(StormRelayBlock.MODE) != StormRelayBlock.Mode.ENERGIZED || !(level instanceof ServerLevel server)) return;
        if (relay.breaker > 0 && ++relay.sinceUse >= RECHARGE_TICKS) {
            relay.sinceUse = 0;
            relay.breaker--;
        }
        long phase = level.getGameTime() + (pos.asLong() & 15);
        if (phase % 10 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.1);
        if (phase % ZAP_INTERVAL != 0) return;
        Vec3 top = Vec3.atBottomCenterOf(pos).add(0, 1.6, 0);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(ZAP_RANGE), p -> !p.isSpectator() && !p.isCreative() && p.distanceToSqr(top) <= ZAP_RANGE * ZAP_RANGE)) {
            Vec3 target = player.position().add(0, player.getBbHeight() * 0.5, 0);
            for (int i = 0; i <= 12; i++) {
                Vec3 point = top.lerp(target, i / 12.0);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 2, 0.05, 0.05, 0.05, 0);
            }
            player.hurt(server.damageSources().lightningBolt(), ZAP_DAMAGE);
            server.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6F, 1.8F);
        }
    }

    @Override
    public boolean isAnimating() {
        return this.mode() == StormRelayBlock.Mode.ENERGIZED;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.pedestal != null) tag.put("Pedestal", NbtUtils.writeBlockPos(this.pedestal));
        tag.putInt("Breaker", this.breaker);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.pedestal = NbtUtils.readBlockPos(tag, "Pedestal").orElse(null);
        this.breaker = tag.getInt("Breaker");
    }
}
