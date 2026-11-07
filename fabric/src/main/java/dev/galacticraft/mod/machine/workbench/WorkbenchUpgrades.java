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

package dev.galacticraft.mod.machine.workbench;

import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.rocket.part.RocketUpgrade;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dev.galacticraft.api.rocket.RocketUpgradeData;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import java.util.Optional;

/**
 * The rocket workbench's upgrade slots, decided by the registered rocket upgrades rather than by
 * item. Every upgrade is asked in registry id order, so addon upgrades take part without any
 * workbench changes; see {@link dev.galacticraft.api.rocket.part.type.RocketUpgradeType#canPlace}.
 */
public final class WorkbenchUpgrades {
    private WorkbenchUpgrades() {
    }

    /** Whether any registered upgrade accepts {@code stack} in upgrade slot {@code slot}. */
    public static boolean canPlace(HolderLookup.Provider access, int slot, ItemStack stack, Container slots) {
        List<ItemStack> contents = contents(slots);
        if (stack.getItem() instanceof RocketModuleItem module && module.type(stack) == RocketPartTypes.UPGRADE) {
            return stack.getCount() == 1 && module.rocketData(stack).upgrade().flatMap(part -> part.unwrap(access)).isPresent()
                    && java.util.stream.IntStream.range(0, contents.size()).allMatch(i -> i == slot || contents.get(i).isEmpty());
        }
        if (contents.stream().anyMatch(WorkbenchUpgrades::isModule)) return false;
        return upgrades(access).stream().anyMatch(upgrade -> upgrade.value().canPlace(slot, stack, contents));
    }

    /** The upgrade the slots' contents install, if any. */
    public static Optional<Holder.Reference<RocketUpgrade<?, ?>>> installed(HolderLookup.Provider access, Container slots) {
        List<ItemStack> contents = contents(slots);
        var module = contents.stream().filter(WorkbenchUpgrades::isModule).findFirst();
        if (module.isPresent()) {
            if (contents.stream().filter(stack -> !stack.isEmpty()).count() != 1) return Optional.empty();
            var part = ((RocketModuleItem) module.get().getItem()).rocketData(module.get()).upgrade();
            return part.flatMap(holder -> holder.unwrap(access)).flatMap(holder -> holder.unwrapKey())
                    .flatMap(key -> access.lookupOrThrow(RocketRegistries.ROCKET_UPGRADE).get(key));
        }
        return upgrades(access).stream().filter(upgrade -> upgrade.value().isInstalledBy(contents)).findFirst();
    }

    public static Optional<RocketUpgradeData> data(HolderLookup.Provider access, Container slots) {
        if (installed(access, slots).isEmpty()) return Optional.empty();
        var module = contents(slots).stream().filter(WorkbenchUpgrades::isModule).findFirst();
        if (module.isPresent()) return ((RocketModuleItem) module.get().getItem()).rocketData(module.get()).upgradeData();
        return installed(access, slots).flatMap(upgrade -> upgrade.value().createData(contents(slots)));
    }

    private static boolean isModule(ItemStack stack) {
        return stack.getItem() instanceof RocketModuleItem module && module.type(stack) == RocketPartTypes.UPGRADE;
    }

    public static List<ItemStack> contents(Container slots) {
        List<ItemStack> contents = new ArrayList<>(slots.getContainerSize());
        for (int slot = 0; slot < slots.getContainerSize(); ++slot) {
            contents.add(slots.getItem(slot));
        }
        return contents;
    }

    private static List<Holder.Reference<RocketUpgrade<?, ?>>> upgrades(HolderLookup.Provider access) {
        return access.lookup(RocketRegistries.ROCKET_UPGRADE)
                .map(lookup -> lookup.listElements()
                        .sorted(Comparator.comparing(holder -> holder.key().location().toString()))
                        .toList())
                .orElse(List.of());
    }
}
