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

import com.mojang.serialization.Codec;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;

/**
 * The fixed slot geometry of a non-rocket workbench page.
 *
 * <p>Every position is transcribed from the matching legacy Galacticraft container (with legacy's
 * {@code change} offset folded in) and is relative to the top-left of the page background, so the
 * legacy GUI textures line up without further fudging. Rocket pages are not listed here: their slots
 * are generated from the recipe's body height by {@link dev.galacticraft.mod.recipe.RocketRecipe}.
 */
public enum WorkbenchLayout implements StringRepresentable {
    /** Legacy {@code ContainerBuggyBench}: a 3x4 plating body flanked by four wheels. */
    BUGGY("buggy", Constant.id("textures/gui/buggy_bench.png"), 0, 221, buggySlots(), addonSlots(12), new Position(142, 106), 138, 196, Translations.RocketWorkbench.PAGE_BUGGY),

    /** Legacy {@code ContainerSchematicCargoRocket}: an unmanned rocket with no cockpit. */
    CARGO_ROCKET("cargo_rocket", Constant.id("textures/gui/cargo_rocket_bench.png"), 0, 220, cargoRocketSlots(), addonSlots(12), new Position(142, 96), 138, 196, Translations.RocketWorkbench.PAGE_CARGO_ROCKET),

    /**
     * Legacy {@code ContainerSchematicAstroMiner}: three hull layers plus the mining lasers. Its
     * texture starts 26px down, and legacy carried its chests as recipe ingredients rather than as
     * an upgrade, so this page has no separate upgrade wells.
     */
    ASTRO_MINER("astro_miner", Constant.id("textures/gui/astro_miner_bench.png"), 26, 221, astroMinerSlots(), List.of(), new Position(142, 72), 114, 172, Translations.RocketWorkbench.PAGE_ASTRO_MINER);

    public static final Codec<WorkbenchLayout> CODEC = StringRepresentable.fromEnum(WorkbenchLayout::values);

    private final String name;
    private final ResourceLocation texture;
    private final int textureV;
    private final int imageHeight;
    private final List<Position> ingredientSlots;
    private final List<Position> chestSlots;
    private final Position resultSlot;
    private final int playerInventoryY;
    private final int hotbarY;
    private final String titleKey;

    WorkbenchLayout(String name, ResourceLocation texture, int textureV, int imageHeight, List<Position> ingredientSlots,
                    List<Position> chestSlots, Position resultSlot, int playerInventoryY, int hotbarY, String titleKey) {
        this.name = name;
        this.texture = texture;
        this.textureV = textureV;
        this.imageHeight = imageHeight;
        this.ingredientSlots = ingredientSlots;
        this.chestSlots = chestSlots;
        this.resultSlot = resultSlot;
        this.playerInventoryY = playerInventoryY;
        this.hotbarY = hotbarY;
        this.titleKey = titleKey;
    }

    /** Slot positions in recipe-ingredient order. */
    public List<Position> ingredientSlots() {
        return this.ingredientSlots;
    }

    /** Wells for the storage/explosive upgrade, which this port keeps out of the recipe itself. */
    public List<Position> chestSlots() {
        return this.chestSlots;
    }

    public Position resultSlot() {
        return this.resultSlot;
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    /** Row of the page's texture the background starts at. */
    public int textureV() {
        return this.textureV;
    }

    public int imageWidth() {
        return Constant.RocketWorkbench.PAGE_WIDTH;
    }

    public int imageHeight() {
        return this.imageHeight;
    }

    public int playerInventoryY() {
        return this.playerInventoryY;
    }

    public int hotbarY() {
        return this.hotbarY;
    }

    public Component title() {
        return Component.translatable(this.titleKey);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** 12 plating slots down three columns, then the four wheels, matching legacy's slot order. */
    private static List<Position> buggySlots() {
        List<Position> slots = new ArrayList<>(16);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 4; row++) {
                slots.add(new Position(39 + column * 18, 41 + row * 18));
            }
        }
        for (int column = 0; column < 2; column++) {
            for (int row = 0; row < 2; row++) {
                slots.add(new Position(21 + column * 72, 41 + row * 54));
            }
        }
        return List.copyOf(slots);
    }

    private static List<Position> cargoRocketSlots() {
        List<Position> slots = new ArrayList<>(13);
        slots.add(new Position(48, 18));
        slots.add(new Position(48, 36));
        for (int row = 0; row < 3; row++) {
            slots.add(new Position(39, 54 + row * 18));
        }
        for (int row = 0; row < 3; row++) {
            slots.add(new Position(57, 54 + row * 18));
        }
        slots.add(new Position(21, 90));
        slots.add(new Position(21, 108));
        slots.add(new Position(48, 108));
        slots.add(new Position(75, 90));
        slots.add(new Position(75, 108));
        return List.copyOf(slots);
    }

    private static List<Position> astroMinerSlots() {
        List<Position> slots = new ArrayList<>(14);
        for (int i = 0; i < 4; i++) {
            slots.add(new Position(27 + i * 18, 35));
        }
        for (int i = 0; i < 5; i++) {
            slots.add(new Position(16 + i * 18, 53));
        }
        for (int i = 0; i < 3; i++) {
            slots.add(new Position(44 + i * 18, 71));
        }
        for (int i = 0; i < 2; i++) {
            slots.add(new Position(8 + i * 18, 77));
        }
        return List.copyOf(slots);
    }

    /** Legacy drew the three addon wells in a row along the top right of the page. */
    private static List<Position> addonSlots(int y) {
        List<Position> slots = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            slots.add(new Position(93 + i * 26, y));
        }
        return List.copyOf(slots);
    }

    public record Position(int x, int y) {
        @Override
        public String toString() {
            return "(" + this.x + ", " + this.y + ")";
        }
    }
}
