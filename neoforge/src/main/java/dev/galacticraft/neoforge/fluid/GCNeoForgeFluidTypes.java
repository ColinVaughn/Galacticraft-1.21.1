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

package dev.galacticraft.neoforge.fluid;

import dev.galacticraft.api.gas.Gas;
import dev.galacticraft.api.gas.GasFluid;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCFluids;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

public final class GCNeoForgeFluidTypes {
    private static final FluidType CRUDE_OIL = new FluidType(FluidType.Properties.create()
            .descriptionId("block.galacticraft.crude_oil")
            .density(900)
            .viscosity(6000));
    private static final FluidType FUEL = new FluidType(FluidType.Properties.create()
            .descriptionId("block.galacticraft.fuel")
            .density(800)
            .viscosity(2000));
    private static final FluidType SULFURIC_ACID = new FluidType(FluidType.Properties.create()
            .descriptionId("block.galacticraft.sulfuric_acid")
            .density(1800)
            .viscosity(1000));
    private static final FluidType LIQUID_OXYGEN = new FluidType(FluidType.Properties.create()
            .descriptionId("block.galacticraft.oxygen")
            .density(1141)
            .temperature(90)
            .viscosity(500));
    private static final FluidType GAS = new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.galacticraft.gas")
            .density(-100)
            .viscosity(50)
            .canPushEntity(false)
            .canSwim(false)
            .canDrown(false)
            .fallDistanceModifier(1.0F)
            .pathType(null)
            .adjacentPathType(null)) {
        @Override
        public Component getDescription(FluidStack stack) {
            Gas gas = stack.getFluid() instanceof Gas value ? value : null;
            return gasDescription(gas, super.getDescription(stack));
        }
    };

    private GCNeoForgeFluidTypes() {
    }

    static Component gasDescription(@Nullable Gas gas, Component fallback) {
        return gas == null ? fallback : gas.getName();
    }

    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> {
            helper.register(Constant.id("crude_oil"), CRUDE_OIL);
            helper.register(Constant.id("fuel"), FUEL);
            helper.register(Constant.id("sulfuric_acid"), SULFURIC_ACID);
            helper.register(Constant.id("liquid_oxygen"), LIQUID_OXYGEN);
            helper.register(Constant.id("gas"), GAS);
        });
    }

    public static @Nullable FluidType get(Fluid fluid) {
        if (fluid == GCFluids.CRUDE_OIL || fluid == GCFluids.FLOWING_CRUDE_OIL) {
            return CRUDE_OIL;
        }
        if (fluid == GCFluids.FUEL || fluid == GCFluids.FLOWING_FUEL) {
            return FUEL;
        }
        if (fluid == GCFluids.SULFURIC_ACID || fluid == GCFluids.FLOWING_SULFURIC_ACID) {
            return SULFURIC_ACID;
        }
        if (fluid == GCFluids.LIQUID_OXYGEN) {
            return LIQUID_OXYGEN;
        }
        if (fluid instanceof GasFluid) {
            return GAS;
        }
        return null;
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(textures(Constant.Fluid.fluidId(Constant.Fluid.CRUDE_OIL_STILL),
                Constant.Fluid.fluidId(Constant.Fluid.CRUDE_OIL_FLOWING)), CRUDE_OIL);
        event.registerFluidType(textures(Constant.Fluid.fluidId(Constant.Fluid.FUEL_STILL),
                Constant.Fluid.fluidId(Constant.Fluid.FUEL_FLOWING)), FUEL);
        event.registerFluidType(textures(Constant.Fluid.fluidId(Constant.Fluid.SULFURIC_ACID_STILL),
                Constant.Fluid.fluidId(Constant.Fluid.SULFURIC_ACID_FLOWING)), SULFURIC_ACID);
        ResourceLocation oxygen = Constant.Fluid.fluidId(Constant.Fluid.LIQUID_OXYGEN);
        event.registerFluidType(textures(oxygen, oxygen), LIQUID_OXYGEN);
    }

    private static IClientFluidTypeExtensions textures(ResourceLocation still, ResourceLocation flowing) {
        return new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return still; }
            @Override public ResourceLocation getFlowingTexture() { return flowing; }
        };
    }
}
