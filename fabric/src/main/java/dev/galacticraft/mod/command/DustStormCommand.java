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

package dev.galacticraft.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormTuning;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormManager;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Op-only debug command for driving Mars dust storms in-game:
 * {@code /duststorm <start|stop|forecast|status|intensity <0..1>>}.
 */
public final class DustStormCommand {
    private DustStormCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("duststorm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(ctx -> start(ctx, 1.0f)))
                .then(Commands.literal("stop").executes(DustStormCommand::stop))
                .then(Commands.literal("forecast").executes(DustStormCommand::forecast))
                .then(Commands.literal("status").executes(DustStormCommand::status))
                .then(Commands.literal("intensity")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                .executes(ctx -> start(ctx, FloatArgumentType.getFloat(ctx, "value"))))));
    }

    private static DustStormTuning tuning() {
        var config = Galacticraft.CONFIG;
        return new DustStormTuning(true, config.dustStormMeanInterval(), config.dustStormMinDuration(),
                config.dustStormMaxDuration(), config.dustStormIntensity());
    }

    private static ServerLevel requireMars(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var body = level.galacticraft$getCelestialBody();
        if (body == null || !body.is(GCCelestialBodies.MARS)) {
            ctx.getSource().sendFailure(Component.literal("Dust storms only occur on Mars."));
            return null;
        }
        return level;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, float intensity) {
        ServerLevel level = requireMars(ctx);
        if (level == null) return 0;
        MarsDustStormState state = MarsDustStormState.get(level);
        state.debugStart(level.random, tuning(), intensity);
        MarsDustStormManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Started a dust storm at intensity %.2f.", intensity)), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireMars(ctx);
        if (level == null) return 0;
        MarsDustStormState state = MarsDustStormState.get(level);
        state.debugStop(level.random, tuning());
        MarsDustStormManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared the dust storm."), true);
        return 1;
    }

    private static int forecast(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireMars(ctx);
        if (level == null) return 0;
        MarsDustStormState state = MarsDustStormState.get(level);
        state.debugForecast(level.random, tuning());
        MarsDustStormManager.sync(level, state);
        ctx.getSource().sendSuccess(() -> Component.literal("A dust storm is now incoming."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = requireMars(ctx);
        if (level == null) return 0;
        MarsDustStormState state = MarsDustStormState.get(level);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Dust storm: phase=%s, intensity=%.2f, clears in %ds.",
                state.phase(), state.currentIntensity(), state.remainingStormTicks() / 20)), false);
        return 1;
    }
}
