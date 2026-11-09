/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.mod.Constant;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.util.*;

/** Explicit acquisition hints for schematics whose part components exist only on stacks. */
public final class ResearchSourceData implements ResourceManagerReloadListener {
    public static final ResearchSourceData INSTANCE = new ResearchSourceData();
    public record Source(List<ResourceLocation> research, Component description) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("research").forGetter(Source::research),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Source::description)
        ).apply(i, Source::new));
    }
    private volatile Map<ResourceLocation, Component> sources = Map.of();
    private ResearchSourceData() { }
    public Map<ResourceLocation, Component> sources() { return sources; }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation, Component> loaded = new LinkedHashMap<>();
        manager.listResources("galacticraft/research_source", id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try (var reader = entry.getValue().openAsReader()) {
                Source source = Source.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
                for (var research : source.research()) loaded.put(research, source.description());
            } catch (Exception exception) {
                Constant.LOGGER.error("Failed to load research source {}", entry.getKey(), exception);
            }
        });
        sources = Map.copyOf(loaded);
    }
}