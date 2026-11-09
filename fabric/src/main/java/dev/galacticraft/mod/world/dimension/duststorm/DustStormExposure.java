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

package dev.galacticraft.mod.world.dimension.duststorm;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shared storm exposure checks for visuals, sound, and hazards. Roofs, caves, and sealed breathable
 * spaces block dust consistently (issue #21).
 */
public final class DustStormExposure {
    /** The most of the wind a sealed, breathable space lets through. */
    public static final float SEALED_WIND = 0.3F;
    /** How much of the wind carries through whatever stands overhead, right under it. */
    public static final float THROUGH_COVER_WIND = 0.3F;
    /** How deep under cover, in blocks, the wind carried through it has died away. */
    public static final int COVER_FADE_DEPTH = 24;
    /** The sky light at which the open storm, and its thick dust, fills the view. */
    private static final int STORM_IN_VIEW_SKY_LIGHT = 8;

    private DustStormExposure() {
    }

    /** Returns whether the storm reaches this block. */
    public static boolean isExposed(Level level, BlockPos pos) {
        return depthUnderCover(level, pos) == 0 && !level.isBreathable(pos);
    }

    /** Returns overhead cover depth in blocks, or 0 under open sky. */
    public static int depthUnderCover(Level level, BlockPos pos) {
        return Math.max(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - pos.getY());
    }

    /** Returns the block containing the entity's eyes. */
    public static BlockPos eyeBlock(Entity entity) {
        return BlockPos.containing(entity.getEyePosition());
    }

    /**
     * Whether the open storm fills the view from here. Sky light reaches through glass, so a sealed dome still
     * looks out into the storm, while a cave or a closed room does not.
     */
    public static boolean isStormInView(Level level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) >= STORM_IN_VIEW_SKY_LIGHT;
    }

    /** Returns local wind exposure in {@code [0, 1]}, before storm intensity is applied. */
    public static float wind(Level level, BlockPos pos) {
        return wind(level.getBrightness(LightLayer.SKY, pos), depthUnderCover(level, pos), level.isBreathable(pos));
    }

    /**
     * Computes wind volume from sky light, overhead cover, and breathable air. Thin cover muffles the
     * wind; deep rock silences it.
     *
     * @param skyLight sky light at the listener, 0 to 15
     * @param depthUnderCover blocks of overhead cover, 0 under open sky
     * @param breathable whether the listener is in sealed, breathable air
     */
    public static float wind(int skyLight, int depthUnderCover, boolean breathable) {
        float open = Mth.clamp(skyLight / 15.0F, 0.0F, 1.0F);
        float throughCover = THROUGH_COVER_WIND * Mth.clamp(1.0F - depthUnderCover / (float) COVER_FADE_DEPTH, 0.0F, 1.0F);
        float wind = Math.max(open, throughCover);
        return breathable ? Math.min(wind, SEALED_WIND) : wind;
    }
}
