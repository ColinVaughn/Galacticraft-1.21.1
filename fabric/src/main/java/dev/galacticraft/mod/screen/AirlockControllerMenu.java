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

package dev.galacticraft.mod.screen;

import dev.galacticraft.mod.content.block.entity.AirlockControllerBlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class AirlockControllerMenu extends AbstractContainerMenu {
    public static final int BUTTON_REDSTONE = 0;
    public static final int BUTTON_PLAYER_DISTANCE = 1;
    public static final int BUTTON_DISTANCE = 2;
    public static final int BUTTON_PLAYER_NAME = 3;
    public static final int BUTTON_INVERT = 4;
    public static final int BUTTON_HORIZONTAL = 5;

    private static final int DATA_REDSTONE = 0;
    private static final int DATA_PLAYER_DISTANCE = 1;
    private static final int DATA_DISTANCE = 2;
    private static final int DATA_PLAYER_NAME = 3;
    private static final int DATA_INVERT = 4;
    private static final int DATA_HORIZONTAL = 5;
    private static final int DATA_ACTIVE = 6;
    private static final int DATA_CAN_EDIT = 7;
    private static final int DATA_NAME_LENGTH = 8;
    private static final int DATA_NAME_START = 9;
    private static final int MAX_PLAYER_NAME_LENGTH = 16;
    private static final int DATA_COUNT = DATA_NAME_START + MAX_PLAYER_NAME_LENGTH;

    private final @Nullable AirlockControllerBlockEntity controller;
    private final ContainerData data;

    /** Client-side constructor used by the registered menu factory. */
    public AirlockControllerMenu(int syncId, Inventory inventory) {
        this(syncId, null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server-side constructor bound to the controller being configured. */
    public AirlockControllerMenu(int syncId, Inventory inventory, AirlockControllerBlockEntity controller) {
        this(syncId, controller, createData(controller, inventory.player));
    }

    private AirlockControllerMenu(int syncId, @Nullable AirlockControllerBlockEntity controller, ContainerData data) {
        super(GCMenuTypes.AIRLOCK_CONTROLLER_MENU, syncId);
        this.controller = controller;
        this.data = data;
        this.addDataSlots(data);
    }

    private static ContainerData createData(AirlockControllerBlockEntity controller, Player player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_REDSTONE -> controller.redstoneActivation ? 1 : 0;
                    case DATA_PLAYER_DISTANCE -> controller.playerDistanceActivation ? 1 : 0;
                    case DATA_DISTANCE -> controller.playerDistanceSelection;
                    case DATA_PLAYER_NAME -> controller.playerNameMatches ? 1 : 0;
                    case DATA_INVERT -> controller.invertSelection ? 1 : 0;
                    case DATA_HORIZONTAL -> controller.horizontalModeEnabled ? 1 : 0;
                    case DATA_ACTIVE -> controller.active ? 1 : 0;
                    case DATA_CAN_EDIT -> controller.canPlayerEdit(player) ? 1 : 0;
                    case DATA_NAME_LENGTH -> Math.min(controller.playerToOpenFor.length(), MAX_PLAYER_NAME_LENGTH);
                    default -> {
                        int character = index - DATA_NAME_START;
                        yield character >= 0 && character < controller.playerToOpenFor.length()
                                ? controller.playerToOpenFor.charAt(character)
                                : 0;
                    }
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case DATA_REDSTONE -> controller.redstoneActivation = value != 0;
                    case DATA_PLAYER_DISTANCE -> controller.playerDistanceActivation = value != 0;
                    case DATA_DISTANCE -> controller.playerDistanceSelection = Mth.clamp(value, 0, 3);
                    case DATA_PLAYER_NAME -> controller.playerNameMatches = value != 0;
                    case DATA_INVERT -> controller.invertSelection = value != 0;
                    case DATA_HORIZONTAL -> controller.horizontalModeEnabled = value != 0;
                    default -> {
                    }
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (this.controller != null && !this.controller.canPlayerEdit(player)) {
            return false;
        }
        if (this.controller == null && !this.canEdit()) {
            return false;
        }

        int dataIndex = switch (buttonId) {
            case BUTTON_REDSTONE -> DATA_REDSTONE;
            case BUTTON_PLAYER_DISTANCE -> DATA_PLAYER_DISTANCE;
            case BUTTON_PLAYER_NAME -> DATA_PLAYER_NAME;
            case BUTTON_INVERT -> DATA_INVERT;
            case BUTTON_HORIZONTAL -> DATA_HORIZONTAL;
            default -> -1;
        };
        if (dataIndex >= 0) {
            this.data.set(dataIndex, this.data.get(dataIndex) == 0 ? 1 : 0);
        } else if (buttonId == BUTTON_DISTANCE) {
            this.data.set(DATA_DISTANCE, (this.data.get(DATA_DISTANCE) + 1) % 4);
        } else {
            return false;
        }

        if (this.controller != null) {
            this.controller.configurationChanged();
        }
        return true;
    }

    public void setPlayerName(Player player, String name) {
        if (this.controller != null && this.controller.canPlayerEdit(player)) {
            this.controller.setPlayerToOpenFor(name);
        }
    }

    public boolean redstoneActivation() {
        return this.data.get(DATA_REDSTONE) != 0;
    }

    public boolean playerDistanceActivation() {
        return this.data.get(DATA_PLAYER_DISTANCE) != 0;
    }

    public int playerDistanceSelection() {
        return Mth.clamp(this.data.get(DATA_DISTANCE), 0, 3);
    }

    public boolean playerNameMatches() {
        return this.data.get(DATA_PLAYER_NAME) != 0;
    }

    public boolean invertSelection() {
        return this.data.get(DATA_INVERT) != 0;
    }

    public boolean horizontalModeEnabled() {
        return this.data.get(DATA_HORIZONTAL) != 0;
    }

    public boolean active() {
        return this.data.get(DATA_ACTIVE) != 0;
    }

    public boolean canEdit() {
        return this.data.get(DATA_CAN_EDIT) != 0;
    }

    public String playerName() {
        int length = Mth.clamp(this.data.get(DATA_NAME_LENGTH), 0, MAX_PLAYER_NAME_LENGTH);
        StringBuilder name = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            name.append((char) this.data.get(DATA_NAME_START + index));
        }
        return name.toString();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.controller == null || this.controller.getLevel() == null) {
            return true;
        }
        return this.controller.getLevel().getBlockEntity(this.controller.getBlockPos()) == this.controller
                && player.distanceToSqr(
                this.controller.getBlockPos().getX() + 0.5D,
                this.controller.getBlockPos().getY() + 0.5D,
                this.controller.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
