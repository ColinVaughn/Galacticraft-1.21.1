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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.client.spacerace.TeamFlagLayout;
import dev.galacticraft.mod.client.spacerace.TeamFlagTextures;
import dev.galacticraft.mod.content.FlagDesigns;
import dev.galacticraft.mod.content.block.entity.decoration.FlagBlockEntity;
import dev.galacticraft.mod.spacerace.TeamFlagOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FlagBlockEntityRenderer implements BlockEntityRenderer<FlagBlockEntity> {
    protected final ModelPart flag;
    /** A team's flag: the space race menu's 3:2 design, as long from the pole as a banner flag and a little deeper. */
    protected final ModelPart teamFlag;

    public FlagBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(GCEntityModelLayer.FLAG);
        this.flag = root.getChild("flag");
        this.teamFlag = root.getChild("team_flag");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition flag = root.addOrReplaceChild("flag",
                CubeListBuilder.create().texOffs(0, 0).addBox(48.5F, 1.5F, -0.5F, 20, 40, 1),
                PartPose.rotation(0, 0, Mth.HALF_PI)
        );
        root.addOrReplaceChild("team_flag",
                CubeListBuilder.create().texOffs(0, 0).addBox(68.5F - TeamFlagLayout.BOX_WIDTH, 1.5F, -0.5F,
                        TeamFlagLayout.BOX_WIDTH, TeamFlagLayout.BOX_HEIGHT, TeamFlagLayout.BOX_DEPTH),
                PartPose.rotation(0, 0, Mth.HALF_PI)
        );

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(FlagBlockEntity flag, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        Level level = flag.getLevel();
        BlockPos pos = flag.getBlockPos();
        if (shouldSway(level, pos)) {
            long gameTime = level.getGameTime();
            float sway = (tickDelta + Math.floorMod(pos.getX() * 7L + pos.getY() * 9L + pos.getZ() * 13L + gameTime, 100)) / 100;
            this.flag.xRot = 0.01f * Mth.cos(Mth.TWO_PI * sway) * Mth.PI;
        } else {
            this.flag.xRot = 0.0f;
        }
        this.teamFlag.xRot = this.flag.xRot;

        matrices.pushPose();
        matrices.translate(0.5, 0, 0.5);

        matrices.mulPose(Axis.YN.rotationDegrees(flag.getYaw()));

        matrices.scale(2f/3f, 2f/3f, 2f/3f);
        ResourceLocation design = flag.getDesign();
        TeamFlagOwner team = flag.getTeam();
        if (team != null) {
            ResourceLocation texture = TeamFlagTextures.texture(teamShown(flag, team));
            this.teamFlag.render(matrices, vertexConsumers.getBuffer(RenderType.entitySolid(texture)), light, overlay);
        } else if (design != null) {
            this.flag.render(matrices, vertexConsumers.getBuffer(RenderType.entitySolid(FlagDesigns.texture(design))), light, overlay);
        } else {
            renderPatterns(
                    matrices, vertexConsumers, light, overlay, this.flag, ModelBakery.BANNER_BASE, flag.getBaseColor(), flag.getPatterns()
            );
        }
        matrices.popPose();
    }

    /**
     * The team whose design a flag shows. An unclaimed team flag in a player's hand or inventory previews their own
     * design, since crafting or planting it makes it theirs; planted unclaimed, it flies plain cloth.
     */
    private static @Nullable UUID teamShown(FlagBlockEntity flag, TeamFlagOwner team) {
        if (team.isClaimed()) return team.id();
        if (flag.getLevel() != null) return null;
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getUUID() : null;
    }

    public static boolean shouldSway(@Nullable Level level, BlockPos pos) {
        return level != null && level.galacticraft$getCelestialBody() != null &&
                (level.galacticraft$getCelestialBody().value().atmosphere().pressure() >= 1f ||
                level.isBreathable(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void renderPatterns(
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light,
            int overlay,
            ModelPart canvas,
            Material baseSprite,
            DyeColor color,
            BannerPatternLayers pattern
    ) {
        canvas.render(matrices, baseSprite.buffer(vertexConsumers, RenderType::entitySolid, false), light, overlay);
        renderPatternLayer(matrices, vertexConsumers, light, overlay, canvas, Sheets.BANNER_BASE, color);

        for (int i = 0; i < pattern.layers().size(); i++) {
            BannerPatternLayers.Layer layer = pattern.layers().get(i);
            Material material = Sheets.getBannerMaterial(layer.pattern());
            renderPatternLayer(matrices, vertexConsumers, light, overlay, canvas, material, layer.color());
        }
    }

    private static void renderPatternLayer(
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, ModelPart canvas, Material textureId, DyeColor color
    ) {
        int i = color.getTextureDiffuseColor();
        canvas.render(matrices, textureId.buffer(vertexConsumers, RenderType::entityNoOutline), light, overlay, i);
    }
}
