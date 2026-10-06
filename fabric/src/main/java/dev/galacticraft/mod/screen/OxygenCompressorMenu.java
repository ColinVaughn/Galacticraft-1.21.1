package dev.galacticraft.mod.screen;


import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.entity.machine.OxygenCompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class OxygenCompressorMenu extends MachineMenu<OxygenCompressorBlockEntity> {
    public boolean compressionMode;

    public OxygenCompressorMenu(int syncId, Player player, OxygenCompressorBlockEntity machine) {
        super(GCMenuTypes.OXYGEN_COMPRESSOR, syncId, player, machine);
        this.copyFromMachine();
    }

    public OxygenCompressorMenu(int syncId, Inventory inv, BlockPos pos) {
        super(GCMenuTypes.OXYGEN_COMPRESSOR, syncId, inv, pos, 8, 84);
    }

    @SuppressWarnings("UnstableApiUsage")
    private void copyFromMachine() {
        this.compressionMode = this.be.getCompressionMode();
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void registerData(@NotNull MenuData data) {
        super.registerData(data);
        data.registerBoolean(this.be::getCompressionMode, value -> this.compressionMode = value);
    }
}
