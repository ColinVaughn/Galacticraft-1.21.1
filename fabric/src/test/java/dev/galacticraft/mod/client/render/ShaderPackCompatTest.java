package dev.galacticraft.mod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackCompatTest {
    @BeforeEach void reset() {
        FakeApi.active = false;
        FakeIris.manager.pipeline = new FakePipeline();
    }

    @Test void detectsPackTogglesWithoutTreatingAnInstalledLoaderAsAnActivePack() throws Exception {
        var api = ShaderPackCompat.ApiAccess.load(FakeApi.class);
        assertFalse(api.isActive());
        FakeApi.active = true;
        assertTrue(api.isActive());
        FakeApi.active = false;
        assertFalse(api.isActive());
    }

    @Test void sunUsesThePackStageAndItsTiltAndRestoresThePreviousStage() throws Exception {
        var access = ShaderPackCompat.SunAccess.load(FakeIris.class);
        FakeIris.manager.pipeline.tilt = 30;
        Matrix4f view = new Matrix4f().rotateY(0.3F);
        access.render(view, 45, new Matrix4f(), matrix -> {
            assertEquals(Phase.SUN, FakeIris.manager.pipeline.phase);
            Vector4f expected = new Matrix4f(view).rotateY((float) -Math.PI / 2)
                    .rotateZ((float) Math.toRadians(30)).rotateX((float) Math.toRadians(45))
                    .transform(new Vector4f(0, 100, 0, 1));
            assertTrue(expected.equals(matrix.transform(new Vector4f(0, 100, 0, 1)), 0.0001F));
        });
        assertEquals(Phase.CUSTOM_SKY, FakeIris.manager.pipeline.phase);
        assertTrue(view.equals(new Matrix4f().rotateY(0.3F), 0.00001F));
    }

    @Test void proceduralPacksCanSuppressTheDiskAndMissingPipelinesUseTheVanillaMatrix() throws Exception {
        var access = ShaderPackCompat.SunAccess.load(FakeIris.class);
        FakeIris.manager.pipeline.visible = false;
        AtomicBoolean drawn = new AtomicBoolean();
        access.render(new Matrix4f(), 0, new Matrix4f(), matrix -> drawn.set(true));
        assertFalse(drawn.get());
        FakeIris.manager.pipeline = null;
        Matrix4f vanilla = new Matrix4f().rotateX(1);
        access.render(new Matrix4f(), 0, vanilla, matrix -> assertSame(vanilla, matrix));
    }

    @Test void restoresTheStageWhenRenderingThrows() throws Exception {
        var access = ShaderPackCompat.SunAccess.load(FakeIris.class);
        assertThrows(IllegalStateException.class, () -> access.render(new Matrix4f(), 0, new Matrix4f(), matrix -> {
            throw new IllegalStateException("draw failed");
        }));
        assertEquals(Phase.CUSTOM_SKY, FakeIris.manager.pipeline.phase);
    }

    public enum Phase { CUSTOM_SKY, SUN }

    public static class FakeApi {
        static boolean active;
        public static FakeApi getInstance() { return new FakeApi(); }
        public boolean isShaderPackInUse() { return active; }
    }

    public static class FakeIris {
        static final FakeManager manager = new FakeManager();
        public static FakeManager getPipelineManager() { return manager; }
    }

    public static class FakeManager {
        FakePipeline pipeline;
        public FakePipeline getPipelineNullable() { return pipeline; }
    }

    public static class FakePipeline {
        Phase phase = Phase.CUSTOM_SKY;
        float tilt;
        boolean visible = true;
        public Phase getPhase() { return phase; }
        public void setPhase(Phase phase) { this.phase = phase; }
        public boolean shouldRenderSun() { return visible; }
        public float getSunPathRotation() { return tilt; }
    }
}
