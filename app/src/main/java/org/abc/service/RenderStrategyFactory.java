package org.abc.service;

import org.abc.model.ScanMesh;

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
        if (forceFallback || isMacOs()) {
            System.out.println("[INFO] Using JavaFX 3D Renderer strategy (macOS detected or forced fallback)");
            return new JavaFX3DRenderer(mesh);
        }

        try {
            OpenGLRenderer openGLRenderer = new OpenGLRenderer(mesh);
            System.out.println("[INFO] Using primary OpenGL Renderer strategy");
            return openGLRenderer;
        } catch (Throwable t) {
            System.err.println("[WARN] OpenGL Renderer initialization failed: " + t.getMessage());
            System.err.println("[INFO] Falling back to JavaFX 3D Renderer strategy.");
            return new JavaFX3DRenderer(mesh);
        }
    }
}
