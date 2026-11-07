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

package dev.galacticraft.mod.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.galacticraft.mod.accessor.DimensionDayTimeAccessor;
import dev.galacticraft.mod.accessor.GCLevelAccessor;
import dev.galacticraft.mod.content.entity.MoonVillagerEntity;
import dev.galacticraft.mod.content.entity.SpaceEvolution;
import dev.galacticraft.mod.misc.footprint.FootprintManager;
import dev.galacticraft.mod.misc.footprint.ServerFootprintManager;
import dev.galacticraft.mod.util.DimensionTime;
import dev.galacticraft.mod.world.dimension.DimensionDayTimeState;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.gen.spawner.EvolvedPillagerSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements GCLevelAccessor {
    @Shadow
    @Final
    @Mutable
    private List<CustomSpawner> customSpawners;

    @Shadow
    public abstract ServerLevel getLevel();

    private final @Unique FootprintManager footprintManager = new ServerFootprintManager();
    /** Resolved on the level's first tick: the data storage is not ready while it is being built. */
    private @Unique DimensionDayTimeState galacticraft$dayTime = null;

    protected ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, Supplier<ProfilerFiller> profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setSpawnersGC(MinecraftServer server, Executor workerExecutor, LevelStorageSource.LevelStorageAccess session, ServerLevelData properties, ResourceKey<Level> worldKey, LevelStem dimensionOptions, ChunkProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, @Nullable RandomSequences randomSequences, CallbackInfo ci) {
        if (worldKey.equals(GCDimensions.MOON)) {
            this.customSpawners = ImmutableList.<CustomSpawner>builder().add(new EvolvedPillagerSpawner()).build();
        }
    }

    /**
     * Maintains an independent celestial clock because {@code DerivedLevelData} reads Overworld
     * time and ignores dimension-local writes. Initializes from the shared clock on the first tick
     * to preserve the time of existing saves.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gc$runOwnDayTime(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerLevel level = this.getLevel();
        // The Overworld keeps the save's own level data, and dimensions Galacticraft did not add are
        // left on vanilla's shared clock.
        if (!(level.getLevelData() instanceof DimensionDayTimeAccessor clock)) return;
        if (level.galacticraft$getCelestialBody() == null) return;

        if (this.galacticraft$dayTime == null) {
            this.galacticraft$dayTime = DimensionDayTimeState.get(level);
        }
        if (!clock.galacticraft$hasOwnDayTime()) {
            // Still reads the shared clock at this point, which is what a dimension seen for the
            // first time should start from.
            clock.galacticraft$startOwnDayTime(this.galacticraft$dayTime.dayTimeOr(level.getDayTime()));
        } else if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            clock.galacticraft$advanceOwnDayTime();
        }
        this.galacticraft$dayTime.setDayTime(level.getDayTime());
    }

    /**
     * Advances sleeping players to their celestial body's sunrise. Vanilla's 24000-tick boundary
     * only matches the Overworld day length.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void gc$sleepSkipsToLocalMorning(ServerLevel level, long vanillaMorning, Operation<Void> original) {
        long dayLength = DimensionTime.dayLength(level);
        original.call(level, dayLength == DimensionTime.VANILLA_DAY_LENGTH
                ? vanillaMorning
                : DimensionTime.nextMorning(level.getDayTime(), dayLength));
    }

    /**
     * Restores carried Moon villagers before adding them to the level.
     *
     * @see MoonVillagerEntity#restoreCarried
     */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void gc$restoreCarriedMoonVillagers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        MoonVillagerEntity moonVillager = MoonVillagerEntity.restoreCarried((ServerLevel) (Object) this, entity);
        if (moonVillager != null) {
            cir.setReturnValue(((ServerLevel) (Object) this).addFreshEntity(moonVillager));
        }
    }

    /**
     * Applies space evolution before tracking. All spawn and dimension-entry paths funnel through
     * {@code addEntity}, so the initial entity sync includes the evolved state.
     */
    @Inject(method = "addEntity", at = @At("HEAD"))
    private void gc$evolveArrivalsForSpace(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        SpaceEvolution.onEntityAdded(this, entity);
    }

    /** Mobs placed by world generation join their chunk later and never pass through {@code addEntity}. */
    @ModifyVariable(method = "addWorldGenChunkEntities", at = @At("HEAD"), argsOnly = true)
    private Stream<Entity> gc$evolveGeneratedMobsForSpace(Stream<Entity> entities) {
        return entities.peek(entity -> SpaceEvolution.onEntityAdded(this, entity));
    }

    @Inject(method = "sendBlockUpdated", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;", remap = false))
    private void onBlockChanges(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        // Skip if both old and new states are solid blocks
        if (oldState.isSolid() && newState.isSolid()) {
            return;
        }

        // Check if the block is a leaf (for oxygen collection logic)
        if (newState.is(BlockTags.LEAVES)) {
            // Oxygen collector code update (if needed)
        }
    }

    @Override
    public FootprintManager galacticraft$getFootprintManager() {
        return footprintManager;
    }
}
