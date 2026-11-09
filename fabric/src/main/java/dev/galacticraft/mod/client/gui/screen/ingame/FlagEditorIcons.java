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

package dev.galacticraft.mod.client.gui.screen.ingame;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Flag editor icons as 12 x 12 character maps. Dots are transparent; other characters use {@link #color}.
 */
final class FlagEditorIcons {
    static final int SIZE = 12;

    static final String[] PENCIL = {
            "........ooo.",
            ".......oppo.",
            "......olplo.",
            ".....oylloo.",
            "....oyyYo...",
            "...oyyYo....",
            "..oyyYo.....",
            ".otyYo......",
            ".ottoo......",
            "oooo........",
            "oo..........",
            "............",
    };
    static final String[] LINE = {
            "..........oo",
            ".........owo",
            "........owo.",
            ".......owo..",
            "......owo...",
            ".....owo....",
            "....owo.....",
            "...owo......",
            "..owo.......",
            ".owo........",
            "owo.........",
            "oo..........",
    };
    static final String[] RECTANGLE = {
            "............",
            "oooooooooooo",
            "owwwwwwwwwwo",
            "owoooooooowo",
            "owo......owo",
            "owo......owo",
            "owo......owo",
            "owo......owo",
            "owoooooooowo",
            "owwwwwwwwwwo",
            "oooooooooooo",
            "............",
    };
    static final String[] ELLIPSE = {
            "....oooo....",
            "..oowwwwoo..",
            ".owwooooowo.",
            ".owo....owo.",
            "owo......owo",
            "owo......owo",
            "owo......owo",
            "owo......owo",
            ".owo....owo.",
            ".owwooooowo.",
            "..oowwwwoo..",
            "....oooo....",
    };
    static final String[] FILL = {
            "....oooo....",
            "...o....o...",
            "..o......o..",
            ".oooooooooo.",
            ".obbbbbbbbo.",
            ".olbllllblo.",
            "..olbllllo..",
            "..ollllllo..",
            "...ollllo...",
            "...oooooo...",
            "............",
            "............",
    };
    static final String[] PICKER = {
            ".........oo.",
            "........orro",
            ".......orrro",
            "......oorroo",
            ".....owwoo..",
            "....owwwo...",
            "...owwwo....",
            "..owbwo.....",
            ".owbbo......",
            ".obbo.......",
            "o.oo........",
            "............",
    };
    static final String[] SIZE_1 = {
            "............",
            "............",
            "............",
            "............",
            "....oooo....",
            "....owwo....",
            "....owwo....",
            "....oooo....",
            "............",
            "............",
            "............",
            "............",
    };
    static final String[] SIZE_2 = {
            "............",
            "............",
            "............",
            "...oooooo...",
            "...owwwwo...",
            "...owwwwo...",
            "...owwwwo...",
            "...owwwwo...",
            "...oooooo...",
            "............",
            "............",
            "............",
    };
    static final String[] SIZE_3 = {
            "............",
            "............",
            "..oooooooo..",
            "..owwwwwwo..",
            "..owwwwwwo..",
            "..owwwwwwo..",
            "..owwwwwwo..",
            "..owwwwwwo..",
            "..owwwwwwo..",
            "..oooooooo..",
            "............",
            "............",
    };
    static final String[] FILLED = {
            "............",
            ".oooooooooo.",
            ".owwwwwwwwo.",
            ".owbbbbbbwo.",
            ".owbbbbbbwo.",
            ".owbbbbbbwo.",
            ".owbbbbbbwo.",
            ".owbbbbbbwo.",
            ".owbbbbbbwo.",
            ".owwwwwwwwo.",
            ".oooooooooo.",
            "............",
    };
    static final String[] MIRROR_X = {
            ".....ww.....",
            "............",
            ".....ww.....",
            "..oo....oo..",
            ".owo.ww.owo.",
            "owwo....owwo",
            "owwo.ww.owwo",
            ".owo....owo.",
            "..oo.ww.oo..",
            "............",
            ".....ww.....",
            "............",
    };
    static final String[] MIRROR_Y = {
            ".....oo.....",
            "....owwo....",
            "...owwwwo...",
            "...oooooo...",
            "............",
            "w.w.w.w.w.w.",
            "w.w.w.w.w.w.",
            "............",
            "...oooooo...",
            "...owwwwo...",
            "....owwo....",
            ".....oo.....",
    };
    static final String[] GRID = {
            "............",
            ".wwwwwwwwww.",
            ".w..w..w..w.",
            ".w..w..w..w.",
            ".wwwwwwwwww.",
            ".w..w..w..w.",
            ".w..w..w..w.",
            ".wwwwwwwwww.",
            ".w..w..w..w.",
            ".w..w..w..w.",
            ".wwwwwwwwww.",
            "............",
    };
    static final String[] SWAP = {
            "............",
            "...o........",
            "..ow........",
            ".owwwwwwwwo.",
            "..ow......o.",
            "...o......o.",
            ".o......o...",
            ".o......wo..",
            ".owwwwwwwwo.",
            "........wo..",
            "........o...",
            "............",
    };

    static final String[] ERASER = {
            "............",
            "......oooo..",
            ".....oolloo.",
            "....oollllo.",
            "...oolllllo.",
            "..oollllloo.",
            ".ooppllloo..",
            ".opppploo...",
            ".oppppoo....",
            ".ooppoo.....",
            "..oooo......",
            "............",
    };
    static final String[] LAYER_ADD = {
            "............",
            "....oooo....",
            "....owwo....",
            "....owwo....",
            ".oooowwoooo.",
            ".owwwwwwwwo.",
            ".owwwwwwwwo.",
            ".oooowwoooo.",
            "....owwo....",
            "....owwo....",
            "....oooo....",
            "............",
    };
    static final String[] LAYER_DUPLICATE = {
            "oooooooo....",
            "ollllllo....",
            "ollllllo....",
            "ollllllo....",
            "ollllllooooo",
            "ollllwwwwwwo",
            "ollllwwwwwwo",
            "ooooowwwwwwo",
            "....owwwwwwo",
            "....owwwwwwo",
            "....owwwwwwo",
            "....oooooooo",
    };
    static final String[] LAYER_DELETE = {
            "...oooooo...",
            "oooommmmoooo",
            "ollllllllllo",
            "ollllllllllo",
            "oooooooooooo",
            ".ollllllllo.",
            ".ollmllmllo.",
            ".ollmllmllo.",
            ".ollmllmllo.",
            ".ollmllmllo.",
            ".ollllllllo.",
            ".oooooooooo.",
    };
    static final String[] LAYER_UP = {
            "....oooo....",
            "...oowwoo...",
            "..oowwwwoo..",
            ".oowwwwwwoo.",
            "oowwwwwwwwoo",
            "owwwwwwwwwwo",
            "ooooowwooooo",
            "....owwo....",
            "....owwo....",
            "....owwo....",
            "....owwo....",
            "....oooo....",
    };
    static final String[] LAYER_DOWN = {
            "....oooo....",
            "....owwo....",
            "....owwo....",
            "....owwo....",
            "....owwo....",
            "ooooowwooooo",
            "owwwwwwwwwwo",
            "oowwwwwwwwoo",
            ".oowwwwwwoo.",
            "..oowwwwoo..",
            "...oowwoo...",
            "....oooo....",
    };
    static final String[] LAYER_MERGE = {
            "....oooo....",
            "....owwo....",
            "....owwo....",
            ".oooowwoooo.",
            ".owwwwwwwwo.",
            ".oowwwwwwoo.",
            "..oowwwwoo..",
            "...oowwoo...",
            "oooooooooooo",
            "obbbbbbbbbbo",
            "obbbbbbbbbbo",
            "oooooooooooo",
    };
    static final String[] EYE = {
            "............",
            "............",
            "..oooooooo..",
            "ooowwwwwwooo",
            "owwwwbbwwwwo",
            "wwwwboobwwww",
            "wwwwboobwwww",
            "owwwwbbwwwwo",
            "ooowwwwwwooo",
            "..oooooooo..",
            "............",
            "............",
    };
    static final String[] EYE_CLOSED = {
            "............",
            "............",
            "............",
            "............",
            "............",
            ".m........m.",
            "..mmmmmmmm..",
            "...m..m.m...",
            "..mm....mm..",
            "............",
            "............",
            "............",
    };

    private FlagEditorIcons() {
    }

    /** Draws an icon with its top left corner here, one screen pixel to an icon pixel. */
    static void draw(GuiGraphics graphics, String[] icon, int x, int y) {
        for (int row = 0; row < SIZE; row++) {
            String line = icon[row];
            int column = 0;
            while (column < SIZE) {
                char key = line.charAt(column);
                int end = column + 1;
                while (end < SIZE && line.charAt(end) == key) end++;
                if (key != '.') graphics.fill(x + column, y + row, x + end, y + row + 1, color(key));
                column = end;
            }
        }
    }

    private static int color(char key) {
        return switch (key) {
            case 'o' -> 0xFF262626;
            case 'w' -> 0xFFF4F4F4;
            case 'l' -> 0xFFC6C6C6;
            case 'm' -> 0xFF8B8B8B;
            case 'y' -> 0xFFF5C542;
            case 'Y' -> 0xFFC9952B;
            case 'p' -> 0xFFEE8FA6;
            case 't' -> 0xFFE9C79B;
            case 'b' -> 0xFF3D8BE0;
            case 'B' -> 0xFF2A63A8;
            case 'r' -> 0xFFD94B3F;
            case 'g' -> 0xFF9AA0A6;
            default -> throw new IllegalArgumentException("No icon colour " + key);
        };
    }
}
