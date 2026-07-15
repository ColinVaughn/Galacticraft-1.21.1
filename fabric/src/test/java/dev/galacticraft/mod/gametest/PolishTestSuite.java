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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.entity.ThrowableMeteorChunkEntity;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.carver.CraterCarver;
import dev.galacticraft.mod.world.gen.carver.config.CraterCarverConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.CarverDebugSettings;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;

public final class PolishTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void meteorCreditsItsThrower(GameTestHelper context) {
        Player thrower = context.makeMockPlayer(GameType.SURVIVAL);
        TestMeteor meteor = new TestMeteor(thrower, context.getLevel());
        DamageSource source = meteor.damageSource();
        Zombie target = context.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        target.setHealth(1.0F);
        target.hurt(source, 2.0F);

        if (source.getDirectEntity() != meteor) {
            context.fail("Meteor was not retained as the direct damage entity");
        } else if (source.getEntity() != thrower) {
            context.fail("Meteor thrower was not retained as the causing entity");
        } else if (target.getKillCredit() != thrower) {
            context.fail("Meteor kill was not credited to its thrower");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void craterUsesPlanetSpecificReplaceables(GameTestHelper context) {
        CraterCarverConfig marsConfig = new CraterCarverConfig(
                1.0F,
                ConstantHeight.of(VerticalAnchor.absolute(128)),
                UniformFloat.of(0.4F, 0.6F),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MARS_CRATER_CARVER_REPLACEABLES),
                24,
                7,
                7,
                1.0F);
        TestCraterCarver carver = new TestCraterCarver();

        if (!carver.canReplace(marsConfig, GCBlocks.MARS_SURFACE_ROCK.defaultBlockState())) {
            context.fail("Mars crater tag does not include Martian surface rock");
        } else if (carver.canReplace(marsConfig, GCBlocks.MOON_SURFACE_ROCK.defaultBlockState())) {
            context.fail("Mars crater configuration incorrectly accepts Moon surface rock");
        } else {
            context.succeed();
        }
    }

    private static final class TestMeteor extends ThrowableMeteorChunkEntity {
        private TestMeteor(LivingEntity shooter, Level level) {
            super(shooter, level, false);
        }

        private DamageSource damageSource() {
            return super.createMeteorDamageSource();
        }
    }

    private static final class TestCraterCarver extends CraterCarver {
        private TestCraterCarver() {
            super(CraterCarverConfig.CRATER_CODEC);
        }

        private boolean canReplace(CraterCarverConfig config, BlockState state) {
            return super.canReplaceBlock(config, state);
        }
    }
}
