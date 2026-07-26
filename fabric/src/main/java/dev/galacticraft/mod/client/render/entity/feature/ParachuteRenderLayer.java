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

package dev.galacticraft.mod.client.render.entity.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.entity.model.ParachestModel;
import dev.galacticraft.mod.content.item.ParachuteItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the open canopy of a deployed parachute, strung up above its wearer.
 *
 * <p>Reuses the parachest's canopy mesh, which is already sized for a person: the harness sits at the
 * shoulders and the panels ride a little over two blocks higher.
 */
public class ParachuteRenderLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /**
     * Centres the canopy on its wearer. The mesh is pivoted off to one side, the same amount that
     * {@code ParachestRenderer} shifts back out when it hangs the canopy over a chest.
     */
    private static final float CANOPY_X = -9.0F / 16.0F;
    private static final float CANOPY_Y = 0.0F;
    private static final float CANOPY_Z = -2.0F / 16.0F;

    private final ParachestModel canopy;

    public ParachuteRenderLayer(RenderLayerParent<T, M> context) {
        super(context);
        this.canopy = new ParachestModel(ParachestModel.createParachuteLayer().bakeRoot());
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, T entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!entity.galacticraft$isParachuteVisible()) return;

        ResourceLocation texture = getCanopyTexture(entity);
        if (texture == null) return;

        matrices.pushPose();
        // Living entity models are drawn upside down and mirrored; the canopy mesh is authored the
        // right way up, so undo that before hanging it.
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(CANOPY_X, CANOPY_Y, CANOPY_Z);
        this.canopy.setupParachutePose();
        this.canopy.renderToBuffer(matrices, vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }

    /** @return the canopy texture for the wearer's equipped parachute, or {@code null} if it lost the parachute */
    private static @Nullable ResourceLocation getCanopyTexture(LivingEntity entity) {
        Container accessories = entity.galacticraft$getAccessories();
        for (int slot = 0; slot < accessories.getContainerSize(); slot++) {
            if (accessories.getItem(slot).getItem() instanceof ParachuteItem parachute) {
                DyeColor color = parachute.getColor();
                return Constant.id("textures/model/parachute/" + (color == null ? DyeColor.WHITE : color).getName() + ".png");
            }
        }
        return null;
    }

    @Override
    protected ResourceLocation getTextureLocation(T entity) {
        ResourceLocation texture = getCanopyTexture(entity);
        return texture != null ? texture : super.getTextureLocation(entity);
    }
}
