/*
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

package dev.galacticraft.mod.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Jupiter's east-west cloud bands: {@code +1} at the centre of a stormy belt (z = 0 modulo {@code period}) and
 * {@code -1} at the centre of a pale zone, in phase with the sky haze, wind and storm activity. {@code warp} bends the
 * band edges by up to {@code warpBlocks} so they do not read as ruled lines.
 */
public record JupiterBands(DensityFunction warp, double period, double warpBlocks) implements DensityFunction.SimpleFunction {
    public static final MapCodec<JupiterBands> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("warp").forGetter(JupiterBands::warp),
            Codec.doubleRange(1.0, 1.0E6).fieldOf("period").forGetter(JupiterBands::period),
            Codec.doubleRange(0.0, 1.0E4).fieldOf("warp_blocks").forGetter(JupiterBands::warpBlocks)
    ).apply(instance, JupiterBands::new));
    public static final KeyDispatchDataCodec<JupiterBands> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public static void register() {
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.DENSITY_FUNCTION_TYPE,
                dev.galacticraft.mod.Constant.id("jupiter_bands"), DATA_CODEC);
    }

    /** The band value at a block z, without warp; matches the client's band haze. */
    public static double band(double z, double period) {
        return Math.cos(z * 2.0 * Math.PI / period);
    }

    @Override
    public double compute(FunctionContext context) {
        return band(context.blockZ() + this.warp.compute(context) * this.warpBlocks, this.period);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new JupiterBands(this.warp.mapAll(visitor), this.period, this.warpBlocks));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
