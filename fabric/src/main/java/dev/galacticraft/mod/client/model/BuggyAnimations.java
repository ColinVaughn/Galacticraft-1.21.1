package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Plays the numeric Blockbench clips on the buggy's existing OBJ part groups. */
public final class BuggyAnimations {
    public static final float UNITS_PER_OBJ = 16 * 0.41F;
    private final Map<String, BurrowerAnimations.Clip> clips = new HashMap<>();

    public BuggyAnimations(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(Constant.id("animations/buggy.animation.json")).openAsReader()) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").entrySet().forEach(entry ->
                    clips.put(entry.getKey(), BurrowerAnimations.readClip(entry.getValue().getAsJsonObject())));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load buggy animations", error);
        }
    }

    public BurrowerAnimations.Pose sample(String clip, String bone, double time) {
        var track = clips.get("animation.buggy." + clip);
        if (track == null) throw new IllegalArgumentException("Missing buggy clip: " + clip);
        // Reduce large times before narrowing to float; long worlds must retain smooth animation.
        var pose = track.sample(bone, (float) (track.loop ? (time % track.length + track.length) % track.length : time));
        // Bedrock exports mirror X and negate X/Y rotations; the OBJ uses Blockbench world axes.
        return new BurrowerAnimations.Pose(
                new BurrowerAnimations.Vector(-pose.position().x(), pose.position().y(), pose.position().z()),
                new BurrowerAnimations.Vector(-pose.rotation().x(), -pose.rotation().y(), pose.rotation().z()), pose.scale());
    }

    public BurrowerAnimations.Pose steering(String bone, float degrees) {
        return sample("steer", bone, (Mth.clamp(degrees, -30, 30) + 30) / 60);
    }

    public static void apply(PoseStack matrices, BurrowerAnimations.Pose pose, float weight) {
        if (weight == 0) return;
        matrices.translate(pose.position().x() * weight / UNITS_PER_OBJ,
                pose.position().y() * weight / UNITS_PER_OBJ, pose.position().z() * weight / UNITS_PER_OBJ);
        // Match Blockbench's ZYX Euler order, with X applied first to vertices.
        matrices.mulPose(Axis.ZP.rotationDegrees(pose.rotation().z() * weight));
        matrices.mulPose(Axis.YP.rotationDegrees(pose.rotation().y() * weight));
        matrices.mulPose(Axis.XP.rotationDegrees(pose.rotation().x() * weight));
    }
}
