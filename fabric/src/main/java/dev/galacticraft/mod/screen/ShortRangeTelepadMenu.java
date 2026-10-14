/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.screen;

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class ShortRangeTelepadMenu extends MachineMenu<ShortRangeTelepadBlockEntity> {
    public int address = -1, destination = -1, targetResult = 1, teleportTime, cooldown;
    public boolean addressValid, disabled, canEdit;
    public ShortRangeTelepadMenu(int id, Player player, ShortRangeTelepadBlockEntity pad) { super(GCMenuTypes.SHORT_RANGE_TELEPAD, id, player, pad); }
    public ShortRangeTelepadMenu(int id, Inventory inventory, BlockPos pos) { super(GCMenuTypes.SHORT_RANGE_TELEPAD, id, inventory, pos, 8, 127); }
    @Override protected void addPlayerInventorySlots(Inventory inventory, int x, int y) { super.addPlayerInventorySlots(inventory, 8, 127); }
    @Override public void registerData(MenuData data) {
        super.registerData(data);
        data.registerInt(be::getAddress, value -> address = value);
        data.registerInt(be::getDestination, value -> destination = value);
        data.registerInt(() -> be.getTargetResult().ordinal(), value -> targetResult = value);
        data.registerInt(be::getTeleportTime, value -> teleportTime = value);
        data.registerInt(be::getCooldown, value -> cooldown = value);
        data.registerBoolean(be::hasValidAddress, value -> addressValid = value);
        data.registerBoolean(be::isManuallyDisabled, value -> disabled = value);
        data.registerBoolean(() -> be.canEdit(player), value -> canEdit = value);
    }
    private boolean mayEdit(Player player) {
        return !player.level().isClientSide && stillValid(player) && be.canEdit(player)
                && player.level().getBlockEntity(be.getBlockPos()) == be;
    }
    public void configure(Player player, boolean destination, int value) {
        if (!mayEdit(player) || value < -1 || value > 999_999) return;
        be.configure(destination ? be.getAddress() : value, destination ? value : be.getDestination());
        broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !mayEdit(player)) return false;
        be.toggleDisabled();
        return true;
    }
}
