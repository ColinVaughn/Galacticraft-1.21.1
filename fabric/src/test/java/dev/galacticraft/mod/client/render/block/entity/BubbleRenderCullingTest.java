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

package dev.galacticraft.mod.client.render.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The oxygen bubble is drawn by the distributor's block entity renderer but reaches many blocks past
 * the machine, so the renderer has to declare that extent or the vanilla/NeoForge culling passes drop
 * the whole sphere as soon as the one block it is anchored to leaves the view.
 */
class BubbleRenderCullingTest {
    private static final BubbleDistributorRenderer RENDERER = new BubbleDistributorRenderer(null);

    @Test
    void bubbleSurvivesItsOwnSectionBeingCulled() {
        // Off-screen renderers are collected into LevelRenderer#globalBlockEntities, which is drawn every
        // frame; otherwise the renderer only runs while the machine's 16^3 section is in the frustum.
        assertTrue(RENDERER.shouldRenderOffScreen(null));
    }

    @Test
    void bubbleIsVisibleFromBeyondTheDefaultBlockEntityRange() {
        // BlockEntityRenderer#shouldRender measures from the machine block, not from the bubble surface.
        assertTrue(RENDERER.getViewDistance() >= 256);
    }

    @Test
    void boundsCoverTheWholeSphere() {
        AABB bounds = BubbleDistributorRenderer.bubbleBounds(new BlockPos(10, 64, -20), 7.5D);

        // Unit sphere anchored at the machine's top face, scaled by the bubble size.
        assertEquals(10.5D - 7.5D, bounds.minX);
        assertEquals(10.5D + 7.5D, bounds.maxX);
        assertEquals(65.0D - 7.5D, bounds.minY);
        assertEquals(65.0D + 7.5D, bounds.maxY);
        assertEquals(-19.5D - 7.5D, bounds.minZ);
        assertEquals(-19.5D + 7.5D, bounds.maxZ);
    }

    @Test
    void boundsGrowWithTheBubble() {
        BlockPos pos = new BlockPos(0, 0, 0);
        // A bubble larger than the machine block must not be culled against the machine block.
        assertTrue(BubbleDistributorRenderer.bubbleBounds(pos, 12.0D).getSize() > new AABB(pos).getSize());
    }

    @Test
    void collapsedBubbleStaysAWellFormedBox() {
        // Size briefly dips below zero while the bubble is shrinking; an inverted box is never visible.
        AABB bounds = BubbleDistributorRenderer.bubbleBounds(new BlockPos(0, 0, 0), -3.0D);

        assertTrue(bounds.minX <= bounds.maxX);
        assertTrue(bounds.minY <= bounds.maxY);
        assertTrue(bounds.minZ <= bounds.maxZ);
    }
}
