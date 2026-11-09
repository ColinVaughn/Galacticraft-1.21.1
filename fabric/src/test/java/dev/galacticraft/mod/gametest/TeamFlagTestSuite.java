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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.decoration.FlagBlockEntity;
import dev.galacticraft.mod.content.item.FlagItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.spacerace.SpaceRaceTeams;
import dev.galacticraft.mod.spacerace.TeamFlagImage;
import dev.galacticraft.mod.spacerace.TeamFlagOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A team's flag, designed in the space race menu, has to reach the world: the server keeps each player's design,
 * a team flag crafted or planted becomes that player's, and it stays theirs when it is broken and moved.
 */
public final class TeamFlagTestSuite implements GalacticraftGameTest {
    /** Where a flag stands, on a stone floor, with its three blocks of pole above. */
    private static final BlockPos FLAG = new BlockPos(1, 1, 1);

    @GameTest(template = EMPTY_STRUCTURE)
    public void aDesignSentFromTheSpaceRaceMenuIsKeptForItsPlayer(GameTestHelper context) {
        ServerPlayer player = makeSurvivalServerPlayer(context);
        try {
            SpaceRaceTeams teams = SpaceRaceTeams.get(context.getLevel().getServer());
            byte[] design = design(0x40);
            teams.acceptName(player, "Red Rockets");
            if (!teams.acceptFlag(player, design)) {
                context.fail("the server refused a player's first flag design");
                return;
            }
            CompoundTag saved = teams.save(new CompoundTag(), context.getLevel().registryAccess());
            SpaceRaceTeams reloaded = SpaceRaceTeams.load(saved, context.getLevel().registryAccess());

            if (!Arrays.equals(design, teams.flag(player.getUUID()))) {
                context.fail("the server did not keep the design the player sent");
            } else if (!Arrays.equals(design, reloaded.flag(player.getUUID()))) {
                context.fail("the design was lost when the world was saved and loaded");
            } else if (!"Red Rockets".equals(reloaded.name(player.getUUID()))) {
                context.fail("the team name was lost when the world was saved and loaded");
            } else {
                context.succeed();
            }
        } finally {
            leave(context, player);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void craftingATeamFlagMakesItTheCraftersFlag(GameTestHelper context) {
        ServerPlayer player = makeSurvivalServerPlayer(context);
        try {
            SpaceRaceTeams.get(context.getLevel().getServer()).acceptName(player, "Blue Comets");
            Optional<RecipeHolder<?>> recipe = context.getLevel().getRecipeManager().byKey(Constant.id("team_flag"));
            if (recipe.isEmpty() || !(recipe.get().value() instanceof CraftingRecipe crafting)) {
                context.fail("there is no team flag crafting recipe");
                return;
            }
            // Three steel poles down the left, four canvas flying from the top two.
            List<ItemStack> grid = new ArrayList<>();
            for (int row = 0; row < 3; row++) {
                grid.add(new ItemStack(GCItems.STEEL_POLE));
                grid.add(row < 2 ? new ItemStack(GCItems.CANVAS) : ItemStack.EMPTY);
                grid.add(row < 2 ? new ItemStack(GCItems.CANVAS) : ItemStack.EMPTY);
            }
            CraftingInput input = CraftingInput.of(3, 3, grid);
            if (!crafting.matches(input, context.getLevel())) {
                context.fail("three steel poles and four canvas did not make a team flag");
                return;
            }
            ItemStack flag = crafting.assemble(input, context.getLevel().registryAccess());
            TeamFlagOwner before = flag.get(GCDataComponents.TEAM_FLAG);
            if (before == null || before.isClaimed()) {
                context.fail("the crafting result was not an unclaimed team flag: " + before);
                return;
            }
            flag.onCraftedBy(context.getLevel(), player, 1);
            TeamFlagOwner owner = flag.get(GCDataComponents.TEAM_FLAG);
            if (owner == null || !owner.id().equals(player.getUUID())) {
                context.fail("taking the crafted flag did not make it the crafter's: " + owner);
            } else if (!flag.getHoverName().getString().contains("Blue Comets")) {
                context.fail("the team flag is not named after its team: " + flag.getHoverName().getString());
            } else {
                context.succeed();
            }
        } finally {
            leave(context, player);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void plantingAnUnclaimedTeamFlagMakesItThePlantersFlag(GameTestHelper context) {
        ServerPlayer player = makeSurvivalServerPlayer(context);
        try {
            FlagBlockEntity flag = plant(context, player, FlagItem.teamFlag());
            TeamFlagOwner owner = flag == null ? null : flag.getTeam();
            if (flag == null) {
                context.fail("the team flag was not planted");
            } else if (owner == null || !owner.id().equals(player.getUUID())) {
                context.fail("planting an unclaimed team flag did not make it the planter's: " + owner);
            } else {
                ItemStack dropped = breakAndCollect(context);
                TeamFlagOwner kept = dropped.get(GCDataComponents.TEAM_FLAG);
                if (kept == null || !kept.id().equals(player.getUUID())) {
                    context.fail("breaking the flag dropped one that is no longer the team's: " + dropped + " " + kept);
                } else {
                    context.succeed();
                }
            }
        } finally {
            leave(context, player);
        }
    }

    /** A flag already belongs to a team, so planting someone else's flag leaves it theirs, like any other flag. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void plantingAnotherTeamsFlagLeavesItTheirs(GameTestHelper context) {
        ServerPlayer player = makeSurvivalServerPlayer(context);
        try {
            ItemStack theirs = FlagItem.teamFlag();
            TeamFlagOwner otherTeam = new TeamFlagOwner(java.util.UUID.randomUUID(), "Green Giants");
            theirs.set(GCDataComponents.TEAM_FLAG, otherTeam);
            FlagBlockEntity flag = plant(context, player, theirs);
            if (flag == null) {
                context.fail("the team flag was not planted");
            } else if (!otherTeam.equals(flag.getTeam())) {
                context.fail("planting another team's flag changed whose it is: " + flag.getTeam());
            } else {
                context.succeed();
            }
        } finally {
            leave(context, player);
        }
    }

    /** A design with a recognisable fill, so a mixed-up copy would not match. */
    private static byte[] design(int seed) {
        byte[] design = new byte[TeamFlagImage.BYTES];
        for (int i = 0; i < design.length; i++) design[i] = (byte) (seed + i * 31);
        return design;
    }

    private static FlagBlockEntity plant(GameTestHelper context, ServerPlayer player, ItemStack stack) {
        context.setBlock(FLAG.below(), Blocks.STONE);
        BlockPos floor = context.absolutePos(FLAG.below());
        // Clear of the pole, so the player's own body does not block the planting.
        BlockPos aside = context.absolutePos(FLAG.east(3));
        player.moveTo(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false);
        stack.useOn(new UseOnContext(context.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        return context.getLevel().getBlockEntity(context.absolutePos(FLAG)) instanceof FlagBlockEntity flag ? flag : null;
    }

    private static ItemStack breakAndCollect(GameTestHelper context) {
        BlockPos pos = context.absolutePos(FLAG);
        context.getLevel().destroyBlock(pos, true);
        for (ItemEntity item : context.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
            if (item.getItem().getItem() instanceof FlagItem) {
                ItemStack stack = item.getItem().copy();
                item.discard();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void leave(GameTestHelper context, ServerPlayer player) {
        context.getLevel().getServer().getPlayerList().remove(player);
    }
}
