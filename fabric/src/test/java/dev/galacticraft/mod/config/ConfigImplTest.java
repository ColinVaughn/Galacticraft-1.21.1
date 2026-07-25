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

package dev.galacticraft.mod.config;

import dev.galacticraft.mod.content.block.entity.machine.RefineryFuelLogic;
import dev.galacticraft.mod.content.entity.vehicle.RocketFlightLogic;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigImplTest {
    private static final File CONFIG_FILE = Path.of(".", ".test_config.json").toFile();

    @Test
    public void create() {
        new ConfigImpl(CONFIG_FILE);
        // constructor creates a new config (when it doesn't exist)
        assertTrue(CONFIG_FILE.exists());
    }

    @Test
    public void load() {
        writeConfig(
                """
                {
                    "debug_log": true
                }
                """
        );

        ConfigImpl config = new ConfigImpl(CONFIG_FILE);
        assertTrue(config.isDebugLogEnabled());
    }

    @Test
    public void modify() {
        ConfigImpl config = new ConfigImpl(CONFIG_FILE);
        config.setDebugLog(true);
        config.save();

        config.setDebugLog(false);
        config.load();

        // should load the saved value
        assertTrue(config.isDebugLogEnabled());
    }

    @Test
    public void invalidConfigDoesNotModify() {
        ConfigImpl config = new ConfigImpl(CONFIG_FILE);
        config.setDebugLog(true);

        byte[] randomData = new byte[64];
        new Random().nextBytes(randomData);
        writeConfig(Base64.getEncoder().encodeToString(randomData));
        assertDoesNotThrow(config::load); // shouldn't crash with invalid config file

        // the config should not have changed
        assertTrue(config.isDebugLogEnabled());
    }

    /**
     * The fuel-economy options exist so packs can soften the oil cost of a launch. Their defaults
     * must leave the shipped balance exactly as it was before they were configurable.
     */
    @Test
    public void fuelEconomyDefaultsMatchTheShippedBalance() {
        // A pristine file of its own: the shared one carries whatever the other tests wrote to it.
        File pristine = Path.of(".", ".test_config_defaults.json").toFile();
        assertTrue(!pristine.exists() || pristine.delete());
        try {
            ConfigImpl config = new ConfigImpl(pristine);
            assertEquals(RocketFlightLogic.DEFAULT_FUEL_TANK_CAPACITY_BUCKETS, config.rocketFuelTankCapacity());
            assertEquals(RocketFlightLogic.DEFAULT_BURN_TICKS_PER_BUCKET, config.rocketBurnTicksPerBucket());
            assertEquals(RefineryFuelLogic.DEFAULT_OIL_TO_FUEL_RATIO, config.refineryOilToFuelRatio());
        } finally {
            pristine.delete();
        }
    }

    @Test
    public void fuelEconomyOptionsLoadFromDisk() {
        writeConfig(
                """
                {
                    "rocket_fuel_tank_capacity": 40,
                    "rocket_burn_ticks_per_bucket": 150,
                    "refinery_oil_to_fuel_ratio": 2.5
                }
                """
        );

        ConfigImpl config = new ConfigImpl(CONFIG_FILE);
        assertEquals(40, config.rocketFuelTankCapacity());
        assertEquals(150, config.rocketBurnTicksPerBucket());
        assertEquals(2.5, config.refineryOilToFuelRatio());
    }

    @Test
    public void fuelEconomyOptionsSurviveASaveAndReload() {
        ConfigImpl config = new ConfigImpl(CONFIG_FILE);
        config.setRocketFuelTankCapacity(64);
        config.setRocketBurnTicksPerBucket(200);
        config.setRefineryOilToFuelRatio(3.0);
        config.save();

        config.load();
        assertEquals(64, config.rocketFuelTankCapacity());
        assertEquals(200, config.rocketBurnTicksPerBucket());
        assertEquals(3.0, config.refineryOilToFuelRatio());
    }

    private static void writeConfig(String config) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            writer.write(config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write test config", e);
        }
    }
}