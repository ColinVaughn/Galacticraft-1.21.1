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

package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.screen.GCMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A machinelib port of legacy Galacticraft's Painter. Recolors a dyeable input block into a different
 * color of the same family using a dye. Supported families are the vanilla 16-color sets that cannot
 * otherwise be recolored in a crafting table (glass, terracotta, concrete, ...), plus wool and carpet.
 */
public class PainterBlockEntity extends MachineBlockEntity {
    public static final int INPUT_SLOT = 0;
    public static final int DYE_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    /**
     * Maps every colored variant to the 16-entry array (indexed by {@link DyeColor#getId()}) of its
     * color family, so a recolor is a single array lookup.
     */
    private static final Map<Item, Item[]> RECOLOR = new HashMap<>();

    static {
        String[] families = {"wool", "carpet", "terracotta", "stained_glass", "stained_glass_pane", "concrete", "concrete_powder"};
        for (String family : families) {
            Item[] byColor = new Item[16];
            boolean complete = true;
            for (DyeColor color : DyeColor.values()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getSerializedName() + "_" + family));
                byColor[color.getId()] = item;
                if (item == Items.AIR) complete = false;
            }
            if (!complete) continue;
            for (DyeColor color : DyeColor.values()) {
                RECOLOR.put(byColor[color.getId()], byColor);
            }
        }
    }

    /**
     * {@return the recolored variant of {@code input} for {@code color}, or {@code null} if {@code input}
     * is not a recognized dyeable item or is already that color}
     */
    @Nullable
    public static Item recolor(Item input, DyeColor color) {
        Item[] family = RECOLOR.get(input);
        if (family == null) return null;
        Item result = family[color.getId()];
        if (result == input || result == Items.AIR) return null;
        return result;
    }

    public static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.INPUT)
                            .pos(52, 35),
                    ItemResourceSlot.builder(TransferType.INPUT)
                            .pos(8, 62)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.DUST)),
                    ItemResourceSlot.builder(TransferType.OUTPUT)
                            .pos(113, 35)
            )
    );

    public PainterBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.PAINTER, pos, state, SPEC);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        ItemResourceSlot input = this.itemStorage().slot(INPUT_SLOT);
        ItemResourceSlot dye = this.itemStorage().slot(DYE_SLOT);
        ItemResourceSlot output = this.itemStorage().slot(OUTPUT_SLOT);

        if (input.isEmpty() || dye.isEmpty()) return MachineStatuses.IDLE;
        if (!(dye.getResource() instanceof DyeItem dyeItem)) return MachineStatuses.IDLE;

        Item result = recolor(input.getResource(), dyeItem.getDyeColor());
        if (result == null) return MachineStatuses.IDLE;

        if (!output.canInsert(result, 1)) return MachineStatuses.OUTPUT_FULL;

        input.extract(1);
        dye.extract(1);
        output.insert(result, 1);
        return MachineStatuses.ACTIVE;
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new MachineMenu<>(GCMenuTypes.PAINTER, syncId, player, this);
    }
}
