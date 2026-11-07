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

package dev.galacticraft.mod.content.block.jupiter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.content.block.jupiter.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A Jupiter MachineLib machine. The whole machine is drawn by its animated Blockbench model
 * ({@code JupiterMachineRenderer}) instead of MachineLib's cube, so the idle clip plays while it runs.
 */
public class JupiterMachineBlock extends MachineBlock {
    private static final MapCodec<JupiterMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(block -> block.kind), propertiesCodec()).apply(instance, (kind, properties) -> new JupiterMachineBlock(properties, kind)));
    private final Kind kind;

    public JupiterMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable MachineBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return this.kind.factory.apply(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.has(DataComponents.CUSTOM_NAME) && level.getBlockEntity(pos) instanceof SurveyBeaconBlockEntity beacon) {
            beacon.setBeaconName(stack.getHoverName().getString());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            if (this.kind == Kind.SURVEY_BEACON) dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.get(server).remove(pos);
            if (this.kind == Kind.PRESSURE_STABILIZER) dev.galacticraft.mod.world.dimension.jupiter.PressureHabitats.remove(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public enum Kind implements StringRepresentable {
        ATMOSPHERIC_COLLECTOR(AtmosphericCollectorBlockEntity::new),
        CONDENSATE_SEPARATOR(CondensateSeparatorBlockEntity::new),
        VEHICLE_DOCK(VehicleDockBlockEntity::new),
        SURVEY_BEACON(SurveyBeaconBlockEntity::new),
        STORM_CAPACITOR(StormCapacitorBlockEntity::new),
        LIGHTNING_GROUNDER(LightningGrounderBlockEntity::new),
        PRESSURE_STABILIZER(PressureStabilizerBlockEntity::new);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final BiFunction<BlockPos, BlockState, MachineBlockEntity> factory;

        Kind(BiFunction<BlockPos, BlockState, MachineBlockEntity> factory) {
            this.factory = factory;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
