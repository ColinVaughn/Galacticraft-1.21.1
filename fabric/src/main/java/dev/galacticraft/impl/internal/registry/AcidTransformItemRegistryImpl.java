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

package dev.galacticraft.impl.internal.registry;

import dev.galacticraft.api.registry.AcidTransformItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class AcidTransformItemRegistryImpl implements AcidTransformItemRegistry {
    private final Map<Item, AcidTransformItemRegistry.Entry> registeredEntriesItem = new IdentityHashMap<>();
    private final Map<TagKey<Item>, AcidTransformItemRegistry.Entry> registeredEntriesTag = new HashMap<>();

    private Map<Item, AcidTransformItemRegistry.Entry> getEntryMap() {
        Map<Item, AcidTransformItemRegistry.Entry> map = new IdentityHashMap<>();

            // tags take precedence over items
            for (TagKey<Item> tag : this.registeredEntriesTag.keySet()) {
                AcidTransformItemRegistry.Entry entry = this.registeredEntriesTag.get(tag);

                for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    map.put(item.value(), entry);
                }
            }

            map.putAll(this.registeredEntriesItem);
        return map;
    }

    @Override
    public Entry get(ItemLike item) {
        return this.getEntryMap().get(item.asItem());
    }

    @Override
    public void add(ItemLike item, Entry value) {
        this.registeredEntriesItem.put(item.asItem(), value);
    }

    @Override
    public void add(TagKey<Item> tag, Entry value) {
        this.registeredEntriesTag.put(tag, value);
    }

    @Override
    public void remove(ItemLike item) {
        this.clear(item.asItem());
    }

    @Override
    public void remove(TagKey<Item> tag) {
        this.clear(tag);
    }

    @Override
    public void clear(ItemLike item) {
        this.registeredEntriesItem.remove(item.asItem());
    }

    @Override
    public void clear(TagKey<Item> tag) {
        this.registeredEntriesTag.remove(tag);
    }
}
