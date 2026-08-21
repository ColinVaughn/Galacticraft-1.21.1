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

package dev.galacticraft.impl.internal;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Finds the {@link Level} the game is actually running for a given dimension key.
 *
 * Several mods build throwaway {@code Level} instances that wrap a real one - Create's Ponder
 * scenes and schematic previews go through {@code net.createmod.catnip.levelWrappers.WrappedLevel},
 * which hands the real level's dimension key, dimension type and level data straight to
 * {@code Level}'s constructor. Such a level is indistinguishable from the level it wraps by its own
 * state alone, so a Ponder scene opened on the Moon would otherwise be treated as the Moon:
 * low gravity, no atmosphere, space sky.
 *
 * Object identity against the level the game has actually installed for that key is the one
 * reliable signal, so that is what this class exposes.
 */
public final class VirtualLevels {
    private static volatile Supplier<@Nullable Level> clientLevel = () -> null;

    private VirtualLevels() {
    }

    /**
     * Registers the source of the level the client is currently playing in. Called from a
     * client-only mixin so that common code never has to touch {@code Minecraft}.
     */
    public static void setClientLevelSupplier(Supplier<@Nullable Level> supplier) {
        clientLevel = supplier;
    }

    /**
     * @return the level the game is running for {@code level}'s dimension, or {@code null} when
     *         that cannot be determined - a level still under construction is not installed yet,
     *         and a dedicated server never has a client level. Callers must treat {@code null} as
     *         "no answer" rather than "virtual", otherwise a real level would be misjudged during
     *         the window between its construction and its registration.
     */
    public static @Nullable Level canonical(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getLevel(level.dimension());
        }
        Level current = clientLevel.get();
        return current != null && current.dimension().equals(level.dimension()) ? current : null;
    }
}
