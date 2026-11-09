/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResearchSourceDataTest {
    @Test void reloadReplacesTheSnapshotAndClearsRemovedHints() {
        var id = ResourceLocation.parse("examplepack:galacticraft/research_source/ion.json");
        var research = ResourceLocation.parse("examplepack:rocket_engine/ion");
        String json = "{\"research\":[\"examplepack:rocket_engine/ion\"],\"description\":{\"text\":\"Craft a schematic\"}}";
        var resource = new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        ResearchSourceData.INSTANCE.onResourceManagerReload(manager(Map.of(id, resource)));
        assertEquals("Craft a schematic", ResearchSourceData.INSTANCE.sources().get(research).getString());
        ResearchSourceData.INSTANCE.onResourceManagerReload(manager(Map.of()));
        assertTrue(ResearchSourceData.INSTANCE.sources().isEmpty());
    }

    private ResourceManager manager(Map<ResourceLocation, Resource> resources) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) return resources;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}