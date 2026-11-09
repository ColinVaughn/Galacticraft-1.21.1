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

package dev.galacticraft.mod.client.gui.screen.ingame;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.spacerace.TeamFlagClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists editable flag layers beside the exported image. Discard layers if they no longer match the image.
 */
final class FlagLayerFile {
    private FlagLayerFile() {
    }

    static Path path() {
        return TeamFlagClient.savedFlagPath().resolveSibling("team_flag_layers.dat");
    }

    /** Returns saved layers matching the flag image, or {@code null} when absent or stale. */
    static @Nullable FlagCanvas load(int[] flag) {
        Path path = path();
        if (!Files.isRegularFile(path)) return null;
        try {
            FlagCanvas canvas = FlagCanvas.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
            if (canvas == null) return null;
            int[] made = canvas.exportPixels();
            for (int i = 0; i < made.length; i++) {
                if (made[i] != (flag[i] | 0xFF000000)) return null;
            }
            return canvas;
        } catch (IOException | RuntimeException e) {
            Constant.LOGGER.warn("Could not read the saved flag layers at {}", path, e);
            return null;
        }
    }

    static void save(FlagCanvas canvas) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            CompoundTag tag = canvas.save();
            NbtIo.writeCompressed(tag, path);
        } catch (IOException e) {
            Constant.LOGGER.warn("Could not save the flag layers to {}", path, e);
        }
    }
}
