/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.screen;

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.GasProcessingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public class GasProcessingMenu<M extends GasProcessingBlockEntity> extends MachineMenu<M> {
    public boolean disabled;
    public int cooldown;

    public GasProcessingMenu(MenuType<? extends MachineMenu<M>> type, int id, Player player, M machine) {
        super(type, id, player, machine);
    }

    public GasProcessingMenu(MenuType<? extends MachineMenu<M>> type, int id, Inventory inventory, BlockPos pos) {
        super(type, id, inventory, pos, 8, 86);
    }

    @Override
    protected void addPlayerInventorySlots(Inventory inventory, int x, int y) {
        super.addPlayerInventorySlots(inventory, 8, 86);
    }

    @Override
    public void registerData(MenuData data) {
        super.registerData(data);
        data.registerBoolean(be::isManuallyDisabled, value -> disabled = value);
        data.registerInt(be::getDisableCooldown, value -> cooldown = value);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !stillValid(player)) return false;
        be.toggleDisabled();
        return true;
    }
}
