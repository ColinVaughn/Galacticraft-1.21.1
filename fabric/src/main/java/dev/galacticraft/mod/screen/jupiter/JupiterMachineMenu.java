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

package dev.galacticraft.mod.screen.jupiter;

import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.menu.MenuData;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Menu shared by the Jupiter machines. On top of MachineLib's storage sync it carries the machine's
 * {@link JupiterReadout}: a few numbers for gauges and a few lines of text for the screen's display panel.
 */
public class JupiterMachineMenu<T extends MachineBlockEntity> extends MachineMenu<T> {
    public List<Integer> readout = List.of();
    public List<String> lines = List.of();

    public JupiterMachineMenu(MenuType<? extends JupiterMachineMenu<T>> type, int syncId, Player player, T machine) {
        super(type, syncId, player, machine);
    }

    public JupiterMachineMenu(MenuType<? extends JupiterMachineMenu<T>> type, int syncId, Inventory inventory, BlockPos pos, int invX, int invY) {
        super(type, syncId, inventory, pos, invX, invY);
    }

    @Override
    public void registerData(@NotNull MenuData data) {
        super.registerData(data);
        data.register(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), () -> this.be instanceof JupiterReadout r ? r.readout() : List.of(), list -> this.readout = list);
        data.register(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), () -> this.be instanceof JupiterReadout r ? r.readoutLines() : List.of(), list -> this.lines = list);
    }

    /** Readout value {@code index}, or 0 before the first sync. */
    public int value(int index) {
        return index < this.readout.size() ? this.readout.get(index) : 0;
    }
}
