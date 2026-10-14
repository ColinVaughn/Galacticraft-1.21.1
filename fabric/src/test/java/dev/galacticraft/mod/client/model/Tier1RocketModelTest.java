package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.entity.rocket.render.RocketModelSlot;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.types.UnbakedObjModel.BakedMaterial;
import dev.galacticraft.mod.client.render.rocket.Tier1RocketPartRenderer;
import dev.galacticraft.mod.util.Tier1RocketAnimationState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.joml.Matrix4f;
import org.joml.Intersectionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class Tier1RocketModelTest {
    private static final Map<Tier1RocketPartRenderer.Part, String> PARTS = Map.of(
            Tier1RocketPartRenderer.Part.BODY, "rocket_body", Tier1RocketPartRenderer.Part.CONE, "rocket_cone_basic",
            Tier1RocketPartRenderer.Part.FINS, "rocket_fins", Tier1RocketPartRenderer.Part.ENGINE, "rocket_engine");
    private static final Map<Tier1RocketPartRenderer.Part, Set<String>> GROUPS = Map.of(
            Tier1RocketPartRenderer.Part.BODY, Set.of("Body", "Hatch", "Window"),
            Tier1RocketPartRenderer.Part.CONE, Set.of("Cone"),
            Tier1RocketPartRenderer.Part.FINS, Set.of("Fins", "LegEast", "LegWest", "LegSouth", "LegNorth"),
            Tier1RocketPartRenderer.Part.ENGINE, Set.of("Engine", "Nozzle", "Glow"));

    private InputStream asset(String path) {
        return Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/" + path));
    }

    private Obj geometry(String name) throws Exception {
        try (var stream = asset("models/misc/" + name + ".obj")) { return ObjReader.read(stream); }
    }

    private Tier1RocketAnimations animations() {
        var resources = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> new Resource(null, () -> asset(((net.minecraft.resources.ResourceLocation) args[0]).getPath())));
        return new Tier1RocketAnimations(resources);
    }

    private Tier1RocketPartRenderer renderer(Tier1RocketPartRenderer.Part part) {
        return new Tier1RocketPartRenderer(Constant.id("models/misc/" + PARTS.get(part) + ".json"), part);
    }

    @Test void everyModularPartRendersItsAtlasIncludingAnimatedGroups() throws Exception {
        class Sprite extends TextureAtlasSprite {
            Sprite(SpriteContents contents) { super(Constant.id("textures/atlas/test.png"), contents, 512, 256, 256, 0); }
        }
        try (var contents = new SpriteContents(Constant.id("obj/test"), new FrameSize(256, 256),
                new NativeImage(256, 256, true), ResourceMetadata.EMPTY)) {
            for (var entry : PARTS.entrySet()) {
                var obj = geometry(entry.getValue());
                for (int i = 0; i < obj.getNumFaces(); i++) {
                    var face = obj.getFace(i);
                    assertTrue(face.getNumVertices() == 3 || face.getNumVertices() == 4);
                    for (int v = 0; v < face.getNumVertices(); v++) {
                        var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                        var normal = obj.getNormal(face.getNormalIndex(v));
                        assertTrue(uv.getX() >= 0 && uv.getX() <= 1 && uv.getY() >= 0 && uv.getY() <= 1);
                        assertEquals(1, normal.getX() * normal.getX() + normal.getY() * normal.getY() + normal.getZ() * normal.getZ(), 0.00001);
                    }
                }
                try (var material = asset("models/misc/" + entry.getValue() + ".mtl")) {
                    var mtl = MtlReader.read(material).getFirst();
                    assertEquals("galacticraft:obj/rocket_tier1", mtl.getMapKdOptions().getFileName());
                    var model = new ObjModel(obj, List.of(new BakedMaterial(mtl, new Sprite(contents))));
                    List<Float> uvs = new ArrayList<>();
                    var consumer = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class},
                            (proxy, method, args) -> { if (method.getName().equals("setUv")) uvs.add((float) args[0]); return proxy; });
                    assertDoesNotThrow(() -> renderer(entry.getKey()).draw(model, animations(), new Tier1RocketAnimationState(),
                            LaunchStage.IDLE, 1, 0, 0, new PoseStack(), consumer, () -> consumer, 0, 0, -1));
                    assertFalse(uvs.isEmpty());
                    assertTrue(uvs.stream().allMatch(u -> u >= .5 && u <= 1), entry.getValue());
                }
            }
        }
        try (var texture = asset("textures/obj/rocket_tier1.png")) {
            var image = ImageIO.read(texture);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (x >= 144 && x < 152 && y >= 128 && y < 144) assertTrue(alpha > 0 && alpha <= 90);
                else assertEquals(255, alpha, "Transparency leaks out of the window UV patch");
            }
        }
    }

    @Test void tieredModulesKeepTheirMaterialsWhenEachAnimatedGroupIsDrawn() throws Exception {
        class Sprite extends TextureAtlasSprite {
            Sprite(SpriteContents contents) { super(Constant.id("textures/atlas/test.png"), contents, 512, 256, 256, 0); }
        }
        try (var contents = new SpriteContents(Constant.id("obj/test"), new FrameSize(256, 256),
                new NativeImage(256, 256, true), ResourceMetadata.EMPTY)) {
            var textures = Map.of(Tier1RocketPartRenderer.Part.ENGINE, "rocket_engines",
                    Tier1RocketPartRenderer.Part.BODY, "rocket_bodies", Tier1RocketPartRenderer.Part.FINS, "rocket_fins",
                    Tier1RocketPartRenderer.Part.CONE, "rocket_cones");
            for (var part : textures.keySet()) {
                String prefix = part == Tier1RocketPartRenderer.Part.CONE ? "rocket_cone" : PARTS.get(part);
                String textureName = textures.get(part);
                for (int tier = 1; tier <= 3; tier++) {
                    String name = prefix + "_tier_" + tier;
                    try (var descriptor = new InputStreamReader(asset("models/misc/" + name + ".json"));
                         var material = asset("models/misc/" + name + ".mtl")) {
                        var json = JsonParser.parseReader(descriptor).getAsJsonObject();
                        assertEquals("galacticraft:obj", json.get("galacticraft:type").getAsString());
                        assertEquals("galacticraft:models/misc/" + name + ".obj", json.get("model").getAsString());
                        assertEquals("galacticraft:models/misc/" + name + ".mtl", json.get("mtl").getAsString());
                        var materials = MtlReader.read(material);
                        var mtl = materials.getFirst();
                        assertEquals("galacticraft:obj/" + textureName, mtl.getMapKdOptions().getFileName());
                        var obj = geometry(name);
                        Set<String> exported = new HashSet<>();
                        for (int i = 0; i < obj.getNumGroups(); i++) if (obj.getGroup(i).getNumFaces() > 0)
                            exported.add(obj.getGroup(i).getName());
                        assertEquals(GROUPS.get(part), exported);
                        var model = new ObjModel(obj, materials.stream().map(entry -> new BakedMaterial(entry, new Sprite(contents))).toList());
                        for (String group : GROUPS.get(part)) {
                            assertTrue(obj.getGroup(group).getNumFaces() > 0);
                            List<Float> uvs = new ArrayList<>();
                            var consumer = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class},
                                    (proxy, method, args) -> { if (method.getName().equals("setUv")) uvs.add((float) args[0]); return proxy; });
                            model.render(new PoseStack(), new GCModelState(group), consumer, 0, 0, -1);
                            assertFalse(uvs.isEmpty());
                            assertTrue(uvs.stream().allMatch(u -> u >= .5 && u <= 1), name + ": " + group);
                        }
                    }
                }
            }
        }
        for (String name : List.of("rocket_engines", "rocket_fins", "rocket_cones")) {
            try (var texture = asset("textures/obj/" + name + ".png")) {
                var image = ImageIO.read(texture);
                assertEquals(128, image.getWidth());
                assertEquals(128, image.getHeight());
            }
        }
    }

    @Test void tieredConesFitEveryBodyAtTheSharedMount() throws Exception {
        float[] tips = {83.6F, 91, 78.4F};
        for (int tier = 1; tier <= 3; tier++) {
            String name = "rocket_cone_tier_" + tier;
            var part = new Tier1RocketPartRenderer(Constant.id("models/misc/" + name + ".json"), Tier1RocketPartRenderer.Part.CONE);
            var matrices = new PoseStack();
            RocketModelSlot.CONE.mount(matrices);
            var before = new Matrix4f(matrices.last().pose());
            var model = new RecordingModel();
            part.draw(model, animations(), new Tier1RocketAnimationState(), LaunchStage.LAUNCHED, 1, 3, 1,
                    matrices, null, () -> null, 0, 0, -1);
            assertEquals(before, matrices.last().pose());
            assertEquals(List.of("Cone"), model.calls);
            var points = groupVertices(geometry(name), "Cone");
            points.forEach(model.transforms.get("Cone")::transformPosition);
            assertTrue(points.stream().allMatch(Vector3f::isFinite));
            assertEquals(55.0 / 16, points.stream().mapToDouble(p -> p.y).min().orElseThrow(), .000001);
            assertEquals(tips[tier - 1] / 16, points.stream().mapToDouble(p -> p.y).max().orElseThrow(), .000001);
            var rim = points.stream().filter(p -> Math.abs(p.y - 55F / 16) < .000001 && p.x * p.x + p.z * p.z > .1).toList();
            assertFalse(rim.isEmpty());
            for (int bodyTier = 1; bodyTier <= 3; bodyTier++) {
                var bodyTop = groupVertices(geometry("rocket_body_tier_" + bodyTier), "Body").stream()
                        .filter(p -> Math.abs(p.y + 31F / 16 - 55F / 16) < .000001).toList();
                assertFalse(bodyTop.isEmpty());
                for (var point : rim) assertTrue(bodyTop.stream().anyMatch(p ->
                        Math.abs(p.x - point.x) < .000001 && Math.abs(p.z - point.z) < .000001),
                        name + " does not match body tier " + bodyTier);
            }
        }
        try (var texture = asset("textures/obj/rocket_cones.png")) {
            var image = ImageIO.read(texture);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                assertEquals(255, image.getRGB(x, y) >>> 24, "Cone texture must stay opaque");
        }
    }

    @Test void tieredFinsPreserveTheLandingLegsAndTheirMotion() throws Exception {
        var reference = geometry("rocket_fins");
        var animation = animations();
        for (int tier = 1; tier <= 3; tier++) {
            String name = "rocket_fins_tier_" + tier;
            var obj = geometry(name);
            for (String group : GROUPS.get(Tier1RocketPartRenderer.Part.FINS)) {
                if (group.equals("Fins")) continue;
                var expected = groupVertices(reference, group);
                var actual = groupVertices(obj, group);
                assertEquals(expected.size(), actual.size());
                for (var point : actual)
                    assertTrue(expected.stream().anyMatch(p -> p.distanceSquared(point) < 1e-10), name + ": " + group);
            }
            var part = new Tier1RocketPartRenderer(Constant.id("models/misc/" + name + ".json"), Tier1RocketPartRenderer.Part.FINS);
            var motion = new Tier1RocketAnimationState();
            for (int frame = 0; frame <= 21; frame++) {
                var model = new RecordingModel();
                var original = new RecordingModel();
                var matrices = new PoseStack();
                RocketModelSlot.FIN.mount(matrices);
                var before = new Matrix4f(matrices.last().pose());
                part.draw(model, animation, motion, LaunchStage.LAUNCHED, 1, 0, 1, matrices, null, () -> null, 0, 0, -1);
                assertEquals(before, matrices.last().pose());
                renderer(Tier1RocketPartRenderer.Part.FINS).draw(original, animation, motion, LaunchStage.LAUNCHED,
                        1, 0, 1, matrices, null, () -> null, 0, 0, -1);
                assertEquals(original.transforms, model.transforms);
                assertEquals(5, model.calls.size());
                for (var entry : model.transforms.entrySet()) {
                    for (var vertex : groupVertices(obj, entry.getKey())) {
                        var point = entry.getValue().transformPosition(vertex);
                        assertTrue(point.isFinite() && point.y >= 0, name + " reaches below the ground");
                        if (entry.getKey().equals("Fins")) assertTrue(point.y < 25.5 / 16, name + " obstructs the hatch");
                    }
                }
                motion.tick(LaunchStage.LAUNCHED, true);
            }
        }
        try (var fins = new InputStreamReader(asset("animations/rocket_fins.animation.json"));
             var rocket = new InputStreamReader(asset("animations/rocket_tier1.animation.json"))) {
            var finClips = JsonParser.parseReader(fins).getAsJsonObject().getAsJsonObject("animations");
            var rocketClips = JsonParser.parseReader(rocket).getAsJsonObject().getAsJsonObject("animations");
            assertEquals(Set.of("animation.rocket_tier1.gear"), finClips.keySet());
            assertEquals(rocketClips.get("animation.rocket_tier1.gear"), finClips.get("animation.rocket_tier1.gear"));
        }
    }

    private static List<Vector3f> groupVertices(Obj obj, String name) {
        var points = new ArrayList<Vector3f>();
        var group = obj.getGroup(name);
        for (int f = 0; f < group.getNumFaces(); f++) {
            var face = group.getFace(f);
            for (int v = 0; v < face.getNumVertices(); v++) {
                var vertex = obj.getVertex(face.getVertexIndex(v));
                points.add(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));
            }
        }
        return points;
    }

    @Test void tieredBodiesKeepTheirInterfacesAndGlassThroughoutHatchMotion() throws Exception {
        var cone = geometry("rocket_cone_basic");
        double coneBottom = java.util.stream.IntStream.range(0, cone.getNumVertices())
                .mapToDouble(i -> cone.getVertex(i).getY()).min().orElseThrow() + RocketModelSlot.CONE.y();
        var animation = animations();
        for (int tier = 1; tier <= 3; tier++) {
            var body = geometry("rocket_body_tier_" + tier);
            var engine = geometry("rocket_engine_tier_" + tier);
            double engineTop = java.util.stream.IntStream.range(0, engine.getNumVertices())
                    .mapToDouble(i -> engine.getVertex(i).getY()).max().orElseThrow() + RocketModelSlot.ENGINE.y();
            var part = new Tier1RocketPartRenderer(Constant.id("models/misc/rocket_body_tier_" + tier + ".json"), Tier1RocketPartRenderer.Part.BODY);
            var motion = new Tier1RocketAnimationState();
            for (int frame = 0; frame <= 11; frame++) {
                var model = new RecordingModel();
                var matrices = new PoseStack();
                RocketModelSlot.BODY.mount(matrices);
                var before = new Matrix4f(matrices.last().pose());
                part.draw(model, animation, motion, LaunchStage.IDLE, 1, 0, 1, matrices, null, () -> null, 0, 0, -1);
                assertEquals(before, matrices.last().pose());
                assertEquals(List.of("Body", "Hatch", "Window"), model.calls);
                assertEquals(model.transforms.get("Hatch"), model.transforms.get("Window"));
                assertEquals(new Vector3f(-4.4F / 16, 25.5F / 16, -11.3F / 16),
                        model.transforms.get("Hatch").transformPosition(new Vector3f()));
                double bottom = Double.POSITIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
                var group = body.getGroup("Body");
                for (int f = 0; f < group.getNumFaces(); f++) {
                    var face = group.getFace(f);
                    for (int v = 0; v < face.getNumVertices(); v++) {
                        var vertex = body.getVertex(face.getVertexIndex(v));
                        var point = model.transforms.get("Body").transformPosition(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));
                        assertTrue(point.isFinite());
                        bottom = Math.min(bottom, point.y);
                        top = Math.max(top, point.y);
                    }
                }
                assertEquals(engineTop, bottom, .000001);
                assertEquals(coneBottom, top, .000001);
                motion.tick(LaunchStage.IDLE, false);
            }
            assertTrue(body.getGroup("Window").getNumFaces() > 0);
            try (var texture = asset("textures/obj/rocket_bodies.png")) {
                var image = ImageIO.read(texture);
                assertEquals(256, image.getWidth());
                assertEquals(256, image.getHeight());
                var window = body.getGroup("Window");
                for (int f = 0; f < window.getNumFaces(); f++) {
                    var pane = window.getFace(f);
                    for (int v = 0; v < pane.getNumVertices(); v++) {
                        var uv = body.getTexCoord(pane.getTexCoordIndex(v));
                        assertTrue(uv.getX() >= 144F / 256 && uv.getX() <= 152F / 256);
                        assertTrue(uv.getY() >= 1 - 144F / 256 && uv.getY() <= 1 - 128F / 256);
                        int x = Math.min(151, (int) (uv.getX() * 256)), y = Math.min(143, (int) ((1 - uv.getY()) * 256));
                        int alpha = image.getRGB(x, y) >>> 24;
                        assertTrue(alpha > 0 && alpha <= 90, "Glass became opaque");
                    }
                }
            }
        }
        try (var bodies = new InputStreamReader(asset("animations/rocket_bodies.animation.json"));
             var rocket = new InputStreamReader(asset("animations/rocket_tier1.animation.json"))) {
            var bodyClips = JsonParser.parseReader(bodies).getAsJsonObject().getAsJsonObject("animations");
            var rocketClips = JsonParser.parseReader(rocket).getAsJsonObject().getAsJsonObject("animations");
            assertEquals(Set.of("animation.rocket_tier1.hatch"), bodyClips.keySet());
            assertEquals(rocketClips.get("animation.rocket_tier1.hatch"), bodyClips.get("animation.rocket_tier1.hatch"));
        }
    }

    @Test void tieredEnginesFitTheSharedMountThroughoutTheirAnimation() throws Exception {
        var body = geometry("rocket_body");
        double bodyBottom = java.util.stream.IntStream.range(0, body.getNumVertices())
                .mapToDouble(i -> body.getVertex(i).getY()).min().orElseThrow() + RocketModelSlot.BODY.y();
        var animation = animations();
        for (int tier = 1; tier <= 3; tier++) {
            String name = "rocket_engine_tier_" + tier;
            var obj = geometry(name);
            var part = new Tier1RocketPartRenderer(Constant.id("models/misc/" + name + ".json"), Tier1RocketPartRenderer.Part.ENGINE);
            for (int frame = 0; frame <= 32; frame++) {
                var model = new RecordingModel();
                var matrices = new PoseStack();
                RocketModelSlot.ENGINE.mount(matrices);
                var before = new Matrix4f(matrices.last().pose());
                part.draw(model, animation, new Tier1RocketAnimationState(), frame == 0 ? LaunchStage.IDLE : LaunchStage.LAUNCHED,
                        1, frame / 20.0, 0, matrices, null, () -> null, 0, 0, -1);
                assertEquals(before, matrices.last().pose());
                assertEquals(GROUPS.get(Tier1RocketPartRenderer.Part.ENGINE), model.transforms.keySet());
                assertEquals(9.0 / 16, model.transforms.get("Nozzle").transformPosition(new Vector3f()).y, .000001);
                double top = Double.NEGATIVE_INFINITY;
                for (var entry : model.transforms.entrySet()) {
                    var group = obj.getGroup(entry.getKey());
                    for (int f = 0; f < group.getNumFaces(); f++) {
                        var face = group.getFace(f);
                        for (int v = 0; v < face.getNumVertices(); v++) {
                            var vertex = obj.getVertex(face.getVertexIndex(v));
                            var point = entry.getValue().transformPosition(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));
                            assertTrue(point.isFinite() && point.y > 0, name + " touches the ground");
                            top = Math.max(top, point.y);
                        }
                    }
                }
                assertEquals(bodyBottom, top, .000001, name + " leaves a gap at the body mount");
            }
        }
        try (var engines = new InputStreamReader(asset("animations/rocket_engines.animation.json"));
             var rocket = new InputStreamReader(asset("animations/rocket_tier1.animation.json"))) {
            var engineClips = JsonParser.parseReader(engines).getAsJsonObject().getAsJsonObject("animations");
            var rocketClips = JsonParser.parseReader(rocket).getAsJsonObject().getAsJsonObject("animations");
            assertEquals(Set.of("animation.rocket_tier1.gimbal", "animation.rocket_tier1.exhaust"), engineClips.keySet());
            for (String clip : engineClips.keySet()) assertEquals(rocketClips.get(clip), engineClips.get(clip));
        }
    }

    @Test void boardingClosesTheHatchAndOnlyFlightRetractsTheLegs() {
        var state = new Tier1RocketAnimationState();
        state.tick(LaunchStage.IDLE, false);
        assertEquals(0, state.hatch(0));
        assertEquals(.05, state.hatch(.5F), .000001);
        for (int i = 0; i < 10; i++) state.tick(LaunchStage.WARNING, false);
        assertEquals(1, state.hatch(1));
        state.tick(LaunchStage.WARNING, true);
        assertTrue(state.hatch(1) < 1);
        for (int i = 0; i < 20; i++) state.tick(LaunchStage.IGNITED, false);
        assertEquals(0, state.hatch(1));
        assertEquals(0, state.gear(1));
        for (int i = 0; i < 21; i++) state.tick(LaunchStage.LAUNCHED, true);
        assertEquals(1, state.gear(1));
        assertEquals(0, state.hatch(1));
        for (int i = 0; i < 21; i++) state.tick(LaunchStage.FAILED, false);
        assertEquals(0, state.gear(1));
        assertEquals(0, state.hatch(1));
    }

    @Test void clipsMatchBlockbenchAxesAndHaveSafeFiniteMotion() {
        var animation = animations();
        assertEquals(70, animation.sample("hatch", "Hatch", 1).rotation().y());
        assertEquals(135, animation.sample("gear", "LegEast", 1).rotation().z());
        assertEquals(-135, animation.sample("gear", "LegWest", 1).rotation().z());
        assertEquals(-135, animation.sample("gear", "LegSouth", 1).rotation().x());
        assertEquals(135, animation.sample("gear", "LegNorth", 1).rotation().x());
        assertEquals(2, animation.sample("gimbal", "Nozzle", .4).rotation().x(), .000001);
        assertEquals(2, animation.sample("gimbal", "Nozzle", .4 + 104_000_000).rotation().x(), .000001);
        var clips = JsonParser.parseReader(new InputStreamReader(asset("animations/rocket_tier1.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        assertEquals(4, clips.size());
        for (double time = 0; time <= 1.6; time += .025) {
            var gimbal = animation.sample("gimbal", "Nozzle", time).rotation();
            assertTrue(Math.abs(gimbal.x()) <= 2 && Math.abs(gimbal.z()) <= 2);
            var glow = animation.sample("exhaust", "Glow", time).scale();
            assertTrue(glow.x() >= .89 && glow.x() <= 1.09);
            assertEquals(1, glow.y());
        }
    }

    @Test void manualLandingDeploysTheLegsWithoutOpeningTheHatch() {
        var motion = new Tier1RocketAnimationState();
        for (int i = 0; i < 21; i++) motion.tick(LaunchStage.LAUNCHED, true, false);
        assertEquals(1, motion.gear(1));
        motion.tick(LaunchStage.LAUNCHED, true, true);
        assertTrue(motion.gear(1) < 1);
        for (int i = 0; i < 21; i++) motion.tick(LaunchStage.LAUNCHED, true, true);
        assertEquals(0, motion.gear(1));
        assertEquals(0, motion.hatch(1));
        for (int i = 0; i < 21; i++) motion.tick(LaunchStage.LAUNCHED, true, false);
        assertEquals(1, motion.gear(1));
    }

    private static class RecordingModel implements GCModel {
        final Map<String, Matrix4f> transforms = new LinkedHashMap<>();
        final Map<String, Integer> lights = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        final Map<String, VertexConsumer> consumers = new LinkedHashMap<>();
        final Map<String, Integer> colors = new LinkedHashMap<>();
        @Override public void render(PoseStack matrices, GCModelState state, VertexConsumer consumer, int light, int overlay, int color) {
            calls.add(state.getName());
            consumers.put(state.getName(), consumer);
            colors.put(state.getName(), color);
            transforms.put(state.getName(), new Matrix4f(matrices.last().pose()));
            lights.put(state.getName(), light);
        }
        @Override public void close() { }
    }

    @Test void rendererUsesTheExportedPivotsAndDoesNotLoseOrDuplicateParts() throws Exception {
        Map<String, Matrix4f> transforms = new LinkedHashMap<>();
        for (var part : PARTS.keySet()) {
            var model = new RecordingModel();
            var matrices = new PoseStack();
            RocketModelSlot.valueOf(part == Tier1RocketPartRenderer.Part.FINS ? "FIN" : part.name()).mount(matrices);
            var before = new Matrix4f(matrices.last().pose());
            renderer(part).draw(model, animations(), new Tier1RocketAnimationState(), LaunchStage.IDLE, 1, 0, 0, matrices, null, () -> null, 0, 0, -1);
            assertEquals(before, matrices.last().pose());
            transforms.putAll(model.transforms);
            assertEquals(0, model.lights.getOrDefault("Glow", 0));
            var obj = geometry(PARTS.get(part));
            for (int i = 0; i < obj.getNumGroups(); i++) if (obj.getGroup(i).getNumFaces() > 0)
                assertTrue(model.transforms.containsKey(obj.getGroup(i).getName()));
        }
        assertEquals(12, transforms.size());
        assertEquals(transforms.get("Hatch"), transforms.get("Window"));
        assertEquals(new Vector3f(-4.4F / 16, 25.5F / 16, -11.3F / 16), transforms.get("Hatch").transformPosition(new Vector3f()));
        assertEquals(9.0 / 16, transforms.get("Nozzle").transformPosition(new Vector3f()).y, .000001);
        assertEquals(13.0 / 16, transforms.get("LegEast").transformPosition(new Vector3f()).y, .000001);
        assertEquals(11.4 / 16, transforms.get("LegEast").transformPosition(new Vector3f()).x, .000001);
        assertEquals(2.2 / 16, transforms.get("LegEast").transformPosition(new Vector3f()).z, .000001);
        var body = geometry("rocket_body");
        var cone = geometry("rocket_cone_basic");
        double bodyTop = java.util.stream.IntStream.range(0, body.getNumVertices()).mapToDouble(i -> body.getVertex(i).getY()).max().orElseThrow() + 31.0 / 16;
        double coneBottom = java.util.stream.IntStream.range(0, cone.getNumVertices()).mapToDouble(i -> cone.getVertex(i).getY()).min().orElseThrow() + 59.0 / 16;
        assertEquals(bodyTop, coneBottom, .000001);
        var engine = geometry("rocket_engine");
        double bodyBottom = java.util.stream.IntStream.range(0, body.getNumVertices()).mapToDouble(i -> body.getVertex(i).getY()).min().orElseThrow() + RocketModelSlot.BODY.y();
        double engineTop = java.util.stream.IntStream.range(0, engine.getNumVertices()).mapToDouble(i -> engine.getVertex(i).getY()).max().orElseThrow() + RocketModelSlot.ENGINE.y();
        assertEquals(bodyBottom, engineTop, .000001);
    }

    @Test void modulesOwnTheirGeometryAndAnimationsIndependentlyAtEveryPose() throws Exception {
        var clips = JsonParser.parseReader(new InputStreamReader(asset("animations/rocket_tier1.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        var owners = Map.of("hatch", Tier1RocketPartRenderer.Part.BODY, "gear", Tier1RocketPartRenderer.Part.FINS,
                "gimbal", Tier1RocketPartRenderer.Part.ENGINE, "exhaust", Tier1RocketPartRenderer.Part.ENGINE);
        for (var entry : owners.entrySet()) {
            var bones = clips.getAsJsonObject("animation.rocket_tier1." + entry.getKey()).getAsJsonObject("bones").keySet();
            assertFalse(bones.isEmpty());
            assertTrue(GROUPS.get(entry.getValue()).containsAll(bones), "Animation crosses a module boundary");
        }
        var animation = animations();
        for (var part : PARTS.keySet()) {
            var obj = geometry(PARTS.get(part));
            Set<String> exported = new HashSet<>();
            for (int i = 0; i < obj.getNumGroups(); i++) if (obj.getGroup(i).getNumFaces() > 0)
                exported.add(obj.getGroup(i).getName());
            assertEquals(GROUPS.get(part), exported, "Geometry leaks into a different slot");
            var motion = new Tier1RocketAnimationState();
            for (int i = 0; i < 11; i++) motion.tick(LaunchStage.IDLE, false);
            for (int frame = 0; frame <= 21; frame++) {
                var model = new RecordingModel();
                var matrices = new PoseStack();
                matrices.translate(3, 4, -2);
                matrices.scale(.5F, .5F, .5F);
                var before = new Matrix4f(matrices.last().pose());
                renderer(part).draw(model, animation, motion, frame == 0 ? LaunchStage.IDLE : LaunchStage.LAUNCHED, 1,
                        frame / 20.0, .5F, matrices, null, () -> null, 0, 0, -1);
                assertEquals(exported, model.transforms.keySet());
                assertEquals(exported.size(), model.calls.size(), "A child was rendered twice");
                assertEquals(before, matrices.last().pose(), "Animation moves the next module");
                motion.tick(LaunchStage.LAUNCHED, true);
            }
        }
    }

    @Test void missingSlotsDoNotChangeMountsAndItemAndEntityLayoutsAgree() {
        var slots = RocketModelSlot.values();
        double[] authoredY = {7.0 / 16, 15.0 / 16, 15.0 / 16, 31.0 / 16, 59.0 / 16};
        for (int mask = 0; mask < (1 << slots.length); mask++) {
            for (boolean item : new boolean[]{false, true}) {
                var matrices = new PoseStack();
                if (item) matrices.translate(0, -RocketModelSlot.ENGINE.y(), 0);
                var before = new Matrix4f(matrices.last().pose());
                for (int i = 0; i < slots.length; i++) if ((mask & (1 << i)) != 0) {
                    matrices.pushPose();
                    slots[i].mount(matrices);
                    assertEquals(authoredY[i] - (item ? 7.0 / 16 : 0),
                            matrices.last().pose().transformPosition(new Vector3f()).y, .000001);
                    matrices.popPose();
                    assertEquals(before, matrices.last().pose());
                }
            }
        }
    }

    @Test void engineGlowOnlyLightsWhileBurningAndGimbalMovesAroundItsMount() {
        var model = new RecordingModel();
        renderer(Tier1RocketPartRenderer.Part.ENGINE).draw(model, animations(), new Tier1RocketAnimationState(),
                LaunchStage.LAUNCHED, 1, .4, 0, new PoseStack(), null, () -> null, 7, 0, -1);
        assertEquals(LightTexture.FULL_BRIGHT, model.lights.get("Glow"));
        var mount = model.transforms.get("Nozzle").transformPosition(new Vector3f());
        assertEquals(2.0 / 16, mount.y, .000001);
        assertTrue(model.transforms.get("Nozzle").transformDirection(new Vector3f(0, 1, 0)).z > 0);
    }

    @Test void cuttingThrustTurnsOffEngineGlowAndGimbal() {
        var model = new RecordingModel();
        renderer(Tier1RocketPartRenderer.Part.ENGINE).draw(model, animations(), new Tier1RocketAnimationState(),
                LaunchStage.LAUNCHED, 0, .4, 0, new PoseStack(), null, () -> null, 7, 0, -1);
        assertEquals(7, model.lights.get("Glow"));
        assertEquals(0xFF30343C, model.colors.get("Glow"));
        assertEquals(0, model.transforms.get("Nozzle").transformDirection(new Vector3f(0, 1, 0)).z, .000001);
    }

    @Test void windowUsesItsOwnBufferAfterOpaqueDrawsAndFollowsTheHatch() {
        var opaque = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class}, (p, m, a) -> p);
        var glass = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class}, (p, m, a) -> p);
        var motion = new Tier1RocketAnimationState();
        for (int i = 0; i < 11; i++) motion.tick(LaunchStage.IDLE, false);
        var model = new RecordingModel();
        renderer(Tier1RocketPartRenderer.Part.BODY).draw(model, animations(), motion, LaunchStage.IDLE, 1,
                0, 1, new PoseStack(), opaque, () -> {
                    assertEquals(List.of("Body", "Hatch"), model.calls);
                    return glass;
                }, 7, 0, 0xFFAA0000);
        assertSame(opaque, model.consumers.get("Hatch"));
        assertSame(glass, model.consumers.get("Window"));
        assertEquals(0xFFFFFFFF, model.colors.get("Window"));
        assertEquals(model.transforms.get("Hatch"), model.transforms.get("Window"));
    }

    @Test void closedWindowHasAnUnobstructedViewIntoTheRiderSpace() throws Exception {
        for (String name : List.of("rocket_body", "rocket_body_tier_1", "rocket_body_tier_2", "rocket_body_tier_3")) {
            var model = new RecordingModel();
            var matrices = new PoseStack();
            RocketModelSlot.BODY.mount(matrices);
            new Tier1RocketPartRenderer(Constant.id("models/misc/" + name + ".json"), Tier1RocketPartRenderer.Part.BODY).draw(model, animations(), new Tier1RocketAnimationState(),
                    LaunchStage.IDLE, 1, 0, 0, matrices, null, () -> null, 0, 0, -1);
            var body = geometry(name);
            var window = body.getGroup("Window");
            assertTrue(window.getNumFaces() > 0);
            float bottom = Float.POSITIVE_INFINITY, top = Float.NEGATIVE_INFINITY;
            Float paneZ = null;
            for (int f = 0; f < window.getNumFaces(); f++) {
                var face = window.getFace(f);
                for (int v = 0; v < face.getNumVertices(); v++) {
                    var vertex = body.getVertex(face.getVertexIndex(v));
                    var point = model.transforms.get("Window").transformPosition(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));
                    bottom = Math.min(bottom, point.y);
                    top = Math.max(top, point.y);
                    if (paneZ == null) paneZ = point.z;
                    else assertEquals(paneZ, point.z, .000001, "Glass must be a single planar pane");
                }
            }
            for (float x : new float[]{-.1F, 0, .1F}) for (float fraction : new float[]{.25F, .5F, .75F}) {
                float y = bottom + (top - bottom) * fraction;
                var origin = new Vector3f(x, y, -20F / 16);
                var direction = new Vector3f(0, 0, 1);
                for (var entry : model.transforms.entrySet()) {
                    boolean intersects = false;
                    var group = body.getGroup(entry.getKey());
                    for (int f = 0; f < group.getNumFaces(); f++) {
                        var face = group.getFace(f);
                        var points = new Vector3f[face.getNumVertices()];
                        for (int v = 0; v < points.length; v++) {
                            var vertex = body.getVertex(face.getVertexIndex(v));
                            points[v] = entry.getValue().transformPosition(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));
                        }
                        for (int v = 1; v < points.length - 1; v++) {
                            float distance = Intersectionf.intersectRayTriangle(origin, direction, points[0], points[v], points[v + 1], .0000001F);
                            if (distance >= 0 && distance < 20F / 16) intersects = true;
                        }
                    }
                    assertEquals(entry.getKey().equals("Window"), intersects, "Opaque geometry blocks the rider: " + name + ": " + entry.getKey());
                }
            }
        }
    }
}
