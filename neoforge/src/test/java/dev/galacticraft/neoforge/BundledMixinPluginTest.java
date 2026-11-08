/*
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.neoforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mixin config plugin runs while Mixin is still preparing every mod's configs. A game class it loads
 * then is loaded before the mixins aimed at it are ready, and the first required config that targets it
 * aborts the launch with {@code MixinTargetAlreadyLoadedException}. In 5.4.18, Dynamic Dimensions' Sable plugin
 * logged through {@code Constants.LOGGER}, and that pulled in {@code ResourceLocation} and {@code ExtraCodecs},
 * which crashed packs running ModernFix.
 *
 * <p>This runs the prepare-time hooks of every {@code dev.galacticraft.*} plugin on the test classpath (the
 * bundled jars included) inside a recording class loader, and fails if any game class gets loaded.
 */
class BundledMixinPluginTest {
    /** Packages loaded through the game layer, where any mod may have mixins waiting. */
    private static final List<String> GAME_PACKAGES = List.of("net.minecraft.", "com.mojang.blaze3d.", "net.neoforged.neoforge.");

    @Test
    void bundledMixinPluginsLoadNoGameClassesWhilePreparing() throws Exception {
        TreeMap<String, JsonObject> configs = galacticraftPluginConfigs();
        assertFalse(configs.isEmpty(), "found no dev.galacticraft mixin plugins on the test classpath; the scan is broken");

        List<String> failures = new ArrayList<>();
        for (var entry : configs.entrySet()) {
            String problem = prepare(entry.getKey(), entry.getValue());
            if (problem != null) failures.add(entry.getKey() + " (" + entry.getValue().get("plugin").getAsString() + "): " + problem);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Drives the plugin the way {@code MixinConfig} does before any mixin is applied; returns what went wrong, or null. */
    private static String prepare(String configName, JsonObject config) throws IOException {
        String pluginClass = config.get("plugin").getAsString();
        String mixinPackage = config.has("package") ? config.get("package").getAsString() : "";
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (RecordingClassLoader loader = new RecordingClassLoader(classpath())) {
            thread.setContextClassLoader(loader);
            try {
                Object plugin = Class.forName(pluginClass, true, loader).getConstructor().newInstance();
                plugin.getClass().getMethod("onLoad", String.class).invoke(plugin, mixinPackage);
                plugin.getClass().getMethod("getRefMapperConfig").invoke(plugin);
                for (String mixin : mixinNames(config)) {
                    plugin.getClass().getMethod("shouldApplyMixin", String.class, String.class)
                            .invoke(plugin, "placeholder.Target", mixinPackage + "." + mixin);
                }
                plugin.getClass().getMethod("acceptTargets", Set.class, Set.class)
                        .invoke(plugin, Collections.emptySet(), Collections.emptySet());
                plugin.getClass().getMethod("getMixins").invoke(plugin);
            } catch (InvocationTargetException e) {
                return "threw " + e.getCause();
            } catch (ReflectiveOperationException | LinkageError e) {
                return "could not be driven: " + e;
            } finally {
                thread.setContextClassLoader(previous);
            }
            List<String> gameClasses = loader.requested().stream()
                    .filter(name -> GAME_PACKAGES.stream().anyMatch(name::startsWith))
                    .toList();
            return gameClasses.isEmpty() ? null : "loaded game classes during prepare " + gameClasses;
        }
    }

    private static List<String> mixinNames(JsonObject config) {
        List<String> names = new ArrayList<>();
        for (String side : List.of("mixins", "client", "server")) {
            if (config.has(side)) {
                for (JsonElement element : config.getAsJsonArray(side)) names.add(element.getAsString());
            }
        }
        return names;
    }

    /** Every root-level mixin config on the test classpath whose plugin belongs to a Galacticraft-family mod. */
    private static TreeMap<String, JsonObject> galacticraftPluginConfigs() throws IOException {
        TreeMap<String, JsonObject> found = new TreeMap<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path path = Path.of(entry);
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.list(path)) {
                    for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".mixins.json")).toList()) {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            collect(found, file.getFileName().toString(), reader);
                        }
                    }
                }
            } else if (entry.endsWith(".jar") && Files.isRegularFile(path)) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    for (JarEntry jarEntry : Collections.list(jar.entries())) {
                        String name = jarEntry.getName();
                        if (name.indexOf('/') < 0 && name.endsWith(".mixins.json")) {
                            try (Reader reader = new InputStreamReader(jar.getInputStream(jarEntry), StandardCharsets.UTF_8)) {
                                collect(found, name, reader);
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private static void collect(TreeMap<String, JsonObject> found, String name, Reader reader) {
        JsonElement parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) return;
        JsonObject config = parsed.getAsJsonObject();
        if (config.has("plugin") && config.get("plugin").getAsString().startsWith("dev.galacticraft.")) {
            found.put(name, config);
        }
    }

    private static URL[] classpath() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    /** Sees every class a plugin asks for, including the ones the JVM resolves on its behalf. */
    private static final class RecordingClassLoader extends URLClassLoader {
        private final Set<String> requested = Collections.synchronizedSet(new LinkedHashSet<>());

        RecordingClassLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            this.requested.add(name);
            return super.loadClass(name, resolve);
        }

        List<String> requested() {
            synchronized (this.requested) {
                return List.copyOf(this.requested);
            }
        }
    }
}
