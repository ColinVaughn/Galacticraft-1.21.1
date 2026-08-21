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

package dev.galacticraft.mod.world.dimension.meteor;

import dev.galacticraft.mod.Galacticraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a meteor strike is allowed to damage terrain or entities in a given dimension.
 *
 * Priority goes to an op's per-dimension {@link Override}, then the config's exception list, then
 * the global {@code meteorImpactBlockDamage} flag. The override is set with
 * {@code /meteorshower blockdamage} and stored in the dimension's {@link MeteorShowerState}.
 *
 * A strike in a protected dimension still flashes, roars and leaves its meteorite behind, but
 * cannot hurt entities or rearrange terrain - see {@link MeteorImpact}.
 */
public final class MeteorImpactRules {
    private MeteorImpactRules() {
    }

    /** An op's per-dimension answer, or {@link #DEFAULT} to let the config decide. */
    public enum Override {
        DEFAULT((byte) 0),
        NEVER((byte) 1),
        ALWAYS((byte) 2);

        private final byte id;

        Override(byte id) {
            this.id = id;
        }

        public byte id() {
            return this.id;
        }

        /** Reads an id back, falling back to following the config for anything unrecognised. */
        public static Override byId(byte id) {
            for (Override override : values()) {
                if (override.id == id) return override;
            }
            return DEFAULT;
        }
    }

    /** Whether a strike landing in this dimension right now may damage terrain and entities. */
    public static boolean blockDamageEnabled(ServerLevel level) {
        return resolve(Galacticraft.CONFIG.meteorImpactBlockDamage(),
                Galacticraft.CONFIG.meteorImpactBlockDamageExceptions(),
                level.dimension().location(),
                MeteorShowerState.get(level).blockDamageOverride());
    }

    /** What the config alone says for this dimension, ignoring any command override. */
    public static boolean configDefaultFor(ServerLevel level) {
        return resolve(Galacticraft.CONFIG.meteorImpactBlockDamage(),
                Galacticraft.CONFIG.meteorImpactBlockDamageExceptions(),
                level.dimension().location(),
                Override.DEFAULT);
    }

    /**
     * Resolves the three layers into a single yes/no.
     *
     * @param globalDefault the config's {@code meteorImpactBlockDamage} flag
     * @param exceptions    dimension ids that do the opposite of {@code globalDefault}; may be null
     * @param dimension     the dimension the strike landed in
     * @param override      that dimension's command override
     */
    public static boolean resolve(boolean globalDefault, List<String> exceptions, ResourceLocation dimension,
                                  Override override) {
        return switch (override) {
            case ALWAYS -> true;
            case NEVER -> false;
            case DEFAULT -> globalDefault != isException(exceptions, dimension);
        };
    }

    /**
     * Whether the config lists this dimension. Entries are normalised so a hand-edited config can
     * say {@code overworld}, {@code Minecraft:Overworld} or {@code  minecraft:overworld } and still
     * be understood; anything that is not a resource location at all is skipped rather than
     * breaking the whole list.
     */
    private static boolean isException(List<String> exceptions, ResourceLocation dimension) {
        if (exceptions == null || exceptions.isEmpty()) return false;

        for (String entry : exceptions) {
            if (entry == null) continue;
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;

            ResourceLocation parsed = ResourceLocation.tryParse(trimmed);
            if (parsed != null && parsed.equals(dimension)) return true;
        }
        return false;
    }
}
