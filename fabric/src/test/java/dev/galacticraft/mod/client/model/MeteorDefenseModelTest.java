package dev.galacticraft.mod.client.model;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.util.BurrowerAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MeteorDefenseModelTest {
    private JsonObject asset(String path) {
        return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/galacticraft/" + path)), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private JsonObject geometry(String name) {
        return asset("models/block/" + name + ".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }
    private BurrowerAnimations.Clip clip(String machine, String name) {
        return BurrowerAnimations.readClip(asset("animations/" + machine + ".animation.json")
                .getAsJsonObject("animations").getAsJsonObject("animation." + machine + "." + name));
    }

    @Test void exportedRigsBakeAndEveryAnimatedBoneExists() {
        for (String name : List.of("radar", "cannon")) {
            JsonObject geo = geometry(name);
            ModelPart model = MeteorDefenseModel.bake(geo);
            assertFalse(model.getChild("root").isEmpty() && model.getAllParts().count() == 1);
            Set<String> bones = new HashSet<>();
            geo.getAsJsonArray("bones").forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));
            asset("animations/" + name + ".animation.json").getAsJsonObject("animations").entrySet().forEach(entry -> {
                var clip = BurrowerAnimations.readClip(entry.getValue().getAsJsonObject());
                assertTrue(bones.containsAll(clip.boneNames()));
                for (String bone : clip.boneNames()) for (float time = 0; time <= clip.length; time += 0.025F) {
                    var pose = clip.sample(bone, time);
                    for (var vector : List.of(pose.position(), pose.rotation(), pose.scale()))
                        assertTrue(Float.isFinite(vector.x()) && Float.isFinite(vector.y()) && Float.isFinite(vector.z()));
                }
            });
        }
    }

    @Test void cannonMuzzleKeepsTheServerPivotAndAimsInEveryDirection() {
        ModelPart root = MeteorDefenseModel.bake(geometry("cannon")).getChild("root");
        ModelPart yaw = root.getChild("yaw"), pitch = yaw.getChild("pitch"), barrel = pitch.getChild("barrel");
        for (float heading : new float[]{0, 90, 180, 270}) for (float elevation : new float[]{-10, 15, 80}) {
            double h = Math.toRadians(heading), e = Math.toRadians(elevation);
            yaw.yRot = (float) h; pitch.zRot = (float) e;
            PoseStack stack = new PoseStack();
            stack.scale(-1, -1, 1);
            root.translateAndRotate(stack); yaw.translateAndRotate(stack); pitch.translateAndRotate(stack); barrel.translateAndRotate(stack);
            Vector3f muzzle = stack.last().pose().transformPosition(new Vector3f(-3, 0, 0));
            assertEquals(3 * Math.cos(e) * Math.cos(h), muzzle.x, 0.00001);
            assertEquals(2 + 3 * Math.sin(e), muzzle.y, 0.00001);
            assertEquals(3 * Math.cos(e) * Math.sin(h), muzzle.z, 0.00001);
        }
    }

    @Test void scanLoopsAndRecoilReturnsToRestWithoutRepeating() {
        var scan = clip("radar", "scan");
        assertTrue(scan.loop);
        assertEquals(scan.sample("scanner", 2), scan.sample("scanner", 2 + scan.length));
        assertNotEquals(scan.sample("scanner", 0), scan.sample("scanner", 2));
        var recoil = clip("cannon", "recoil");
        assertFalse(recoil.loop);
        assertEquals(3, recoil.sample("barrel", 0.05F).position().x());
        assertEquals(BurrowerAnimations.Pose.REST, recoil.sample("barrel", recoil.length + 10));
        var charge = clip("cannon", "charge");
        assertTrue(charge.loop);
        assertNotEquals(charge.sample("capacitor", 0), charge.sample("capacitor", 0.5F));
    }

    private ResourceManager resources() {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> new Resource(null,
                        () -> Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/"
                                + ((net.minecraft.resources.ResourceLocation) args[0]).getPath()))));
    }

    @Test void radarDishAlwaysPointsUpDuringItsScan() {
        var radar = new MeteorDefenseModel(resources(), "radar");
        for (double time = 0; time <= 8; time += 0.1) {
            radar.reset(); radar.animate("animation.radar.scan", time);
            PoseStack stack = new PoseStack();
            stack.scale(-1, -1, 1);
            for (String bone : List.of("root", "scanner", "dish")) radar.bone(bone).translateAndRotate(stack);
            Vector3f normal = stack.last().pose().transformDirection(new Vector3f(0, 0, -1));
            assertTrue(normal.y > 0.6, "Dish pointed down at " + time);
            if (time == 0) assertTrue(normal.z < 0, "Dish did not face authored north");
        }
    }

    @Test void rendererLoadsExportedAssetsAndResetsBetweenMachines() {
        ResourceManager resources = resources();
        var cannon = new MeteorDefenseModel(resources, "cannon");
        cannon.animate("animation.cannon.recoil", 0.05);
        assertEquals(3, cannon.bone("barrel").x);
        cannon.reset();
        assertEquals(0, cannon.bone("barrel").x);
        cannon.animate("animation.cannon.recoil", 50);
        assertEquals(0, cannon.bone("barrel").x);
        var radar = new MeteorDefenseModel(resources, "radar");
        radar.animate("animation.radar.scan", 2);
        float angle = radar.bone("scanner").yRot;
        radar.reset();
        assertEquals(0, radar.bone("scanner").yRot);
        radar.animate("animation.radar.scan", 2 + 8 * 100_000_000.0);
        assertEquals(angle, radar.bone("scanner").yRot);
    }

    @Test void fallingMeteorLoadsIndependentBodyAndFlameRigsAndLoopsCleanly() {
        var meteor = new MeteorDefenseModel(resources(), "entity", "falling_meteor");
        for (double time = 0; time <= 6; time += 0.05) {
            meteor.reset();
            meteor.animate("animation.falling_meteor.tumble", time);
            assertEquals(0, meteor.bone("burning").xRot);
            assertEquals(0, meteor.bone("burning").yRot);
            assertEquals(0, meteor.bone("burning").zRot);
            meteor.animate("animation.falling_meteor.burn", time);
            for (String bone : List.of("flame_main", "flame_west", "flame_east", "flame_north", "flame_south"))
                assertTrue(meteor.bone(bone).yScale >= 0.8f && meteor.bone(bone).yScale <= 1.3f);
        }
        meteor.reset();
        meteor.animate("animation.falling_meteor.tumble", 1.5);
        assertNotEquals(0, meteor.bone("body").xRot);
        meteor.reset();
        meteor.animate("animation.falling_meteor.tumble", 6);
        assertEquals(0, meteor.bone("body").xRot);
        meteor.animate("animation.falling_meteor.burn", 0.2);
        float flameHeight = meteor.bone("flame_main").yScale;
        meteor.reset();
        meteor.animate("animation.falling_meteor.burn", 1.0);
        assertEquals(flameHeight, meteor.bone("flame_main").yScale, 0.00001);
        meteor.reset();
        assertEquals(1, meteor.bone("flame_main").yScale);
    }
}
