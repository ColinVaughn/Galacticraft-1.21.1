package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.boss.SpiderEggEntity;
import dev.galacticraft.mod.content.entity.boss.SpiderBoss;
import dev.galacticraft.mod.content.entity.boss.SpiderVenomEntity;
import dev.galacticraft.mod.content.entity.boss.SpiderlingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Damaging an egg prevents hatching; an untouched egg creates two attackers. */
public final class SpiderBroodTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 130)
    public void woundedQueenBuildsNestAndHealsWhileUnderAttack(GameTestHelper context) {
        var level = context.getLevel();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                level.setBlock(context.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        BlockPos center = context.absolutePos(new BlockPos(3, 1, 3));
        SpiderBoss boss = new SpiderBoss(GCEntityTypes.SPIDER_BOSS, level);
        boss.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        boss.setHealth(90.0F);
        level.addFreshEntity(boss);

        runAt(context, 5, () -> context.assertTrue(boss.getSpecialAttack() == SpiderBoss.NEST_BUILD,
                "A wounded queen without a target did not begin nesting"));
        runAt(context, 20, () -> {
            context.assertTrue(boss.hurt(level.damageSources().magic(), 4.0F), "Nest test damage did not register");
            context.assertTrue(boss.getSpecialAttack() == SpiderBoss.NEST_BUILD,
                    "Taking damage cancelled the nest before it could heal");
        });
        runFinalTaskAt(context, 115, () -> {
            context.assertTrue(boss.getSpecialAttack() == 0, "The queen did not finish its nest sequence");
            context.assertTrue(boss.getHealth() > 90.0F,
                    "The nest did not restore health after the queen was attacked");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void venomSplatSettlesOnFloorAfterHittingRaisedWall(GameTestHelper context) {
        var level = context.getLevel();
        for (int x = 2; x <= 6; x++) {
            level.setBlock(context.absolutePos(new BlockPos(x, 0, 3)), Blocks.STONE.defaultBlockState(), 2);
        }
        for (int y = 1; y <= 3; y++) {
            level.setBlock(context.absolutePos(new BlockPos(4, y, 3)), Blocks.STONE.defaultBlockState(), 2);
        }

        BlockPos start = context.absolutePos(new BlockPos(2, 1, 3));
        SpiderBoss boss = new SpiderBoss(GCEntityTypes.SPIDER_BOSS, level);
        boss.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        SpiderVenomEntity venom = new SpiderVenomEntity(boss,
                new Vec3(start.getX() + 4.5D, start.getY() + 1.0D, start.getZ() + 0.5D));
        level.addFreshEntity(venom);

        runFinalTaskAt(context, 25, () -> {
            context.assertTrue(venom.isPooled(), "Venom did not become a pool after hitting the wall");
            context.assertTrue(Math.abs(venom.getY() - start.getY()) < 0.05D,
                    "Venom pool floated above the floor at y=" + venom.getY());
            context.assertTrue(venom.getX() < context.absolutePos(new BlockPos(4, 1, 3)).getX(),
                    "Venom pool passed through the wall");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
    public void onlyUntouchedEggsHatch(GameTestHelper context) {
        var level = context.getLevel();
        BlockPos first = context.absolutePos(new BlockPos(3, 1, 3));
        BlockPos second = context.absolutePos(new BlockPos(5, 1, 3));
        level.setBlock(first.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(second.below(), Blocks.STONE.defaultBlockState(), 2);

        SpiderEggEntity broken = new SpiderEggEntity(GCEntityTypes.SPIDER_EGG, level);
        broken.setPos(first.getX() + 0.5D, first.getY(), first.getZ() + 0.5D);
        level.addFreshEntity(broken);
        SpiderEggEntity intact = new SpiderEggEntity(GCEntityTypes.SPIDER_EGG, level);
        intact.setPos(second.getX() + 0.5D, second.getY(), second.getZ() + 0.5D);
        level.addFreshEntity(intact);

        context.assertTrue(broken.hurt(level.damageSources().magic(), 1.0F) && broken.isRemoved(),
                "A damaged brood egg must break before hatching");
        runAt(context, 50, () -> context.assertTrue(!intact.isRemoved() && intact.getHatchAge() >= 40,
                "An untouched egg disappeared before its hatch timer finished"));
        runFinalTaskAt(context, 110, () -> {
            context.assertTrue(intact.isRemoved(), "An untouched brood egg did not hatch");
            int babies = level.getEntitiesOfClass(SpiderlingEntity.class,
                    intact.getBoundingBox().inflate(3.0D)).size();
            context.assertTrue(babies == 2, "Expected exactly two spiderlings, got " + babies);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void spiderlingsCrossIntactWallsToReachHiddenPlayers(GameTestHelper context) {
        var level = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(2, 1, 3));
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                level.setBlock(context.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        for (int x = 4; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x != 5 || z != 3 || y == 3) {
                        level.setBlock(context.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
        }

        var player = makeSurvivalServerPlayer(context);
        BlockPos hidingSpot = context.absolutePos(new BlockPos(5, 1, 3));
        player.setPos(hidingSpot.getX() + 0.5D, hidingSpot.getY(), hidingSpot.getZ() + 0.5D);
        SpiderlingEntity baby = new SpiderlingEntity(GCEntityTypes.SPIDERLING, level);
        baby.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        baby.setTarget(player);
        level.addFreshEntity(baby);

        runFinalTaskAt(context, 75, () -> {
            context.assertTrue(!baby.isRemoved() && baby.getX() > context.absolutePos(new BlockPos(4, 1, 3)).getX() + 0.5D,
                    "Spiderling did not burrow through the sealed wall");
            context.assertTrue(level.getBlockState(context.absolutePos(new BlockPos(4, 1, 3))).is(Blocks.STONE),
                    "Burrowing destroyed the wall");
        });
    }
}
