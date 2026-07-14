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

package dev.galacticraft.mod.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.accessor.GameRendererAccessor;
import dev.galacticraft.mod.client.render.dimension.LunarSkyExposure;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererAccessor {
    @Unique
    private static final float DEPTH_FAR = 32.0F * 16.0F * 4.0F;

    @Unique
    private static final ResourceLocation MOON_POST_EFFECT = Constant.id("shaders/post/moon.json");

    @Unique
    private static final ResourceLocation MARS_POST_EFFECT = Constant.id("shaders/post/mars.json");

    @Unique
    private ResourceLocation galacticraft$activePlanetPostEffect;

    @Unique
    private ResourceLocation galacticraft$failedPlanetPostEffect;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int confusionAnimationTick;

    @Shadow
    private float zoom;

    @Shadow
    private float zoomX;

    @Shadow
    private float zoomY;

    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    private PostChain postEffect;

    @Shadow
    public abstract void shutdownEffect();

    @Shadow
    public abstract double getFov(Camera camera, float f, boolean bl);

    @Shadow
    public abstract void bobHurt(PoseStack poseStack, float f);

    @Shadow
    public abstract void bobView(PoseStack poseStack, float f);

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f matrix4f);

    @Inject(method = "tick", at = @At("TAIL"))
    private void galacticraft$updatePlanetPostEffect(CallbackInfo ci) {
        LunarSkyExposure.tick(this.minecraft);
        ResourceLocation desiredEffect = this.galacticraft$getPlanetPostEffect();

        if (this.galacticraft$activePlanetPostEffect != null
                && !this.galacticraft$activePlanetPostEffect.equals(desiredEffect)) {
            this.shutdownEffect();
        }

        // Respect mob vision and other temporary post effects. The planetary pass
        // returns automatically when they release vanilla's post-effect slot.
        if (desiredEffect != null
                && this.postEffect == null
                && !desiredEffect.equals(this.galacticraft$failedPlanetPostEffect)) {
            ((GameRendererInvoker) this).galacticraft$invokeLoadEffect(desiredEffect);
        }

        if (MOON_POST_EFFECT.equals(this.galacticraft$activePlanetPostEffect) && this.postEffect != null) {
            this.postEffect.setUniform("Exposure", LunarSkyExposure.value());
        }
    }

    @Inject(method = "loadEffect", at = @At("RETURN"))
    private void galacticraft$trackPlanetPostEffect(ResourceLocation effect, CallbackInfo ci) {
        if (!galacticraft$isPlanetPostEffect(effect)) {
            this.galacticraft$activePlanetPostEffect = null;
            return;
        }

        if (this.postEffect != null) {
            this.galacticraft$activePlanetPostEffect = effect;
            this.galacticraft$failedPlanetPostEffect = null;
            Constant.LOGGER.info("Loaded planetary post effect {}", effect);
        } else {
            // Avoid retrying a malformed shader every tick and flooding the log.
            this.galacticraft$activePlanetPostEffect = null;
            this.galacticraft$failedPlanetPostEffect = effect;
        }
    }

    @Inject(method = "shutdownEffect", at = @At("HEAD"))
    private void galacticraft$clearPlanetPostEffect(CallbackInfo ci) {
        this.galacticraft$activePlanetPostEffect = null;
    }

    @Inject(method = "reloadShaders", at = @At("HEAD"))
    private void galacticraft$retryPlanetPostEffectAfterReload(ResourceProvider resources, CallbackInfo ci) {
        this.galacticraft$failedPlanetPostEffect = null;
    }

    @Unique
    private ResourceLocation galacticraft$getPlanetPostEffect() {
        if (this.minecraft.level == null) return null;
        if (this.minecraft.level.dimension().equals(GCDimensions.MOON)) return MOON_POST_EFFECT;
        if (this.minecraft.level.dimension().equals(GCDimensions.MARS)) return MARS_POST_EFFECT;
        return null;
    }

    @Unique
    private static boolean galacticraft$isPlanetPostEffect(ResourceLocation effect) {
        return effect.equals(MOON_POST_EFFECT) || effect.equals(MARS_POST_EFFECT);
    }

    @Override
    public void galacticraft$overworldProjectionMatrix(float partialTicks, Camera camera) {
        Matrix4f matrix4f = new Matrix4f();
        if (this.zoom != 1.0F) {
            matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
            matrix4f.scale(this.zoom, this.zoom, 1.0F);
        }
        double fov = this.getFov(camera, partialTicks, true);
        matrix4f = matrix4f.perspective((float) (fov * 0.01745329238474369), (float) this.minecraft.getWindow().getWidth() / (float) this.minecraft.getWindow().getHeight(), 0.05F, DEPTH_FAR);

        PoseStack poseStack = new PoseStack();
        this.bobHurt(poseStack, camera.getPartialTickTime());
        if (this.minecraft.options.bobView().get().booleanValue()) {
            this.bobView(poseStack, camera.getPartialTickTime());
        }
        matrix4f.mul(poseStack.last().pose());
        float scale = this.minecraft.options.screenEffectScale().get().floatValue();
        float intensity = Mth.lerp(partialTicks, this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity) * (scale * scale);
        if (intensity > 0.0F) {
            float baseAngle = this.minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7.0F : 20.0F;
            float warp = 5.0F / (intensity * intensity + 5.0F) - intensity * 0.04F;
            warp *= warp;
            Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float angle = ((float) this.confusionAnimationTick + partialTicks) * baseAngle * Mth.DEG_TO_RAD;
            matrix4f.rotate(angle, vector3f);
            matrix4f.scale(1.0f / warp, 1.0F, 1.0F);
            matrix4f.rotate(-angle, vector3f);
        }
        this.resetProjectionMatrix(matrix4f);
    }
}
