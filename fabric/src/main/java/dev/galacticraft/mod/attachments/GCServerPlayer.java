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

package dev.galacticraft.mod.attachments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.rocket.RocketData;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;

import java.util.List;
import java.util.Optional;

public class GCServerPlayer {
    public static final int RESERVED_RETURN_STACKS = 2;

    private RocketData rocketData;
    public NonNullList<ItemStack> stacks = NonNullList.withSize(RESERVED_RETURN_STACKS, ItemStack.EMPTY);
    public long fuel;
    private ItemStack launchpadStack = ItemStack.EMPTY;

    public static final Codec<GCServerPlayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RocketData.CODEC.optionalFieldOf("rocket_data").forGetter(o -> Optional.ofNullable(o.rocketData)),
            Codec.LONG.fieldOf("fuel").forGetter(GCServerPlayer::getFuel),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("rocket_stacks").forGetter(GCServerPlayer::getRocketStacks),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("launchpad_stack", ItemStack.EMPTY).forGetter(GCServerPlayer::getLaunchpadStack)
    ).apply(instance, (data, fuel, stacks, launchpad) -> new GCServerPlayer(data.orElse(null), fuel, stacks, launchpad)));

    public static GCServerPlayer get(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrCreate(GCAttachments.SERVER_PLAYER, () -> new GCServerPlayer(player));
    }


    public GCServerPlayer(ServerPlayer player) {
    }

    public GCServerPlayer(RocketData data, long fuel, List<ItemStack> stacks, ItemStack launchpadStack) {
        this.rocketData = data;
        this.fuel = fuel;
        this.stacks = NonNullList.of(ItemStack.EMPTY, stacks.toArray(ItemStack[]::new));
        this.launchpadStack = launchpadStack.copy();
        this.ensureReturnStackSlots();
    }

    public RocketData getRocketData() {
        return rocketData;
    }

    public void setRocketData(RocketData rocketData) {
        this.rocketData = rocketData;
    }

    public NonNullList<ItemStack> getRocketStacks() {
        return this.stacks;
    }

    public void setRocketStacks(NonNullList<ItemStack> rocketStacks) {
        this.stacks = rocketStacks;
        this.ensureReturnStackSlots();
    }

    public long getFuel() {
        return fuel;
    }

    public void setFuel(long fuel) {
        this.fuel = fuel;
    }

    public void setRocketItem(ItemStack rocketItem) {
        this.ensureReturnStackSlots();
        getRocketStacks().set(getRocketStacks().size() - 1, rocketItem.copy());
    }

    public ItemStack getLaunchpadStack() {
        return this.launchpadStack;
    }

    public void setLaunchpadStack(ItemStack launchpad) {
        this.launchpadStack = launchpad == null ? ItemStack.EMPTY : launchpad.copy();
    }

    /**
     * Writes the vehicle items into the two slots reserved at the end of the transferred cargo.
     * The launch pad is kept separately until this point because the rocket replaces the cargo list
     * when it reaches space.
     */
    public void finishReturnInventory(ItemStack rocketItem) {
        this.ensureReturnStackSlots();
        getRocketStacks().set(getRocketStacks().size() - 2, this.launchpadStack.copy());
        this.setRocketItem(rocketItem);
        this.launchpadStack = ItemStack.EMPTY;
    }

    private void ensureReturnStackSlots() {
        if (this.stacks.size() >= RESERVED_RETURN_STACKS) {
            return;
        }

        NonNullList<ItemStack> expanded = NonNullList.withSize(RESERVED_RETURN_STACKS, ItemStack.EMPTY);
        for (int i = 0; i < this.stacks.size(); i++) {
            expanded.set(i, this.stacks.get(i));
        }
        this.stacks = expanded;
    }
}
