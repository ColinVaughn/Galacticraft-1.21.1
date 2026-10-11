package dev.galacticraft.mod.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.MtlReader;
import dev.galacticraft.mod.client.render.entity.BuggyRenderer;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class BuggyModelTest {
    private InputStream asset(String path) {
        return Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/" + path));
    }

    @Test
    void exportedPartsHaveValidQuadsNormalsAndAtlasCoordinates() throws Exception {
        Obj obj;
        try (var stream = asset("models/misc/buggy.obj")) {
            obj = ObjReader.read(stream);
        }
        for (var name : List.of("MainBody", "WheelLeft", "WheelRight", "LeftFender",
                "RightFender", "RadarDish", "CargoLeft", "CargoMid", "CargoRight", "SteeringWheel")) {
            assertNotNull(obj.getGroup(name), name);
            assertTrue(obj.getGroup(name).getNumFaces() > 0, name);
        }
        assertEquals(10, java.util.stream.IntStream.range(0, obj.getNumGroups())
                .filter(i -> obj.getGroup(i).getNumFaces() > 0).count(),
                "Preview wheel copies must not ship as extra groups");
        String material = null;
        for (int i = 0; i < obj.getNumFaces(); i++) {
            var face = obj.getFace(i);
            assertEquals(4, face.getNumVertices());
            if (obj.getActivatedMaterialGroupName(face) != null) material = obj.getActivatedMaterialGroupName(face);
            assertEquals("buggy_rover", material);
            for (int v = 0; v < 4; v++) {
                var position = obj.getVertex(face.getVertexIndex(v));
                var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                var normal = obj.getNormal(face.getNormalIndex(v));
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(Float.isFinite(position.get(axis)));
                    assertTrue(Float.isFinite(normal.get(axis)));
                }
                assertTrue(uv.getX() >= 0 && uv.getX() <= 1);
                assertTrue(uv.getY() >= 0 && uv.getY() <= 1);
                assertEquals(1, normal.getX() * normal.getX() + normal.getY() * normal.getY()
                        + normal.getZ() * normal.getZ(), 0.00001);
            }
        }
    }

    @Test
    void materialResolvesToAnOpaquePixelAtlas() throws Exception {
        try (var stream = asset("models/misc/buggy.mtl")) {
            var materials = MtlReader.read(stream);
            assertEquals(1, materials.size());
            assertEquals("buggy_rover", materials.getFirst().getName());
            assertEquals("galacticraft:obj/buggy_rover", materials.getFirst().getMapKdOptions().getFileName());
        }
        try (var stream = asset("textures/obj/buggy_rover.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                assertEquals(255, image.getRGB(x, y) >>> 24);
        }
    }

    @Test
    void storageVariantsDrawOnlyTheirInstalledBoxes() {
        class RecordingModel implements GCModel {
            final List<String> parts = new ArrayList<>();
            @Override public void render(PoseStack matrices, GCModelState state, VertexConsumer consumer,
                                         int light, int overlay, int color) { parts.add(state.getName()); }
            @Override public void close() { }
        }
        var model = new RecordingModel();
        BuggyRenderer.renderCargo(model, Buggy.BuggyType.NORMAL, null, null, 0, 0);
        assertTrue(model.parts.isEmpty());
        BuggyRenderer.renderCargo(model, Buggy.BuggyType.STORAGE_18, null, null, 0, 0);
        assertEquals(List.of("CargoMid"), model.parts);
        model.parts.clear();
        BuggyRenderer.renderCargo(model, Buggy.BuggyType.STORAGE_36, null, null, 0, 0);
        assertEquals(List.of("CargoLeft", "CargoRight"), model.parts);
    }
}
