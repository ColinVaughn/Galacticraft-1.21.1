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

package dev.galacticraft.mod.client.spacerace;

import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.FlagDataPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.spacerace.TeamFlagImage;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locally saved flag design shared with each joined world. Loaders call {@link #onJoin} and
 * {@link #onLeave} from their connection events.
 */
public final class TeamFlagClient {
    private TeamFlagClient() {
    }

    /** Returns the path used by the space race menu to save the player's flag. */
    public static Path savedFlagPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("galacticraft/team_flag.png");
    }

    /** Shares the player's saved flag with the world they have just joined. */
    public static void onJoin() {
        int[] design = readSavedFlag();
        if (design != null) {
            NetworkManager.sendToServer(new FlagDataPayload(TeamFlagImage.toRgb(design)));
        }
    }

    /**
     * Forgets the teams of the world the player has left. A disconnect can arrive off the render thread, and textures
     * may only be released on it, so the clearing is handed over.
     */
    public static void onLeave() {
        Minecraft.getInstance().execute(TeamFlagTextures::clear);
    }

    /** Returns saved ARGB pixels, or {@code null} if the flag is absent or unreadable. */
    public static @Nullable int[] readSavedFlag() {
        Path path = savedFlagPath();
        if (!Files.isRegularFile(path)) return null;
        try (InputStream input = Files.newInputStream(path); NativeImage image = NativeImage.read(input)) {
            if (image.getWidth() != TeamFlagImage.WIDTH || image.getHeight() != TeamFlagImage.HEIGHT) return null;
            int[] design = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
            for (int y = 0; y < TeamFlagImage.HEIGHT; y++) {
                for (int x = 0; x < TeamFlagImage.WIDTH; x++) {
                    design[y * TeamFlagImage.WIDTH + x] = TeamFlagImage.swapRedBlue(image.getPixelRGBA(x, y));
                }
            }
            return design;
        } catch (IOException e) {
            Constant.LOGGER.warn("Could not read the saved team flag at {}", path, e);
            return null;
        }
    }
}
