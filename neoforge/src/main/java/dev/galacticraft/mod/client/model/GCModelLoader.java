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

package dev.galacticraft.mod.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.galacticraft.mod.Constant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Native model registry; populated by the NeoForge client reload listener. */
public final class GCModelLoader {
    public static final GCModelLoader INSTANCE = new GCModelLoader();
    public static final GCModel MISSING_MODEL = new GCMissingModel();
    public static final ResourceLocation MODEL_LOADER_ID = Constant.id("model_loader");
    public static final ResourceLocation WHITE_SPRITE = Constant.id("obj/white");
    public static final ResourceLocation TYPE_KEY = Constant.id("type");
    static final Map<ResourceLocation, GCUnbakedModel.GCModelType> REGISTERED_TYPES = new ConcurrentHashMap<>();
    public static final Codec<GCUnbakedModel.GCModelType> MODEL_TYPE_CODEC = ResourceLocation.CODEC.flatXmap(id ->
                    Optional.ofNullable(REGISTERED_TYPES.get(id)).map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "No Galacticraft model type with id: " + id)),
            type -> DataResult.success(type.getId()));
    public static final Codec<GCUnbakedModel> MODEL_CODEC = MODEL_TYPE_CODEC.dispatch(
            TYPE_KEY.toString(), GCUnbakedModel::getType, GCUnbakedModel.GCModelType::codec);

    private volatile Map<ResourceLocation, GCModel> models = Map.of();
    private volatile AtlasSet atlases;

    public static void registerModelType(GCUnbakedModel.GCModelType type) { REGISTERED_TYPES.put(type.getId(), type); }
    public Map<ResourceLocation, GCModel> getModels() { return this.models; }
    public GCModel getModel(ResourceLocation id) { return this.models.getOrDefault(id, MISSING_MODEL); }
    public AtlasSet getAtlases() { return this.atlases; }
    public TextureAtlasSprite getDefaultSprite() {
        return this.atlases.getAtlas(GCRenderTypes.OBJ_ATLAS).getSprite(WHITE_SPRITE);
    }
    public synchronized void update(Map<ResourceLocation, GCModel> models, AtlasSet atlases) {
        this.models.values().forEach(model -> {
            try {
                model.close();
            } catch (Exception exception) {
                Constant.LOGGER.warn("Failed to close an old Galacticraft client model", exception);
            }
        });
        if (this.atlases != null) this.atlases.close();
        this.models = Map.copyOf(models);
        this.atlases = atlases;
    }

    private GCModelLoader() {}
}
