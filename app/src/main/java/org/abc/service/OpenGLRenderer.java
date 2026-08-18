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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OpenGLRenderer implements RenderStrategy, Runnable {

    private volatile long window;
    private volatile int windowWidth = 800;
    private volatile int windowHeight = 600;
    private volatile int framebufferWidth = 800;
    private volatile int framebufferHeight = 600;

    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;

    private volatile float cameraDistance = 3.5f;

    private volatile float positionX;
    private volatile float positionY;
    private volatile float positionZ;

    private volatile float rotationX = 25.0f;
    private volatile float rotationY = 35.0f;
    private volatile float rotationZ;

    private double lastMouseX;
    private double lastMouseY;

    private volatile boolean rotating;
    private volatile boolean moving;

    private volatile boolean running;
    private volatile boolean renderFinished;

    private final ScanMesh object;

    private final Map<Texture, Integer> textureIds = new HashMap<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private Thread renderThread;

    public OpenGLRenderer(ScanMesh object) {
        this.object = object;
    }

    @Override
    public void run() {
        open();
    }

    @Override
    public void open() {
        GLFWManager.initialize();
        GLFWManager.execute(this::createWindowOnMainThread);
    }

    private void createWindowOnMainThread() {
        if (!GLFWManager.isOwnerThread()) {
            throw new IllegalStateException(
                    "GLFW window creation must happen on the JVM main thread"
            );
        }

        if (window != 0) {
            return;
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(
                windowWidth,
                windowHeight,
                "3D Renderer",
                0,
                0
        );

        if (window == 0) {
            throw new IllegalStateException("Unable to create GLFW window");
        }

        setupCallbacks();

        GLFWManager.register(this);

        running = true;
        renderFinished = false;

        GLFW.glfwShowWindow(window);

        renderThread = new Thread(this::renderLoop, "OpenGL-Renderer");
        renderThread.start();
    }

    private void setupCallbacks() {
        GLFW.glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            framebufferWidth = Math.max(1, width);
            framebufferHeight = Math.max(1, height);
        });

        GLFW.glfwSetScrollCallback(
                window,
                (w, xOffset, yOffset) -> zoom((float) -Math.signum(yOffset) * 0.15f)
        );

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                running = false;
            }
        });

        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
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

        GLFW.glfwSetCursorPosCallback(window, (w, x, y) -> {
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

        GLFW.glfwSetWindowCloseCallback(window, w -> running = false);
    }

    private void renderLoop() {
        try {
            GLFW.glfwMakeContextCurrent(window);

            GL.createCapabilities();
            GLFW.glfwSwapInterval(0);

            initializeOpenGL();
            loadTextures();
            updateViewport();

            while (running) {
                processCommands();
                render();
            }
        } finally {
            cleanupOpenGL();
            GLFW.glfwMakeContextCurrent(0);

            renderFinished = true;
            GLFW.glfwPostEmptyEvent();
        }
    }

    private void initializeOpenGL() {
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
    }

    private void processCommands() {
        Runnable command;

        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    @Override
    public void close() {
        running = false;

        if (GLFWManager.isInitialized()) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    void requestCloseFromManager() {
        running = false;
    }

    boolean isRenderFinished() {
        return renderFinished;
    }

    void destroyWindowOnMainThread() {
        if (!GLFWManager.isOwnerThread()) {
            throw new IllegalStateException(
                    "Window destruction must happen on the GLFW main thread"
            );
        }

        if (window == 0 || !renderFinished) {
            return;
        }

        GLFW.glfwDestroyWindow(window);

        window = 0;
        renderThread = null;

        GLFWManager.unregister(this);
    }

    @Override
    public void refresh() {
        // Continuous renderer.
    }

    @Override
    public void resetCamera() {
        cameraDistance = 3.5f;

        positionX = 0.0f;
        positionY = 0.0f;
        positionZ = 0.0f;

        rotationX = 25.0f;
        rotationY = 35.0f;
        rotationZ = 0.0f;
    }

    @Override
    public void reload() {
        commands.add(() -> {
            cleanupOpenGL();
            loadTextures();
            resetCamera();
        });
    }

    @Override
    public void render() {
        updateViewport();

        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(positionX, positionY, positionZ - cameraDistance);
        GL11.glRotatef(rotationX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(rotationZ, 0.0f, 0.0f, 1.0f);

        drawMesh(object);

        GLFW.glfwSwapBuffers(window);
    }

    private void updateViewport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer fw = stack.mallocInt(1);
            IntBuffer fh = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, fw, fh);
            framebufferWidth = Math.max(1, fw.get(0));
            framebufferHeight = Math.max(1, fh.get(0));
        }

        int width = framebufferWidth;
        int height = framebufferHeight;

        if (width == lastViewportWidth && height == lastViewportHeight) {
            return;
        }

        lastViewportWidth = width;
        lastViewportHeight = height;

        GL11.glViewport(0, 0, width, height);
        updateProjection(width, height);
    }

    private void loadTextures() {
        Material[] materials = object.getVertexMaterials();

        if (materials == null) {
            return;
        }

        for (Material material : materials) {
            if (material == null || !material.hasTexture()) {
                continue;
            }

            Texture texture = material.getTexture();

            if (!textureIds.containsKey(texture)) {
                textureIds.put(texture, createTexture(texture));
            }
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
                        "Failed to load texture: " + STBImage.stbi_failure_reason()
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
        if (height <= 0) {
            return;
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / height;
        float near = 0.1f;
        float far = 100.0f;
        float fov = 60.0f;

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

            float[] normal = LightNormalizer.calculateNormal(p1, p2, p3);

            GL11.glNormal3f(normal[0], normal[1], normal[2]);

            drawVertex(p1, getMaterial(materials, i1), uvs, i, 0);
            drawVertex(p2, getMaterial(materials, i2), uvs, i, 1);
            drawVertex(p3, getMaterial(materials, i3), uvs, i, 2);
        }

        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void drawVertex(float[] vertex, Material material, float[] uvs, int triangleIndex, int vertexIndex) {
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
                    int uvIndex = triangleIndex * 2 + vertexIndex * 2;

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
            GL11.glColor4f(0.7f, 0.7f, 0.7f, 1.0f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
    }

    private float[] getVertex(float[] vertices, int index) {
        int offset = index * 3;

        return new float[]{
                vertices[offset],
                vertices[offset + 1],
                vertices[offset + 2]
        };
    }

    private Material getMaterial(Material[] materials, int index) {
        if (materials == null || index < 0 || index >= materials.length) {
            return null;
        }

        return materials[index];
    }

    private void bindTexture(Texture texture) {
        Integer id = textureIds.get(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id == null ? 0 : id);
    }

    private void cleanupOpenGL() {
        for (int id : textureIds.values()) {
            GL11.glDeleteTextures(id);
        }

        textureIds.clear();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance = Math.clamp(cameraDistance + amount, 0.5f, 20.0f);
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