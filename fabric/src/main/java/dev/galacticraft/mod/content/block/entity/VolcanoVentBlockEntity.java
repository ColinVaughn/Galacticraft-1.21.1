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

package dev.galacticraft.mod.content.block.entity;

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Runs the Venus volcano eruption cycle while its chunk is loaded. */
public class VolcanoVentBlockEntity extends BlockEntity {
    public static final int DORMANT = 0;
    public static final int RUMBLING = 1;
    public static final int ERUPTING = 2;
    public static final int COOLDOWN = 3;

    private static final double DAMAGE_RADIUS = 6.0;
    private static final double KNOCKBACK_RADIUS = 11.0;

    private int state = DORMANT;
    private int timer = -1;
    private int rimRadius = 4;
    private final List<BlockPos> overflow = new ArrayList<>();

    public VolcanoVentBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.VOLCANO_VENT, pos, state);
    }

    /** Called by the volcano feature so the vent knows how big its crater is. */
    public void configure(int rimRadius) {
        this.rimRadius = rimRadius;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VolcanoVentBlockEntity be) {
        be.tick((ServerLevel) level, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        RandomSource random = level.random;
        if (this.timer < 0) {
            this.timer = dormantDuration(random); // first load
        }

        if (--this.timer <= 0) {
            this.advanceState(level, pos, random);
        }

        switch (this.state) {
            case DORMANT -> {
                if (random.nextInt(40) == 0) {
                    smoke(level, pos, 2, 1.5);
                }
            }
            case RUMBLING -> {
                if (this.timer % 40 == 0) {
                    level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2.5F, 0.35F);
                }
                smoke(level, pos, 3, 3.0);
                tremor(level, pos, false);
            }
            case ERUPTING -> erupt(level, pos, random);
            case COOLDOWN -> {
                if (random.nextInt(8) == 0) {
                    smoke(level, pos, 2, 2.0);
                }
            }
        }
        this.setChanged();
    }

    private void advanceState(ServerLevel level, BlockPos pos, RandomSource random) {
        switch (this.state) {
            case DORMANT -> {
                this.state = RUMBLING;
                this.timer = 200 + random.nextInt(120);
            }
            case RUMBLING -> {
                this.state = ERUPTING;
                this.timer = 300 + random.nextInt(220);
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.5F);
                placeOverflow(level, pos, true);
            }
            case ERUPTING -> {
                this.state = COOLDOWN;
                this.timer = 600 + random.nextInt(400);
                placeOverflow(level, pos, false); // recede
            }
            case COOLDOWN -> {
                this.state = DORMANT;
                this.timer = dormantDuration(random);
            }
        }
    }

    private void erupt(ServerLevel level, BlockPos pos, RandomSource random) {
        // Vent plume and overhead ash canopy.
        double px = pos.getX() + 0.5, pz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, px, pos.getY() + 1.5, pz, 6, 0.6, 1.0, 0.6, 0.05);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, pos.getY() + 3.0, pz, 3, 1.2, 2.0, 1.2, 0.02);
        level.sendParticles(ParticleTypes.ASH, px, pos.getY() + 8.0, pz, 12, 7.0, 3.0, 7.0, 0.02);
        level.sendParticles(ParticleTypes.LAVA, px, pos.getY() + 1.0, pz, 2, 0.4, 0.3, 0.4, 0.1);

        // Magma blocks launch outward onto the slopes.
        if (this.timer % 8 == 0) {
            int bombs = 1 + random.nextInt(2);
            for (int i = 0; i < bombs; i++) {
                launchBomb(level, pos, random);
            }
            level.playSound(null, px, pos.getY() + 0.5, pz, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
        if (this.timer % 60 == 0) {
            level.playSound(null, px, pos.getY() + 0.5, pz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 3.0F, 0.5F);
        }
        tremor(level, pos, true);
    }

    private void launchBomb(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos spawn = pos.above(2);
        FallingBlockEntity bomb = FallingBlockEntity.fall(level, spawn, Blocks.MAGMA_BLOCK.defaultBlockState());
        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizontal = 0.15 + random.nextDouble() * 0.35;
        bomb.setDeltaMovement(Math.cos(angle) * horizontal, 0.7 + random.nextDouble() * 0.5, Math.sin(angle) * horizontal);
        bomb.time = 1; // mark as falling so it is not treated as a fresh block
    }

    private void tremor(ServerLevel level, BlockPos pos, boolean erupting) {
        if (this.timer % 20 != 0) return;
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        AABB area = new AABB(pos).inflate(KNOCKBACK_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double dist = entity.position().distanceTo(center);
            if (dist > KNOCKBACK_RADIUS) continue;
            Vec3 push = entity.position().subtract(center).normalize().scale(erupting ? 0.5 : 0.2);
            entity.push(push.x, 0.35, push.z);
            entity.hurtMarked = true;
            if (erupting && dist <= DAMAGE_RADIUS) {
                entity.setRemainingFireTicks(100);
                entity.hurt(level.damageSources().lava(), 3.0F);
            }
        }
    }

    /**
     * Places eruption lava sources on the crater rim, then removes only those sources on cooldown.
     */
    private void placeOverflow(ServerLevel level, BlockPos pos, boolean place) {
        if (!place) {
            for (BlockPos p : this.overflow) {
                if (level.getFluidState(p).is(Fluids.LAVA)) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }
            this.overflow.clear();
            return;
        }
        this.overflow.clear();
        int points = 10;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < points; i++) {
            double angle = i * (Math.PI * 2.0 / points);
            int x = pos.getX() + Mth.floor(Math.cos(angle) * this.rimRadius + 0.5);
            int z = pos.getZ() + Mth.floor(Math.sin(angle) * this.rimRadius + 0.5);
            m.set(x, pos.getY() + 10, z);
            while (m.getY() > pos.getY() - 2 && level.getBlockState(m).isAir()) {
                m.move(0, -1, 0);
            }
            BlockPos spill = m.above();
            if (level.getBlockState(spill).canBeReplaced()) {
                level.setBlockAndUpdate(spill, Blocks.LAVA.defaultBlockState());
                this.overflow.add(spill.immutable());
            }
        }
    }

    private void smoke(ServerLevel level, BlockPos pos, int count, double spread) {
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                count, spread * 0.3, 0.5, spread * 0.3, 0.01);
    }

    private static int dormantDuration(RandomSource random) {
        return 1800 + random.nextInt(1800); // 1.5 - 3 minutes
    }

    public int getEruptionState() {
        return this.state;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.state = tag.getInt("EruptionState");
        this.timer = tag.getInt("EruptionTimer");
        this.rimRadius = tag.contains("RimRadius") ? tag.getInt("RimRadius") : 4;
        this.overflow.clear();
        for (long packed : tag.getLongArray("Overflow")) {
            this.overflow.add(BlockPos.of(packed));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("EruptionState", this.state);
        tag.putInt("EruptionTimer", this.timer);
        tag.putInt("RimRadius", this.rimRadius);
        long[] packed = new long[this.overflow.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = this.overflow.get(i).asLong();
        }
        tag.putLongArray("Overflow", packed);
    }
}
