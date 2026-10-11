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

public final class Tier1RocketAnimations {
    private record Track(BurrowerAnimations.Clip clip, double length) { }
    private final Map<String, Track> clips = new HashMap<>();

    public Tier1RocketAnimations(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(Constant.id("animations/rocket_tier1.animation.json")).openAsReader()) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").entrySet().forEach(entry -> {
                var json = entry.getValue().getAsJsonObject();
                clips.put(entry.getKey(), new Track(BurrowerAnimations.readClip(json), json.get("animation_length").getAsDouble()));
            });
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load tier 1 rocket animations", error);
        }
    }

    public BurrowerAnimations.Pose sample(String clip, String bone, double time) {
        var track = clips.get("animation.rocket_tier1." + clip);
        if (track == null) throw new IllegalArgumentException("Missing tier 1 rocket clip: " + clip);
        // Preserve the authored loop period before reducing large tick ages to a float sample.
        var pose = track.clip.sample(bone, (float) (track.clip.loop ? (time % track.length + track.length) % track.length : time));
        return new BurrowerAnimations.Pose(
                new BurrowerAnimations.Vector(-pose.position().x(), pose.position().y(), pose.position().z()),
                new BurrowerAnimations.Vector(-pose.rotation().x(), -pose.rotation().y(), pose.rotation().z()), pose.scale());
    }

    public static void apply(PoseStack matrices, BurrowerAnimations.Pose pose, float weight) {
        if (weight == 0) return;
        matrices.translate(pose.position().x() * weight / 16, pose.position().y() * weight / 16, pose.position().z() * weight / 16);
        matrices.mulPose(Axis.ZP.rotationDegrees(pose.rotation().z() * weight));
        matrices.mulPose(Axis.YP.rotationDegrees(pose.rotation().y() * weight));
        matrices.mulPose(Axis.XP.rotationDegrees(pose.rotation().x() * weight));
        matrices.scale(1 + (pose.scale().x() - 1) * weight, 1 + (pose.scale().y() - 1) * weight, 1 + (pose.scale().z() - 1) * weight);
    }
}
