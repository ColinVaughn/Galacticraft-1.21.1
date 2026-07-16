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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.impl.universe.celestialbody.type.SatelliteWorldMigration;
import dev.galacticraft.mod.data.gen.SatelliteChunkGenerator;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class SatelliteWorldMigrationTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void repairsLegacyZeroGravity(GameTestHelper context) {
        float repairedGravity = SatelliteWorldMigration.repairLegacyGravity(0.0F);

        if (repairedGravity != 1.0F) {
            context.fail("Legacy satellite gravity was not restored");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void preservesConfiguredGravity(GameTestHelper context) {
        float configuredGravity = 0.38F;

        if (SatelliteWorldMigration.repairLegacyGravity(configuredGravity) != configuredGravity) {
            context.fail("A nonzero satellite gravity value was overwritten");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void repairsLegacyDirectBiomeHolder(GameTestHelper context) {
        var biomes = context.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var registeredSpaceBiome = biomes.getHolderOrThrow(GCBiomes.SPACE);
        var structure = new StructureTemplate();
        var legacyGenerator = new SatelliteChunkGenerator(Holder.direct(registeredSpaceBiome.value()), structure, false);
        var legacyOptions = new LevelStem(context.getLevel().dimensionTypeRegistration(), legacyGenerator);

        LevelStem repairedOptions = SatelliteWorldMigration.repairLegacyBiomeHolder(legacyOptions, context.getLevel().registryAccess());
        if (repairedOptions == legacyOptions) {
            context.fail("Legacy satellite options were not repaired");
        } else if (!(repairedOptions.generator() instanceof SatelliteChunkGenerator repairedGenerator)) {
            context.fail("Repair replaced the satellite generator with the wrong generator type");
        } else if (!repairedGenerator.getBiome().unwrapKey().filter(GCBiomes.SPACE::equals).isPresent()) {
            context.fail("Repair did not bind the station biome to galacticraft:space");
        } else if (repairedGenerator.getStructure() != structure) {
            context.fail("Repair did not preserve the station structure");
        } else if (repairedGenerator.placesStructure()) {
            context.fail("Repair did not preserve the station placement mode");
        } else if (repairedOptions.type() != legacyOptions.type()) {
            context.fail("Repair did not preserve the station dimension type");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void leavesRegisteredBiomeHolderUnchanged(GameTestHelper context) {
        var registeredSpaceBiome = context.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(GCBiomes.SPACE);
        var generator = new SatelliteChunkGenerator(registeredSpaceBiome, new StructureTemplate());
        var options = new LevelStem(context.getLevel().dimensionTypeRegistration(), generator);

        if (SatelliteWorldMigration.repairLegacyBiomeHolder(options, context.getLevel().registryAccess()) != options) {
            context.fail("Valid satellite options were unnecessarily replaced");
        } else {
            context.succeed();
        }
    }
}
