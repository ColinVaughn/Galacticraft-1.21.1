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

package dev.galacticraft.mod.compat.astrocraft;

import dev.architectury.platform.Platform;
import dev.galacticraft.mod.Constant;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class AstrocraftCompat {
    private static final String MOD_ID = "astrocraft";
    private static final List<DimensionObserver> DIMENSION_OBSERVERS = List.of(
            new DimensionObserver("galacticraft:moon", "Moon"),
            new DimensionObserver("galacticraft:venus", "Venus"),
            new DimensionObserver("galacticraft:mars", "Mars"),
            new DimensionObserver("galacticraft:mercury", "Mercury"),
            new DimensionObserver("galacticraft:asteroid", "Ceres")
    );

    private AstrocraftCompat() {
    }

    public static boolean isLoaded() {
        return Platform.isModLoaded(MOD_ID);
    }

    public static void initialize() {
        if (!isLoaded()) {
            return;
        }

        try {
            Class<?> astrocraftClass = Class.forName("mod.lwhrvw.astrocraft.Astrocraft");
            Object config = astrocraftClass.getField("CONFIG").get(null);
            Field observersField = config.getClass().getField("dimensionObservers");
            List<?> existingObservers = (List<?>) observersField.get(config);
            List<Object> observers = new ArrayList<>(existingObservers);

            Class<?> observerClass = Class.forName("mod.lwhrvw.astrocraft.config.AstrocraftConfig$DimensionObserver");
            Field dimensionField = observerClass.getField("dimension");
            Constructor<?> constructor = observerClass.getConstructor(String.class, String.class);

            for (DimensionObserver mapping : DIMENSION_OBSERVERS) {
                boolean alreadyMapped = observers.stream().anyMatch(observer -> hasDimension(observer, dimensionField, mapping.dimension()));
                if (!alreadyMapped) {
                    observers.add(constructor.newInstance(mapping.dimension(), mapping.observer()));
                }
            }

            observersField.set(config, observers);
            Constant.LOGGER.info("Enabled Astrocraft sky compatibility.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Constant.LOGGER.warn("Unable to initialize Astrocraft sky compatibility.", exception);
        }
    }

    static List<DimensionObserver> dimensionObservers() {
        return DIMENSION_OBSERVERS;
    }

    private static boolean hasDimension(Object observer, Field dimensionField, String dimension) {
        try {
            return dimension.equals(dimensionField.get(observer));
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    record DimensionObserver(String dimension, String observer) {
    }
}
