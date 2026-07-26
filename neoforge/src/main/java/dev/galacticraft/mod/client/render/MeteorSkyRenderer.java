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

package dev.galacticraft.mod.client.render;

import dev.galacticraft.mod.client.render.dimension.meteor.MeteorSkyStreaks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** NeoForge entry point for the meteor sky-streak layer; the drawing lives in {@link MeteorSkyStreaks}. */
public final class MeteorSkyRenderer {
    private MeteorSkyRenderer() {
    }

    public static void renderStreaks(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        // The model-view (frustum) matrix, not the pose stack: the streaks are placed by world-space
        // direction and need the camera's rotation applied, or the field stays pinned to the screen.
        MeteorSkyStreaks.render(event.getModelViewMatrix(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
