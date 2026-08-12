package org.abc.service;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
public class OpenGLRenderer implements Renderer, Runnable{

    private long window;

    @Override
    public void run() {
        initialize();

        while (!GLFW.glfwWindowShouldClose(window) && !Thread.currentThread().isInterrupted()) {
            render();
        }

        cleanup();
    }

    @Override
    public void initialize() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(800, 600, "Regele", 0, 0);

        if (window == 0) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("Unable to create GLFW window");
        }

        GLFW.glfwMakeContextCurrent(window);

        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
    }

    @Override
    public void render() {
        GLFW.glfwPollEvents();
        GLFW.glfwSwapBuffers(window);
    }

    @Override
    public void cleanup() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}