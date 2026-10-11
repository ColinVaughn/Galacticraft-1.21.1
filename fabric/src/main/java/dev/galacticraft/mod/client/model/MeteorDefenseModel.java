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

package dev.galacticraft.mod.client.model;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.*;

/** Loads the cube rigs and numeric, linear animation tracks exported from Blockbench. */
public final class MeteorDefenseModel {
    private final ModelPart root;
    private final Map<String, ModelPart> bones = new HashMap<>();
    private final Map<String, BurrowerAnimations.Clip> animations = new HashMap<>();

    public MeteorDefenseModel(ResourceManager resources, String name) {
        this(resources, "block", name);
    }

    public MeteorDefenseModel(ResourceManager resources, String directory, String name) {
        try (var geometry = resources.getResourceOrThrow(Constant.id("models/" + directory + "/" + name + ".geo.json")).openAsReader();
             var motion = resources.getResourceOrThrow(Constant.id("animations/" + name + ".animation.json")).openAsReader()) {
            JsonObject geo = JsonParser.parseReader(geometry).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            root = bake(geo);
            collect(geo.getAsJsonArray("bones"));
            JsonParser.parseReader(motion).getAsJsonObject().getAsJsonObject("animations").entrySet().forEach(entry -> {
                var clip = BurrowerAnimations.readClip(entry.getValue().getAsJsonObject());
                if (!bones.keySet().containsAll(clip.boneNames())) throw new IllegalArgumentException("Unknown animation bone: " + entry.getKey());
                animations.put(entry.getKey(), clip);
            });
        } catch (IOException error) { throw new IllegalStateException("Cannot load Blockbench model: " + name, error); }
    }

    private void collect(JsonArray definitions) {
        for (JsonElement entry : definitions) {
            JsonObject bone = entry.getAsJsonObject();
            String name = bone.get("name").getAsString();
            ModelPart parent = bone.has("parent") ? bones.get(bone.get("parent").getAsString()) : root;
            bones.put(name, parent.getChild(name));
        }
    }

    static ModelPart bake(JsonObject geometry) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        Map<String, JsonArray> pivots = new HashMap<>();
        for (JsonElement entry : geometry.getAsJsonArray("bones")) {
            JsonObject bone = entry.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            PartDefinition parentPart = parent == null ? mesh.getRoot() : Objects.requireNonNull(parts.get(parent), "Parent must precede bone: " + name);
            JsonArray pivot = bone.getAsJsonArray("pivot"), parentPivot = pivots.get(parent);
            CubeListBuilder cubes = CubeListBuilder.create();
            if (bone.has("cubes")) for (JsonElement element : bone.getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                if (cube.has("rotation") || !cube.get("uv").isJsonArray())
                    throw new IllegalArgumentException("Use box UV and rotate bones instead of cubes: " + name);
                JsonArray from = cube.getAsJsonArray("origin"), size = cube.getAsJsonArray("size"), uv = cube.getAsJsonArray("uv");
                cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                        .mirror(cube.has("mirror") && cube.get("mirror").getAsBoolean())
                        .addBox(v(from, 0) - v(pivot, 0), -v(from, 1) - v(size, 1) + v(pivot, 1), v(from, 2) - v(pivot, 2),
                                v(size, 0), v(size, 1), v(size, 2),
                                new CubeDeformation(cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0));
            }
            JsonArray rotation = bone.getAsJsonArray("rotation");
            PartDefinition part = parentPart.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
                    v(pivot, 0) - v(parentPivot, 0), -v(pivot, 1) + v(parentPivot, 1), v(pivot, 2) - v(parentPivot, 2),
                    v(rotation, 0) * Mth.DEG_TO_RAD, v(rotation, 1) * Mth.DEG_TO_RAD, v(rotation, 2) * Mth.DEG_TO_RAD));
            if (parts.put(name, part) != null) throw new IllegalArgumentException("Duplicate bone: " + name);
            pivots.put(name, pivot);
        }
        JsonObject description = geometry.getAsJsonObject("description");
        return LayerDefinition.create(mesh, description.get("texture_width").getAsInt(), description.get("texture_height").getAsInt()).bakeRoot();
    }

    private static float v(JsonArray vector, int axis) { return vector == null ? 0 : vector.get(axis).getAsFloat(); }
    public ModelPart bone(String name) { return Objects.requireNonNull(bones.get(name), name); }
    public void reset() { root.getAllParts().forEach(ModelPart::resetPose); }

    public void animate(String name, double seconds) {
        var clip = Objects.requireNonNull(animations.get(name), name);
        float time = (float) (clip.loop ? (seconds % clip.length + clip.length) % clip.length
                : Math.max(0, Math.min(seconds, clip.length)));
        for (String bone : clip.boneNames()) {
            var pose = clip.sample(bone, time);
            var part = bones.get(bone);
            part.x += pose.position().x(); part.y -= pose.position().y(); part.z += pose.position().z();
            part.xRot += pose.rotation().x() * Mth.DEG_TO_RAD;
            part.yRot += pose.rotation().y() * Mth.DEG_TO_RAD;
            part.zRot += pose.rotation().z() * Mth.DEG_TO_RAD;
            part.xScale *= pose.scale().x(); part.yScale *= pose.scale().y(); part.zScale *= pose.scale().z();
        }
    }

    public void render(PoseStack pose, VertexConsumer vertices, int light, int overlay) {
        pose.pushPose();
        // Bedrock mirrors X and ModelPart uses downward Y; this restores Blockbench's world axes.
        pose.scale(-1, -1, 1);
        root.render(pose, vertices, light, overlay);
        pose.popPose();
    }

    /** Renders a top-level bone separately so the flame rig can follow velocity without tumbling. */
    public void renderBone(String name, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        pose.pushPose();
        pose.scale(-1, -1, 1);
        bone(name).render(pose, vertices, light, overlay, color);
        pose.popPose();
    }
}
