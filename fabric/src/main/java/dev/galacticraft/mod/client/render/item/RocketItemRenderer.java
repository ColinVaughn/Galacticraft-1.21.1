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

package dev.galacticraft.mod.client.render.item;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.entity.rocket.render.RocketPartRendererRegistry;
import dev.galacticraft.api.entity.rocket.render.RocketModelSlot;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.api.rocket.RocketGeometry;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.client.util.LazyRocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class RocketItemRenderer {
    private final LazyRocketEntity lazyRocket = new LazyRocketEntity();

    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        ClientLevel level = Minecraft.getInstance().level;
        RocketEntity rocket = this.lazyRocket.get();
        if (level == null || rocket == null) {
            return; // nothing to render the rocket against
        }

        RocketModuleItem module = stack.getItem() instanceof RocketModuleItem item ? item : null;
        RocketData data = module != null ? module.rocketData(stack)
                : stack.has(GCDataComponents.ROCKET_DATA) ? stack.get(GCDataComponents.ROCKET_DATA) : RocketPrefabs.MISSING;
        rocket.setData(data);
        rocket.setOldPosAndRot();
        matrices.pushPose();

        if (module != null) {
            matrices.translate(0.5, 0.5, 0.5);
            matrices.mulPose(Axis.YP.rotationDegrees(45));
            matrices.mulPose(Axis.XP.rotationDegrees(15));
            RocketPartTypes moduleType = module.type(stack);
            float scale = moduleType == RocketPartTypes.ENGINE ? .6F : moduleType == RocketPartTypes.FIN ? .3F : .4F;
            matrices.scale(scale, scale, scale);
        } else switch (mode) {
            case ItemDisplayContext.THIRD_PERSON_LEFT_HAND:
                matrices.mulPose(Axis.XP.rotation(Mth.HALF_PI - 0.2F));
                matrices.mulPose(Axis.ZN.rotation(Mth.HALF_PI - 0.4F));
                matrices.mulPose(Axis.YP.rotation(Mth.PI));
                matrices.translate(-0.4F, 0.0F, 0.5F);
                break;
            case ItemDisplayContext.THIRD_PERSON_RIGHT_HAND:
                matrices.mulPose(Axis.XP.rotation(Mth.HALF_PI - 0.2F));
                matrices.mulPose(Axis.ZN.rotation(Mth.HALF_PI + 0.2F));
                matrices.mulPose(Axis.YP.rotation(Mth.PI));
                matrices.translate(0.1F, -1.6F, 0.5F);
                break;
            case ItemDisplayContext.FIRST_PERSON_LEFT_HAND:
            case ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:
                matrices.scale(2.0F, 2.0F, 2.0F);
                matrices.mulPose(Axis.YP.rotationDegrees(45));
                matrices.mulPose(Axis.XP.rotation(Mth.HALF_PI + 0.2F));
                matrices.mulPose(Axis.ZN.rotation(0.65F));
                matrices.translate(0.5F, -2.0F, -2.0F);
                break;
            case ItemDisplayContext.GUI:
                matrices.translate(0.7F, 0.35F, 0.0F);
                matrices.scale(0.25F, 0.25F, 0.25F);
                matrices.mulPose(Axis.ZP.rotationDegrees(55));
                matrices.mulPose(Axis.XP.rotationDegrees(45));
                matrices.mulPose(Axis.YP.rotation((float) (level.getGameTime() * 66.666666666666 / 1000.0F)));
                break;
            case ItemDisplayContext.FIXED:
                matrices.translate(0.5F, 0.0F, 0.4F);
                matrices.scale(0.25F, 0.25F, 0.25F);
                break;
            case ItemDisplayContext.GROUND:
            default:
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.2F, 0.2F, 0.2F);
        }
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        // Preserve the item's existing engine-centred display transforms. A module item centres on
        // its one part, shifted by however far that part's layout moves its middle from stock.
        RocketGeometry geometry = RocketGeometry.of(data, level.registryAccess());
        double center = 7;
        RocketPartTypes moduleType = module == null ? null : module.type(stack);
        if (moduleType != null) {
            RocketModelSlot slot = switch (moduleType) {
                case BODY -> RocketModelSlot.BODY;
                case FIN -> RocketModelSlot.FIN;
                case CONE -> RocketModelSlot.CONE;
                case BOOSTER -> RocketModelSlot.BOOSTER;
                default -> RocketModelSlot.ENGINE;
            };
            double stockCenter = switch (moduleType) {
                case BODY -> 35;
                case ENGINE -> 8;
                case FIN -> 14;
                case CONE -> 69.3;
                default -> 7;
            };
            RocketPart<?, ?> part = modulePart(data, moduleType, level);
            center = stockCenter + (geometry.mountY(slot) - slot.y() * 16)
                    + (RocketGeometry.height(part, slot) - slot.defaultHeight()) / 2.0;
        }
        matrices.translate(0, -center / 16, 0);

        renderPart(data.engine(), RocketModelSlot.ENGINE, geometry, level, rocket, matrices, vertexConsumers, light, overlay);
        renderPart(data.booster(), RocketModelSlot.BOOSTER, geometry, level, rocket, matrices, vertexConsumers, light, overlay);
        renderPart(data.fin(), RocketModelSlot.FIN, geometry, level, rocket, matrices, vertexConsumers, light, overlay);
        renderPart(data.body(), RocketModelSlot.BODY, geometry, level, rocket, matrices, vertexConsumers, light, overlay);
        renderPart(data.cone(), RocketModelSlot.CONE, geometry, level, rocket, matrices, vertexConsumers, light, overlay);

        matrices.popPose();
    }

    private static <T extends RocketPart<?, ?>> void renderPart(Optional<EitherHolder<T>> part, RocketModelSlot slot, RocketGeometry geometry,
                                                               ClientLevel level, RocketEntity rocket, PoseStack matrices,
                                                               MultiBufferSource vertexConsumers, int light, int overlay) {
        part.ifPresent(holder -> {
            matrices.pushPose();
            matrices.translate(0, geometry.mountBlocks(slot), 0);
            RocketPartRendererRegistry.INSTANCE.getRenderer(holder.key()).render(level, matrices, rocket, vertexConsumers, 0, light, overlay);
            matrices.popPose();
        });
    }

    private static @Nullable RocketPart<?, ?> modulePart(RocketData data, RocketPartTypes type, ClientLevel level) {
        Optional<? extends EitherHolder<? extends RocketPart<?, ?>>> part = switch (type) {
            case CONE -> data.cone();
            case BODY -> data.body();
            case FIN -> data.fin();
            case BOOSTER -> data.booster();
            case ENGINE -> data.engine();
            case UPGRADE -> data.upgrade();
        };
        return part.flatMap(holder -> holder.unwrap(level.registryAccess()))
                .filter(Holder::isBound).map(Holder::value).orElse(null);
    }
}
