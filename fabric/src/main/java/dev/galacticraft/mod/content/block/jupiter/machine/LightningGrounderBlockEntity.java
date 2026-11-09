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

package dev.galacticraft.mod.content.block.jupiter.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.EnergySource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterStorms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws Storm Belt lightning onto itself. Any strike within {@link JupiterStorms#GROUNDER_RADIUS} blocks lands on the
 * mast instead of the surroundings, and the grounded charge, {@link #ENERGY_PER_STRIKE} per bolt, is pushed out of
 * the machine's output faces into capacitors or wires.
 */
public class LightningGrounderBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int DRAIN_SLOT = 0;
    public static final int ENERGY_PER_STRIKE = 40_000;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(8, 62).capacity(1)
                            .filter(ResourceFilters.CAN_INSERT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(400_000, 0, 4_000)
    );
    private final EnergySource energySource = new EnergySource(this);
    private int strikes;
    private float activity;

    public LightningGrounderBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.LIGHTNING_GROUNDER, pos, state, SPEC);
    }

    /** Takes a strike: banks its charge and counts it. */
    public void ground() {
        this.energyStorage().setEnergy(Math.min(this.energyStorage().getCapacity(), this.energyStorage().getAmount() + ENERGY_PER_STRIKE));
        this.strikes++;
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.5F, 1.2F);
        }
    }

    public int strikes() {
        return this.strikes;
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        JupiterStorms.addGrounder(level, pos);
        this.drainPowerToSlot(DRAIN_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        this.energySource.trySpreadEnergy(level, pos, state);
        this.activity = JupiterStorms.activity(level, pos);
        return this.activity > 0 ? JupiterMachineStatuses.GROUNDING : JupiterMachineStatuses.CALM_SKIES;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null) JupiterStorms.removeGrounder(this.level, this.worldPosition);
    }

    @Override
    public List<Integer> readout() {
        return List.of(this.strikes, JupiterStorms.GROUNDER_RADIUS, Math.round(this.activity * 100));
    }

    /** The arc gap crackles whenever a storm is overhead. */
    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Strikes", this.strikes);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.strikes = tag.getInt("Strikes");
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.LIGHTNING_GROUNDER, syncId, player, this);
    }
}
