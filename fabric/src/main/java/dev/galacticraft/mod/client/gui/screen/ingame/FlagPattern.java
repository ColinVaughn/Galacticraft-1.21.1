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

import java.util.Locale;

/**
 * Two-color flag layouts. For three colors, use a band layout and recolor a band with the fill tool.
 */
enum FlagPattern {
    SOLID {
        @Override
        boolean primaryAt(int x, int y) {
            return true;
        }
    },
    HORIZONTAL_HALVES {
        @Override
        boolean primaryAt(int x, int y) {
            return y < FlagCanvas.HEIGHT / 2;
        }
    },
    VERTICAL_HALVES {
        @Override
        boolean primaryAt(int x, int y) {
            return x < FlagCanvas.WIDTH / 2;
        }
    },
    /** Three bands of 11, 10 and 11 rows, the middle one primary. */
    HORIZONTAL_BANDS {
        @Override
        boolean primaryAt(int x, int y) {
            return y >= 11 && y < 21;
        }
    },
    /** Three bands of 16 columns, the middle one primary. */
    VERTICAL_BANDS {
        @Override
        boolean primaryAt(int x, int y) {
            return x >= 16 && x < 32;
        }
    },
    CENTERED_CROSS {
        @Override
        boolean primaryAt(int x, int y) {
            return (x >= 21 && x < 27) || (y >= 13 && y < 19);
        }
    },
    /** A cross whose upright stands toward the pole, as on the Scandinavian flags. */
    NORDIC_CROSS {
        @Override
        boolean primaryAt(int x, int y) {
            return (x >= 14 && x < 20) || (y >= 13 && y < 19);
        }
    },
    /** A diagonal cross from corner to corner. */
    SALTIRE {
        @Override
        boolean primaryAt(int x, int y) {
            double cx = x + 0.5, cy = y + 0.5;
            double length = Math.hypot(FlagCanvas.WIDTH, FlagCanvas.HEIGHT);
            double down = Math.abs(cy * FlagCanvas.WIDTH - cx * FlagCanvas.HEIGHT) / length;
            double up = Math.abs(cx * FlagCanvas.HEIGHT + cy * FlagCanvas.WIDTH - FlagCanvas.WIDTH * FlagCanvas.HEIGHT) / length;
            return Math.min(down, up) < 2.5;
        }
    },
    /** The upper quarter at the pole. */
    CANTON {
        @Override
        boolean primaryAt(int x, int y) {
            return x < FlagCanvas.WIDTH / 2 && y < FlagCanvas.HEIGHT / 2;
        }
    },
    /** Split corner to corner, the primary half at the top by the pole. */
    DIAGONAL {
        @Override
        boolean primaryAt(int x, int y) {
            return (x + 0.5) / FlagCanvas.WIDTH + (y + 0.5) / FlagCanvas.HEIGHT < 1.0;
        }
    },
    /** A triangle from the pole to the middle of the flag. */
    HOIST_TRIANGLE {
        @Override
        boolean primaryAt(int x, int y) {
            double half = FlagCanvas.HEIGHT / 2.0;
            return Math.abs(y + 0.5 - half) < half * (1.0 - (x + 0.5) / (FlagCanvas.WIDTH / 2.0));
        }
    },
    BORDER {
        @Override
        boolean primaryAt(int x, int y) {
            return x < 3 || x >= FlagCanvas.WIDTH - 3 || y < 3 || y >= FlagCanvas.HEIGHT - 3;
        }
    },
    /** A disc in the middle, three fifths of the flag high. */
    DISC {
        @Override
        boolean primaryAt(int x, int y) {
            double dx = x + 0.5 - FlagCanvas.WIDTH / 2.0, dy = y + 0.5 - FlagCanvas.HEIGHT / 2.0;
            double radius = FlagCanvas.HEIGHT * 0.3;
            return dx * dx + dy * dy < radius * radius;
        }
    };

    /** Returns whether this pixel uses the primary color. */
    abstract boolean primaryAt(int x, int y);

    String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
