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

package dev.galacticraft.impl.internal.mixin;

import dev.galacticraft.api.accessor.LevelBodyAccessor;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.landable.Landable;
import dev.galacticraft.impl.internal.VirtualLevels;
import dev.galacticraft.impl.internal.accessor.InternalLevelBodyAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = Level.class, priority = 100) // apply before oxygen level mixin
public abstract class LevelCelestialBodyMixin implements LevelBodyAccessor, InternalLevelBodyAccessor {
    @Unique
    private Holder<CelestialBody<?, ?>> celestialBody = null;
    @Unique
    private ResourceKey<Level> galacticraft$levelKey;
    @Unique
    private int galacticraft$celestialBodyRegistrySize = -1;
    @Unique
    private boolean galacticraft$canonicalLevel = false;

    /**
     * Read directly instead of calling {@link Level#registryAccess()}. This mixin runs inside
     * {@code Level.<init>}, where a virtual call still dispatches to subclass overrides whose own
     * fields are not assigned until after {@code super()} returns — Create/Ponder's
     * {@code WrappedLevel} overrides it as {@code return level.registryAccess()} and assigns
     * {@code level} after the super call, so the override throws while we are constructing.
     * The field is assigned by {@code Level.<init>} before any of our injections run.
     */
    @Shadow @Final private RegistryAccess registryAccess;

    @Shadow public abstract DimensionType dimensionType();

    @Inject(method = "<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;Ljava/util/function/Supplier;ZZJI)V", at = @At("RETURN"))
    private void init(WritableLevelData writableLevelData,
                      ResourceKey<Level> levelKey,
                      RegistryAccess registryAccess,
                      Holder<DimensionType> holder,
                      Supplier<ProfilerFiller> supplier,
                      boolean bl,
                      boolean bl2,
                      long l,
                      int i, CallbackInfo ci) {
        this.galacticraft$levelKey = levelKey;
        this.celestialBody = this.galacticraft$resolveCelestialBody();
    }

    @Override
    public @Nullable Holder<CelestialBody<?, ?>> galacticraft$getResolvedCelestialBody() {
        return this.celestialBody;
    }

    @Override
    public boolean galacticraft$isVirtualLevel() {
        if (this.galacticraft$canonicalLevel) return false;
        Level self = (Level) (Object) this;
        Level canonical = VirtualLevels.canonical(self);
        if (canonical == null) return false; // no answer yet — assume real, so nothing regresses
        if (canonical == self) {
            // A level that is installed stays installed; caching this keeps the common case a
            // single field read rather than a registry lookup.
            this.galacticraft$canonicalLevel = true;
            return false;
        }
        // Deliberately not cached: a real level also fails this check between its construction and
        // its registration, and must be free to answer differently once it is installed.
        return true;
    }

    @Override
    public @Nullable Holder<CelestialBody<?, ?>> galacticraft$getCelestialBody() {
        // Checked before the cached value: the constructor resolves optimistically (the level is
        // never installed that early), so a wrapper level does hold its wrapped level's body here.
        if (this.galacticraft$isVirtualLevel()) return null;
        if (this.celestialBody != null) return this.celestialBody;
        // Dynamic satellite levels can be constructed before their celestial body is registered.
        // Retry unresolved levels so a newly-created station starts behaving as a satellite
        // immediately instead of only after the server reloads it from disk.
        Registry<CelestialBody<?, ?>> celestialBodies = this.galacticraft$celestialBodyRegistry();
        if (celestialBodies != null && celestialBodies.size() != this.galacticraft$celestialBodyRegistrySize) {
            this.celestialBody = this.galacticraft$resolveCelestialBody();
        }
        return this.celestialBody;
    }

    @Unique
    private @Nullable Holder<CelestialBody<?, ?>> galacticraft$resolveCelestialBody() {
        if (this.galacticraft$levelKey == null) return null;
        Registry<CelestialBody<?, ?>> celestialBodies = this.galacticraft$celestialBodyRegistry();
        if (celestialBodies == null) return null;
        this.galacticraft$celestialBodyRegistrySize = celestialBodies.size();
        return celestialBodies.holders().filter(
                body -> body.value().type() instanceof Landable landable
                        && landable.world(body.value().config()).equals(this.galacticraft$levelKey)
        ).findFirst().orElse(null);
    }

    /**
     * Virtual levels created by other mods may be built on a {@link RegistryAccess} that does not
     * carry Galacticraft's dynamic registries. Degrade to "not a celestial body" instead of
     * throwing out of whatever unrelated code happened to ask.
     */
    @Unique
    private @Nullable Registry<CelestialBody<?, ?>> galacticraft$celestialBodyRegistry() {
        if (this.registryAccess == null) return null;
        return this.registryAccess.registry(AddonRegistries.CELESTIAL_BODY).orElse(null);
    }

    @Override
    public boolean galacticraft$hasDimensionTypeTag(TagKey<DimensionType> tag) {
        if (this.registryAccess == null) return false;
        Registry<DimensionType> dimensionTypeRegistry = this.registryAccess.registry(Registries.DIMENSION_TYPE).orElse(null);
        if (dimensionTypeRegistry == null) return false;
        return dimensionTypeRegistry.getHolder(dimensionTypeRegistry.getId(this.dimensionType())).map(reference -> reference.is(tag)).orElse(false);
    }
}
