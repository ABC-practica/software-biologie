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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OpenGLRenderer implements RendererControl, Movable, Renderer, Rotatable, Runnable, Zoomable {

    private volatile long window;
    private volatile int windowWidth = 800;
    private volatile int windowHeight = 600;
    private volatile int framebufferWidth = 800;
    private volatile int framebufferHeight = 600;

    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;

    private volatile float cameraDistance = 5.0f;
    private volatile float positionX;
    private volatile float positionY;
    private volatile float positionZ;

    private volatile float rotationX = 25.0f;
    private volatile float rotationY = 35.0f;
    private volatile float rotationZ;

    private double lastMouseX;
    private double lastMouseY;
    private double mousePressX;
    private double mousePressY;

    private volatile boolean rotating;
    private volatile boolean moving;
    private volatile boolean running;
    private volatile boolean renderFinished;

    private final List<ScanMesh> objects;
    private volatile ScanMesh selectedObject;

    private final Map<Texture, Integer> textureIds = new HashMap<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private Thread renderThread;

    public OpenGLRenderer(List<ScanMesh> objects) {
        this.objects = objects;

        if (!objects.isEmpty()) {
            selectedObject = objects.get(0);
        }
    }

    public OpenGLRenderer(ScanMesh object) {
        this.objects = List.of(object);
        this.selectedObject = object;
    }

    public ScanMesh getSelectedObject() {
        return selectedObject;
    }

    public void setSelectedObject(ScanMesh object) {
        if (objects.contains(object)) {
            selectedObject = object;
        }
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
            windowWidth = framebufferWidth;
            windowHeight = framebufferHeight;
        });

        GLFW.glfwSetScrollCallback(
                window,
                (w, xOffset, yOffset) -> zoom((float) -Math.signum(yOffset) * 0.15f)
        );

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                running = false;
                return;
            }

            if (selectedObject == null ||
                    (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT)) {
                return;
            }

            float amount = 0.05f;

            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> selectedObject.move(-amount, 0, 0);
                case GLFW.GLFW_KEY_RIGHT -> selectedObject.move(amount, 0, 0);
                case GLFW.GLFW_KEY_UP -> selectedObject.move(0, amount, 0);
                case GLFW.GLFW_KEY_DOWN -> selectedObject.move(0, -amount, 0);
                case GLFW.GLFW_KEY_PAGE_UP -> selectedObject.move(0, 0, amount);
                case GLFW.GLFW_KEY_PAGE_DOWN -> selectedObject.move(0, 0, -amount);
            }
        });

        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double[] x = new double[1];
            double[] y = new double[1];

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    rotating = true;
                    GLFW.glfwGetCursorPos(window, x, y);
                    lastMouseX = mousePressX = x[0];
                    lastMouseY = mousePressY = y[0];
                } else if (action == GLFW.GLFW_RELEASE) {
                    rotating = false;
                    GLFW.glfwGetCursorPos(window, x, y);

                    double dx = x[0] - mousePressX;
                    double dy = y[0] - mousePressY;

                    if (Math.sqrt(dx * dx + dy * dy) < 5.0) {
                        selectObjectAt(x[0], y[0]);
                    }
                }
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW.GLFW_PRESS) {
                    moving = true;
                    GLFW.glfwGetCursorPos(window, x, y);
                    lastMouseX = x[0];
                    lastMouseY = y[0];
                } else if (action == GLFW.GLFW_RELEASE) {
                    moving = false;
                }
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

    private void selectObjectAt(double mouseX, double mouseY) {
        if (objects.isEmpty()) {
            return;
        }

        ScanMesh closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (ScanMesh object : objects) {
            double[] screen = projectObjectCenter(object);

            if (screen == null) {
                continue;
            }

            double dx = screen[0] - mouseX;
            double dy = screen[1] - mouseY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double radius = Math.max(
                    20,
                    calculateProjectedRadius(
                            object,
                            calculateMarkerSize(object)
                    )
            );

            if (distance <= radius && distance < closestDistance) {
                closest = object;
                closestDistance = distance;
            }
        }

        if (closest != null) {
            selectedObject = closest;
        }
    }

    private double[] projectObjectCenter(ScanMesh object) {
        float pivotX = selectedObject != null ? selectedObject.getPositionX() : 0;
        float pivotY = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pivotZ = selectedObject != null ? selectedObject.getPositionZ() : 0;

        float[] point = rotateX(
                object.getPositionX() - pivotX,
                object.getPositionY() - pivotY,
                object.getPositionZ() - pivotZ
        );

        point = rotateY(point[0], point[1], point[2]);
        point = rotateZ(point[0], point[1], point[2]);

        float cameraX = point[0] + pivotX + positionX;
        float cameraY = point[1] + pivotY + positionY;
        float cameraZ = point[2] + pivotZ + positionZ - cameraDistance;

        if (cameraZ >= -0.1f) {
            return null;
        }

        float aspect = (float) windowWidth / Math.max(1, windowHeight);
        float tanHalfFov = (float) Math.tan(Math.toRadians(60) / 2);

        double ndcX = cameraX / (-cameraZ * tanHalfFov * aspect);
        double ndcY = cameraY / (-cameraZ * tanHalfFov);

        return new double[]{
                (ndcX + 1) * windowWidth / 2,
                (1 - ndcY) * windowHeight / 2
        };
    }

    private double calculateProjectedRadius(ScanMesh object, float radius) {
        double[] center = projectObjectCenter(object);

        if (center == null) {
            return 20;
        }

        float depth = calculateObjectDepth(object);

        if (depth <= 0.1f) {
            return 20;
        }

        float pixelsPerUnit = windowHeight /
                (2.0f * (float) Math.tan(Math.toRadians(60) / 2) * depth);

        return radius * pixelsPerUnit;
    }

    private float calculateObjectDepth(ScanMesh object) {
        float pivotX = selectedObject != null ? selectedObject.getPositionX() : 0;
        float pivotY = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pivotZ = selectedObject != null ? selectedObject.getPositionZ() : 0;

        float[] point = rotateX(
                object.getPositionX() - pivotX,
                object.getPositionY() - pivotY,
                object.getPositionZ() - pivotZ
        );

        point = rotateY(point[0], point[1], point[2]);
        point = rotateZ(point[0], point[1], point[2]);

        return -(point[2] + pivotZ + positionZ - cameraDistance);
    }

    private float[] rotateX(float x, float y, float z) {
        double a = Math.toRadians(rotationX);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);

        return new float[]{
                x,
                y * c - z * s,
                y * s + z * c
        };
    }

    private float[] rotateY(float x, float y, float z) {
        double a = Math.toRadians(rotationY);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);

        return new float[]{
                x * c + z * s,
                y,
                -x * s + z * c
        };
    }

    private float[] rotateZ(float x, float y, float z) {
        double a = Math.toRadians(rotationZ);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);

        return new float[]{
                x * c - y * s,
                x * s + y * c,
                z
        };
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
    }

    @Override
    public void resetCamera() {
        cameraDistance = 5.0f;

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

        float pivotX = selectedObject != null ? selectedObject.getPositionX() : 0;
        float pivotY = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pivotZ = selectedObject != null ? selectedObject.getPositionZ() : 0;

        GL11.glTranslatef(positionX, positionY, positionZ - cameraDistance);
        GL11.glTranslatef(pivotX, pivotY, pivotZ);
        GL11.glRotatef(rotationX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(rotationZ, 0.0f, 0.0f, 1.0f);
        GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);

        for (ScanMesh object : objects) {
            drawObject(object);
        }

        GLFW.glfwSwapBuffers(window);
    }

    private void drawObject(ScanMesh object) {
        GL11.glPushMatrix();

        GL11.glTranslatef(
                object.getPositionX(),
                object.getPositionY(),
                object.getPositionZ()
        );

        GL11.glScalef(
                object.getScale(),
                object.getScale(),
                object.getScale()
        );

        GL11.glRotatef(object.getRotationX(), 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(object.getRotationY(), 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(object.getRotationZ(), 0.0f, 0.0f, 1.0f);

        drawMesh(object);

        if (object == selectedObject) {
            drawSelectionMarker(object);
        }

        GL11.glPopMatrix();
    }

    private void drawSelectionMarker(ScanMesh object) {
        float s = calculateMarkerSize(object);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(1.0f, 1.0f, 0.0f);
        GL11.glLineWidth(2.0f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(-s, -s, -s);
        GL11.glVertex3f(s, -s, -s);
        GL11.glVertex3f(s, s, -s);
        GL11.glVertex3f(-s, s, -s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(-s, -s, s);
        GL11.glVertex3f(s, -s, s);
        GL11.glVertex3f(s, s, s);
        GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(-s, -s, -s);
        GL11.glVertex3f(-s, -s, s);
        GL11.glVertex3f(s, -s, -s);
        GL11.glVertex3f(s, -s, s);
        GL11.glVertex3f(s, s, -s);
        GL11.glVertex3f(s, s, s);
        GL11.glVertex3f(-s, s, -s);
        GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private float calculateMarkerSize(ScanMesh object) {
        float[] vertices = object.getVertices();

        if (vertices == null || vertices.length < 3) {
            return 0.5f;
        }

        float max = 0;

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            max = Math.max(
                    max,
                    (float) Math.sqrt(x * x + y * y + z * z)
            );
        }

        return Math.max(0.5f, max * 1.2f);
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
        for (ScanMesh object : objects) {
            Material[] materials = object.getVertexMaterials();

            if (materials == null) {
                continue;
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
                        "Failed to load texture: " +
                                STBImage.stbi_failure_reason()
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

        float yScale = (float) (
                1.0 / Math.tan(Math.toRadians(fov / 2.0))
        );

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

        if (vertices == null || indices == null) {
            return;
        }

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int i1 = indices[i];
            int i2 = indices[i + 1];
            int i3 = indices[i + 2];

            float[] p1 = getVertex(vertices, i1);
            float[] p2 = getVertex(vertices, i2);
            float[] p3 = getVertex(vertices, i3);

            float[] normal = LightNormalizer.calculateNormal(p1, p2, p3);

            GL11.glNormal3f(
                    normal[0],
                    normal[1],
                    normal[2]
            );

            drawVertex(p1, getMaterial(materials, i1), uvs, i, 0);
            drawVertex(p2, getMaterial(materials, i2), uvs, i, 1);
            drawVertex(p3, getMaterial(materials, i3), uvs, i, 2);
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
        if (material == null) {
            GL11.glColor4f(0.7f, 0.7f, 0.7f, 1.0f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } else {
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
        }

        GL11.glVertex3f(
                vertex[0],
                vertex[1],
                vertex[2]
        );
    }

    private float[] getVertex(float[] vertices, int index) {
        int offset = index * 3;

        if (offset < 0 || offset + 2 >= vertices.length) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

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
        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                id == null ? 0 : id
        );
    }

    private void cleanupOpenGL() {
        for (int id : textureIds.values()) {
            GL11.glDeleteTextures(id);
        }

        textureIds.clear();
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