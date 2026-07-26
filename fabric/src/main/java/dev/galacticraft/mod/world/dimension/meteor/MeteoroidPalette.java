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

package dev.galacticraft.mod.world.dimension.meteor;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.environment.FallenMeteorBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps a meteoroid class and a voxel's material roll onto real block states — the flight body on
 * the way down, and the recoverable remains left in the crater.
 *
 * <p>Kept apart from {@link MeteoroidClass} and {@link MeteoroidShape} so those stay free of
 * Minecraft registries and can be unit tested without bootstrapping the game.
 */
public final class MeteoroidPalette {
    /** Rolls span {@code [0, 127]}; thresholds below are fractions of that range. */
    private static final int ROLL_RANGE = 128;

    private MeteoroidPalette() {
    }

    /**
     * The block state for one voxel of a meteoroid in flight. Mixtures follow the real classes:
     * chondrite is dark silicate rock, iron is nearly all metal, and a pallasite is metal shot
     * through with olivine.
     */
    public static BlockState bodyState(MeteoroidClass type, byte roll) {
        int value = roll & 0x7F;
        return switch (type) {
            case STONY -> value < percent(60)
                    ? GCBlocks.ASTEROID_ROCK.defaultBlockState()
                    : GCBlocks.ASTEROID_ROCK_2.defaultBlockState();
            case IRON -> value < percent(85)
                    ? GCBlocks.RAW_METEORIC_IRON_BLOCK.defaultBlockState()
                    : GCBlocks.ASTEROID_ROCK_2.defaultBlockState();
            case PALLASITE -> value < percent(65)
                    ? GCBlocks.RAW_METEORIC_IRON_BLOCK.defaultBlockState()
                    : GCBlocks.OLIVINE_BLOCK.defaultBlockState();
        };
    }

    /**
     * A block to leave in the crater floor. Every class can drop a still-glowing fallen meteor —
     * the classic prize — with the rest of the deposit reflecting what the body was made of.
     */
    public static BlockState depositState(MeteoroidClass type, RandomSource random) {
        int roll = random.nextInt(100);
        return switch (type) {
            case STONY -> roll < 35
                    ? hotFallenMeteor()
                    : (roll < 75 ? GCBlocks.ASTEROID_ROCK.defaultBlockState() : GCBlocks.ASTEROID_ROCK_2.defaultBlockState());
            case IRON -> roll < 45
                    ? hotFallenMeteor()
                    : GCBlocks.RAW_METEORIC_IRON_BLOCK.defaultBlockState();
            case PALLASITE -> roll < 30
                    ? hotFallenMeteor()
                    : (roll < 70 ? GCBlocks.RAW_METEORIC_IRON_BLOCK.defaultBlockState() : GCBlocks.OLIVINE_BLOCK.defaultBlockState());
        };
    }

    /** A fallen meteor at full heat, so a fresh crater is dangerous to walk into. */
    public static BlockState hotFallenMeteor() {
        return GCBlocks.FALLEN_METEOR.defaultBlockState().setValue(FallenMeteorBlock.HEAT, 5);
    }

    private static int percent(int percent) {
        return percent * ROLL_RANGE / 100;
    }
}
