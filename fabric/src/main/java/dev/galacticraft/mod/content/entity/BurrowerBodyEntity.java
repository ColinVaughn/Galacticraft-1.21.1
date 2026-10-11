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

package dev.galacticraft.mod.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/** Invisible damage targets follow the growing body without trapping or pushing players. */
public class BurrowerBodyEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BurrowerBodyEntity.class, EntityDataSerializers.INT);
    public BurrowerBodyEntity(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }
    public void setOwner(LunarBurrowerEntity owner) { entityData.set(OWNER, owner.getId()); }
    public LunarBurrowerEntity owner() {
        Entity entity = level().getEntity(entityData.get(OWNER));
        return entity instanceof LunarBurrowerEntity worm ? worm : null;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, -1); }
    @Override public void tick() {
        super.tick();
        if (owner() != null && Math.abs(getBbWidth() - getDimensions(getPose()).width()) > 0.01) refreshDimensions();
        if (!level().isClientSide && tickCount > 20 && (owner() == null || !owner().isAlive())) discard();
    }
    @Override public boolean hurt(DamageSource source, float amount) { return owner() != null && owner().hurt(source, amount); }
    @Override public boolean isPickable() { return owner() != null && owner().isAlive(); }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        return owner() == null ? InteractionResult.PASS : owner().interact(player, hand);
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(owner() == null ? 1 : owner().getModelScale());
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
