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

package dev.galacticraft.impl.client.rocket.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.api.entity.rocket.render.RocketPartRenderer;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a whole-rocket OBJ model for a single part slot, scaled and vertically offset.
 *
 * <p>Tiers 2 and 3 do not have per-part meshes; their legacy models are single whole-rocket
 * meshes authored at roughly twice the scale of the tier-1 assembled rocket. We register one
 * of these as the tier's body slot (with the remaining slots left empty) and use {@code scale}
 * and {@code yOffset} to size it and drop it onto the launch pad.
 *
 * <p>{@code yOffset} is applied in entity space and must also absorb the fixed translation the
 * {@code RocketEntityRenderer} accumulates before the body slot is drawn (currently +1.9375).
 * Both constants are visual tuning values; adjust them in-game if the model sits wrong.
 */
@Environment(EnvType.CLIENT)
public record ScaledModelRocketPartRenderer(ResourceLocation model, float scale,
                                            float yOffset) implements RocketPartRenderer {

    @Override
    public void renderGUI(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float delta) {
        // Workbench part previews are not rendered for whole-mesh tiers.
    }

    @Override
    public void render(ClientLevel world, PoseStack matrices, Rocket rocket, MultiBufferSource vertices, float partialTick, int light, int overlay) {
        int color = rocket.getRocketData().color();
        matrices.pushPose();
        matrices.translate(0.0F, this.yOffset, 0.0F);
        matrices.scale(this.scale, this.scale, this.scale);
        VertexConsumer consumer = vertices.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        GCModelLoader.INSTANCE.getModel(this.model).render(matrices, null, consumer, light, overlay, color);
        matrices.popPose();
    }
}
