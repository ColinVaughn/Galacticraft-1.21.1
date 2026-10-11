package dev.galacticraft.mod.client.render.rocket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.api.entity.rocket.render.RocketPartRenderer;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.model.Tier1RocketAnimations;
import dev.galacticraft.mod.client.render.entity.rocket.RocketEntityRenderer;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.util.Tier1RocketAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Supplier;

/** Keeps the modular rocket slots, with local pivots exported from Blockbench. */
public record Tier1RocketPartRenderer(ResourceLocation model, Part part) implements RocketPartRenderer {
    public enum Part {
        BODY("Body"), CONE("Cone"), FINS("Fins"), ENGINE("Engine");
        final GCModelState state;
        Part(String name) { this.state = new GCModelState(name); }
    }

    private static final GCModelState HATCH = new GCModelState("Hatch");
    private static final GCModelState WINDOW = new GCModelState("Window");
    private static final GCModelState NOZZLE = new GCModelState("Nozzle");
    private static final GCModelState GLOW = new GCModelState("Glow");
    private record Leg(GCModelState state, double x, double z) {
        Leg(String name, double x, double z) { this(new GCModelState(name), x / 16, z / 16); }
    }
    private static final List<Leg> LEGS = List.of(new Leg("LegEast", 11.4, 2.2), new Leg("LegWest", -11.4, -2.2),
            new Leg("LegSouth", -2.2, 11.4), new Leg("LegNorth", 2.2, -11.4));

    @Override
    public void render(ClientLevel world, PoseStack matrices, Rocket rocket, MultiBufferSource vertices,
                       float partialTick, int light, int overlay) {
        Tier1RocketAnimations animations = null;
        Tier1RocketAnimationState state = null;
        float thrust = 1;
        double seconds = 0;
        if (rocket instanceof RocketEntity entity) {
            if (entity.isLanding()) thrust = entity.getThrust();
            state = entity.getTier1AnimationState();
            seconds = (entity.tickCount + (double) partialTick) / 20;
            if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof RocketEntityRenderer renderer)
                animations = renderer.getTier1Animations();
        }
        draw(GCModelLoader.INSTANCE.getModel(model), animations, state, rocket.getLaunchStage(), thrust, seconds, partialTick,
                matrices, vertices.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS)),
                () -> vertices.getBuffer(GCRenderTypes.objTranslucent(GCRenderTypes.OBJ_ATLAS)),
                light, overlay, rocket.getRocketData().color());
    }

    public void draw(GCModel model, Tier1RocketAnimations animations, Tier1RocketAnimationState motion, LaunchStage stage, float thrust,
                     double seconds, float partialTick, PoseStack matrices, VertexConsumer consumer,
                     Supplier<VertexConsumer> glassConsumer, int light, int overlay, int color) {
        model.render(matrices, part.state, consumer, light, overlay, color);
        switch (part) {
            case BODY -> {
                matrices.pushPose();
                matrices.translate(-4.4 / 16, (25.5 - 31) / 16, -11.3 / 16);
                if (animations != null && motion != null)
                    Tier1RocketAnimations.apply(matrices, animations.sample("hatch", "Hatch", motion.hatch(partialTick)), 1);
                model.render(matrices, HATCH, consumer, light, overlay, color);
                // Acquire the glass buffer after opaque draws: switching layers can flush the previous builder.
                model.render(matrices, WINDOW, glassConsumer.get(), light, overlay, 0xFFFFFFFF);
                matrices.popPose();
            }
            case FINS -> {
                for (var leg : LEGS) {
                    matrices.pushPose();
                    matrices.translate(leg.x, -2.0 / 16, leg.z);
                    if (animations != null && motion != null)
                        Tier1RocketAnimations.apply(matrices, animations.sample("gear", leg.state.getName(), motion.gear(partialTick)), 1);
                    model.render(matrices, leg.state, consumer, light, overlay, color);
                    matrices.popPose();
                }
            }
            case ENGINE -> {
                boolean burning = stage == LaunchStage.IGNITED || stage == LaunchStage.LAUNCHED && thrust > 0;
                matrices.pushPose();
                matrices.translate(0, 2.0 / 16, 0);
                if (animations != null && burning)
                    Tier1RocketAnimations.apply(matrices, animations.sample("gimbal", "Nozzle", seconds),
                            stage == LaunchStage.IGNITED ? 0.15F : 1);
                model.render(matrices, NOZZLE, consumer, light, overlay, color);
                if (animations != null && burning)
                    Tier1RocketAnimations.apply(matrices, animations.sample("exhaust", "Glow", seconds), 1);
                model.render(matrices, GLOW, consumer, burning ? LightTexture.FULL_BRIGHT : light, overlay,
                        burning ? 0xFFFFFFFF : 0xFF30343C);
                matrices.popPose();
            }
            case CONE -> { }
        }
    }
}
