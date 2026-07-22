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

package dev.galacticraft.impl.internal.mixin.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.impl.internal.client.tabs.InventoryTabLayout;
import dev.galacticraft.impl.internal.client.tabs.InventoryTabRegistryImpl;
import dev.galacticraft.mod.Constant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    @Shadow
    protected int imageHeight;

    @Shadow
    @Final
    protected T menu;

    protected AbstractContainerScreenMixin(Component component) {
        super(component);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onTabClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> ci) {
        List<InventoryTabRegistryImpl.TabData> tabs = this.getVisibleTabs();
        if (tabs.isEmpty()) {
            return;
        }

        InventoryTabLayout.Position position = this.getTabPosition(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            InventoryTabRegistryImpl.TabData data = tabs.get(i);
            if (this.menu.getClass().equals(data.clazz())) {
                continue;
            }

            if (mouseX >= position.x() + (InventoryTabLayout.TAB_WIDTH * i)
                    && mouseX < position.x() + (InventoryTabLayout.TAB_WIDTH * (i + 1))
                    && mouseY >= position.y()
                    && mouseY < position.y() + InventoryTabLayout.TAB_HEIGHT) {
                data.onClick().run();
                ci.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "render", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = At.Shift.AFTER)
    })
    public void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float v, CallbackInfo callbackInfo) {
        List<InventoryTabRegistryImpl.TabData> tabs = this.getVisibleTabs();
        if (tabs.isEmpty()) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceLocation texture = Constant.id("textures/gui/player_inventory_switch_tabs.png");
        InventoryTabLayout.Position position = this.getTabPosition(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            InventoryTabRegistryImpl.TabData data = tabs.get(i);
            int x = position.x() + (InventoryTabLayout.TAB_WIDTH * i);
            if (this.menu.getClass().equals(data.clazz())) {
                if (i == 0) {
                    graphics.blit(texture, x, position.y(), 0.0F, 0.0F, 29, 32, 64, 64);
                } else {
                    graphics.blit(texture, x, position.y(), 29.0F, 32.0F, 29, 32, 64, 64);
                }
            } else {
                if (i == 0) {
                    graphics.blit(texture, x, position.y(), 0.0F, 32.0F, 29, 32, 64, 64);
                } else {
                    graphics.blit(texture, x, position.y(), 29.0F, 0.0F, 29, 32, 64, 64);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float v, CallbackInfo callbackInfo) {
        List<InventoryTabRegistryImpl.TabData> tabs = this.getVisibleTabs();
        if (tabs.isEmpty()) {
            return;
        }

        Lighting.setupFor3DItems();
        InventoryTabLayout.Position position = this.getTabPosition(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            graphics.renderItem(tabs.get(i).icon(), position.x() + 6 + (InventoryTabLayout.TAB_WIDTH * i), position.y() + 8);
        }
        Lighting.setupForFlatItems();
    }

    @Unique
    private List<InventoryTabRegistryImpl.TabData> getVisibleTabs() {
        boolean tabsVisible = InventoryTabRegistryImpl.INSTANCE.TABS.stream().anyMatch(data -> this.menu.getClass().equals(data.clazz()));
        if (!tabsVisible) {
            return List.of();
        }

        return InventoryTabRegistryImpl.INSTANCE.TABS.stream()
                .filter(data -> this.menu.getClass().equals(data.clazz()) || data.visiblePredicate().test(Minecraft.getInstance().player))
                .toList();
    }

    @Unique
    private InventoryTabLayout.Position getTabPosition(int tabCount) {
        List<InventoryTabLayout.Bounds> occupied = this.children().stream()
                .filter(child -> child instanceof AbstractWidget)
                .map(child -> (AbstractWidget) child)
                .filter(widget -> widget.visible)
                .map(widget -> new InventoryTabLayout.Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()))
                .collect(Collectors.toCollection(ArrayList::new));
        occupied.add(new InventoryTabLayout.Bounds(this.leftPos, this.topPos + 4, this.imageWidth, this.imageHeight - 4));
        return InventoryTabLayout.findPosition(this.leftPos, this.topPos - 28, tabCount, this.width, this.height, occupied);
    }
}
