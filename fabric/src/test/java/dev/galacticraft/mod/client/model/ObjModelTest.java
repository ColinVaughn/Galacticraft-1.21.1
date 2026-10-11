package dev.galacticraft.mod.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.types.UnbakedObjModel.BakedMaterial;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ObjModelTest {
    private static class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents, int x) {
            super(Constant.id("textures/atlas/test.png"), contents, 256, 128, x, 0);
        }
    }

    private SpriteContents contents() {
        return new SpriteContents(Constant.id("obj/test"), new FrameSize(128, 128),
                new NativeImage(128, 128, true), ResourceMetadata.EMPTY);
    }

    private InputStream asset(String path) {
        return Objects.requireNonNull(getClass().getResourceAsStream("/assets/galacticraft/" + path));
    }

    private List<float[]> render(ObjModel model, String part) {
        List<float[]> coordinates = new ArrayList<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setUv")) coordinates.add(new float[]{(float) args[0], (float) args[1]});
                    return proxy;
                });
        assertDoesNotThrow(() -> model.render(new PoseStack(), part == null ? null : new GCModelState(part),
                consumer, 0, 0, -1), "Material must resolve independently for " + part);
        return coordinates;
    }

    @Test
    void everyBuggyPartUsesItsAtlasWhenRenderedSeparatelyInAnyOrder() throws Exception {
        try (var geometry = asset("models/misc/buggy.obj"); var material = asset("models/misc/buggy.mtl");
             var contents = contents()) {
            Obj obj = ObjUtils.convertToRenderable(ObjReader.read(geometry));
            var sprite = new Sprite(contents, 128);
            var model = new ObjModel(obj, List.of(new BakedMaterial(MtlReader.read(material).getFirst(), sprite)));
            // Start with the first OBJ group, then draw later parts in reverse file order.
            for (String name : List.of("WheelRight", "SteeringWheel", "CargoRight", "CargoMid", "CargoLeft",
                    "RadarDish", "MainBody", "LeftFender", "RightFender", "WheelLeft")) {
                var coordinates = render(model, name);
                var group = obj.getGroup(name);
                int vertex = 0;
                for (int i = 0; i < group.getNumFaces(); i++) {
                    var face = group.getFace(i);
                    for (int v = 0; v < face.getNumVertices(); v++) {
                        var uv = obj.getTexCoord(face.getTexCoordIndex(v));
                        assertArrayEquals(new float[]{sprite.getU(uv.getX()), sprite.getV(1 - uv.getY())},
                                coordinates.get(vertex++), 0.000001F, name);
                    }
                }
                assertEquals(vertex, coordinates.size(), name);
            }
        }
    }

    @Test
    void interleavedGroupsKeepMaterialChangesFromOutsideTheirOwnFaces() throws Exception {
        Obj obj = ObjReader.read(new StringReader("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                vt 0 0
                vn 0 0 1
                g first
                usemtl a
                f 1/1/1 2/1/1 3/1/1
                g second
                f 1/1/1 2/1/1 3/1/1
                usemtl b
                f 1/1/1 2/1/1 3/1/1
                g first
                f 1/1/1 2/1/1 3/1/1
                """));
        var materials = MtlReader.read(new StringReader("newmtl a\nnewmtl b\n"));
        try (var contents = contents()) {
            var model = new ObjModel(obj, List.of(new BakedMaterial(materials.get(0), new Sprite(contents, 0)),
                    new BakedMaterial(materials.get(1), new Sprite(contents, 128))));
            assertEquals(List.of(0F, 0F, 0F, .5F, .5F, .5F),
                    render(model, "second").stream().map(uv -> uv[0]).toList());
            assertEquals(List.of(0F, 0F, 0F, .5F, .5F, .5F),
                    render(model, "first").stream().map(uv -> uv[0]).toList());
            assertEquals(List.of(0F, 0F, 0F, 0F, 0F, 0F, .5F, .5F, .5F, .5F, .5F, .5F),
                    render(model, null).stream().map(uv -> uv[0]).toList());
        }
    }
}
