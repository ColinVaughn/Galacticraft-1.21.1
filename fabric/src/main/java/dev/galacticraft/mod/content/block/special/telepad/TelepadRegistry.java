/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.special.telepad;

import dev.galacticraft.mod.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Server-wide address ownership survives chunk unloading and world restarts. */
public final class TelepadRegistry extends SavedData {
    public record Entry(GlobalPos location, boolean enabled) {}
    private final Map<Integer, Entry> pads = new HashMap<>();

    public static TelepadRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TelepadRegistry::new, TelepadRegistry::load, null), Constant.MOD_ID + "_telepads");
    }

    public static boolean validAddress(int address) { return address >= 0 && address <= 999_999; }
    public Entry get(int address) { return pads.get(address); }

    public boolean claim(int address, GlobalPos location, boolean enabled) {
        if (!validAddress(address)) return false;
        Entry existing = pads.get(address);
        if (existing != null && !existing.location.equals(location)) return false;
        Entry entry = new Entry(location, enabled);
        if (!entry.equals(existing)) {
            pads.put(address, entry);
            setDirty();
        }
        return true;
    }

    public void remove(int address, GlobalPos location) {
        Entry existing = pads.get(address);
        if (existing != null && existing.location.equals(location)) {
            pads.remove(address);
            setDirty();
        }
    }

    public static TelepadRegistry load(CompoundTag tag, HolderLookup.Provider lookup) {
        TelepadRegistry registry = new TelepadRegistry();
        ListTag entries = tag.getList("pads", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            int address = entry.getInt("address");
            if (dimension != null && validAddress(address)) {
                registry.pads.putIfAbsent(address, new Entry(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("pos"))), entry.getBoolean("enabled")));
            }
        }
        return registry;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag entries = new ListTag();
        pads.forEach((address, entry) -> {
            CompoundTag value = new CompoundTag();
            value.putInt("address", address);
            value.putString("dimension", entry.location.dimension().location().toString());
            value.putLong("pos", entry.location.pos().asLong());
            value.putBoolean("enabled", entry.enabled);
            entries.add(value);
        });
        tag.put("pads", entries);
        return tag;
    }
}
