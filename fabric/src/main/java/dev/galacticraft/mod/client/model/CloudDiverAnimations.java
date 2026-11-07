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

package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Plays the Blockbench clips from cloud_diver.animation.json on the Cloud Diver's model part bones. */
public final class CloudDiverAnimations {
    private record Track(BurrowerAnimations.Clip clip, double length) { }
    private final Map<String, Track> clips = new HashMap<>();

    public CloudDiverAnimations(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(Constant.id("animations/cloud_diver.animation.json")).openAsReader()) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").entrySet().forEach(entry -> {
                var json = entry.getValue().getAsJsonObject();
                clips.put(entry.getKey(), new Track(BurrowerAnimations.readClip(json), json.get("animation_length").getAsDouble()));
            });
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load cloud diver animations", error);
        }
    }

    public double length(String clip) { return track(clip).length; }

    /**
     * Adds a clip's pose to a part. The mesh is in vanilla y-down model space, which is the same frame Bedrock exports
     * rotate in, so rotations apply as exported; exported positions only need their Y flipped.
     */
    public void apply(ModelPart part, String clip, String bone, double time, float weight) {
        if (weight == 0) return;
        var track = track(clip);
        // Preserve the authored loop period before reducing large tick ages to a float sample.
        var pose = track.clip.sample(bone, (float) (track.clip.loop ? (time % track.length + track.length) % track.length : time));
        part.x += pose.position().x() * weight;
        part.y -= pose.position().y() * weight;
        part.z += pose.position().z() * weight;
        part.xRot += pose.rotation().x() * weight * Mth.DEG_TO_RAD;
        part.yRot += pose.rotation().y() * weight * Mth.DEG_TO_RAD;
        part.zRot += pose.rotation().z() * weight * Mth.DEG_TO_RAD;
        part.xScale *= 1 + (pose.scale().x() - 1) * weight;
        part.yScale *= 1 + (pose.scale().y() - 1) * weight;
        part.zScale *= 1 + (pose.scale().z() - 1) * weight;
    }

    private Track track(String clip) {
        var track = clips.get("animation.cloud_diver." + clip);
        if (track == null) throw new IllegalArgumentException("Missing cloud diver clip: " + clip);
        return track;
    }
}
