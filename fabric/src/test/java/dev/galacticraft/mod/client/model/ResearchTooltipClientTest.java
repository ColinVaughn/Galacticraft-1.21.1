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

package dev.galacticraft.mod.client.model;

import dev.architectury.registry.menu.MenuRegistry;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.workbench.ResearchSources;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Opt-in capture of the workbench's missing-research tooltip for a player with no research. */
public final class ResearchTooltipClientTest implements ClientModInitializer {
    private static final BlockPos WORKBENCH = new BlockPos(0, -60, 2);
    private boolean created, opened;
    private int ticks;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.research-tooltip.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!created && client.screen != null) {
                created = true;
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.createWorldOpenFlows().createFreshLevel("research-tooltip-" + System.currentTimeMillis(),
                        new LevelSettings("Research Tooltip Check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(17, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!opened) {
                if (client.screen != null) return;
                opened = true;
                client.getSingleplayerServer().execute(() -> open(client));
                return;
            }
            if (!(client.player.containerMenu instanceof RocketWorkbenchMenu menu)
                    || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
            ticks++;
            if (ticks == 20) {
                require(!menu.getMissingResearch().isEmpty(), "Client saw no missing research");
                for (var id : menu.getMissingResearchIds()) require(ResearchSources.source(id) != null, "No synced source for " + id);
            }
            if (ticks >= 20 && ticks <= 30) hover(client, screen, menu);
            if (ticks == 30) capture(client, "research-tooltip.png");
            if (ticks == 35) {
                LoggerFactory.getLogger("ResearchTooltipClientTest").info("RESEARCH TOOLTIP CLIENT CHECK PASSED: {} missing, sources {}",
                        menu.getMissingResearch().size(), menu.getMissingResearchIds().stream().map(ResearchSources::source).map(c -> c.getString()).toList());
                client.stop();
            }
        });
    }

    private static void open(Minecraft client) {
        var server = client.getSingleplayerServer();
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        level.setBlock(WORKBENCH, GCBlocks.ROCKET_WORKBENCH.defaultBlockState(), 3);
        var workbench = (RocketWorkbenchBlockEntity) level.getBlockEntity(WORKBENCH);
        MenuRegistry.openExtendedMenu(player, workbench.menuOn(Constant.id("rocket/modular")));
        // Tier-1 parts come from an advancement and tier-2 fins from a schematic, so both kinds of hint show.
        int[] tiers = {1, 1, 2, 1};
        List<RocketPartTypes> types = List.of(RocketPartTypes.CONE, RocketPartTypes.BODY, RocketPartTypes.FIN, RocketPartTypes.ENGINE);
        for (int i = 0; i < 4; i++) {
            int slot = i;
            workbench.ingredients.setItem(i, GCItems.ROCKET_MODULES.stream()
                    .filter(module -> module.type() == types.get(slot) && module.tier() == tiers[slot]).findFirst().orElseThrow().getDefaultInstance());
        }
        workbench.ingredients.setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void hover(Minecraft client, AbstractContainerScreen<?> screen, RocketWorkbenchMenu menu) {
        try {
            var left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            var top = AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            double scale = client.getWindow().getGuiScale();
            double x = (left.getInt(screen) + menu.resultSlot().x + 8) * scale;
            double y = (top.getInt(screen) + menu.resultSlot().y + 8) * scale;
            GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x, y);
            // The cursor callback does not always fire for a programmatic move, so set Minecraft's copy as well.
            var mouseX = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var mouseY = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            mouseX.setAccessible(true);
            mouseY.setAccessible(true);
            mouseX.setDouble(client.mouseHandler, x);
            mouseY.setDouble(client.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("ResearchTooltipClientTest").info("{}", message.getString()));
    }
}
