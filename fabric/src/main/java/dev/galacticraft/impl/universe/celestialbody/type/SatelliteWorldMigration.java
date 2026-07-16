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

package dev.galacticraft.impl.universe.celestialbody.type;

import dev.galacticraft.mod.data.gen.SatelliteChunkGenerator;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SatelliteWorldMigration {
    private SatelliteWorldMigration() {
    }

    /**
     * Rebinds a station generator's legacy inline biome to the server biome registry.
     *
     * <p>Old satellite data was encoded without registry-aware ops, so its space biome
     * decodes as a direct holder. Chunks generated from that holder cannot serialize to
     * disk or the network because it has no registry ID.</p>
     *
     * @return the original level stem when no repair was needed, otherwise a repaired copy
     */
    public static @NotNull LevelStem repairLegacyBiomeHolder(@NotNull LevelStem options, @NotNull RegistryAccess registryAccess) {
        if (!(options.generator() instanceof SatelliteChunkGenerator generator) || generator.getBiome().unwrapKey().isPresent()) {
            return options;
        }

        var spaceBiome = registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(GCBiomes.SPACE);
        return new LevelStem(options.type(), generator.withBiome(spaceBiome));
    }
}
