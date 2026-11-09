package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Plays the lander's Blockbench clips on its OBJ bone groups. */
public final class LanderAnimations {
    private final Map<String, BurrowerAnimations.Clip> clips = new HashMap<>();

    public LanderAnimations(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(Constant.id("animations/lander.animation.json")).openAsReader()) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").entrySet().forEach(entry ->
                    clips.put(entry.getKey(), BurrowerAnimations.readClip(entry.getValue().getAsJsonObject())));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load lander animations", error);
        }
    }

    public BurrowerAnimations.Pose sample(String clip, String bone, double time) {
        var track = clips.get("animation.lander." + clip);
        if (track == null) throw new IllegalArgumentException("Missing lander clip: " + clip);
        // Reduce large times before narrowing to float; long worlds must retain smooth animation.
        var pose = track.sample(bone, (float) (track.loop ? (time % track.length + track.length) % track.length : time));
        // Bedrock exports mirror X and negate X/Y rotations; the OBJ uses Blockbench world axes.
        return new BurrowerAnimations.Pose(
                new BurrowerAnimations.Vector(-pose.position().x(), pose.position().y(), pose.position().z()),
                new BurrowerAnimations.Vector(-pose.rotation().x(), -pose.rotation().y(), pose.rotation().z()), pose.scale());
    }

    /** Applies a pose about a bone pivot given in Blockbench units. */
    public static void apply(PoseStack matrices, float pivotX, float pivotY, float pivotZ, BurrowerAnimations.Pose pose, float weight) {
        if (weight == 0) return;
        matrices.translate((pivotX + pose.position().x() * weight) / 16, (pivotY + pose.position().y() * weight) / 16,
                (pivotZ + pose.position().z() * weight) / 16);
        // Match Blockbench's ZYX Euler order, with X applied first to vertices.
        matrices.mulPose(Axis.ZP.rotationDegrees(pose.rotation().z() * weight));
        matrices.mulPose(Axis.YP.rotationDegrees(pose.rotation().y() * weight));
        matrices.mulPose(Axis.XP.rotationDegrees(pose.rotation().x() * weight));
        matrices.scale(1 + (pose.scale().x() - 1) * weight, 1 + (pose.scale().y() - 1) * weight, 1 + (pose.scale().z() - 1) * weight);
        matrices.translate(-pivotX / 16, -pivotY / 16, -pivotZ / 16);
    }
}
