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

package dev.galacticraft.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.world.dimension.meteor.AtmosphereProfile;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerManager;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerState;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerTuning;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Op-only debug command for driving meteor activity in-game:
 * {@code /meteorshower <start|stop|forecast|status|intensity <0..1>|drop <class> <size>|atmosphere>}.
 *
 * <p>{@code drop} puts a single body of a chosen class and size directly overhead, which is the
 * quickest way to watch one class burn where another survives. {@code atmosphere} prints the
 * derived physics for the current dimension, so a wrong-looking outcome can be traced straight
 * back to the celestial body data behind it.
 */
public final class MeteorShowerCommand {
    private MeteorShowerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var drop = Commands.literal("drop");
        for (MeteoroidClass type : MeteoroidClass.values()) {
            drop.then(Commands.literal(type.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(ctx -> drop(ctx, type, 5))
                    .then(Commands.argument("size", IntegerArgumentType.integer(MeteoroidShape.MIN_SIZE, MeteoroidShape.MAX_SIZE))
                            .executes(ctx -> drop(ctx, type, IntegerArgumentType.getInteger(ctx, "size")))));
        }

        dispatcher.register(Commands.literal("meteorshower")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(ctx -> start(ctx, 1.0f)))
                .then(Commands.literal("stop").executes(MeteorShowerCommand::stop))
                .then(Commands.literal("forecast").executes(MeteorShowerCommand::forecast))
                .then(Commands.literal("status").executes(MeteorShowerCommand::status))
                .then(Commands.literal("atmosphere").executes(MeteorShowerCommand::atmosphere))
                .then(drop)
                .then(Commands.literal("intensity")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                .executes(ctx -> start(ctx, FloatArgumentType.getFloat(ctx, "value"))))));
    }

    private static MeteorShowerTuning tuning() {
        var config = Galacticraft.CONFIG;
        return new MeteorShowerTuning(true, config.meteorShowerMeanInterval(), config.meteorShowerMinDuration(),
                config.meteorShowerMaxDuration(), config.meteorShowerIntensity());
    }

    private static ServerLevel requireCelestialBody(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (level.galacticraft$getCelestialBody() == null) {
            ctx.getSource().sendFailure(Component.literal("This dimension is not a registered celestial body."));
            return null;
        }
        return level;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, float intensity) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;
        MeteorShowerState state = MeteorShowerState.get(level);
        state.debugStart(level.random, tuning(), intensity);
        MeteorShowerManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Started a meteor shower at intensity %.2f, radiant %.0f deg / %.0f deg elevation.",
                intensity, state.radiantYaw(), state.radiantPitch())), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;
        MeteorShowerState state = MeteorShowerState.get(level);
        state.debugStop(level.random, tuning());
        MeteorShowerManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal("Meteor shower ended."), true);
        return 1;
    }

    private static int forecast(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;
        MeteorShowerState state = MeteorShowerState.get(level);
        state.debugForecast();
        MeteorShowerManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal("A meteor shower is now incoming."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;
        MeteorShowerState state = MeteorShowerState.get(level);
        int live = level.getEntities(GCEntityTypes.FALLING_METEOR, entity -> true).size();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Meteor shower: phase=%s, intensity=%.2f, subsides in %ds, %d meteoroid(s) in flight.",
                state.phase(), state.currentIntensity(), state.remainingShowerTicks() / 20, live)), false);
        return 1;
    }

    /** Prints the physics the current dimension's celestial body data actually produces. */
    private static int atmosphere(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;
        AtmosphereProfile profile = AtmosphereProfile.of(level);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Atmosphere: surface density %.4g kg/m3, scale height %.0f m, gravity %.2f m/s2, sea level %d.",
                profile.surfaceDensity(), profile.scaleHeight(), profile.gravity(), profile.seaLevel())), false);
        return 1;
    }

    /** Drops one body of a chosen class straight down onto the caller's position. */
    private static int drop(CommandContext<CommandSourceStack> ctx, MeteoroidClass type, int size) {
        ServerLevel level = requireCelestialBody(ctx);
        if (level == null) return 0;

        Vec3 origin = ctx.getSource().getPosition();
        double spawnY = level.getMaxBuildHeight() + 160.0;

        FallingMeteorEntity meteor = new FallingMeteorEntity(GCEntityTypes.FALLING_METEOR, level);
        meteor.setPos(origin.x, spawnY, origin.z);
        meteor.initialise(type, size, level.random.nextInt(), new Vec3(0.0, -18000.0, 0.0));
        level.addFreshEntity(meteor);

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Dropped a %s meteoroid (size %d) from y=%.0f.", type.name().toLowerCase(java.util.Locale.ROOT), size, spawnY)), true);
        return 1;
    }
}
