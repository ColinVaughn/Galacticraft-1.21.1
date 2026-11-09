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

package dev.galacticraft.mod.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.HitboxSpaceGear;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    public abstract <T extends Entity> EntityRenderer<? super T> getRenderer(T entity);

    /**
     * Dresses mobs evolved for space whose renderer drew no gear for them: renderers with models the
     * fitted layer cannot read, and renderers that are not living-entity renderers at all. Runs with
     * the pose still at the entity, right after its renderer returns.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", shift = At.Shift.AFTER))
    private <E extends Entity> void galacticraft$dressUnfittedMobs(E entity, double x, double y, double z, float yaw, float partialTick,
                                                                PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        boolean fitted = SpaceGearRenderState.takeDrawn(entity);
        if (fitted || !SpaceGearRenderState.wearsGear(entity) || SpaceGearRenderState.hasHandTunedGear(this.getRenderer(entity))) return;
        HitboxSpaceGear.draw((LivingEntity) entity, partialTick, pose, buffers, light);
    }
}
