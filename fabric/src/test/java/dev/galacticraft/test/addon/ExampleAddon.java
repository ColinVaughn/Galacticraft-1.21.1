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

package dev.galacticraft.test.addon;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.event.ResearchEvents;
import dev.galacticraft.api.event.RocketEvents;
import dev.galacticraft.api.registry.BuiltInRocketRegistries;
import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketBody;
import dev.galacticraft.api.rocket.part.RocketCone;
import dev.galacticraft.api.rocket.part.RocketEngine;
import dev.galacticraft.api.rocket.part.RocketFin;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.api.rocket.part.RocketUpgrade;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.content.item.SchematicItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public API example with datapack-defined tier 4 parts, a schematic, an engine module,
 * a fuel-pod upgrade, and launch/research listeners. Checked by {@code ExampleAddonGameTest}.
 */
public final class ExampleAddon implements ModInitializer {
    public static final String MOD_ID = "galacticraft-test";

    public static final ResourceKey<RocketCone<?, ?>> TIER_4_CONE = ResourceKey.create(RocketRegistries.ROCKET_CONE, id("tier_4"));
    public static final ResourceKey<RocketBody<?, ?>> TIER_4_BODY = ResourceKey.create(RocketRegistries.ROCKET_BODY, id("tier_4"));
    public static final ResourceKey<RocketFin<?, ?>> TIER_4_FIN = ResourceKey.create(RocketRegistries.ROCKET_FIN, id("tier_4"));
    public static final ResourceKey<RocketEngine<?, ?>> TIER_4_ENGINE = ResourceKey.create(RocketRegistries.ROCKET_ENGINE, id("tier_4"));
    public static final ResourceKey<RocketUpgrade<?, ?>> FUEL_POD = ResourceKey.create(RocketRegistries.ROCKET_UPGRADE, id("fuel_pod"));

    /** Rockets that left the pad, and research gained, as seen by the listeners below. */
    public static final List<Object> LAUNCHED = new CopyOnWriteArrayList<>();
    public static final List<ResourceLocation> RESEARCHED = new CopyOnWriteArrayList<>();

    public static Item tier4Schematic;
    public static RocketModuleItem tier4EngineModule;

    @Override
    public void onInitialize() {
        // Register part types in code; load configured parts from datapacks.
        Registry.register(BuiltInRocketRegistries.ROCKET_UPGRADE_TYPE, id("fuel_pod"), FuelPodUpgradeType.INSTANCE);

        tier4Schematic = Registry.register(BuiltInRegistries.ITEM, id("tier_4_rocket_schematic"), new SchematicItem(new Item.Properties()
                .stacksTo(1)
                .component(GCDataComponents.ROCKET_PART_UNLOCKS, List.of(TIER_4_CONE, TIER_4_BODY, TIER_4_FIN, TIER_4_ENGINE))));

        RocketData engineOnly = new RocketData(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new EitherHolder<>(TIER_4_ENGINE)), Optional.empty(), Optional.empty(), 0xFFFFFFFF);
        tier4EngineModule = Registry.register(BuiltInRegistries.ITEM, id("tier_4_rocket_engine"),
                new RocketModuleItem(RocketPartTypes.ENGINE, 4, engineOnly));

        RocketEvents.LAUNCH.register(LAUNCHED::add);
        ResearchEvents.UNLOCKED.register((player, research) -> RESEARCHED.addAll(research));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
