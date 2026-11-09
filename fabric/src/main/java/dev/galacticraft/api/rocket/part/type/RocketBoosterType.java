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

package dev.galacticraft.api.rocket.part.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.galacticraft.api.rocket.part.RocketBooster;
import dev.galacticraft.api.rocket.part.config.RocketBoosterConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Optional thrust added to the rocket engine during powered ascent. Adds acceleration and caps
 * climb velocity; GC4 fuel mode also adds the booster's configured burn rate.
 */
public non-sealed abstract class RocketBoosterType<C extends RocketBoosterConfig> implements RocketPartType<C> {
    private final @NotNull MapCodec<RocketBooster<C, RocketBoosterType<C>>> codec;

    protected RocketBoosterType(@NotNull Codec<C> configCodec) {
        this.codec = configCodec.fieldOf("config").xmap(this::configure, RocketBooster::config);
    }

    public @NotNull RocketBooster<C, RocketBoosterType<C>> configure(@NotNull C config) {
        return RocketBooster.create(config, this);
    }

    @Override
    public @NotNull MapCodec<? extends RocketBooster<C, ? extends RocketBoosterType<C>>> codec() {
        return this.codec;
    }

    /**
     * Returns the fastest the rocket may climb while this booster is fitted, in blocks/tick.
     *
     * @return the maximum climb velocity in blocks/tick.
     */
    @Contract(pure = true)
    public abstract double getMaximumVelocity(@NotNull C config);

    /**
     * Returns the extra acceleration this booster adds at full thrust, in blocks/tick^2.
     *
     * @return the added acceleration in blocks/tick^2.
     */
    @Contract(pure = true)
    public abstract double getAccelerationPerTick(@NotNull C config);

    /**
     * Returns the fuel this booster burns per tick of powered flight, on top of the engine's.
     *
     * @return the fuel burned per tick. One bucket is 81,000 units.
     */
    @Contract(pure = true)
    public abstract long getFuelUsagePerTick(@NotNull C config);
}
