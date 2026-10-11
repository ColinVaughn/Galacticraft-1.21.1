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

import dev.galacticraft.mod.client.model.entity.LunarBurrowerModel;
import dev.galacticraft.mod.client.render.entity.LunarBurrowerRenderer;
import dev.galacticraft.mod.client.render.entity.BurrowerBodyRenderer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.RandomSource;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Opt-in startup check against Minecraft's actual renderer and baked model registry. */
public final class BurrowerClientSmokeTest implements ClientModInitializer {
    private boolean checked;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.burrower.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (checked || client.getOverlay() != null || client.screen == null) return;
            checked = true;
            try {
                new LunarBurrowerModel(client.getResourceManager());
                new LunarBurrowerModel(client.getResourceManager(), "lunar_burrower_child");
                new LunarBurrowerModel(client.getResourceManager(), "lunar_burrower_grown");
                new LunarBurrowerModel(client.getResourceManager(), "lunar_burrower_equipment");
                for (var state : GCBlocks.LUNAR_BURROWER_EGG.getStateDefinition().getPossibleStates())
                    require(client.getBlockRenderer().getBlockModel(state) != client.getModelManager().getMissingModel(), "Missing egg stage model: " + state);
                var field = EntityRenderDispatcher.class.getDeclaredField("renderers");
                field.setAccessible(true);
                Map<?, ?> renderers = (Map<?, ?>)field.get(client.getEntityRenderDispatcher());
                require(renderers.get(GCEntityTypes.LUNAR_BURROWER) instanceof LunarBurrowerRenderer, "Missing burrower renderer");
                require(renderers.get(GCEntityTypes.BURROWER_BODY) instanceof BurrowerBodyRenderer, "Missing body target renderer");
                int variants = 0;
                for (var block : new net.minecraft.world.level.block.Block[]{GCBlocks.HELION_NODULES, GCBlocks.HELION_WALL_CRUST, GCBlocks.HELIUM_3_VENT}) {
                    for (var state : block.getStateDefinition().getPossibleStates()) {
                        var model = client.getBlockRenderer().getBlockModel(state);
                        require(model instanceof FabricLunarEmissiveBakedModel, "Missing emissive wrapper: " + state);
                        int core = 0, stone = 0;
                        for (var quad : model.getQuads(state, null, RandomSource.create(42))) {
                            if (LunarEmissiveBakedModel.glows(quad)) {
                                core++;
                                require(!quad.isShade(), "Glowing core still has diffuse shading");
                                int[] vertices = quad.getVertices();
                                for (int vertex = 0; vertex < 4; vertex++)
                                    require(vertices[vertex * (vertices.length / 4) + 6] == LightTexture.FULL_BRIGHT, "Core is not full bright");
                            } else { stone++; require(quad.isShade(), "Stone shell lost normal shading"); }
                        }
                        require(core > 0 && stone > 0, "Missing core or stone geometry: " + state);
                        variants++;
                    }
                    var item = client.getItemRenderer().getModel(block.asItem().getDefaultInstance(), null, null, 42);
                    require(item instanceof FabricLunarEmissiveBakedModel, "Inventory model lacks emissive wrapper: " + block);
                }
                LoggerFactory.getLogger("BurrowerClientSmokeTest").info("BURROWER CLIENT CHECK PASSED: rig, both entity renderers, {} emissive block variants, and 3 item models", variants);
                client.stop();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot inspect renderer registry", error); }
        });
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
