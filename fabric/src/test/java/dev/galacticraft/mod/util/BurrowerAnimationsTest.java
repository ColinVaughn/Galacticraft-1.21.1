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

import com.google.gson.JsonParser;
import dev.galacticraft.mod.content.entity.BurrowerState;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class BurrowerAnimationsTest {
    private Reader reader(String path) { return new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/" + path)), StandardCharsets.UTF_8); }
    private BurrowerAnimations library() { return BurrowerAnimations.read(reader("animations/lunar_burrower.animation.json")); }
    @Test void ridingExportMatchesTheSharedWaveAndDelayedTracksWrapTheLoop() {
        var clip = library().clip(BurrowerState.RIDE);
        for (int section = 1; section <= 6; section++) for (int frame = 0; frame < 24; frame++) {
            float time = frame / 10F;
            var exported = clip.sample("body_" + section, time).position();
            var wave = BurrowerPath.rideOffset(1.375 + section - 1,
                    BurrowerPath.RIDE_STRIDE * frame / 24, 1);
            assertEquals(wave.x, exported.x() / 16, 0.00001);
            assertEquals(wave.y, exported.y() / 16, 0.00001);
        }
        var delayed = clip.sample("body_6_shape", -0.4F).scale();
        var wrapped = clip.sample("body_6_shape", clip.length - 0.4F).scale();
        assertEquals(delayed.x(), wrapped.x(), 0.00001);
        assertEquals(delayed.y(), wrapped.y(), 0.00001);
    }
    @Test
    void everyStateHasMovingFiniteTracksAndMatchesTheServerTiming() {
        for (BurrowerState state : BurrowerState.values()) validateState(state);
    }
    private void validateState(BurrowerState state) {
        var clip = library().clip(state);
        assertEquals(state.clipTicks / 20.0F, clip.length, 0.0001F);
        assertEquals(state.loop, clip.loop);
        assertTrue(clip.boneNames().size() >= 25);
        boolean moved = false;
        for (String bone : clip.boneNames()) {
            var initial = clip.sample(bone, 0);
            for (int frame = 1; frame <= state.clipTicks * 2; frame++) {
                var pose = clip.sample(bone, frame / 40.0F);
                for (var v : List.of(pose.position(), pose.rotation(), pose.scale()))
                    assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z()), state + "/" + bone);
                assertTrue(pose.scale().x() > 0 && pose.scale().y() > 0 && pose.scale().z() > 0);
                moved |= !initial.equals(pose);
            }
        }
        assertTrue(moved, state + " was a static pose");
        if (state.loop) {
            var first = clip.sample("head", 0.33F); var repeated = clip.sample("head", clip.length + 0.33F);
            for (var pair : List.of(List.of(first.position(), repeated.position()), List.of(first.rotation(), repeated.rotation()), List.of(first.scale(), repeated.scale()))) {
                assertEquals(pair.get(0).x(), pair.get(1).x(), 0.0001F);
                assertEquals(pair.get(0).y(), pair.get(1).y(), 0.0001F);
                assertEquals(pair.get(0).z(), pair.get(1).z(), 0.0001F);
            }
        }
        else assertEquals(clip.sample("head", clip.length), clip.sample("head", clip.length + 10));
    }
    @Test void exportedTracksReferenceRealBonesAndSwallowUsesAllBodySegments() {
        var geometry = JsonParser.parseReader(reader("models/entity/lunar_burrower.geo.json")).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Set<String> bones = new HashSet<>();
        geometry.getAsJsonArray("bones").forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));
        var animations = library();
        for (BurrowerState state : BurrowerState.values()) assertTrue(bones.containsAll(animations.clip(state).boneNames()));
        float previousPeak = 0;
        for (int i = 1; i <= 6; i++) {
            var clip = animations.clip(BurrowerState.EAT);
            float peak = 1, peakTime = 0;
            for (int frame = 40; frame <= 88; frame++) {
                float time = frame / 20.0F;
                float width = clip.sample("body_" + i + "_shape", time).scale().x();
                if (width > peak) { peak = width; peakTime = time; }
            }
            assertTrue(peak > 1.06F, "Missing swallow bulge on segment " + i);
            assertTrue(peakTime >= previousPeak, "Swallow bulge did not travel toward the tail");
            previousPeak = peakTime;
        }
    }
    @Test void emissiveMaskHasOnlySparseOpaqueOrgansAndTransparentArmor() throws IOException {
        var image = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/textures/entity/lunar_burrower_glow.png")));
        assertEquals(128, image.getWidth()); assertEquals(128, image.getHeight());
        int lit = 0;
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) if ((image.getRGB(x, y) >>> 24) != 0) lit++;
        assertTrue(lit > 0 && lit <= 1024, "Glow mask lights the armor or is empty");
        assertEquals(0, image.getRGB(100, 100) >>> 24);
    }
    @Test void generatedSpawnTablesKeepTheBurrowerOnTheMoon() {
        for (String biome : List.of("lunar_highlands", "lunar_lowlands", "basaltic_mare", "comet_tundra", "olivine_spikes", "ray_crater_field", "cheese_grove", "mars", "mars_canyon", "mars_dunes", "mars_highlands", "mars_polar", "mars_volcanics")) {
            var resource = Objects.requireNonNull(getClass().getResourceAsStream("/data/galacticraft/worldgen/biome/" + biome + ".json"));
            var json = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean listed = false;
            for (var entry : json.getAsJsonObject("spawners").getAsJsonArray("monster"))
                listed |= entry.getAsJsonObject().get("type").getAsString().equals("galacticraft:lunar_burrower");
            assertEquals(!biome.startsWith("mars"), listed, biome);
        }
    }
    @Test void runtimeMeshLoadsTheExportedHierarchyAndFaceUvs() {
        net.minecraft.server.packs.resources.ResourceManager resources = (net.minecraft.server.packs.resources.ResourceManager)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class[]{net.minecraft.server.packs.resources.ResourceManager.class}, (proxy, method, args) -> {
                            if (method.getName().equals("getResourceOrThrow")) {
                                var id = (net.minecraft.resources.ResourceLocation)args[0];
                                return new net.minecraft.server.packs.resources.Resource(null, () ->
                                        Objects.requireNonNull(getClass().getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath())));
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        assertDoesNotThrow(() -> new dev.galacticraft.mod.client.model.entity.LunarBurrowerModel(resources));
    }
    @Test void luminousBlockModelsSeparateCoreMaterialFromTheirStoneShell() {
        for (String block : List.of("helion_nodules", "helion_wall_crust", "helium_3_vent")) {
            var model = JsonParser.parseReader(reader("models/block/" + block + ".json")).getAsJsonObject();
            Set<String> materials = new HashSet<>();
            model.getAsJsonObject("textures").entrySet().forEach(e -> materials.add(e.getValue().getAsString()));
            assertTrue(materials.contains("galacticraft:block/vesicular_lunar_basalt"));
            assertTrue(materials.contains("galacticraft:block/helion_core") || materials.contains("galacticraft:block/helium_3_surface"));
        }
    }
}

