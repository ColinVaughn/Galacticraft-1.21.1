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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.rocket.RocketUpgradeData;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.rocket.part.config.RocketUpgradeConfig;
import dev.galacticraft.api.rocket.part.type.RocketUpgradeType;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType;
import dev.galacticraft.impl.rocket.travelpredicate.config.ConstantTravelPredicateConfig;
import dev.galacticraft.impl.rocket.travelpredicate.type.ConstantTravelPredicateType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** An example upgrade: buckets in the workbench upgrade slots become extra fuel pods on the rocket. */
public final class FuelPodUpgradeType extends RocketUpgradeType<FuelPodUpgradeType.Config> {
    public static final FuelPodUpgradeType INSTANCE = new FuelPodUpgradeType();

    private FuelPodUpgradeType() {
        super(Config.CODEC);
        RocketUpgradeData.register(Pods.TYPE_NAME, Pods.MAP_CODEC);
    }

    @Override
    public boolean canPlace(@NotNull Config config, int slot, @NotNull ItemStack stack, @NotNull List<ItemStack> slots) {
        if (!stack.is(Items.BUCKET)) return false;
        // Keep pods out of a rocket whose slots already hold another upgrade's items.
        return slots.stream().allMatch(other -> other.isEmpty() || other.is(Items.BUCKET));
    }

    @Override
    public boolean isInstalledBy(@NotNull Config config, @NotNull List<ItemStack> slots) {
        return slots.stream().anyMatch(stack -> stack.is(Items.BUCKET));
    }

    @Override
    public @NotNull Optional<RocketUpgradeData> createData(@NotNull Config config, @NotNull List<ItemStack> slots) {
        int pods = (int) slots.stream().filter(stack -> stack.is(Items.BUCKET)).count();
        return Optional.of(new Pods(Math.min(pods, config.maxPods())));
    }

    @Override
    public long additionalFuelCapacity(Config config, dev.galacticraft.api.rocket.RocketData data) {
        return data.getUpgradeData(Pods.class).map(pods -> (long) Math.clamp(pods.count(), 0, config.maxPods()) * 81000).orElse(0L);
    }

    @Override
    public void tick(@NotNull Rocket rocket, @NotNull Config config) {
    }

    @Override
    public @NotNull ConfiguredTravelPredicate<?, ?> travelPredicate(@NotNull Config config) {
        return ConstantTravelPredicateType.INSTANCE.configure(new ConstantTravelPredicateConfig(TravelPredicateType.Result.PASS));
    }

    @Override
    public @Nullable RocketPartRecipe<?, ?> getRecipe(Config config) {
        return null;
    }

    public record Config(int maxPods) implements RocketUpgradeConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64).optionalFieldOf("max_pods", 3).forGetter(Config::maxPods)
        ).apply(instance, Config::new));
    }

    /** Per-rocket data: how many pods this rocket was built with. Namespaced, as addon data should be. */
    public record Pods(int count) implements RocketUpgradeData {
        public static final String TYPE_NAME = "galacticraft-test:fuel_pods";
        public static final MapCodec<Pods> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(0, 64).fieldOf("count").forGetter(Pods::count)
        ).apply(instance, Pods::new));

        @Override
        public String typeName() {
            return TYPE_NAME;
        }
    }
}
