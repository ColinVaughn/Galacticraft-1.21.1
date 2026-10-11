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

package dev.galacticraft.mod.client.model.entity;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.*;
import dev.galacticraft.mod.util.BurrowerAnimations;
import dev.galacticraft.mod.util.BurrowerGrowth;
import dev.galacticraft.mod.util.BurrowerPath;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.*;

/** Renders all authored face UVs and bone channels without repacking the atlas or flattening the rig. */
public final class LunarBurrowerModel {
    private record Face(float u, float v, float width, float height) {}
    private record Cube(Vec3 origin, Vec3 size, Map<String, Face> faces) {}
    private static final class Bone {
        final String name;
        final int section;
        final Vec3 pivot;
        final List<Cube> cubes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
        Bone(String name, Vec3 pivot) {
            this.name = name; this.pivot = pivot;
            section = name.startsWith("body_") ? Integer.parseInt(name.substring(5).split("_", 2)[0]) : 0;
        }
    }
    private final BurrowerAnimations animations;
    private final Bone root;
    private final int authoredSections;
    private final float textureWidth, textureHeight;
    private static final class Poses {
        final Map<String, BurrowerAnimations.Pose> previous = new HashMap<>();
        final Map<String, BurrowerAnimations.Pose> current = new HashMap<>();
        BurrowerState state;
        float blendStarted, preparedClock = -1;
    }
    private final Map<LunarBurrowerEntity, Poses> poses = new WeakHashMap<>();

    public LunarBurrowerModel(ResourceManager resources) {
        this(resources, "lunar_burrower");
    }
    public LunarBurrowerModel(ResourceManager resources, String geometryName) {
        try (var geometry = resources.getResourceOrThrow(Constant.id("models/entity/" + geometryName + ".geo.json")).openAsReader();
             var motion = resources.getResourceOrThrow(Constant.id("animations/lunar_burrower.animation.json")).openAsReader()) {
            JsonObject geo = JsonParser.parseReader(geometry).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject description = geo.getAsJsonObject("description");
            textureWidth = description.get("texture_width").getAsFloat(); textureHeight = description.get("texture_height").getAsFloat();
            Map<String, Bone> bones = new LinkedHashMap<>();
            for (JsonElement entry : geo.getAsJsonArray("bones")) {
                JsonObject json = entry.getAsJsonObject();
                Bone bone = new Bone(json.get("name").getAsString(), vector(json.getAsJsonArray("pivot")));
                bones.put(bone.name, bone);
                if (json.has("cubes")) for (JsonElement element : json.getAsJsonArray("cubes")) {
                    JsonObject cube = element.getAsJsonObject();
                    Map<String, Face> faces = new HashMap<>();
                    cube.getAsJsonObject("uv").entrySet().forEach(face -> {
                        JsonObject f = face.getValue().getAsJsonObject();
                        JsonArray uv = f.getAsJsonArray("uv"), size = f.getAsJsonArray("uv_size");
                        faces.put(face.getKey(), new Face(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                                size.get(0).getAsFloat(), size.get(1).getAsFloat()));
                    });
                    bone.cubes.add(new Cube(vector(cube.getAsJsonArray("origin")), vector(cube.getAsJsonArray("size")), faces));
                }
            }
            for (JsonElement entry : geo.getAsJsonArray("bones")) {
                JsonObject bone = entry.getAsJsonObject();
                if (bone.has("parent")) bones.get(bone.get("parent").getAsString()).children.add(bones.get(bone.get("name").getAsString()));
            }
            root = Objects.requireNonNull(bones.get("root"));
            authoredSections = bones.values().stream().mapToInt(bone -> bone.section).max().orElse(6);
            animations = BurrowerAnimations.read(motion);
            for (BurrowerState state : BurrowerState.values()) {
                if (!bones.keySet().containsAll(animations.clip(state).boneNames())) throw new IllegalArgumentException("Unknown burrower animation bone");
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot load lunar burrower assets", error); }
    }
    private static Vec3 vector(JsonArray v) { return new Vec3(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()); }

    public void render(LunarBurrowerEntity entity, float partialTick, PoseStack pose, VertexConsumer vertices,
                       int light, int overlay, int color) {
        BurrowerState state = entity.getBurrowerState();
        float clock = entity.tickCount + partialTick;
        Poses cache = poses.computeIfAbsent(entity, ignored -> new Poses());
        if (state != cache.state) {
            cache.previous.clear(); cache.previous.putAll(cache.current);
            cache.state = state; cache.blendStarted = clock; cache.preparedClock = -1;
        }
        float time = (entity.getStateTicks() + partialTick) / 20;
        if (state == BurrowerState.CRAWL || state == BurrowerState.CHASE || state == BurrowerState.RIDE) {
            double stride = state == BurrowerState.RIDE ? BurrowerPath.RIDE_STRIDE : state == BurrowerState.CRAWL ? 0.91 : 1.13;
            stride *= entity.getModelScale();
            time = (float)(entity.getTravel(partialTick) / stride * animations.clip(state).length);
        }
        boolean prepared = cache.preparedClock == clock;
        float blend = Math.min(1, Math.max(0, (clock - cache.blendStarted) / 4));
        pose.pushPose();
        pose.translate(0, 0, 52.0 / 16);
        renderBone(root, Vec3.ZERO, entity, partialTick, state, time, blend, pose, vertices, light, overlay, color, cache, prepared, 0);
        pose.popPose();
        cache.preparedClock = clock;
    }

    private void renderBone(Bone bone, Vec3 parentPivot, LunarBurrowerEntity entity, float partialTick, BurrowerState state,
                            float time, float blend, PoseStack pose, VertexConsumer vertices,
                            int light, int overlay, int color, Poses cache, boolean prepared, double rootReach) {
        if (bone.name.equals("saddle") && !entity.isSaddled() || bone.name.equals("cargo_chest") && !entity.hasChest()) return;
        if (bone.section > entity.getBodySectionCount()) return;
        String animationBone = bone.section > 6 ? bone.name.replace("body_" + bone.section, "body_6") : bone.name;
        float delay = state == BurrowerState.RIDE ? BurrowerPath.RIDE_CYCLE / 5.8F : 0.09F;
        var sampled = animations.clip(state).sample(animationBone, time - Math.max(0, bone.section - 6) * delay);
        boolean pathBone = bone.section > 0 && (bone.name.equals("body_" + bone.section) || bone.name.endsWith("_joint")) || bone.name.equals("tail");
        // Riding translation and bend are shared with the rider and damage targets through the centerline.
        if (state == BurrowerState.RIDE && pathBone)
            sampled = new BurrowerAnimations.Pose(BurrowerAnimations.Vector.ZERO, BurrowerAnimations.Vector.ZERO, sampled.scale());
        var motion = prepared ? cache.current.get(bone.name)
                : cache.previous.getOrDefault(bone.name, BurrowerAnimations.Pose.REST).lerp(sampled, blend);
        cache.current.put(bone.name, motion);
        pose.pushPose();
        Vec3 offset = bone.pivot.subtract(parentPivot);
        pose.translate(offset.x / 16, offset.y / 16, offset.z / 16);
        if (pathBone) {
            // Sample the animated center along the trail, then rotate its lateral offset with the bend.
            double distance = ((bone.pivot.z + 52 + motion.position().z()) / 16 + rootReach) * entity.getModelScale();
            if (bone.name.equals("tail")) distance += (entity.getBodySectionCount() - authoredSections) * entity.getModelScale();
            float angle = (float)Math.toRadians(180 - entity.getHeadYaw(partialTick));
            Vec3 origin = entity.getPosition(partialTick);
            Vec3 point = entity.bodyPositionAt(distance, partialTick).subtract(origin).yRot(-angle).scale(1 / entity.getModelScale());
            Vec3 tangent = entity.bodyPositionAt(Math.max(0, distance - 0.35 * entity.getModelScale()), partialTick)
                    .subtract(entity.bodyPositionAt(distance + 0.35 * entity.getModelScale(), partialTick)).yRot(-angle);
            applyBodyAnchor(pose, bone.pivot, point, tangent, motion);
            if (authoredSections > 6 && bone.section > 0) {
                float profile = BurrowerGrowth.bodyProfile(bone.section, entity.getBodySectionCount())
                        / BurrowerGrowth.bodyProfile(bone.section, authoredSections);
                pose.scale(profile, profile, 1);
            }
        } else {
            if (bone == root) {
                // The traction cycle advances every section along its own bend, not the head's straight axis.
                rootReach = motion.position().z() / 16;
                motion = withoutReach(motion);
            }
            if (bone.name.equals("head")) {
                Vec3 anchor = headOffset(entity, partialTick, 13.0 / 16 + rootReach + motion.position().z() / 16);
                pose.translate(anchor.x, anchor.y, anchor.z);
                if (entity.getControllingPassenger() != null)
                    pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
                motion = withoutReach(motion);
            }
            applyMotion(pose, motion);
        }
        for (Cube cube : bone.cubes) renderCube(cube, bone.pivot, pose, vertices, light, overlay, color);
        for (Bone child : bone.children) renderBone(child, bone.pivot, entity, partialTick, state, time, blend, pose, vertices, light, overlay, color, cache, prepared, rootReach);
        pose.popPose();
    }

    public static Vec3 headOffset(LunarBurrowerEntity entity, float partialTick) {
        return headOffset(entity, partialTick, 13.0 / 16);
    }

    private static Vec3 headOffset(LunarBurrowerEntity entity, float partialTick, double distance) {
        float angle = (float)Math.toRadians(180 - entity.getHeadYaw(partialTick));
        // Keep the rear head hinge attached to the neck even while facing a player without moving.
        return headOffset(entity.bodyPositionAt(distance * entity.getModelScale(), partialTick)
                .subtract(entity.getPosition(partialTick)).scale(1 / entity.getModelScale()), angle);
    }

    static Vec3 headOffset(Vec3 worldAnchor, float angle) {
        return worldAnchor.yRot(-angle).subtract(0, 0, 13.0 / 16);
    }

    static void applyBodyAnchor(PoseStack pose, Vec3 pivot, Vec3 point, Vec3 tangent, BurrowerAnimations.Pose motion) {
        pose.translate(point.x - pivot.x / 16, point.y, point.z - (pivot.z + 52) / 16);
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(-tangent.x, -tangent.z)));
        pose.mulPose(Axis.XP.rotation((float)Math.atan2(tangent.y, tangent.horizontalDistance())));
        applyMotion(pose, withoutReach(motion));
    }

    static BurrowerAnimations.Pose withoutReach(BurrowerAnimations.Pose motion) {
        return new BurrowerAnimations.Pose(new BurrowerAnimations.Vector(motion.position().x(), motion.position().y(), 0),
                motion.rotation(), motion.scale());
    }

    static void applyMotion(PoseStack pose, BurrowerAnimations.Pose motion) {
        var p = motion.position(); var r = motion.rotation(); var s = motion.scale();
        pose.translate(p.x() / 16, p.y() / 16, p.z() / 16);
        // Bedrock exports mirror X; preserve that geometry while converting the rotation handedness.
        pose.mulPose(Axis.ZP.rotationDegrees(-r.z()));
        pose.mulPose(Axis.YP.rotationDegrees(r.y()));
        pose.mulPose(Axis.XP.rotationDegrees(-r.x()));
        pose.scale(s.x(), s.y(), s.z());
    }

    private void renderCube(Cube cube, Vec3 pivot, PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
        Vec3 start = cube.origin.subtract(pivot).scale(1.0 / 16), end = start.add(cube.size.scale(1.0 / 16));
        float x = (float)start.x, y = (float)start.y, z = (float)start.z;
        float X = (float)end.x, Y = (float)end.y, Z = (float)end.z;
        quad(cube.faces.get("north"), new float[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}}, 0,0,-1, stack,out,light,overlay,color);
        quad(cube.faces.get("south"), new float[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}}, 0,0,1, stack,out,light,overlay,color);
        quad(cube.faces.get("east"), new float[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}}, 1,0,0, stack,out,light,overlay,color);
        quad(cube.faces.get("west"), new float[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}}, -1,0,0, stack,out,light,overlay,color);
        quad(cube.faces.get("up"), new float[][]{{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}}, 0,1,0, stack,out,light,overlay,color);
        quad(cube.faces.get("down"), new float[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}}, 0,-1,0, stack,out,light,overlay,color);
    }
    private void quad(Face face, float[][] corners, float nx,float ny,float nz, PoseStack stack, VertexConsumer out,
                      int light, int overlay, int color) {
        if (face == null) return;
        float u0 = face.u / textureWidth, u1 = (face.u + face.width) / textureWidth;
        float v0 = face.v / textureHeight, v1 = (face.v + face.height) / textureHeight;
        float[][] uv = {{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
        var pose = stack.last();
        for (int i = 0; i < 4; i++) out.addVertex(pose, corners[i][0], corners[i][1], corners[i][2]).setColor(color)
                .setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
