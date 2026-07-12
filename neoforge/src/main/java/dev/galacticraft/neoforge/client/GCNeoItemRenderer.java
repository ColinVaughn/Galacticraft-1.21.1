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

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.client.render.item.AstroMinerItemRenderer;
import dev.galacticraft.mod.client.render.item.CargoRocketItemRenderer;
import dev.galacticraft.mod.client.render.item.FlagItemRenderer;
import dev.galacticraft.mod.client.render.item.RocketItemRenderer;
import dev.galacticraft.mod.content.item.FlagItem;
import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.ParachestBlock;

/** Dispatches NeoForge's custom-item hook to the shared Galacticraft renderers. */
public final class GCNeoItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final GCNeoItemRenderer INSTANCE = new GCNeoItemRenderer();
    private final RocketItemRenderer rocket = new RocketItemRenderer();
    private final AstroMinerItemRenderer astroMiner = new AstroMinerItemRenderer();
    private final CargoRocketItemRenderer cargoRocket = new CargoRocketItemRenderer();
    private final FlagItemRenderer flag = new FlagItemRenderer();

    private GCNeoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (stack.is(GCItems.ROCKET)) rocket.render(stack, context, poses, buffers, light, overlay);
        else if (stack.is(GCItems.ASTRO_MINER)) astroMiner.render(stack, context, poses, buffers, light, overlay);
        else if (stack.is(GCItems.CARGO_ROCKET)) cargoRocket.render(stack, context, poses, buffers, light, overlay);
        else if (stack.is(GCBlocks.PARACHEST.asItem())) Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                GCBlocks.PARACHEST.defaultBlockState().setValue(ParachestBlock.COLOR,
                        stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE)), poses, buffers, light, overlay);
        else if (stack.getItem() instanceof FlagItem) flag.render(stack, context, poses, buffers, light, overlay);
    }
}
