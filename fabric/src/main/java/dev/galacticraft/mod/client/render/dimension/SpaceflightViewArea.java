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

package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.mixin.client.RenderSectionAccessor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

/** Reuses compatible terrain buffers and stages allocation or retirement within a small render-thread budget. */
public final class SpaceflightViewArea extends ViewArea {
    private static final ArrayDeque<Iterator<SectionRenderDispatcher.RenderSection>> retired = new ArrayDeque<>();
    private SectionRenderDispatcher dispatcher;
    private int created;
    private final boolean reused;
    private double cameraX, cameraZ;

    public SpaceflightViewArea(SectionRenderDispatcher dispatcher, Level level, int distance, LevelRenderer renderer, ViewArea previous) {
        super(dispatcher, level, distance, renderer);
        var arrival = SpaceflightTransition.arrivalPosition();
        if (arrival != null) { cameraX = arrival.x; cameraZ = arrival.z; }
        reused = previous != null && previous.getViewDistance() == distance
                && previous.getLevelHeightAccessor().getSectionsCount() == level.getSectionsCount()
                && (!(previous instanceof SpaceflightViewArea staged) || staged.isReady());
        // RenderSection belongs to the shared dispatcher, whose level vanilla has already replaced.
        if (reused) sections = previous.sections;
        else if (previous != null) retire(previous);
    }

    @Override
    protected void createSections(SectionRenderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        sections = new SectionRenderDispatcher.RenderSection[sectionGridSizeX * sectionGridSizeY * sectionGridSizeZ];
    }

    public static void retire(ViewArea area) {
        retired.add(Arrays.asList(area.sections).iterator());
    }

    public static void releaseRetired(long deadline) {
        while (!retired.isEmpty() && System.nanoTime() < deadline) {
            var iterator = retired.peek();
            if (!iterator.hasNext()) retired.remove();
            else {
                var section = iterator.next();
                if (section != null) section.releaseBuffers();
            }
        }
    }

    public boolean isReady() { return created == sections.length; }

    public boolean prepare() {
        long deadline = System.nanoTime() + 2_000_000L;
        releaseRetired(deadline);
        if (!retired.isEmpty()) return false;
        while (created < sections.length && System.nanoTime() < deadline) {
            int x = created % sectionGridSizeX;
            int y = created / sectionGridSizeX % sectionGridSizeY;
            int z = created / (sectionGridSizeX * sectionGridSizeY);
            int originX = sectionOrigin(cameraX, x, sectionGridSizeX);
            int originZ = sectionOrigin(cameraZ, z, sectionGridSizeZ);
            if (reused) {
                sections[created].setOrigin(originX, level.getMinBuildHeight() + y * 16, originZ);
                // allChanged cleared the renderer's global set; do not retain the old world's block entities here.
                var blockEntities = ((RenderSectionAccessor) sections[created]).galacticraft$globalBlockEntities();
                synchronized (blockEntities) { blockEntities.clear(); }
            } else sections[created] = dispatcher.new RenderSection(created, originX, level.getMinBuildHeight() + y * 16, originZ);
            created++;
        }
        if (!isReady()) return false;
        return true;
    }

    @Override
    public void repositionCamera(double x, double z) {
        // allChanged still has the source camera; prepare directly around the announced destination.
        if (!isReady() && SpaceflightTransition.isReplacingLevel()) return;
        cameraX = x;
        cameraZ = z;
        if (!isReady()) return;
        // Vanilla scans every vertical section even when the column is already positioned correctly.
        for (int sx = 0; sx < sectionGridSizeX; sx++) for (int sz = 0; sz < sectionGridSizeZ; sz++) {
            int originX = sectionOrigin(x, sx, sectionGridSizeX);
            int originZ = sectionOrigin(z, sz, sectionGridSizeZ);
            int bottom = sz * sectionGridSizeY * sectionGridSizeX + sx;
            var origin = sections[bottom].getOrigin();
            if (origin.getX() == originX && origin.getZ() == originZ) continue;
            for (int sy = 0; sy < sectionGridSizeY; sy++)
                sections[bottom + sy * sectionGridSizeX].setOrigin(originX, level.getMinBuildHeight() + sy * 16, originZ);
        }
    }

    static int sectionOrigin(double camera, int index, int gridSize) {
        int base = Mth.ceil(camera) - 8 - gridSize * 8;
        return base + Math.floorMod(index * 16 - base, gridSize * 16);
    }

    @Override
    public void setDirty(int x, int y, int z, boolean playerChanged) {
        // Incoming chunk/light packets may arrive before the section array is complete.
        if (isReady()) super.setDirty(x, y, z, playerChanged);
    }

    @Override
    public void releaseAllBuffers() {
        for (var section : sections) if (section != null) section.releaseBuffers();
    }
}