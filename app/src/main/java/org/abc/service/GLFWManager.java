package org.abc.service;

import org.lwjgl.glfw.GLFW;

public final class GLFWManager {

    private static boolean initialized = false;

    private GLFWManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        GLFW.glfwInitHint(
                GLFW.GLFW_WAYLAND_LIBDECOR,
                GLFW.GLFW_WAYLAND_DISABLE_LIBDECOR
        );

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        initialized = true;
    }

    public static synchronized void terminate() {
        if (!initialized) {
            return;
        }

        GLFW.glfwTerminate();
        initialized = false;
    }
}