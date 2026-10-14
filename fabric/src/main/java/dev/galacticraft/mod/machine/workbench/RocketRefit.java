/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.machine.workbench;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Converts installed parts to module items; the input rocket owns every attached part. */
public final class RocketRefit {
    private RocketRefit() { }

    public static RocketData data(ItemStack rocket) {
        return rocket.getOrDefault(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1);
    }

    private static Optional<? extends EitherHolder<?>> part(RocketData data, RocketPartTypes type) {
        return switch (type) {
            case CONE -> data.cone();
            case BODY -> data.body();
            case FIN -> data.fin();
            case ENGINE -> data.engine();
            case BOOSTER -> data.booster();
            case UPGRADE -> data.upgrade();
        };
    }

    public static boolean hasPart(RocketData data, RocketPartTypes type) { return part(data, type).isPresent(); }

    public static Optional<ResourceKey<?>> key(RocketData data, RocketPartTypes type) {
        return part(data, type).map(EitherHolder::key);
    }

    public static ItemStack module(RocketData data, RocketPartTypes type) {
        var key = key(data, type);
        if (key.isEmpty()) return ItemStack.EMPTY;
        for (RocketModuleItem module : GCItems.ROCKET_MODULES) {
            if (module.type() == type && key.equals(key(module.rocketData(), type))) {
                ItemStack result = module.getDefaultInstance();
                RocketData defaults = module.rocketData();
                result.set(GCDataComponents.ROCKET_DATA, new RocketData(defaults.cone(), defaults.body(), defaults.fin(),
                        defaults.booster(), defaults.engine(), defaults.upgrade(), defaults.upgradeData(), data.tints().color(type).orElse(data.color()),
                        RocketPaint.NONE.withColor(type, data.paint().color(type).orElse(null))));
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    public static RocketData replace(RocketData original, RocketPartTypes type, ItemStack incoming) {
        RocketData replacement = incoming.isEmpty() ? RocketPrefabs.MISSING : ((RocketModuleItem) incoming.getItem()).rocketData(incoming);
        RocketPaint paint = original.paint().withColor(type, incoming.isEmpty() ? null : replacement.paint().color(type).orElse(null));
        Integer tint = incoming.isEmpty() ? null : replacement.tints().color(type).orElse(replacement.color() == 0 ? 0xFFFFFFFF : replacement.color() | 0xFF000000);
        RocketPaint tints = original.tints().withColor(type, tint);
        return new RocketData(type == RocketPartTypes.CONE ? replacement.cone() : original.cone(),
                type == RocketPartTypes.BODY ? replacement.body() : original.body(),
                type == RocketPartTypes.FIN ? replacement.fin() : original.fin(), original.booster(),
                type == RocketPartTypes.ENGINE ? replacement.engine() : original.engine(), original.upgrade(), original.upgradeData(),
                original.color(), paint, tints);
    }
}