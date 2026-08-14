package org.abc.service;

import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.abc.model.Texture;
import org.abc.util.LightNormalizer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpenGLRenderer implements Movable, Renderer, Rotatable, Runnable, Zoomable {

    private long window;
    private int windowWidth = 800;
    private int windowHeight = 600;

    private float cameraDistance = 2.5f;
    private float positionX;
    private float positionY;
    private float positionZ;

    private float rotationX = 25.0f;
    private float rotationY = 35.0f;
    private float rotationZ;

    private double lastMouseX;
    private double lastMouseY;
    private boolean rotating;
    private boolean moving;

    private final ScanMesh object;
    private final Map<Texture, Integer> textureIds = new HashMap<>();

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
                windowWidth, windowHeight, "Regele", 0, 0
        );

        if (window == 0)
            throw new IllegalStateException("Unable to create GLFW window");

        GLFW.glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            GL11.glViewport(0, 0, width, height);
            updateProjection(width, height);
        });

        GLFW.glfwSetScrollCallback(
                window,
                (w, xOffset, yOffset) -> zoom((float) -yOffset * 0.2f)
        );

        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                rotating = action == GLFW.GLFW_PRESS;

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                moving = action == GLFW.GLFW_PRESS;

            if (rotating || moving) {
                double[] x = new double[1];
                double[] y = new double[1];
                GLFW.glfwGetCursorPos(window, x, y);
                lastMouseX = x[0];
                lastMouseY = y[0];
            }
        });

        GLFW.glfwSetCursorPosCallback(window, (w, x, y) -> {
            float dx = (float) (x - lastMouseX);
            float dy = (float) (y - lastMouseY);

            if (rotating)
                rotate(-dy * 0.5f, dx * 0.5f, 0.0f);

            if (moving)
                move(dx * 0.005f, -dy * 0.005f, 0.0f);

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

        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(
                GL11.GL_FRONT_AND_BACK,
                GL11.GL_AMBIENT_AND_DIFFUSE
        );

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_POSITION,
                new float[]{2.0f, 3.0f, 4.0f, 1.0f}
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_DIFFUSE,
                new float[]{1.0f, 1.0f, 1.0f, 1.0f}
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_AMBIENT,
                new float[]{0.2f, 0.2f, 0.2f, 1.0f}
        );

        loadTextures();

        GL11.glViewport(0, 0, windowWidth, windowHeight);
        updateProjection(windowWidth, windowHeight);
    }

    private void loadTextures() {
        Material[] materials = object.getVertexMaterials();

        if (materials == null)
            return;

        for (Material material : materials) {
            if (material == null || !material.hasTexture())
                continue;

            Texture texture = material.getTexture();

            if (!textureIds.containsKey(texture))
                textureIds.put(texture, createTexture(texture));
        }
    }

    private int createTexture(Texture texture) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_LINEAR
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_LINEAR
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S,
                GL11.GL_REPEAT
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T,
                GL11.GL_REPEAT
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer image = STBImage.stbi_load_from_memory(
                    ByteBuffer.wrap(texture.getData()),
                    width,
                    height,
                    channels,
                    4
            );

            if (image == null) {
                GL11.glDeleteTextures(id);
                throw new IllegalStateException(
                        "Failed to load texture: "
                                + STBImage.stbi_failure_reason()
                );
            }

            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    width.get(0),
                    height.get(0),
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    image
            );

            STBImage.stbi_image_free(image);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }

    private void updateProjection(int width, int height) {
        if (height == 0)
            return;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / height;
        float near = 0.1f;
        float far = 100.0f;
        float fov = 60.0f;

        float yScale =
                (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));

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

        boolean ctrl =
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                        == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
                        == GLFW.GLFW_PRESS;

        if (ctrl) {
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_EQUAL)
                    == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_ADD)
                    == GLFW.GLFW_PRESS)
                zoom(-0.05f);

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_MINUS)
                    == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_SUBTRACT)
                    == GLFW.GLFW_PRESS)
                zoom(0.05f);
        }

        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(
                positionX,
                positionY,
                positionZ - cameraDistance
        );

        GL11.glRotatef(rotationX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(rotationZ, 0.0f, 0.0f, 1.0f);

        drawMesh(object);

        GLFW.glfwSwapBuffers(window);
    }

    private void drawMesh(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();
        float[] uvs = mesh.getTextureCoordinates();
        Material[] materials = mesh.getVertexMaterials();

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < indices.length; i += 3) {
            int i1 = indices[i];
            int i2 = indices[i + 1];
            int i3 = indices[i + 2];

            float[] p1 = getVertex(vertices, i1);
            float[] p2 = getVertex(vertices, i2);
            float[] p3 = getVertex(vertices, i3);

            float[] normal =
                    LightNormalizer.calculateNormal(p1, p2, p3);

            GL11.glNormal3f(
                    normal[0],
                    normal[1],
                    normal[2]
            );

            drawVertex(
                    p1,
                    getMaterial(materials, i1),
                    uvs,
                    i,
                    0
            );

            drawVertex(
                    p2,
                    getMaterial(materials, i2),
                    uvs,
                    i,
                    1
            );

            drawVertex(
                    p3,
                    getMaterial(materials, i3),
                    uvs,
                    i,
                    2
            );
        }

        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void drawVertex(
            float[] vertex,
            Material material,
            float[] uvs,
            int triangleIndex,
            int vertexIndex
    ) {
        if (material != null) {
            float[] color = material.getDiffuseColor();

            GL11.glColor4f(
                    color[0],
                    color[1],
                    color[2],
                    color.length > 3 ? color[3] : 1.0f
            );

            if (material.hasTexture()) {
                bindTexture(material.getTexture());

                if (uvs != null) {
                    int uvIndex =
                            triangleIndex * 2 + vertexIndex * 2;

                    if (uvIndex + 1 < uvs.length) {
                        GL11.glTexCoord2f(
                                uvs[uvIndex],
                                1.0f - uvs[uvIndex + 1]
                        );
                    }
                }
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
        } else {
            GL11.glColor4f(
                    0.7f,
                    0.7f,
                    0.7f,
                    1.0f
            );

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        GL11.glVertex3f(
                vertex[0],
                vertex[1],
                vertex[2]
        );
    }

    private float[] getVertex(float[] vertices, int index) {
        int offset = index * 3;

        return new float[]{
                vertices[offset],
                vertices[offset + 1],
                vertices[offset + 2]
        };
    }

    private Material getMaterial(
            Material[] materials,
            int index
    ) {
        if (materials == null
                || index < 0
                || index >= materials.length)
            return null;

        return materials[index];
    }

    private void bindTexture(Texture texture) {
        Integer id = textureIds.get(texture);

        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                id == null ? 0 : id
        );
    }

    @Override
    public void cleanup() {
        for (int id : textureIds.values())
            GL11.glDeleteTextures(id);

        textureIds.clear();
        GLFW.glfwDestroyWindow(window);
    }

    @Override
    public void zoom(float amount) {
        cameraDistance = Math.clamp(
                cameraDistance + amount,
                0.5f,
                20.0f
        );
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