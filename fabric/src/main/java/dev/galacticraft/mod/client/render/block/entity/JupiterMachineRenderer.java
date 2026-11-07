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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.MeteorDefenseModel;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws Jupiter block models with idle clips and a full-bright emissive pass.
 * Stopped {@link JupiterAnimated} machines hold the idle clip's first frame.
 */
@Environment(EnvType.CLIENT)
public class JupiterMachineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    protected record Machine(MeteorDefenseModel model, ResourceLocation texture, ResourceLocation emissive, String idle) { }
    private static final Map<Block, Machine> MACHINES = new HashMap<>();

    public JupiterMachineRenderer(BlockEntityRendererProvider.Context context) {
        MACHINES.clear(); // Use current resources after a renderer reload.
    }

    @Override
    public void render(T machine, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var level = machine.getLevel();
        // Offset each machine's clock by its position so a row of them doesn't move in lockstep.
        double seconds = machine instanceof JupiterAnimated animated && !animated.isAnimating() ? 0
                : ((level == null ? 0 : level.getGameTime()) + partialTick) / 20.0 + (machine.getBlockPos().asLong() & 63) / 8.0;
        var state = machine.getBlockState();
        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            poses.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()));
        }
        draw(machine.getBlockState().getBlock(), seconds, poses, buffers, light, overlay);
        poses.popPose();
    }

    public static void draw(Block block, double seconds, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        Machine machine = machine(block);
        machine.model.reset();
        if (machine.model.hasAnimation(machine.idle)) machine.model.animate(machine.idle, seconds);
        drawPosed(machine, poses, buffers, light, overlay);
    }

    /** Draws a machine whose bones have already been posed: the textured model, then its glow pixels full-bright. */
    protected static void drawPosed(Machine machine, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        machine.model.render(poses, buffers.getBuffer(RenderType.entityCutout(machine.texture)), light, overlay);
        machine.model.render(poses, buffers.getBuffer(RenderType.eyes(machine.emissive)), LightTexture.FULL_BRIGHT, overlay);
    }

    /** Item form: vanilla's block display transforms, shrunk to fit models taller than a block. */
    public static void renderItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        float fit = 16F / Math.max(16F, machine(block).model.height());
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        switch (context) {
            case GUI -> { poses.mulPose(Axis.XP.rotationDegrees(30)); poses.mulPose(Axis.YP.rotationDegrees(225)); poses.scale(0.625F, 0.625F, 0.625F); }
            case GROUND -> { poses.translate(0, 0.1875, 0); poses.scale(0.25F, 0.25F, 0.25F); }
            case FIXED -> poses.scale(0.5F, 0.5F, 0.5F);
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                poses.translate(0, 0.15625, 0);
                poses.mulPose(Axis.XP.rotationDegrees(75)); poses.mulPose(Axis.YP.rotationDegrees(45)); poses.scale(0.375F, 0.375F, 0.375F);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> { poses.mulPose(Axis.YP.rotationDegrees(45)); poses.scale(0.4F, 0.4F, 0.4F); }
            default -> { }
        }
        poses.scale(fit, fit, fit);
        poses.translate(0, -0.5, 0);
        var level = Minecraft.getInstance().level;
        draw(block, level == null ? Util.getMillis() / 1000.0 : level.getGameTime() / 20.0, poses, buffers, light, overlay);
        poses.popPose();
    }

    protected static Machine machine(Block block) {
        return MACHINES.computeIfAbsent(block, key -> {
            String id = BuiltInRegistries.BLOCK.getKey(key).getPath();
            return new Machine(new MeteorDefenseModel(Minecraft.getInstance().getResourceManager(), "block/jupiter", id),
                    Constant.id("textures/model/jupiter/" + id + ".png"), Constant.id("textures/model/jupiter/" + id + "_emissive.png"),
                    "animation." + id + ".idle");
        });
    }

    @Override public boolean shouldRenderOffScreen(T machine) { return true; }
    @Override public int getViewDistance() { return 96; }
}
