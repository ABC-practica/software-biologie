package org.abc.service;

import org.abc.model.ScanMesh;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
public class OpenGLRenderer implements Renderer, Runnable{

    private long window;
    private final ScanMesh object;

    public OpenGLRenderer(ScanMesh object) {
        this.object = object;
    }

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

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = 800.0f / 600.0f;
        float fov = 60.0f;
        float near = 0.1f;
        float far = 100.0f;

        float yScale = (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));
        float xScale = yScale / aspect;

        GL11.glFrustum(
                -near * xScale,
                near * xScale,
                -near * yScale,
                near * yScale,
                near,
                far
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Override
    public void render() {
        GLFW.glfwPollEvents();

        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(0.0f, 0.0f, -2.5f);
        GL11.glRotatef(25.0f, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(35.0f, 0.0f, 1.0f, 0.0f);

        drawMesh(object);

        GLFW.glfwSwapBuffers(window);
    }

    @Override
    public void cleanup() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    private void drawMesh(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int index : indices) {
            int vertexIndex = index * 3;

            GL11.glVertex3f(
                    vertices[vertexIndex],
                    vertices[vertexIndex + 1],
                    vertices[vertexIndex + 2]
            );
        }

        GL11.glEnd();
    }
}