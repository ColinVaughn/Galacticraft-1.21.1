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

package dev.galacticraft.mod.machine.workbench;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Everything the screen and menu need to lay a page out, independent of what it crafts.
 *
 * @param texture          the page background
 * @param textureV         row of {@code texture} the background starts at
 * @param height           height of the page panel; the width is always
 *                         {@link Constant.RocketWorkbench#PAGE_WIDTH}
 * @param chestSlots       wells for the storage/explosive upgrade, empty if the page has none
 * @param resultSlot       where the crafted item appears, or {@code null} if the page crafts nothing
 * @param playerInventoryY top row of the player's inventory
 * @param hotbarY          the player's hotbar row
 * @param title            the page heading
 * @param rocketPreview    whether to render the live rocket preview beside the slots
 * @param slotFrames       whether the screen draws slot frames itself; the legacy page textures
 *                         already have their wells painted in, this port's rocket art does not
 */
public record WorkbenchPageDisplay(
        ResourceLocation texture,
        int textureV,
        int height,
        List<WorkbenchLayout.Position> chestSlots,
        WorkbenchLayout.Position resultSlot,
        int playerInventoryY,
        int hotbarY,
        Component title,
        boolean rocketPreview,
        boolean slotFrames
) {
    /** The rocket pages keep this port's own workbench art, which legacy had no equivalent of. */
    public static WorkbenchPageDisplay rocket(Component title) {
        return new WorkbenchPageDisplay(
                Constant.RocketWorkbench.SCREEN_TEXTURE,
                0,
                Constant.RocketWorkbench.ROCKET_PAGE_HEIGHT,
                rocketChestSlots(),
                new WorkbenchLayout.Position(Constant.RocketWorkbench.OUTPUT_X, Constant.RocketWorkbench.OUTPUT_Y),
                Constant.RocketWorkbench.ROCKET_INVENTORY_Y,
                Constant.RocketWorkbench.ROCKET_HOTBAR_Y,
                title,
                true,
                true
        );
    }

    public static WorkbenchPageDisplay of(WorkbenchLayout layout) {
        return new WorkbenchPageDisplay(
                layout.texture(),
                layout.textureV(),
                layout.imageHeight(),
                layout.chestSlots(),
                layout.resultSlot(),
                layout.playerInventoryY(),
                layout.hotbarY(),
                layout.title(),
                false,
                false
        );
    }

    /** Legacy's unlock page: one schematic well, a button, and no result. */
    public static WorkbenchPageDisplay addSchematic() {
        return new WorkbenchPageDisplay(
                Constant.RocketWorkbench.ADD_SCHEMATIC_TEXTURE,
                0,
                Constant.RocketWorkbench.ADD_SCHEMATIC_HEIGHT,
                List.of(),
                null,
                Constant.RocketWorkbench.ADD_SCHEMATIC_INVENTORY_Y,
                Constant.RocketWorkbench.ADD_SCHEMATIC_HOTBAR_Y,
                Component.translatable(Translations.RocketWorkbench.PAGE_ADD_SCHEMATIC),
                false,
                false
        );
    }

    public int width() {
        return Constant.RocketWorkbench.PAGE_WIDTH;
    }

    private static List<WorkbenchLayout.Position> rocketChestSlots() {
        return List.of(
                new WorkbenchLayout.Position(Constant.RocketWorkbench.CHEST_X, Constant.RocketWorkbench.CHEST_Y),
                new WorkbenchLayout.Position(Constant.RocketWorkbench.CHEST_X + Constant.RocketWorkbench.CHEST_X_OFFSET, Constant.RocketWorkbench.CHEST_Y),
                new WorkbenchLayout.Position(Constant.RocketWorkbench.CHEST_X + 2 * Constant.RocketWorkbench.CHEST_X_OFFSET, Constant.RocketWorkbench.CHEST_Y)
        );
    }
}
