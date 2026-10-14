/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.screen;

import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.GeothermalGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class GeothermalGeneratorMenu extends MachineMenu<GeothermalGeneratorBlockEntity> {
    public boolean disabled;
    public boolean validSpout;
    public int cooldown;
    public int generation;

    public GeothermalGeneratorMenu(int id, Player player, GeothermalGeneratorBlockEntity machine) {
        super(GCMenuTypes.GEOTHERMAL_GENERATOR, id, player, machine);
    }

    public GeothermalGeneratorMenu(int id, Inventory inventory, BlockPos pos) {
        super(GCMenuTypes.GEOTHERMAL_GENERATOR, id, inventory, pos, 8, 119);
    }

    @Override
    protected void addPlayerInventorySlots(Inventory inventory, int x, int y) {
        super.addPlayerInventorySlots(inventory, 8, 119);
    }

    @Override
    public void registerData(MenuData data) {
        super.registerData(data);
        data.registerBoolean(be::isManuallyDisabled, value -> disabled = value);
        data.registerBoolean(be::hasValidSpout, value -> validSpout = value);
        data.registerInt(be::getDisableCooldown, value -> cooldown = value);
        data.registerInt(be::getGeneration, value -> generation = value);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !stillValid(player)) return false;
        be.toggleDisabled();
        return true;
    }
}
