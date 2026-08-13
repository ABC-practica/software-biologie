package org.abc.service;

import org.abc.model.ScanMesh;
import org.abc.util.LightNormalizer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public class OpenGLRenderer implements Movable, Renderer, Rotatable, Runnable, Zoomable {

    private long window;

    private int windowWidth = 800;
    private int windowHeight = 600;

    private float cameraDistance = 2.5f;

    private float positionX = 0.0f;
    private float positionY = 0.0f;
    private float positionZ = 0.0f;

    private float rotationX = 25.0f;
    private float rotationY = 35.0f;
    private float rotationZ = 0.0f;

    private double lastMouseX;
    private double lastMouseY;

    private boolean rotating;
    private boolean moving;

    private final ScanMesh object;

    public OpenGLRenderer(ScanMesh object) {
        this.object = object;
    }

    @Override
    public void run() {
        initialize();

        while (!GLFW.glfwWindowShouldClose(window)
                && !Thread.currentThread().isInterrupted()) {
            render();
        }

        cleanup();
    }

    @Override
    public void initialize() {
        GLFWManager.initialize();

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(
                windowWidth,
                windowHeight,
                "Regele",
                0,
                0
        );

        if (window == 0) {
            throw new IllegalStateException("Unable to create GLFW window");
        }

        GLFW.glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            windowWidth = width;
            windowHeight = height;

            GL11.glViewport(0, 0, width, height);
            updateProjection(width, height);
        });

        GLFW.glfwSetScrollCallback(window, (window, xOffset, yOffset) -> {
            zoom((float) -yOffset * 0.2f);
        });

        GLFW.glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                rotating = action == GLFW.GLFW_PRESS;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                moving = action == GLFW.GLFW_PRESS;
            }

            if (rotating || moving) {
                double[] x = new double[1];
                double[] y = new double[1];

                GLFW.glfwGetCursorPos(window, x, y);

                lastMouseX = x[0];
                lastMouseY = y[0];
            }
        });

        GLFW.glfwSetCursorPosCallback(window, (window, x, y) -> {
            float dx = (float) (x - lastMouseX);
            float dy = (float) (y - lastMouseY);

            if (rotating) {
                rotate(-dy * 0.5f, dx * 0.5f, 0.0f);
            }

            if (moving) {
                move(dx * 0.005f, -dy * 0.005f, 0.0f);
            }

            lastMouseX = x;
            lastMouseY = y;
        });

        GLFW.glfwMakeContextCurrent(window);

        GL.createCapabilities();

        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);

        float[] lightPosition = {
                2.0f, 3.0f, 4.0f, 1.0f
        };

        float[] lightDiffuse = {
                1.0f, 1.0f, 1.0f, 1.0f
        };

        float[] lightAmbient = {
                0.2f, 0.2f, 0.2f, 1.0f
        };

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_POSITION,
                lightPosition
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_DIFFUSE,
                lightDiffuse
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_AMBIENT,
                lightAmbient
        );

        GL11.glEnable(GL11.GL_COLOR_MATERIAL);

        GL11.glColorMaterial(
                GL11.GL_FRONT_AND_BACK,
                GL11.GL_AMBIENT_AND_DIFFUSE
        );

        GL11.glViewport(0, 0, windowWidth, windowHeight);
        updateProjection(windowWidth, windowHeight);
    }

    private void updateProjection(int width, int height) {
        if (height == 0) {
            return;
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / height;
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

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_EQUAL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_ADD) == GLFW.GLFW_PRESS) {
                zoom(-0.05f);
            }

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_MINUS) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_SUBTRACT) == GLFW.GLFW_PRESS) {
                zoom(0.05f);
            }
        }

        GL11.glClearColor(
                0.1f,
                0.1f,
                0.1f,
                1.0f
        );

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(positionX, positionY, positionZ - cameraDistance);
        GL11.glRotatef(rotationX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(rotationZ, 0.0f, 0.0f, 1.0f);

        GL11.glColor3f(0.7f, 0.7f, 0.7f);

        drawMesh(object);

        GLFW.glfwSwapBuffers(window);
    }

    @Override
    public void cleanup() {
        GLFW.glfwDestroyWindow(window);
    }

    private void drawMesh(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < indices.length; i += 3) {
            int index1 = indices[i] * 3;
            int index2 = indices[i + 1] * 3;
            int index3 = indices[i + 2] * 3;

            float[] p1 = {vertices[index1], vertices[index1 + 1], vertices[index1 + 2]};
            float[] p2 = {vertices[index2], vertices[index2 + 1], vertices[index2 + 2]};
            float[] p3 = {vertices[index3], vertices[index3 + 1], vertices[index3 + 2]};
            float[] normal = LightNormalizer.calculateNormal(p1, p2, p3);

            GL11.glNormal3f(normal[0], normal[1], normal[2]);
            GL11.glVertex3f(p1[0], p1[1], p1[2]);
            GL11.glVertex3f(p2[0], p2[1], p2[2]);
            GL11.glVertex3f(p3[0], p3[1], p3[2]);
        }

        GL11.glEnd();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance += amount;
        cameraDistance = Math.clamp(cameraDistance, 0.5f, 20.0f);
    }

    @Override
    public void move(float x, float y, float z) {
        positionX += x;
        positionY += y;
        positionZ += z;
    }

    @Override
    public void rotate(float x, float y, float z) {
        rotationX += x;
        rotationY += y;
        rotationZ += z;
    }
}