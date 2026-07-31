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

package dev.galacticraft.mod.client.render.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerraformerRenderCullingTest {
    private static final TerraformerRenderer RENDERER = new TerraformerRenderer(null);

    @Test
    void legacyBubbleRemainsVisibleAwayFromTheMachineBlock() {
        assertTrue(RENDERER.shouldRenderOffScreen(null));
        assertTrue(RENDERER.getViewDistance() >= 256);
    }

    @Test
    void bubbleUsesLegacyTranslucentAlpha() {
        assertEquals(30, TerraformerRenderer.LEGACY_GREEN >>> 24);
    }

    @Test
    void boundsCoverTheFifteenBlockSphereCenteredOnTheMachine() {
        AABB bounds = TerraformerRenderer.bubbleBounds(new BlockPos(10, 64, -20), 15.0D);

        assertEquals(-4.5D, bounds.minX);
        assertEquals(25.5D, bounds.maxX);
        assertEquals(49.5D, bounds.minY);
        assertEquals(79.5D, bounds.maxY);
        assertEquals(-34.5D, bounds.minZ);
        assertEquals(-4.5D, bounds.maxZ);
    }
}
