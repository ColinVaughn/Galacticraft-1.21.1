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

package dev.galacticraft.mod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.util.Graphics;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.tag.GCEntityTypeTags;
import dev.galacticraft.mod.tag.GCItemTags;
import dev.galacticraft.mod.util.Translations;
import dev.galacticraft.mod.particle.GCParticleTypes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client-side rendering and scan state for the Galacticraft Legacy Sensor Glasses. */
public final class SensorGlassesOverlay {
    public static final ResourceLocation MOB_TEXTURE = Constant.id("textures/misc/sensor_mobs.png");
    private static final ResourceLocation HUD_TEXTURE = Constant.id("textures/gui/sensor_glasses_hud.png");
    private static final ResourceLocation INDICATOR_TEXTURE = Constant.id("textures/gui/sensor_glasses_indicator.png");
    private static final int INDICATOR_COLOR = 0x03B88F;

    private static final Set<BlockPos> VALUABLE_BLOCKS = new HashSet<>();
    private static List<BlockPos> leakTrace = List.of();
    private static long lastScanTick = Long.MIN_VALUE;
    private static int zoom;
    private static boolean advancedMode;
    private static LocalPlayer lastPlayer;

    private SensorGlassesOverlay() {
    }

    public static boolean isWearing(LocalPlayer player) {
        return player != null && player.getItemBySlot(EquipmentSlot.HEAD).is(GCItemTags.SENSOR_GLASSES);
    }

    public static boolean shouldHighlight(LivingEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return isWearing(player) && entity.getType().is(GCEntityTypeTags.SENSOR_GLASSES_DETECTABLE);
    }

    public static void toggleAdvancedMode() {
        advancedMode = !advancedMode;
    }

    public static boolean isAdvancedMode() {
        return advancedMode;
    }

    public static void acceptLeakTrace(List<BlockPos> trace) {
        leakTrace = List.copyOf(trace);
    }

    public static void clientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != lastPlayer) {
            lastPlayer = player;
            advancedMode = false;
            VALUABLE_BLOCKS.clear();
            leakTrace = List.of();
            lastScanTick = Long.MIN_VALUE;
        }
        if (minecraft.level == null || !isWearing(player)) {
            VALUABLE_BLOCKS.clear();
            leakTrace = List.of();
            lastScanTick = Long.MIN_VALUE;
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (gameTime % 20L == 0L && gameTime != lastScanTick) {
            lastScanTick = gameTime;
            scanValuableBlocks(player);
        }

        if (!leakTrace.isEmpty()) {
            spawnLeakParticles(player);
        }
    }

    private static void scanValuableBlocks(LocalPlayer player) {
        VALUABLE_BLOCKS.clear();
        BlockPos origin = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    if (player.level().getBlockState(cursor).is(GCBlockTags.SENSOR_GLASSES_DETECTABLE)) {
                        VALUABLE_BLOCKS.add(cursor.immutable());
                    }
                }
            }
        }
    }

    private static void spawnLeakParticles(LocalPlayer player) {
        for (int i = leakTrace.size() - 1; i >= 0; i--) {
            if (i == 1) continue;
            BlockPos current = leakTrace.get(i);
            int nextIndex = i - 2;
            if (i > 2 && player.getRandom().nextInt(3) == 0) nextIndex--;
            BlockPos next = i > 1 ? leakTrace.get(nextIndex) : current.below(2);
            double x = current.getX() + 0.5D + player.getRandom().nextDouble() * 0.5D - 0.25D;
            double y = current.getY() + 0.5D + player.getRandom().nextDouble() * 0.5D - 0.25D;
            double z = current.getZ() + 0.5D + player.getRandom().nextDouble() * 0.5D - 0.25D;
            player.level().addParticle(GCParticleTypes.OXYGEN, x, y, z,
                    next.getX() - current.getX(), next.getY() - current.getY(), next.getZ() - current.getZ());
        }
    }

    public static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || minecraft.level == null || !isWearing(player)) return;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        float pulse = Mth.sin(++zoom / 80.0F) * 0.1F + 0.1F;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        Graphics.blit(graphics.pose().last().pose(),
                width / 2.0F - height - pulse * 80.0F,
                -pulse * 40.0F,
                height * 2.0F + pulse * 160.0F,
                height + pulse * 80.0F,
                -90.0F, 0.0F, 0.0F, 512.0F, 256.0F, 512, 256, HUD_TEXTURE);

        Component mode = Component.translatable(Translations.Ui.SENSOR_ADVANCED)
                .append(": ")
                .append(Component.translatable(advancedMode ? Translations.Ui.SENSOR_ADVANCED_ON : Translations.Ui.SENSOR_ADVANCED_OFF));
        graphics.drawString(minecraft.font, mode, width / 2 - 50, 4, INDICATOR_COLOR, false);

        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        double playerX = Mth.lerp(partialTick, player.xo, player.getX());
        double playerY = Mth.lerp(partialTick, player.yo, player.getY());
        double playerZ = Mth.lerp(partialTick, player.zo, player.getZ());
        float playerYaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());

        for (BlockPos pos : VALUABLE_BLOCKS) {
            double dx = playerX - pos.getX() - 0.5D;
            double dy = playerY - pos.getY() - 0.5D;
            double dz = playerZ - pos.getZ() - 0.5D;
            float angle = (float) Math.toDegrees(Math.atan2(dx, dz));
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5D;
            if (distance >= 4.0D) continue;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz) * 0.5D;
            float rotation = -angle - playerYaw + 180.0F;
            float radius = (float) ((advancedMode ? distance : horizontalDistance) * 16.0D);
            int alpha = (int) (Mth.clamp((float) ((distance - 1.0D) * 0.1D), 0.2F, 1.0F) * 255.0F);
            int color = FastColor.ARGB32.color(alpha, 0, 255, 198);

            graphics.pose().pushPose();
            graphics.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
            graphics.pose().translate(0.0F, -radius, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(-rotation));
            Graphics.blitCentered(graphics.pose().last().pose(), 0.0F, 0.0F,
                    8.0F, 8.0F, 1.0F, 0.0F, 0.0F, 16.0F, 16.0F,
                    16, 16, INDICATOR_TEXTURE, color);
            graphics.pose().popPose();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
