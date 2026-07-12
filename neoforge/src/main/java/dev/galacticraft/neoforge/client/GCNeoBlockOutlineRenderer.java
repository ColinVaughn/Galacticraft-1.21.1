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

package dev.galacticraft.neoforge.client;

import dev.galacticraft.mod.content.block.special.CryogenicChamberBlock;
import dev.galacticraft.mod.content.block.special.CryogenicChamberPart;
import dev.galacticraft.mod.content.block.special.TransportTube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Draws complete selection shapes for Galacticraft's translucent multiblocks. */
public final class GCNeoBlockOutlineRenderer {
    public static void render(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        var entity = Minecraft.getInstance().getCameraEntity();
        if (level == null || entity == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CryogenicChamberBlock)
                && !(state.getBlock() instanceof CryogenicChamberPart)
                && !(state.getBlock() instanceof TransportTube)) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack().last();
        var consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        state.getShape(level, pos, CollisionContext.of(entity)).forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
            float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (length != 0) { nx /= length; ny /= length; nz /= length; }
            float ox = (float) (pos.getX() - camera.x), oy = (float) (pos.getY() - camera.y), oz = (float) (pos.getZ() - camera.z);
            consumer.addVertex(pose, ox + (float) x1, oy + (float) y1, oz + (float) z1).setColor(.15F, .15F, .15F, 1).setNormal(pose, nx, ny, nz);
            consumer.addVertex(pose, ox + (float) x2, oy + (float) y2, oz + (float) z2).setColor(.15F, .15F, .15F, 1).setNormal(pose, nx, ny, nz);
        });
        event.setCanceled(true);
    }

    private GCNeoBlockOutlineRenderer() {}
}
