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

package dev.galacticraft.mod.content;

import com.google.common.collect.ImmutableMap;
import dev.galacticraft.mod.Constant;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** NeoForge registry queue matching the eager object API used by Fabric. */
public class GCRegistry<T> {
    public static final DyeColor[] COLOR_ORDER = {
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK,
            DyeColor.BROWN, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW,
            DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK
    };
    private static final List<GCRegistry<?>> REGISTRIES = new ArrayList<>();

    private final Registry<T> registry;
    private final List<Pending<T>> pending = new ArrayList<>();
    private final List<Holder<T>> entries = new ArrayList<>();

    public GCRegistry(Registry<T> registry) {
        this.registry = registry;
        REGISTRIES.add(this);
    }

    protected ResourceLocation getId(String id) {
        return Constant.id(id);
    }

    public <V extends T> V register(String id, V object) {
        ResourceLocation location = getId(id);
        pending.add(new Pending<>(location, object));
        entries.add(DeferredHolder.create(registry.key(), location));
        return object;
    }

    @SuppressWarnings("unchecked")
    public <V extends T> Holder<V> registerForHolder(String id, V object) {
        ResourceLocation location = getId(id);
        pending.add(new Pending<>(location, object));
        Holder<V> holder = (Holder<V>) (Holder<?>) DeferredHolder.create(registry.key(), location);
        entries.add((Holder<T>) holder);
        return holder;
    }

    public <V extends T> ColorSet<V> registerColored(String id, Function<DyeColor, V> consumer) {
        ImmutableMap.Builder<DyeColor, V> colors = ImmutableMap.builder();
        for (DyeColor color : COLOR_ORDER) colors.put(color, register(color.getName() + '_' + id, consumer.apply(color)));
        return new ColorSet<>(colors.build());
    }

    public List<Holder<T>> getEntries() {
        return entries;
    }

    public static void registerAll(RegisterEvent event) {
        for (GCRegistry<?> registry : List.copyOf(REGISTRIES)) registry.registerPending(event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerPending(RegisterEvent event) {
        if (!event.getRegistryKey().equals(registry.key())) return;
        event.register((net.minecraft.resources.ResourceKey) registry.key(), helper -> {
            for (Pending<T> entry : pending) helper.register(entry.id(), entry.value());
        });
    }

    private record Pending<T>(ResourceLocation id, T value) {
    }

    public record ColorSet<T>(Map<DyeColor, T> colorMap) {
        public T get(DyeColor color) { return colorMap.get(color); }
    }
}
