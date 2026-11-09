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

package dev.galacticraft.api.entity.rocket.render;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import dev.galacticraft.impl.client.rocket.render.ScaledModelRocketPartRenderer;
import dev.galacticraft.impl.client.rocket.render.BakedModelRocketPartRenderer;
import dev.galacticraft.impl.client.rocket.render.BakedModelItemRocketPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.api.rocket.entity.Rocket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface RocketPartRenderer {
    /**
     * Renders the rocket part in a GUI.
     *
     * @param graphics GUI rendering context
     * @param x horizontal render position
     * @param y vertical render position
     * @param mouseX mouse position on the horizontal axis
     * @param mouseY mouse position on the vertical axis
     * @param delta partial tick time
     */
    default void renderGUI(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float delta) {
    }

    void render(ClientLevel world, PoseStack matrices, Rocket rocket, MultiBufferSource vertices, float partialTick, int light, int overlay);

    /**
     * Draws a Galacticraft model tinted with the rocket's color. Addon models use resource paths
     * such as {@code assets/mymod/models/misc/rocket_cone_tier_4.json}.
     *
     * @param model model resource ID, for example {@code mymod:models/misc/rocket_cone_tier_4.json}
     */
    static RocketPartRenderer model(ResourceLocation model) {
        return new BakedModelRocketPartRenderer(model);
    }

    /**
     * Like {@link #model}, but scaled and raised, for a model authored at a different size from the
     * rocket's part slots.
     *
     * @param scale   uniform scale applied to the model
     * @param yOffset extra height, in blocks, applied after the part's mount height
     */
    static RocketPartRenderer scaledModel(ResourceLocation model, float scale, float yOffset) {
        return new ScaledModelRocketPartRenderer(model, scale, yOffset);
    }

    /** A renderer that draws an item stack, as the stock storage upgrade draws a chest. */
    static RocketPartRenderer item(ItemStack stack) {
        return new BakedModelItemRocketPartRenderer(stack, null);
    }
}
