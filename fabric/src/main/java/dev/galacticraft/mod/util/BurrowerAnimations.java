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

package dev.galacticraft.mod.util;

import com.google.gson.*;
import dev.galacticraft.mod.content.entity.BurrowerState;

import java.io.Reader;
import java.util.*;

/** Samples the exported numeric Blockbench tracks, including position, rotation and scale. */
public final class BurrowerAnimations {
    public record Vector(float x, float y, float z) {
        public static final Vector ZERO = new Vector(0, 0, 0), ONE = new Vector(1, 1, 1);
        Vector lerp(Vector other, float alpha) {
            return new Vector(x + (other.x - x) * alpha, y + (other.y - y) * alpha, z + (other.z - z) * alpha);
        }
    }
    public record Pose(Vector position, Vector rotation, Vector scale) {
        public static final Pose REST = new Pose(Vector.ZERO, Vector.ZERO, Vector.ONE);
        public Pose lerp(Pose other, float alpha) {
            return new Pose(position.lerp(other.position, alpha), rotation.lerp(other.rotation, alpha), scale.lerp(other.scale, alpha));
        }
    }
    private record Key(float time, Vector value) {}
    private record Track(Key[] keys) {
        Vector sample(float time) {
            if (time <= keys[0].time) return keys[0].value;
            int low = 0, high = keys.length - 1;
            if (time >= keys[high].time) return keys[high].value;
            while (low + 1 < high) {
                int mid = (low + high) >>> 1;
                if (keys[mid].time <= time) low = mid; else high = mid;
            }
            return keys[low].value.lerp(keys[high].value, (time - keys[low].time) / (keys[high].time - keys[low].time));
        }
    }
    private record Bone(Track position, Track rotation, Track scale) {
        Pose sample(float time) {
            return new Pose(position == null ? Vector.ZERO : position.sample(time),
                    rotation == null ? Vector.ZERO : rotation.sample(time), scale == null ? Vector.ONE : scale.sample(time));
        }
    }
    public static final class Clip {
        public final float length;
        public final boolean loop;
        private final Map<String, Bone> bones;
        Clip(float length, boolean loop, Map<String, Bone> bones) { this.length = length; this.loop = loop; this.bones = Map.copyOf(bones); }
        public Set<String> boneNames() { return bones.keySet(); }
        public Pose sample(String bone, float time) {
            float bounded = loop ? (time % length + length) % length : Math.max(0, Math.min(time, length));
            Bone track = bones.get(bone);
            return track == null ? Pose.REST : track.sample(bounded);
        }
    }
    private final EnumMap<BurrowerState, Clip> clips = new EnumMap<>(BurrowerState.class);
    public Clip clip(BurrowerState state) { return clips.get(state); }
    public static BurrowerAnimations read(Reader reader) {
        JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        BurrowerAnimations result = new BurrowerAnimations();
        for (BurrowerState state : BurrowerState.values()) {
            JsonObject clip = animations.getAsJsonObject("animation.lunar_burrower." + state.clipName());
            if (clip == null) throw new IllegalArgumentException("Missing burrower clip: " + state);
            float length = clip.get("animation_length").getAsFloat();
            boolean loop = clip.has("loop") && clip.get("loop").getAsBoolean();
            if (Math.abs(length * 20 - state.clipTicks) > 0.001 || loop != state.loop)
                throw new IllegalArgumentException("Burrower clip timing mismatch: " + state);
            result.clips.put(state, readClip(clip));
        }
        return result;
    }
    public static Clip readClip(JsonObject clip) {
        float length = clip.get("animation_length").getAsFloat();
        if (!Float.isFinite(length) || length <= 0) throw new IllegalArgumentException("Invalid animation length");
        boolean loop = clip.has("loop") && clip.get("loop").isJsonPrimitive()
                && clip.get("loop").getAsJsonPrimitive().isBoolean() && clip.get("loop").getAsBoolean();
        Map<String, Bone> bones = new HashMap<>();
        clip.getAsJsonObject("bones").entrySet().forEach(entry -> {
            JsonObject bone = entry.getValue().getAsJsonObject();
            bones.put(entry.getKey(), new Bone(track(bone.get("position")), track(bone.get("rotation")), track(bone.get("scale"))));
        });
        return new Clip(length, loop, bones);
    }
    private static Track track(JsonElement json) {
        if (json == null) return null;
        if (json.isJsonArray()) return new Track(new Key[]{new Key(0, vector(json))});
        List<Key> keys = new ArrayList<>();
        json.getAsJsonObject().entrySet().forEach(entry -> keys.add(new Key(Float.parseFloat(entry.getKey()), vector(entry.getValue()))));
        keys.sort(Comparator.comparingDouble(Key::time));
        if (keys.isEmpty()) throw new IllegalArgumentException("Empty burrower animation channel");
        return new Track(keys.toArray(Key[]::new));
    }
    private static Vector vector(JsonElement value) {
        JsonArray v = (value.isJsonObject() ? value.getAsJsonObject().get("post") : value).getAsJsonArray();
        Vector result = new Vector(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat());
        if (!Float.isFinite(result.x) || !Float.isFinite(result.y) || !Float.isFinite(result.z))
            throw new IllegalArgumentException("Non-finite burrower keyframe");
        return result;
    }
}
