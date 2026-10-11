package dev.galacticraft.mod.client.render;

import dev.galacticraft.mod.Constant;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Optional Iris integration shared by both loaders; no shader mod is required. */
public final class ShaderPackCompat {
    private static ApiAccess api = loadApi();
    private static SunAccess sun = loadSun();

    private ShaderPackCompat() {}

    public static boolean isActive() {
        if (api == null) return false;
        try {
            return api.isActive();
        } catch (ReflectiveOperationException exception) {
            Constant.LOGGER.warn("Unable to query Iris shader-pack status; disabling shader compatibility.", exception);
            api = null;
            return false;
        }
    }

    public static void renderSun(Matrix4f view, float skyAngle, Matrix4f vanilla, Consumer<Matrix4f> draw) {
        if (!isActive() || sun == null) {
            draw.accept(vanilla);
            return;
        }
        try {
            sun.render(view, skyAngle, vanilla, draw);
        } catch (ReflectiveOperationException exception) {
            Constant.LOGGER.warn("Unable to use Iris sun rendering; disabling the sun integration.", exception);
            sun = null;
        }
    }

    private static ApiAccess loadApi() {
        try {
            return ApiAccess.load(Class.forName("net.irisshaders.iris.api.v0.IrisApi"));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            Constant.LOGGER.warn("Unable to initialize Iris shader compatibility.", exception);
            return null;
        }
    }

    private static SunAccess loadSun() {
        if (api == null) return null;
        try {
            return SunAccess.load(Class.forName("net.irisshaders.iris.Iris"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            Constant.LOGGER.warn("Unable to initialize Iris sun compatibility.", exception);
            return null;
        }
    }

    record ApiAccess(Object instance, Method active) {
        static ApiAccess load(Class<?> type) throws ReflectiveOperationException {
            return new ApiAccess(type.getMethod("getInstance").invoke(null), type.getMethod("isShaderPackInUse"));
        }

        boolean isActive() throws ReflectiveOperationException {
            return (boolean) active.invoke(instance);
        }
    }

    record SunAccess(Method manager, Method pipeline, Method phase, Method setPhase,
                     Method visible, Method rotation, Object sunPhase) {
        static SunAccess load(Class<?> iris) throws ReflectiveOperationException {
            Method manager = iris.getMethod("getPipelineManager");
            Method pipeline = manager.getReturnType().getMethod("getPipelineNullable");
            Class<?> type = pipeline.getReturnType();
            Method phase = type.getMethod("getPhase");
            Class<?> phaseType = phase.getReturnType();
            return new SunAccess(manager, pipeline, phase, type.getMethod("setPhase", phaseType),
                    type.getMethod("shouldRenderSun"), type.getMethod("getSunPathRotation"),
                    phaseType.getField("SUN").get(null));
        }

        void render(Matrix4f view, float skyAngle, Matrix4f vanilla, Consumer<Matrix4f> draw)
                throws ReflectiveOperationException {
            Object current = pipeline.invoke(manager.invoke(null));
            if (current == null) {
                draw.accept(vanilla);
                return;
            }
            // Packs with procedural suns disable the textured disk to avoid drawing two suns.
            if (!(boolean) visible.invoke(current)) return;
            float tilt = (float) rotation.invoke(current);
            Matrix4f matrix = new Matrix4f(view).rotateY((float) -Math.PI / 2)
                    .rotateZ((float) Math.toRadians(tilt)).rotateX((float) Math.toRadians(skyAngle));
            Object previous = phase.invoke(current);
            setPhase.invoke(current, sunPhase);
            try {
                draw.accept(matrix);
            } finally {
                setPhase.invoke(current, previous);
            }
        }
    }
}
