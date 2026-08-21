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

package dev.galacticraft.mod.machine;

import dev.galacticraft.machinelib.api.machine.configuration.IOConfig;
import dev.galacticraft.machinelib.api.transfer.ResourceFlow;
import dev.galacticraft.machinelib.api.transfer.ResourceType;
import dev.galacticraft.machinelib.api.util.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The defaults are read off each machine's own energy storage spec, so these pin that reading against
 * the shapes the mod's machines actually declare.
 */
class MachineFaceDefaultsTest {
    @Test
    void aMachineThatOnlyDrawsPowerTakesItIn() {
        // Oxygen sealer, circuit fabricator, deconstructor: spec(capacity, insertion, 0).
        assertEquals(ResourceFlow.INPUT, MachineFaceDefaults.defaultEnergyFlow(20L, 0L));
    }

    @Test
    void aGeneratorGivesPowerOut() {
        // Solar panels and the coal generator: spec(capacity, 0, extraction).
        assertEquals(ResourceFlow.OUTPUT, MachineFaceDefaults.defaultEnergyFlow(0L, 60L));
    }

    @Test
    void aBatteryDoesBoth() {
        // Energy storage module and cluster: spec(capacity, io) sets one rate for both directions.
        assertEquals(ResourceFlow.BOTH, MachineFaceDefaults.defaultEnergyFlow(12000L, 12000L));
    }

    /** Nothing to expose means nothing to configure - such a machine keeps its blank faces. */
    @Test
    void aMachineThatExchangesNoPowerIsLeftAlone() {
        assertNull(MachineFaceDefaults.defaultEnergyFlow(0L, 0L));
    }

    @Test
    void defaultsUseRightForInputAndLeftForOutput() {
        IOConfig config = new IOConfig();

        MachineFaceDefaults.applyDefaultFaces(config, ResourceFlow.BOTH, null);

        assertEquals(ResourceType.ENERGY, config.get(BlockFace.RIGHT).getType());
        assertEquals(ResourceFlow.INPUT, config.get(BlockFace.RIGHT).getFlow());
        assertEquals(ResourceType.ENERGY, config.get(BlockFace.LEFT).getType());
        assertEquals(ResourceFlow.OUTPUT, config.get(BlockFace.LEFT).getFlow());
        for (BlockFace face : new BlockFace[]{BlockFace.FRONT, BlockFace.BACK, BlockFace.TOP, BlockFace.BOTTOM}) {
            assertEquals(ResourceType.NONE, config.get(face).getType());
        }
    }

    @Test
    void oldEverySideDefaultsAreMigrated() {
        IOConfig config = new IOConfig();
        for (BlockFace face : BlockFace.values()) {
            config.get(face).setOption(ResourceType.ENERGY, ResourceFlow.INPUT);
        }

        MachineFaceDefaults.applyDefaultFaces(config, ResourceFlow.INPUT, null);

        assertEquals(ResourceType.ENERGY, config.get(BlockFace.RIGHT).getType());
        for (BlockFace face : new BlockFace[]{BlockFace.FRONT, BlockFace.BACK, BlockFace.LEFT, BlockFace.TOP, BlockFace.BOTTOM}) {
            assertEquals(ResourceType.NONE, config.get(face).getType());
        }
    }

    @Test
    void terraformerKeepsItsAuthoredFrontAndBackTextures() {
        IOConfig config = new IOConfig();

        MachineFaceDefaults.applyTerraformer(config);

        assertEquals(ResourceType.NONE, config.get(BlockFace.FRONT).getType());
        assertEquals(ResourceType.NONE, config.get(BlockFace.BACK).getType());
        assertEquals(ResourceType.ENERGY, config.get(BlockFace.RIGHT).getType());
        assertEquals(ResourceFlow.INPUT, config.get(BlockFace.RIGHT).getFlow());
        for (BlockFace face : new BlockFace[]{BlockFace.LEFT, BlockFace.TOP, BlockFace.BOTTOM}) {
            assertEquals(ResourceType.ANY, config.get(face).getType());
            assertEquals(ResourceFlow.BOTH, config.get(face).getFlow());
        }
    }

    @Test
    void oldUniformTerraformerFacesAreRecognizedForMigration() {
        IOConfig config = new IOConfig();
        for (BlockFace face : BlockFace.values()) {
            config.get(face).setOption(ResourceType.ENERGY, ResourceFlow.INPUT);
        }

        assertTrue(MachineFaceDefaults.isOldTerraformerDefault(config));
        config.get(BlockFace.FRONT).setOption(ResourceType.NONE, ResourceFlow.BOTH);
        assertFalse(MachineFaceDefaults.isOldTerraformerDefault(config));
    }
}
