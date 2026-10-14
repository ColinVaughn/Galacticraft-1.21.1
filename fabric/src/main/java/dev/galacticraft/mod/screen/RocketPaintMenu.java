/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.screen;

import dev.architectury.registry.menu.MenuRegistry;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.item.RocketItem;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

public class RocketPaintMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300;
    public static final int INVENTORY_X = (WIDTH - Constant.RocketWorkbench.PAGE_WIDTH) / 2 + 8;
    private static final List<RocketPartTypes> PARTS = List.of(RocketPartTypes.CONE, RocketPartTypes.BODY, RocketPartTypes.FIN, RocketPartTypes.ENGINE);
    private static final Set<ResourceKey<?>> PAINTABLE = Set.of(
            GCRocketParts.TIER_1_CONE, GCRocketParts.TIER_2_CONE, GCRocketParts.TIER_3_CONE,
            GCRocketParts.TIER_1_BODY, GCRocketParts.TIER_2_BODY, GCRocketParts.TIER_3_BODY,
            GCRocketParts.TIER_1_FIN, GCRocketParts.TIER_2_FIN, GCRocketParts.TIER_3_FIN,
            GCRocketParts.TIER_1_ENGINE, GCRocketParts.TIER_2_ENGINE, GCRocketParts.TIER_3_ENGINE);
    public final RocketWorkbenchBlockEntity workbench;
    private final ResourceLocation returnPage;
    private int revision;

    public RocketPaintMenu(int id, Inventory inventory, RocketWorkbenchMenu.OpeningData opening) {
        this(id, (RocketWorkbenchBlockEntity) inventory.player.level().getBlockEntity(opening.pos()), inventory, opening.page().orElse(null));
    }

    public RocketPaintMenu(int id, RocketWorkbenchBlockEntity workbench, Inventory inventory, ResourceLocation returnPage) {
        super(GCMenuTypes.ROCKET_PAINT, id);
        this.workbench = workbench;
        this.returnPage = returnPage;
        addSlot(new Slot(workbench.paintInput, 0, 22, 34) {
            @Override public boolean mayPlace(ItemStack stack) { return canPaint(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, 9 + row * 9 + column, INVENTORY_X + column * 18, Constant.RocketWorkbench.ROCKET_INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, Constant.RocketWorkbench.ROCKET_HOTBAR_Y));
        // Vanilla menu data transmits shorts; split the revision so long sessions cannot wrap it.
        for (int shift : new int[]{0, 16}) addDataSlot(new DataSlot() {
            @Override public int get() { return revision() >>> shift & 0xFFFF; }
            @Override public void set(int value) { revision = revision & ~(0xFFFF << shift) | (value & 0xFFFF) << shift; }
        });
    }

    public int revision() { return workbench.getLevel().isClientSide ? revision : workbench.paintRevision; }
    public ItemStack input() { return workbench.paintInput.getItem(0); }

    public static RocketData data(ItemStack stack) {
        return stack.getItem() instanceof RocketModuleItem module ? module.rocketData(stack)
                : stack.getOrDefault(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1);
    }

    public static boolean canPaint(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof RocketItem || stack.getItem() instanceof RocketModuleItem)
                && !parts(stack).isEmpty();
    }

    public static List<RocketPartTypes> parts(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof RocketItem || stack.getItem() instanceof RocketModuleItem)) return List.of();
        RocketData data = data(stack);
        return PARTS.stream().filter(part -> switch (part) {
            case CONE -> data.cone().filter(holder -> PAINTABLE.contains(holder.key())).isPresent();
            case BODY -> data.body().filter(holder -> PAINTABLE.contains(holder.key())).isPresent();
            case FIN -> data.fin().filter(holder -> PAINTABLE.contains(holder.key())).isPresent();
            case ENGINE -> data.engine().filter(holder -> PAINTABLE.contains(holder.key())).isPresent();
            default -> false;
        }).toList();
    }

    public boolean apply(Player player, int expectedRevision, RocketPaint paint) {
        if (player.level().isClientSide || player.containerMenu != this || !stillValid(player)) return false;
        if (expectedRevision != revision() || !canPaint(input())) {
            broadcastFullState();
            return false;
        }
        RocketData data = data(input());
        List<RocketPartTypes> parts = parts(input());
        for (RocketPartTypes part : RocketPartTypes.values()) {
            if (!parts.contains(part) && !paint.color(part).equals(data.paint().color(part))) return false;
        }
        if (paint.equals(data.paint())) return false;
        ItemStack result = input().copy();
        result.set(GCDataComponents.ROCKET_DATA, data.withPaint(paint));
        workbench.paintInput.setItem(0, result);
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(workbench, player); }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || !stillValid(player) || !(player instanceof ServerPlayer serverPlayer)) return false;
        serverPlayer.doCloseContainer();
        MenuRegistry.openExtendedMenu(serverPlayer, workbench.menuOn(returnPage));
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (canPaint(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}