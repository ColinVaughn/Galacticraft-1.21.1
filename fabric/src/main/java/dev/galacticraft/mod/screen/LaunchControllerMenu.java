/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 * Licensed under the MIT license. See LICENSE in the project root.
 */
package dev.galacticraft.mod.screen;

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.LaunchControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class LaunchControllerMenu extends MachineMenu<LaunchControllerBlockEntity> {
    public int frequency = -1;
    public int destination = -1;
    public int options;
    public int condition;
    public boolean canEdit;
    public boolean frequencyValid;
    public boolean destinationValid;
    public boolean padConnected;
    public int saveResult;

    public LaunchControllerMenu(int id, Inventory inventory, BlockPos pos) {
        super(GCMenuTypes.LAUNCH_CONTROLLER, id, inventory, pos, 8, 127);
    }

    public LaunchControllerMenu(int id, Inventory inventory, LaunchControllerBlockEntity controller) {
        super(GCMenuTypes.LAUNCH_CONTROLLER, id, inventory.player, controller);
    }

    @Override
    public void registerData(MenuData data) {
        super.registerData(data);
        data.registerInt(this.be::getFrequency, value -> this.frequency = value);
        data.registerInt(() -> this.be.canEdit(this.player) || !this.be.hasOption(LaunchControllerBlockEntity.HIDE_DESTINATION)
                ? this.be.getDestination() : -1, value -> this.destination = value);
        data.registerInt(this.be::getOptions, value -> this.options = value);
        data.registerInt(() -> this.be.getCondition().ordinal(), value -> this.condition = value);
        data.registerBoolean(() -> this.be.canEdit(this.player), value -> this.canEdit = value);
        data.registerBoolean(this.be::hasValidFrequency, value -> this.frequencyValid = value);
        data.registerBoolean(this.be::hasValidDestination, value -> this.destinationValid = value);
        data.registerBoolean(() -> this.be.getConnectedPad() != null, value -> this.padConnected = value);
        data.registerInt(() -> this.saveResult, value -> this.saveResult = value);
    }

    public void configure(Player player, int frequency, int destination, int condition, int options) {
        if (player.level().isClientSide || !this.stillValid(player) || !this.be.canEdit(player)
                || player.level().getBlockEntity(this.be.getBlockPos()) != this.be) return;
        this.saveResult = this.be.configure(frequency, destination, condition, options) ? 1 : -1;
        this.broadcastChanges();
    }
}
