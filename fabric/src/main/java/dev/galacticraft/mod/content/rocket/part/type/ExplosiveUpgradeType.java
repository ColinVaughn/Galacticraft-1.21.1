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
import dev.galacticraft.mod.content.rocket.part.config.ExplosiveUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.data.ExplosiveRocketData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ExplosiveUpgradeType extends RocketUpgradeType<ExplosiveUpgradeConfig> {
    public static final ExplosiveUpgradeType INSTANCE = new ExplosiveUpgradeType();

    protected ExplosiveUpgradeType() {
        super(ExplosiveUpgradeConfig.CODEC);
        RocketUpgradeData.register(ExplosiveRocketData.TYPE_NAME, ExplosiveRocketData.MAP_CODEC);
    }

    /** A charge goes in the first slot alone: crafting consumes every slot, so loaded chests would be eaten. */
    @Override
    public boolean canPlace(@NotNull ExplosiveUpgradeConfig config, int slot, @NotNull ItemStack stack, @NotNull List<ItemStack> slots) {
        if (slot != 0 || !isExplosive(stack)) return false;
        for (int other = 1; other < slots.size(); ++other) {
            if (!slots.get(other).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean isInstalledBy(@NotNull ExplosiveUpgradeConfig config, @NotNull List<ItemStack> slots) {
        return !slots.isEmpty() && isExplosive(slots.get(0));
    }

    @Override
    public @NotNull Optional<RocketUpgradeData> createData(@NotNull ExplosiveUpgradeConfig config, @NotNull List<ItemStack> slots) {
        Block block = ((BlockItem) slots.get(0).getItem()).getBlock();
        return Optional.of(new ExplosiveRocketData(BuiltInRegistries.BLOCK.getKey(block)));
    }

    public static boolean isExplosive(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TntBlock;
    }

    public float getBlastRadiusMultiplier(@NotNull ExplosiveUpgradeConfig config) {
        return config.blastRadiusMultiplier();
    }

    public int getFuseTicks(@NotNull ExplosiveUpgradeConfig config) {
        return config.fuseTicks();
    }

    @Override
    public void tick(@NotNull Rocket rocket, @NotNull ExplosiveUpgradeConfig config) {
    }

    @Override
    public @NotNull ConfiguredTravelPredicate<?, ?> travelPredicate(@NotNull ExplosiveUpgradeConfig config) {
        return ConstantTravelPredicateType.INSTANCE.configure(
                new ConstantTravelPredicateConfig(TravelPredicateType.Result.PASS)
        );
    }

    @Override
    public @Nullable RocketPartRecipe<?, ?> getRecipe(ExplosiveUpgradeConfig config) {
        return config.recipe();
    }
}