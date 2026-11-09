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

package dev.galacticraft.mod.world.dimension.jupiter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every Survey Beacon placed in a dimension, kept even while its chunk is unloaded so navigation works across an expedition. */
public final class SurveyBeacons extends SavedData {
    private static final String[] BEARINGS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    public record Beacon(BlockPos pos, String name, boolean active) { }
    private final Map<BlockPos, Beacon> beacons = new HashMap<>();

    public static SurveyBeacons get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SurveyBeacons::new, SurveyBeacons::load, null), "galacticraft_survey_beacons");
    }

    private static SurveyBeacons load(CompoundTag tag, HolderLookup.Provider registries) {
        SurveyBeacons data = new SurveyBeacons();
        for (Tag entry : tag.getList("Beacons", Tag.TAG_COMPOUND)) {
            CompoundTag beacon = (CompoundTag) entry;
            NbtUtils.readBlockPos(beacon, "Pos").ifPresent(pos -> data.beacons.put(pos, new Beacon(pos, beacon.getString("Name"), beacon.getBoolean("Active"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Beacon beacon : this.beacons.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Pos", NbtUtils.writeBlockPos(beacon.pos()));
            entry.putString("Name", beacon.name());
            entry.putBoolean("Active", beacon.active());
            list.add(entry);
        }
        tag.put("Beacons", list);
        return tag;
    }

    public void update(BlockPos pos, String name, boolean active) {
        Beacon beacon = new Beacon(pos.immutable(), name, active);
        if (!beacon.equals(this.beacons.put(beacon.pos(), beacon))) this.setDirty();
    }

    public void remove(BlockPos pos) {
        if (this.beacons.remove(pos) != null) this.setDirty();
    }

    /** Broadcasting beacons nearest to {@code from}, skipping the one at {@code exclude}. */
    public List<Beacon> nearest(Vec3 from, BlockPos exclude, int limit) {
        return this.beacons.values().stream().filter(Beacon::active).filter(beacon -> !beacon.pos().equals(exclude))
                .sorted(Comparator.comparingDouble(beacon -> beacon.pos().getCenter().distanceToSqr(from))).limit(limit).toList();
    }

    public static int distance(Vec3 from, BlockPos to) {
        return (int) Math.round(Math.sqrt(to.getCenter().distanceToSqr(from)));
    }

    /** Compass bearing from {@code from} to {@code to}; north is -Z. */
    public static String bearing(Vec3 from, BlockPos to) {
        double degrees = Math.toDegrees(Math.atan2(to.getX() + 0.5 - from.x, -(to.getZ() + 0.5 - from.z)));
        return BEARINGS[Math.floorMod((int) Math.round(degrees / 45), 8)];
    }
}
