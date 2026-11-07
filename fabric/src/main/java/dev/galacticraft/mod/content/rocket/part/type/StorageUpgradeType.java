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

package dev.galacticraft.mod.content.rocket.part.type;

import dev.galacticraft.api.rocket.RocketUpgradeData;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.rocket.part.type.RocketUpgradeType;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType;
import dev.galacticraft.impl.rocket.travelpredicate.config.ConstantTravelPredicateConfig;
import dev.galacticraft.impl.rocket.travelpredicate.type.ConstantTravelPredicateType;
import dev.galacticraft.mod.content.entity.vehicle.RocketCargoLogic;
import dev.galacticraft.mod.content.rocket.part.config.StorageUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import dev.galacticraft.mod.tag.GCItemTags;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class StorageUpgradeType extends RocketUpgradeType<StorageUpgradeConfig> {
    public static final StorageUpgradeType INSTANCE = new StorageUpgradeType();

    protected StorageUpgradeType() {
        super(StorageUpgradeConfig.CODEC);
        RocketUpgradeData.register(StorageRocketData.TYPE_NAME, StorageRocketData.MAP_CODEC);
    }

    /** Chests go in any slot, as long as the first slot does not already hold another upgrade's item. */
    @Override
    public boolean canPlace(@NotNull StorageUpgradeConfig config, int slot, @NotNull ItemStack stack, @NotNull List<ItemStack> slots) {
        if (!stack.is(GCItemTags.ROCKET_STORAGE_UPGRADE_ITEMS)) return false;
        return slot == 0 || slots.isEmpty() || slots.get(0).isEmpty() || slots.get(0).is(GCItemTags.ROCKET_STORAGE_UPGRADE_ITEMS);
    }

    @Override
    public boolean isInstalledBy(@NotNull StorageUpgradeConfig config, @NotNull List<ItemStack> slots) {
        return installedChests(slots) > 0;
    }

    /** The rocket's cargo scales with how many chests are installed. */
    @Override
    public @NotNull Optional<RocketUpgradeData> createData(@NotNull StorageUpgradeConfig config, @NotNull List<ItemStack> slots) {
        return Optional.of(new StorageRocketData(installedChests(slots)));
    }

    private static int installedChests(List<ItemStack> slots) {
        int chests = 0;
        for (ItemStack stack : slots) {
            if (stack.is(GCItemTags.ROCKET_STORAGE_UPGRADE_ITEMS)) ++chests;
        }
        return chests;
    }

    /**
     * The cargo slots a rocket built with {@code chests} chests gets. {@link StorageUpgradeConfig#chests()}
     * is the ceiling, so a rocket can never claim more storage than the upgrade allows.
     */
    public int getSlots(@NotNull StorageUpgradeConfig config, int chests) {
        return RocketCargoLogic.storageSlots(chests, config.chests());
    }

    /** The cargo slots a fully upgraded rocket gets. */
    public int getMaxSlots(@NotNull StorageUpgradeConfig config) {
        return RocketCargoLogic.storageSlots(config.chests(), config.chests());
    }

    @Override
    public void tick(@NotNull Rocket rocket, @NotNull StorageUpgradeConfig config) {

    }

    @Override
    public @NotNull ConfiguredTravelPredicate<?, ?> travelPredicate(@NotNull StorageUpgradeConfig config) {
        return ConstantTravelPredicateType.INSTANCE.configure(new ConstantTravelPredicateConfig(TravelPredicateType.Result.PASS));
    }

    @Override
    public @Nullable RocketPartRecipe<?, ?> getRecipe(StorageUpgradeConfig config) {
        return config.recipe();
    }
}
