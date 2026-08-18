package org.abc.service;

import org.abc.model.ScanMesh;

import java.util.List;

public class RenderStrategyFactory {

    private static boolean forceFallback = false;

    public static void setForceFallback(boolean forceFallback) {
        RenderStrategyFactory.forceFallback = forceFallback;
    }

    public static boolean isForceFallback() {
        return forceFallback;
    }

    public static boolean isMacOs() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("mac");
    }

    public static RenderStrategy createRenderer(ScanMesh mesh) {
        return createRenderer(List.of(mesh));
    }

    public static RenderStrategy createRenderer(List<ScanMesh> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one mesh is required"
            );
        }

        if (forceFallback || isMacOs()) {
            System.out.println(
                    "[INFO] Using JavaFX 3D Renderer strategy"
            );

            return new JavaFX3DRenderer(meshes);
        }

        try {
            OpenGLRenderer openGLRenderer =
                    new OpenGLRenderer(meshes);

            System.out.println(
                    "[INFO] Using primary OpenGL Renderer strategy"
            );

            return openGLRenderer;

        } catch (Throwable t) {
            System.err.println(
                    "[WARN] OpenGL Renderer initialization failed: "
                            + t.getMessage()
            );

            System.err.println(
                    "[INFO] Falling back to JavaFX 3D Renderer strategy."
            );

            return new JavaFX3DRenderer(meshes);
        }
    }
}