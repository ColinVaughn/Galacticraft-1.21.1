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

package dev.galacticraft.neoforge.client;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.model.GCUnbakedModel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reloads Galacticraft's JSON-described OBJ meshes and their private atlas. */
public final class GCNeoModelReloadListener implements PreparableReloadListener {
    public static final GCNeoModelReloadListener INSTANCE = new GCNeoModelReloadListener();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models/misc");

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
            ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
            Executor backgroundExecutor, Executor gameExecutor) {
        AtlasSet atlases = new AtlasSet(Map.of(GCRenderTypes.OBJ_ATLAS, Constant.id("obj")),
                Minecraft.getInstance().getTextureManager());
        var modelsFuture = loadModels(resources, backgroundExecutor);
        var stitches = atlases.scheduleLoad(resources, Minecraft.getInstance().options.mipmapLevels().get(), backgroundExecutor);
        return CompletableFuture.allOf(Stream.concat(stitches.values().stream(), Stream.of(modelsFuture))
                        .toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> bake(stitches.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().join())), modelsFuture.join(), resources), backgroundExecutor)
                .thenCompose(result -> result.readyForUpload.thenApply(ignored -> result))
                .thenCompose(barrier::wait)
                .thenAcceptAsync(result -> {
                    result.preparations.values().forEach(AtlasSet.StitchResult::upload);
                    GCModelLoader.INSTANCE.update(result.models, atlases);
                }, gameExecutor);
    }

    private static ReloadState bake(Map<ResourceLocation, AtlasSet.StitchResult> preparations,
            Map<ResourceLocation, GCUnbakedModel> unbaked, ResourceManager resources) {
        Map<ResourceLocation, GCModel> baked = new HashMap<>();
        Multimap<ResourceLocation, Material> missing = HashMultimap.create();
        unbaked.forEach((id, model) -> baked.put(id, model.bake(resources, material -> {
            var stitch = preparations.get(material.atlasLocation());
            var sprite = stitch.getSprite(material.texture());
            if (sprite == null) {
                missing.put(id, material);
                return stitch.missing();
            }
            return sprite;
        })));
        missing.asMap().forEach((id, materials) -> Constant.LOGGER.warn("Missing OBJ textures in {}: {}", id, materials));
        CompletableFuture<Void> ready = CompletableFuture.allOf(preparations.values().stream()
                .map(AtlasSet.StitchResult::readyForUpload).toArray(CompletableFuture[]::new));
        return new ReloadState(baked, preparations, ready);
    }

    private static CompletableFuture<Map<ResourceLocation, GCUnbakedModel>> loadModels(ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> MODEL_LISTER.listMatchingResources(resources), executor)
                .thenCompose(found -> {
                    List<CompletableFuture<Pair<ResourceLocation, GCUnbakedModel>>> tasks = new ArrayList<>();
                    for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
                        tasks.add(CompletableFuture.supplyAsync(() -> readModel(entry), executor));
                    }
                    return Util.sequence(tasks).thenApply(models -> models.stream().filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
                });
    }

    private static Pair<ResourceLocation, GCUnbakedModel> readModel(Map.Entry<ResourceLocation, Resource> entry) {
        try (Reader reader = entry.getValue().openAsReader()) {
            JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
            DataResult<GCUnbakedModel> parsed = GCModelLoader.MODEL_CODEC.parse(JsonOps.INSTANCE,
                    GsonHelper.convertToJsonObject(json, "model"));
            return Pair.of(entry.getKey(), parsed.getOrThrow(message ->
                    new IllegalArgumentException("Failed to load " + entry.getKey() + ": " + message)));
        } catch (Exception exception) {
            Constant.LOGGER.error("Failed to load Galacticraft model {}", entry.getKey(), exception);
            return null;
        }
    }

    private record ReloadState(Map<ResourceLocation, GCModel> models,
            Map<ResourceLocation, AtlasSet.StitchResult> preparations, CompletableFuture<Void> readyForUpload) {}

    private GCNeoModelReloadListener() {}
}
