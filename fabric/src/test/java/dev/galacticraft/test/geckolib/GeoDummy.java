/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.test.geckolib;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;

/** Registers the GeckoLib dummy mob, only when GeckoLib is loaded (run with {@code -PgeckolibRuntime}). */
public final class GeoDummy implements ModInitializer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("galacticraft-test", "geo_dummy");

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded("geckolib")) Types.register();
    }

    /** Kept apart so nothing touches GeckoLib's classes unless it is installed. */
    static final class Types {
        static EntityType<GeoDummyEntity> type;

        static void register() {
            type = Registry.register(BuiltInRegistries.ENTITY_TYPE, ID,
                    EntityType.Builder.of(GeoDummyEntity::new, MobCategory.MONSTER).sized(1.0F, 1.9F).build(ID.toString()));
            FabricDefaultAttributeRegistry.register(type, Monster.createMonsterAttributes());
        }
    }
}
