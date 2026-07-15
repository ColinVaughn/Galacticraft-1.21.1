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

package dev.galacticraft.mod.content.block.special.launchpad;

import dev.galacticraft.mod.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/** Persistent, server-wide address book for cargo-rocket launch pads. */
public final class CargoPadRegistry extends SavedData {
    public static final int MIN_ADDRESS = 0;
    public static final int MAX_ADDRESS = 999_999;
    private static final String ID = Constant.MOD_ID + "_cargo_pads";

    private final Map<Integer, PadTarget> pads = new HashMap<>();

    public static CargoPadRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CargoPadRegistry::new, CargoPadRegistry::load, null), ID);
    }

    public static boolean isValidAddress(int address) {
        return address >= MIN_ADDRESS && address <= MAX_ADDRESS;
    }

    /** Assigns an address atomically. Existing valid pads keep ownership of their address. */
    public boolean assign(MinecraftServer server, int address, PadTarget target) {
        if (!isValidAddress(address)) return false;

        PadTarget existing = this.pads.get(address);
        if (existing != null && !existing.equals(target)) {
            if (this.resolve(server, address).isPresent()) return false;
            this.pads.remove(address);
        }

        this.removeTarget(target);
        this.pads.put(address, target);
        this.setDirty();
        return true;
    }

    public void unregister(PadTarget target) {
        if (this.removeTarget(target)) this.setDirty();
    }

    public boolean contains(int address) {
        return this.pads.containsKey(address);
    }

    private boolean removeTarget(PadTarget target) {
        boolean removed = false;
        Iterator<Map.Entry<Integer, PadTarget>> iterator = this.pads.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().equals(target)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** Loads and validates the destination chunk before returning the pad. */
    public Optional<ResolvedPad> resolve(MinecraftServer server, int address) {
        PadTarget target = this.pads.get(address);
        if (target == null) return Optional.empty();

        ServerLevel level = server.getLevel(target.dimension());
        if (level == null) {
            this.pads.remove(address);
            this.setDirty();
            return Optional.empty();
        }

        level.getChunkAt(target.pos());
        if (level.getBlockEntity(target.pos()) instanceof LaunchPadBlockEntity pad
                && pad.getPadType() == LaunchPadBlockEntity.Type.ROCKET
                && pad.getBlockState().getBlock() instanceof AbstractLaunchPad
                && pad.getBlockState().getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.CENTER
                && pad.getAddress() == address) {
            return Optional.of(new ResolvedPad(level, pad));
        }

        this.pads.remove(address);
        this.setDirty();
        return Optional.empty();
    }

    public static CargoPadRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        CargoPadRegistry registry = new CargoPadRegistry();
        ListTag entries = tag.getList("pads", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            int address = entry.getInt("address");
            if (dimension != null && isValidAddress(address)) {
                registry.pads.put(address, new PadTarget(
                        ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("pos"))));
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        this.pads.forEach((address, target) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("address", address);
            entry.putString("dimension", target.dimension().location().toString());
            entry.putLong("pos", target.pos().asLong());
            entries.add(entry);
        });
        tag.put("pads", entries);
        return tag;
    }

    public record PadTarget(ResourceKey<Level> dimension, BlockPos pos) {
        public static PadTarget of(ServerLevel level, BlockPos pos) {
            return new PadTarget(level.dimension(), pos.immutable());
        }
    }

    public record ResolvedPad(ServerLevel level, LaunchPadBlockEntity pad) {
    }
}
