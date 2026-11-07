package dev.galacticraft.mod.client.model;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import dev.galacticraft.mod.util.BurrowerAnimations;
import dev.galacticraft.mod.util.LanderAnimationState;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LanderModelTest {
    /** The OBJ groups LanderEntityRenderer draws, one per Blockbench animation bone. */
    private static final Set<String> BONES = Set.of("Body", "Cabin", "Feet", "Engine", "Glow", "Door", "DoorGlass", "Dish", "Whip");

    private InputStream asset(String path) {
        return Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/" + path));
    }

    private Obj obj() throws Exception {
        try (var stream = asset("models/misc/lander.obj")) {
            return ObjReader.read(stream);
        }
    }

    private LanderAnimations animations() {
        var resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> new Resource(null,
                        () -> asset(((net.minecraft.resources.ResourceLocation) args[0]).getPath())));
        return new LanderAnimations(resources);
    }

    /** Sampling mirrors axes, so rest can come back as -0; compare by value. */
    private static void assertRest(BurrowerAnimations.Pose pose, String message) {
        var rest = BurrowerAnimations.Pose.REST;
        for (var pair : new BurrowerAnimations.Vector[][]{{pose.position(), rest.position()},
                {pose.rotation(), rest.rotation()}, {pose.scale(), rest.scale()}}) {
            assertEquals(pair[1].x(), pair[0].x(), 0.00001, message);
            assertEquals(pair[1].y(), pair[0].y(), 0.00001, message);
            assertEquals(pair[1].z(), pair[0].z(), 0.00001, message);
        }
    }

    @Test
    void exportedBonesHaveValidQuadsOnPaintedPixels() throws Exception {
        Obj obj = obj();
        var groups = new HashSet<String>();
        IntStream.range(0, obj.getNumGroups()).filter(i -> obj.getGroup(i).getNumFaces() > 0)
                .forEach(i -> groups.add(obj.getGroup(i).getName()));
        assertEquals(BONES, groups);

        var image = ImageIO.read(asset("textures/obj/lander.png"));
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        var glass = new HashSet<de.javagl.obj.ObjFace>();
        var pane = obj.getGroup("DoorGlass");
        for (int i = 0; i < pane.getNumFaces(); i++) glass.add(pane.getFace(i));
        String material = null;
        for (int i = 0; i < obj.getNumFaces(); i++) {
            var face = obj.getFace(i);
            assertEquals(4, face.getNumVertices());
            // javagl assigns a material only to the face where usemtl activates it.
            if (obj.getActivatedMaterialGroupName(face) != null) material = obj.getActivatedMaterialGroupName(face);
            assertEquals("lander", material);
            float minU = 1, minV = 1, maxU = 0, maxV = 0;
            for (int v = 0; v < 4; v++) {
                var position = obj.getVertex(face.getVertexIndex(v));
                var normal = obj.getNormal(face.getNormalIndex(v));
                var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                for (int axis = 0; axis < 3; axis++) assertTrue(Float.isFinite(position.get(axis)));
                assertEquals(1, normal.getX() * normal.getX() + normal.getY() * normal.getY()
                        + normal.getZ() * normal.getZ(), 0.00001);
                minU = Math.min(minU, uv.getX()); maxU = Math.max(maxU, uv.getX());
                minV = Math.min(minV, 1 - uv.getY()); maxV = Math.max(maxV, 1 - uv.getY());
            }
            assertTrue(minU >= 0 && maxU <= 1 && minV >= 0 && maxV <= 1);
            int x0 = Math.round(minU * 256), x1 = Math.round(maxU * 256), y0 = Math.round(minV * 256), y1 = Math.round(maxV * 256);
            if (glass.contains(face)) {
                // The hatch window must be see-through in its middle.
                int alpha = image.getRGB((x0 + x1) / 2, (y0 + y1) / 2) >>> 24;
                assertTrue(alpha > 0 && alpha < 160, "Window glass alpha " + alpha);
                continue;
            }
            // Every other textured face must land on painted, opaque texels.
            for (int y = y0; y < y1; y++)
                for (int x = x0; x < x1; x++)
                    assertEquals(255, image.getRGB(x, y) >>> 24, "Unpainted texel " + x + "," + y);
        }
    }

    @Test
    void lightLayerSharesTheUvLayoutAndOnlyLightsRealFixtures() throws Exception {
        try (var stream = asset("models/misc/lander_emissive.mtl")) {
            var materials = MtlReader.read(stream);
            assertEquals(1, materials.size());
            assertEquals("lander", materials.getFirst().getName(), "Must match the OBJ's usemtl");
            assertEquals("galacticraft:obj/lander_emissive", materials.getFirst().getMapKdOptions().getFileName());
        }
        var lights = ImageIO.read(asset("textures/obj/lander_emissive.png"));
        assertEquals(256, lights.getWidth());
        assertEquals(256, lights.getHeight());
        // Lit texels must sit inside a drawn face of a group the renderer lights (not the glass or the engine).
        Obj obj = obj();
        var covered = new boolean[256][256];
        for (String name : List.of("Body", "Cabin", "Whip")) {
            var group = obj.getGroup(name);
            for (int i = 0; i < group.getNumFaces(); i++) {
                var face = group.getFace(i);
                float minU = 1, minV = 1, maxU = 0, maxV = 0;
                for (int v = 0; v < 4; v++) {
                    var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                    minU = Math.min(minU, uv.getX()); maxU = Math.max(maxU, uv.getX());
                    minV = Math.min(minV, 1 - uv.getY()); maxV = Math.max(maxV, 1 - uv.getY());
                }
                for (int y = Math.round(minV * 256); y < Math.round(maxV * 256); y++)
                    for (int x = Math.round(minU * 256); x < Math.round(maxU * 256); x++) covered[y][x] = true;
            }
        }
        int lit = 0;
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int argb = lights.getRGB(x, y);
            if ((argb & 0xFFFFFF) == 0) continue;
            lit++;
            assertTrue(covered[y][x], "Light texel " + x + "," + y + " is not on a lit face");
        }
        assertTrue(lit > 50, "Expected cabin, landing and navigation lights, got " + lit + " texels");
    }

    @Test
    void feetStayOnTheGroundUnderTheHitbox() throws Exception {
        Obj obj = obj();
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < obj.getNumVertices(); i++) {
            minY = Math.min(minY, obj.getVertex(i).getY());
            maxY = Math.max(maxY, obj.getVertex(i).getY());
        }
        assertEquals(0, minY, 0.0001);
        assertTrue(maxY <= 4, "Lander must fit its 4-block-tall hitbox");
    }

    @Test
    void materialResolvesToTheLanderAtlasSprite() throws Exception {
        try (var stream = asset("models/misc/lander.mtl")) {
            var materials = MtlReader.read(stream);
            assertEquals(1, materials.size());
            assertEquals("lander", materials.getFirst().getName());
            assertEquals("galacticraft:obj/lander", materials.getFirst().getMapKdOptions().getFileName());
        }
    }

    @Test
    void exportedClipsDriveOnlyExportedBones() {
        var json = JsonParser.parseReader(new InputStreamReader(asset("animations/lander.animation.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations");
        assertEquals(Set.of("animation.lander.door", "animation.lander.touchdown", "animation.lander.gimbal",
                "animation.lander.glow", "animation.lander.antenna"), json.keySet());
        json.entrySet().forEach(entry -> {
            var clip = BurrowerAnimations.readClip(entry.getValue().getAsJsonObject());
            assertTrue(BONES.containsAll(clip.boneNames()), entry.getKey());
            for (String bone : clip.boneNames()) for (float time = 0; time <= clip.length; time += 0.025F) {
                var pose = clip.sample(bone, time);
                for (var v : new BurrowerAnimations.Vector[]{pose.position(), pose.rotation(), pose.scale()})
                    assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z()));
            }
        });
        var touchdown = BurrowerAnimations.readClip(json.getAsJsonObject("animation.lander.touchdown"));
        assertFalse(touchdown.loop);
        for (String bone : touchdown.boneNames())
            assertRest(touchdown.sample(bone, touchdown.length), bone + " must settle at rest");
    }

    @Test
    void openDoorSwingsOutOfTheCabinOnItsHinge() {
        var matrices = new PoseStack();
        LanderAnimations.apply(matrices, 6, 29, -16.5F, animations().sample("door", "Door", 1), 1);
        var freeEdge = matrices.last().pose().transformPosition(new Vector3f(-6 / 16F, 38 / 16F, -17 / 16F));
        assertTrue(freeEdge.z < -26 / 16F, "Door should open outward, toward the porch");
        var hinge = matrices.last().pose().transformPosition(new Vector3f(6 / 16F, 38 / 16F, -16.5F / 16));
        assertEquals(6 / 16F, hinge.x, 0.0001);
        assertEquals(-16.5F / 16, hinge.z, 0.0001);
    }

    @Test
    void touchdownPlaysOnlyWhenLandingAndTheDoorWaitsForAnEmptyCabin() {
        var animation = animations();
        var state = new LanderAnimationState();
        state.tick(true, false, false);
        assertRest(animation.sample("touchdown", "Body", state.touchdownTime(0)), "A lander loaded on the ground must not bounce");

        state.tick(false, true, true);
        state.tick(true, true, false);
        assertTrue(animation.sample("touchdown", "Body", state.touchdownTime(1)).position().y() < -1,
                "Landing should compress the struts");
        assertEquals(0, state.door(1), "Door stays shut with a pilot aboard");
        for (int i = 0; i < 20; i++) state.tick(true, false, false);
        assertEquals(1, state.door(1));
        assertEquals(0, state.thrust(1));
        assertRest(animation.sample("touchdown", "Body", state.touchdownTime(1)), "Touchdown should settle");
    }
}
