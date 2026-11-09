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

package dev.galacticraft.mod.content.block.entity.decoration;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.content.FlagDesigns;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.item.FlagItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.spacerace.TeamFlagOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlagBlockEntity extends BannerBlockEntity {
    protected float yaw = 0;
    protected @Nullable ResourceLocation design = null;
    /** The space race team this flag flies for, or null for a banner or preset flag. */
    protected @Nullable TeamFlagOwner team = null;

    public FlagBlockEntity(BlockPos pos, BlockState state, DyeColor baseColor) {
        super(pos, state, baseColor);
    }

    public FlagBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registryLookup) {
        super.saveAdditional(compound, registryLookup);

        compound.putFloat("yaw", this.yaw);
        if (this.design != null) {
            compound.putString("design", this.design.toString());
        }
        if (this.team != null) {
            compound.put("team", TeamFlagOwner.CODEC.encodeStart(NbtOps.INSTANCE, this.team).getOrThrow());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registryLookup) {
        super.loadAdditional(compound, registryLookup);

        this.yaw = compound.getFloat("yaw");
        this.design = compound.contains("design") ? ResourceLocation.tryParse(compound.getString("design")) : null;
        this.team = compound.contains("team") ? TeamFlagOwner.CODEC.parse(NbtOps.INSTANCE, compound.get("team")).result().orElse(null) : null;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        this.design = input.get(GCDataComponents.FLAG_DESIGN);
        this.team = input.get(GCDataComponents.TEAM_FLAG);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(GCDataComponents.FLAG_DESIGN, this.design);
        builder.set(GCDataComponents.TEAM_FLAG, this.team);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("design");
        tag.remove("team");
    }

    public @Nullable ResourceLocation getDesign() {
        return this.design;
    }

    public @Nullable TeamFlagOwner getTeam() {
        return this.team;
    }

    /** Makes this flag fly for a team, as when an unclaimed team flag is planted. */
    public void claim(TeamFlagOwner team) {
        this.team = team;
        if (this.level != null) {
            this.setChanged();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        if (this.level != null) {
            this.setChanged();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public @NotNull Component getName() {
        if (super.getCustomName() != null) {
            return super.getCustomName();
        }
        if (this.team != null) {
            return FlagItem.teamFlagName(this.team);
        }
        return this.design != null ? Component.translatable(FlagDesigns.translationKey(this.design)) : this.getBlockState().getBlock().getName();
    }

    @Override
    public @NotNull ItemStack getItem() {
        ItemStack itemStack = new ItemStack(GCItems.FLAGS.get(this.getBaseColor()));
        itemStack.applyComponents(this.collectComponents());
        return itemStack;
    }

    // Overriding these two to replace the block entity type
    @Override
    public @NotNull BlockEntityType<FlagBlockEntity> getType() {
        return GCBlockEntityTypes.FLAG;
    }

    @Override
    public boolean isValidBlockState(BlockState state) {
        return GCBlockEntityTypes.FLAG.isValid(state);
    }
}
