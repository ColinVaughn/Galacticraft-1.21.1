package dev.galacticraft.mod.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.javagl.obj.MtlReader;
import de.javagl.obj.ObjReader;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.types.UnbakedObjModel.BakedMaterial;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RocketPaintModelTest {
    private java.io.InputStream asset(String path) { return getClass().getResourceAsStream("/assets/galacticraft/" + path); }

    @Test void eachTierUsesNeutralPaintAndRetainsFixedAndLegacyMaterials() throws Exception {
        class Sprite extends TextureAtlasSprite {
            Sprite(SpriteContents contents, int x) { super(Constant.id("textures/atlas/test.png"), contents, 256, 128, x, 0); }
        }
        try (var contents = new SpriteContents(Constant.id("obj/test"), new FrameSize(128, 128),
                new NativeImage(128, 128, true), ResourceMetadata.EMPTY)) {
            var original = new Sprite(contents, 0);
            var neutral = new Sprite(contents, 128);
            for (String part : List.of("body", "cone", "fins", "engine")) for (int tier = 1; tier <= 3; tier++) {
                String name = "rocket_" + part + "_tier_" + tier;
                try (var geometry = asset("models/misc/" + name + ".obj"); var mtl = asset("models/misc/" + name + ".mtl")) {
                    var obj = ObjReader.read(geometry);
                    var materials = MtlReader.read(mtl).stream().map(material -> new BakedMaterial(material, original,
                            material.getName().endsWith("_paint") ? neutral : null)).toList();
                    var model = new ObjModel(obj, materials);
                    List<Integer> colors = new ArrayList<>();
                    List<Float> uvs = new ArrayList<>();
                    VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VertexConsumer.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("setColor")) {
                                    if (args.length == 1) colors.add((int) args[0]);
                                    else colors.add((int) args[3] << 24 | (int) args[0] << 16 | (int) args[1] << 8 | (int) args[2]);
                                }
                                if (method.getName().equals("setUv")) uvs.add((float) args[0]);
                                return proxy;
                            });
                    model.renderPainted(new PoseStack(), null, consumer, 0, 0, 0xFF426BA8);
                    assertTrue(colors.contains(0xFF426BA8), name + " has no painted faces");
                    assertTrue(colors.contains(0xFFFFFFFF), name + " has no fixed faces");
                    assertTrue(uvs.stream().anyMatch(u -> u > .5F), name + " did not use the neutral sprite");
                    for (int group = 0; group < obj.getNumGroups(); group++) {
                        String groupName = obj.getGroup(group).getName();
                        if (groupName.startsWith("Leg") || List.of("Window", "Nozzle", "Glow").contains(groupName)) {
                            colors.clear();
                            model.renderPainted(new PoseStack(), new GCModelState(groupName), consumer, 0, 0, 0xFF426BA8);
                            assertTrue(colors.stream().allMatch(color -> color == 0xFFFFFFFF), name + ": " + groupName);
                        }
                    }
                    colors.clear();
                    uvs.clear();
                    model.render(new PoseStack(), null, consumer, 0, 0, 0x80426BA8);
                    assertTrue(colors.stream().allMatch(color -> color == 0x80426BA8), "Legacy tint changed");
                    assertTrue(uvs.stream().allMatch(u -> u <= .5F), "Legacy draw used neutral texture");
                }
            }
        }
    }

    @Test void neutralAtlasesRetainShadingDimensionsAndAlpha() throws Exception {
        for (String atlas : List.of("rocket_bodies", "rocket_cones", "rocket_fins", "rocket_engines")) {
            try (var source = asset("textures/obj/" + atlas + ".png"); var paint = asset("textures/obj/" + atlas + "_paint.png")) {
                var original = ImageIO.read(source);
                var neutral = ImageIO.read(paint);
                assertEquals(original.getWidth(), neutral.getWidth());
                assertEquals(original.getHeight(), neutral.getHeight());
                for (int y = 0; y < neutral.getHeight(); y++) for (int x = 0; x < neutral.getWidth(); x++) {
                    int pixel = neutral.getRGB(x, y);
                    assertEquals(original.getRGB(x, y) >>> 24, pixel >>> 24);
                    assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                    assertEquals(pixel >> 8 & 255, pixel & 255);
                }
            }
        }
    }
}