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

package dev.galacticraft.impl.internal.mixin.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.impl.internal.accessor.AdvancementRewardsAccessor;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mixin(AdvancementRewards.class)
public abstract class AdvancementRewardsMixin implements AdvancementRewardsAccessor {
    @Mutable
    @Shadow
    @Final
    public static Codec<AdvancementRewards> CODEC;

    @Unique
    @NotNull
    private ResourceLocation @Nullable [] rocketPartRecipes = null;

    @Shadow
    public abstract int experience();

    @Shadow
    public abstract List<ResourceKey<LootTable>> loot();

    @Shadow
    public abstract List<ResourceLocation> recipes();

    @Shadow
    public abstract Optional<CacheableFunction> function();

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void galacticraft_addRocketPartRewardCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("experience", 0).forGetter(AdvancementRewards::experience),
                ResourceKey.codec(Registries.LOOT_TABLE).listOf().optionalFieldOf("loot", List.of()).forGetter(AdvancementRewards::loot),
                ResourceLocation.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(AdvancementRewards::recipes),
                CacheableFunction.CODEC.optionalFieldOf("function").forGetter(AdvancementRewards::function),
                ResourceLocation.CODEC.listOf().optionalFieldOf("rocket_parts").forGetter(AdvancementRewardsMixin::galacticraft_getRocketPartRecipeRewards)
        ).apply(instance, AdvancementRewardsMixin::galacticraft_createRewards));
    }

    @Inject(method = "grant", at = @At("RETURN"))
    private void galacticraft_applyRocketPartsToPlayer(ServerPlayer player, CallbackInfo ci) {
        if (this.rocketPartRecipes != null) {
            ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(this.rocketPartRecipes);
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void galacticraft_compareRocketParts(Object object, CallbackInfoReturnable<Boolean> cir) {
        if (!(object instanceof AdvancementRewards rewards)) {
            return;
        }

        ResourceLocation[] otherRecipes = ((AdvancementRewardsAccessor) (Object) rewards).getRocketPartRecipeRewards();
        if (this.rocketPartRecipes == null && otherRecipes == null) {
            return;
        }

        cir.setReturnValue(
                this.experience() == rewards.experience()
                        && Objects.equals(this.loot(), rewards.loot())
                        && Objects.equals(this.recipes(), rewards.recipes())
                        && Objects.equals(this.function(), rewards.function())
                        && Arrays.equals(this.rocketPartRecipes, otherRecipes)
        );
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true)
    private void galacticraft_hashRocketParts(CallbackInfoReturnable<Integer> cir) {
        if (this.rocketPartRecipes != null) {
            cir.setReturnValue(31 * cir.getReturnValue() + Arrays.hashCode(this.rocketPartRecipes));
        }
    }

    @Inject(method = "toString", at = @At("RETURN"), cancellable = true)
    private void galacticraft_appendRocketPartsToString(CallbackInfoReturnable<String> cir) {
        String s = cir.getReturnValue();
        cir.setReturnValue(s.substring(0, s.length() - 1) + ", parts=" + Arrays.toString(this.rocketPartRecipes) + '}');
    }

    @Override
    public void setRocketPartRecipeRewards(@NotNull ResourceLocation @Nullable [] recipes) {
        this.rocketPartRecipes = recipes;
    }

    @Override
    public @NotNull ResourceLocation @Nullable [] getRocketPartRecipeRewards() {
        return this.rocketPartRecipes;
    }

    @Unique
    private static Optional<List<ResourceLocation>> galacticraft_getRocketPartRecipeRewards(AdvancementRewards rewards) {
        ResourceLocation[] recipes = ((AdvancementRewardsAccessor) (Object) rewards).getRocketPartRecipeRewards();
        return recipes == null ? Optional.empty() : Optional.of(List.of(recipes));
    }

    @Unique
    private static AdvancementRewards galacticraft_createRewards(int experience, List<ResourceKey<LootTable>> loot, List<ResourceLocation> recipes, Optional<CacheableFunction> function, Optional<List<ResourceLocation>> rocketParts) {
        AdvancementRewards rewards = new AdvancementRewards(experience, loot, recipes, function);
        rocketParts.ifPresent(ids -> ((AdvancementRewardsAccessor) (Object) rewards).setRocketPartRecipeRewards(ids.toArray(ResourceLocation[]::new)));
        return rewards;
    }
}
