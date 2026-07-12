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

package dev.galacticraft.mod.storage;

import com.mojang.serialization.DataResult;
import dev.galacticraft.machinelib.api.storage.StorageAccess;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Loader-neutral, single-variant tank whose amounts use MachineLib droplets. */
public final class SimpleFluidTank implements StorageAccess<Fluid> {
    private final long capacity;
    private Fluid fluid = Fluids.EMPTY;
    private DataComponentPatch components = DataComponentPatch.EMPTY;
    private long amount;
    private final Runnable changed;

    public SimpleFluidTank(long capacity, Runnable changed) {
        this.capacity = capacity;
        this.changed = changed;
    }
    public @Nullable Fluid getResource() { return isEmpty() ? null : fluid; }
    public DataComponentPatch getComponents() { return components; }
    public long getAmount() { return amount; }
    public long getCapacity() { return capacity; }
    public boolean isResourceBlank() { return isEmpty(); }
    public void set(Fluid fluid, long amount) { set(fluid, DataComponentPatch.EMPTY, amount); }
    public void set(Fluid fluid, DataComponentPatch components, long amount) {
        this.fluid = amount <= 0 ? Fluids.EMPTY : fluid;
        this.components = amount <= 0 ? DataComponentPatch.EMPTY : components;
        this.amount = Math.max(0, Math.min(capacity, amount));
        changed.run();
    }
    public void clear() { set(Fluids.EMPTY, 0); }
    public long extract(long maximum) {
        long extracted = Math.min(amount, Math.max(0, maximum));
        if (extracted > 0) set(fluid, components, amount - extracted);
        return extracted;
    }
    @Override public boolean isEmpty() { return amount <= 0 || fluid == Fluids.EMPTY; }
    @Override public boolean isFull() { return amount >= capacity; }
    private boolean matches(Fluid resource, @Nullable DataComponentPatch patch) {
        return !isEmpty() && fluid.isSame(resource) && (patch == null || Objects.equals(components, patch));
    }
    @Override public boolean canInsert(@NotNull Fluid resource, @NotNull DataComponentPatch patch) { return tryInsert(resource, patch, 1) > 0; }
    @Override public boolean canInsert(@NotNull Fluid resource, @NotNull DataComponentPatch patch, long value) { return tryInsert(resource, patch, value) == value; }
    @Override public long tryInsert(@NotNull Fluid resource, @NotNull DataComponentPatch patch, long value) {
        if (value <= 0 || (!isEmpty() && !matches(resource, patch))) return 0;
        return Math.min(value, capacity - amount);
    }
    @Override public long insert(@NotNull Fluid resource, @NotNull DataComponentPatch patch, long value) {
        long inserted = tryInsert(resource, patch, value);
        if (inserted > 0) set(resource, patch, amount + inserted);
        return inserted;
    }
    @Override public long insertMatching(@NotNull Fluid resource, @NotNull DataComponentPatch patch, long value) { return insert(resource, patch, value); }
    @Override public boolean contains(@NotNull Fluid resource) { return matches(resource, null); }
    @Override public boolean contains(@NotNull Fluid resource, @Nullable DataComponentPatch patch) { return matches(resource, patch); }
    @Override public boolean canExtract(@NotNull Fluid resource, @Nullable DataComponentPatch patch, long value) { return tryExtract(resource, patch, value) == value; }
    @Override public long tryExtract(@NotNull Fluid resource, @Nullable DataComponentPatch patch, long value) { return matches(resource, patch) ? Math.min(amount, Math.max(0, value)) : 0; }
    @Override public boolean extractOne(@NotNull Fluid resource, @Nullable DataComponentPatch patch) { return extract(resource, patch, 1) == 1; }
    @Override public long extract(@NotNull Fluid resource, @Nullable DataComponentPatch patch, long value) {
        long extracted = tryExtract(resource, patch, value);
        if (extracted > 0) extract(extracted);
        return extracted;
    }
    public void readNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        if (!tag.contains("variant") || !tag.contains("amount")) {
            clear();
            return;
        }
        DataResult<MLFluidStack> parsed = MLFluidStack.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag.get("variant"));
        parsed.result().ifPresentOrElse(value -> set(value.fluid(), value.components(), tag.getLong("amount")), this::clear);
    }
    public void writeNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        if (isEmpty()) return;
        tag.put("variant", MLFluidStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),
                new MLFluidStack(fluid, components)).getOrThrow());
        tag.putLong("amount", amount);
    }
}
