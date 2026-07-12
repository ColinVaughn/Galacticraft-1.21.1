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

package dev.galacticraft.mod.client.model.types;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.model.GCUnbakedModel;
import dev.galacticraft.mod.client.model.ObjModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** OBJ loader used by the native NeoForge reload pipeline. */
public final class UnbakedObjModel implements GCUnbakedModel {
    public static final ObjType TYPE = new ObjType();
    private final ResourceLocation model;
    private final ResourceLocation material;
    private final Optional<ResourceLocation> atlas;

    public UnbakedObjModel(ResourceLocation model, ResourceLocation material, Optional<ResourceLocation> atlas) {
        this.model = model;
        this.material = material;
        this.atlas = atlas;
    }

    @Override public GCModelType getType() { return TYPE; }

    @Override
    public GCModel bake(ResourceManager resources, Function<Material, TextureAtlasSprite> sprites) {
        try {
            Obj obj = ObjReader.read(resources.getResourceOrThrow(this.model).open());
            List<BakedMaterial> baked = new ArrayList<>();
            for (Mtl mtl : MtlReader.read(resources.getResourceOrThrow(this.material).open())) {
                if (mtl.getMapKdOptions() != null && mtl.getMapKdOptions().getFileName() != null) {
                    var texture = ResourceLocation.parse(mtl.getMapKdOptions().getFileName());
                    baked.add(new BakedMaterial(mtl, sprites.apply(new Material(this.atlas.orElse(GCRenderTypes.OBJ_ATLAS), texture))));
                }
            }
            return new ObjModel(obj, baked);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public record BakedMaterial(Mtl material, TextureAtlasSprite sprite) {}

    public static final class ObjType implements GCModelType {
        public static final ResourceLocation ID = Constant.id("obj");
        public static final MapCodec<UnbakedObjModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("model").forGetter(value -> value.model),
                ResourceLocation.CODEC.fieldOf("mtl").forGetter(value -> value.material),
                ResourceLocation.CODEC.optionalFieldOf("atlas").forGetter(value -> value.atlas)
        ).apply(instance, UnbakedObjModel::new));
        @Override public MapCodec<? extends GCUnbakedModel> codec() { return CODEC; }
        @Override public ResourceLocation getId() { return ID; }
    }
}
