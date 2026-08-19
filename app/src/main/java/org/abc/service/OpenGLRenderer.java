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
import java.util.concurrent.CopyOnWriteArrayList;

public class OpenGLRenderer implements RenderStrategy, RendererControl, Movable,
        Renderer, Rotatable, Runnable, Zoomable {

    private volatile long window;
    private volatile int windowWidth = 800, windowHeight = 600;
    private volatile int framebufferWidth = 800, framebufferHeight = 600;
    private int lastViewportWidth = -1, lastViewportHeight = -1;

    private volatile float cameraDistance = 5f;
    private volatile float positionX, positionY, positionZ;
    private volatile float rotationX = 25f, rotationY = 35f, rotationZ;

    private double lastMouseX, lastMouseY, mousePressX, mousePressY;
    private volatile boolean rotating, moving, running, renderFinished;
    private volatile boolean objectRotationMode;
    private volatile boolean axesVisible;
    private volatile boolean objectScalingMode;

    private final List<ScanMesh> objects;
    private volatile ScanMesh selectedObject;
    private final Map<Texture, Integer> textureIds = new HashMap<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private Thread renderThread;

    public OpenGLRenderer(List<ScanMesh> objects) {
        this.objects = new CopyOnWriteArrayList<>(objects);
        // Select first editable (non-locked) object by default
        for (ScanMesh o : objects) {
            if (!o.isLocked()) {
                selectedObject = o;
                break;
            }
        }
    }

    public OpenGLRenderer(ScanMesh object) {
        this.objects = new CopyOnWriteArrayList<>(List.of(object));
        if (!object.isLocked()) selectedObject = object;
    }

    public ScanMesh getSelectedObject() {
        return selectedObject;
    }

    public void setSelectedObject(ScanMesh object) {
        if (objects.contains(object) && !object.isLocked()) selectedObject = object;
    }

    @Override
    public void setObjectRotationEnabled(boolean enabled) {
        objectRotationMode = enabled;
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
        if (!GLFWManager.isOwnerThread())
            throw new IllegalStateException("GLFW window creation must happen on the JVM main thread");

        if (window != 0) return;

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(windowWidth, windowHeight, "3D Renderer", 0, 0);
        if (window == 0) throw new IllegalStateException("Unable to create GLFW window");

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

        GLFW.glfwSetScrollCallback(window,
                (w, x, y) -> {
                    float amount = (float) -Math.signum(y) * .15f;
                    if (objectScalingMode && selectedObject != null && !selectedObject.isLocked())
                        selectedObject.setScale(selectedObject.getScale() + amount);
                    else
                        zoom(amount);
                });

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                running = false;
                return;
            }

                if (selectedObject == null || selectedObject.isLocked() ||
                    (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT)) return;

            float amount = .05f;

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
            double[] x = new double[1], y = new double[1];

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    rotating = true;
                    GLFW.glfwGetCursorPos(window, x, y);
                    lastMouseX = mousePressX = x[0];
                    lastMouseY = mousePressY = y[0];
                } else if (action == GLFW.GLFW_RELEASE) {
                    rotating = false;
                    GLFW.glfwGetCursorPos(window, x, y);

                    double dx = x[0] - mousePressX, dy = y[0] - mousePressY;
                    if (Math.hypot(dx, dy) < 5) selectObjectAt(x[0], y[0]);
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
                if (objectRotationMode && selectedObject != null && !selectedObject.isLocked())
                    selectedObject.rotate(-dy * .5f, dx * .5f, 0);
                else
                    rotate(-dy * .5f, dx * .5f, 0);
            }

            if (moving) move(dx * .005f, -dy * .005f, 0);

            lastMouseX = x;
            lastMouseY = y;
        });

        GLFW.glfwSetWindowCloseCallback(window, w -> running = false);
    }

    private void selectObjectAt(double mouseX, double mouseY) {
        ScanMesh closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (ScanMesh object : objects) {
            if (object.isLocked()) continue;
            double[] screen = projectObjectCenter(object);
            if (screen == null) continue;

            double distance = Math.hypot(screen[0] - mouseX, screen[1] - mouseY);
            double radius = Math.max(20,
                    calculateProjectedRadius(object, calculateMarkerSize(object)));

            if (distance <= radius && distance < closestDistance) {
                closest = object;
                closestDistance = distance;
            }
        }

        if (closest != null) selectedObject = closest;
    }

    private double[] projectObjectCenter(ScanMesh object) {
        float px = selectedObject != null ? selectedObject.getPositionX() : 0;
        float py = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pz = selectedObject != null ? selectedObject.getPositionZ() : 0;

        float[] point = rotatePoint(object.getPositionX() - px,
                object.getPositionY() - py,
                object.getPositionZ() - pz);

        float cameraX = point[0] + px + positionX;
        float cameraY = point[1] + py + positionY;
        float cameraZ = point[2] + pz + positionZ - cameraDistance;

        if (cameraZ >= -.1f) return null;

        float aspect = (float) windowWidth / Math.max(1, windowHeight);
        float tan = (float) Math.tan(Math.toRadians(60) / 2);

        double ndcX = cameraX / (-cameraZ * tan * aspect);
        double ndcY = cameraY / (-cameraZ * tan);

        return new double[]{
                (ndcX + 1) * windowWidth / 2,
                (1 - ndcY) * windowHeight / 2
        };
    }

    private float calculateProjectedRadius(ScanMesh object, float radius) {
        if (projectObjectCenter(object) == null) return 20;

        float depth = calculateObjectDepth(object);
        if (depth <= .1f) return 20;

        float pixelsPerUnit = windowHeight /
                (2 * (float) Math.tan(Math.toRadians(60) / 2) * depth);

        return radius * pixelsPerUnit;
    }

    private float calculateObjectDepth(ScanMesh object) {
        float px = selectedObject != null ? selectedObject.getPositionX() : 0;
        float py = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pz = selectedObject != null ? selectedObject.getPositionZ() : 0;

        float[] point = rotatePoint(object.getPositionX() - px,
                object.getPositionY() - py,
                object.getPositionZ() - pz);

        return -(point[2] + pz + positionZ - cameraDistance);
    }

    private float[] rotatePoint(float x, float y, float z) {
        double ax = Math.toRadians(rotationX);
        double ay = Math.toRadians(rotationY);
        double az = Math.toRadians(rotationZ);

        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
        float cy = (float) Math.cos(ay), sy = (float) Math.sin(ay);
        float cz = (float) Math.cos(az), sz = (float) Math.sin(az);

        float y1 = y * cx - z * sx;
        float z1 = y * sx + z * cx;

        float x2 = x * cy + z1 * sy;
        float z2 = -x * sy + z1 * cy;

        return new float[]{x2 * cz - y1 * sz, x2 * sz + y1 * cz, z2};
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
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{2, 3, 4, 1});
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{1, 1, 1, 1});
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{.2f, .2f, .2f, 1});
    }

    private void processCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) command.run();
    }

    @Override
    public void close() {
        running = false;
        if (GLFWManager.isInitialized()) GLFW.glfwPostEmptyEvent();
    }

    void requestCloseFromManager() {
        running = false;
    }

    boolean isRenderFinished() {
        return renderFinished;
    }

    void destroyWindowOnMainThread() {
        if (!GLFWManager.isOwnerThread())
            throw new IllegalStateException("Window destruction must happen on the GLFW main thread");

        if (window == 0 || !renderFinished) return;

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
        cameraDistance = 5;
        positionX = positionY = positionZ = 0;
        rotationX = 25;
        rotationY = 35;
        rotationZ = 0;
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

        GL11.glClearColor(.1f, .1f, .1f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        float px = selectedObject != null ? selectedObject.getPositionX() : 0;
        float py = selectedObject != null ? selectedObject.getPositionY() : 0;
        float pz = selectedObject != null ? selectedObject.getPositionZ() : 0;

        GL11.glTranslatef(positionX, positionY, positionZ - cameraDistance);
        GL11.glTranslatef(px, py, pz);
        GL11.glRotatef(rotationX, 1, 0, 0);
        GL11.glRotatef(rotationY, 0, 1, 0);
        GL11.glRotatef(rotationZ, 0, 0, 1);
        GL11.glTranslatef(-px, -py, -pz);

        for (ScanMesh object : objects) drawObject(object);
        if (objectRotationMode && selectedObject != null) drawRotationAxis(selectedObject);

        GLFW.glfwSwapBuffers(window);
    }

    private void drawObject(ScanMesh object) {
        GL11.glPushMatrix();

        GL11.glTranslatef(object.getPositionX(), object.getPositionY(), object.getPositionZ());
        GL11.glScalef(object.getScale(), object.getScale(), object.getScale());
        GL11.glRotatef(object.getRotationX(), 1, 0, 0);
        GL11.glRotatef(object.getRotationY(), 0, 1, 0);
        GL11.glRotatef(object.getRotationZ(), 0, 0, 1);

        drawMesh(object);
        if (object == selectedObject) drawSelectionMarker(object);

        GL11.glPopMatrix();
    }

    private void drawRotationAxis(ScanMesh object) {
        float length = calculateMarkerSize(object) * 1.5f;

        GL11.glPushMatrix();
        GL11.glTranslatef(object.getPositionX(), object.getPositionY(), object.getPositionZ());
        GL11.glScalef(object.getScale(), object.getScale(), object.getScale());
        GL11.glRotatef(object.getRotationX(), 1, 0, 0);
        GL11.glRotatef(object.getRotationY(), 0, 1, 0);
        GL11.glRotatef(object.getRotationZ(), 0, 0, 1);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(3);

        GL11.glBegin(GL11.GL_LINES);
        axis(length, 0);
        axis(length, 1);
        axis(length, 2);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    private void axis(float l, int axis) {
        float s = Math.max(.05f, l * .08f);

        if (axis == 0) {
            GL11.glColor3f(1, 0, 0);
            GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(l, 0, 0);
            GL11.glVertex3f(l, 0, 0); GL11.glVertex3f(l - s, s, 0);
            GL11.glVertex3f(l, 0, 0); GL11.glVertex3f(l - s, -s, 0);
        } else if (axis == 1) {
            GL11.glColor3f(0, 1, 0);
            GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(0, l, 0);
            GL11.glVertex3f(0, l, 0); GL11.glVertex3f(s, l - s, 0);
            GL11.glVertex3f(0, l, 0); GL11.glVertex3f(-s, l - s, 0);
        } else {
            GL11.glColor3f(0, 0, 1);
            GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(0, 0, l);
            GL11.glVertex3f(0, 0, l); GL11.glVertex3f(s, 0, l - s);
            GL11.glVertex3f(0, 0, l); GL11.glVertex3f(-s, 0, l - s);
        }
    }

    private void drawSelectionMarker(ScanMesh object) {
        float s = calculateMarkerSize(object);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(1, 1, 0);
        GL11.glLineWidth(2);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(-s, -s, -s); GL11.glVertex3f(s, -s, -s);
        GL11.glVertex3f(s, s, -s); GL11.glVertex3f(-s, s, -s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(-s, -s, s); GL11.glVertex3f(s, -s, s);
        GL11.glVertex3f(s, s, s); GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(-s, -s, -s); GL11.glVertex3f(-s, -s, s);
        GL11.glVertex3f(s, -s, -s); GL11.glVertex3f(s, -s, s);
        GL11.glVertex3f(s, s, -s); GL11.glVertex3f(s, s, s);
        GL11.glVertex3f(-s, s, -s); GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private float calculateMarkerSize(ScanMesh object) {
        float[] v = object.getVertices();
        if (v == null || v.length < 3) return .5f;

        float max = 0;
        for (int i = 0; i < v.length; i += 3)
            max = Math.max(max, (float) Math.sqrt(v[i] * v[i] + v[i + 1] * v[i + 1] + v[i + 2] * v[i + 2]));

        return Math.max(.5f, max * 1.2f);
    }

    private void updateViewport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, w, h);
            framebufferWidth = Math.max(1, w.get(0));
            framebufferHeight = Math.max(1, h.get(0));
        }

        int w = framebufferWidth, h = framebufferHeight;
        if (w == lastViewportWidth && h == lastViewportHeight) return;

        lastViewportWidth = w;
        lastViewportHeight = h;
        GL11.glViewport(0, 0, w, h);
        updateProjection(w, h);
    }

    private void loadTextures() {
        for (ScanMesh object : objects) {
            Material[] materials = object.getVertexMaterials();
            if (materials == null) continue;

            for (Material material : materials) {
                if (material == null || !material.hasTexture()) continue;

                Texture texture = material.getTexture();
                if (!textureIds.containsKey(texture))
                    textureIds.put(texture, createTexture(texture));
            }
        }
    }

    private int createTexture(Texture texture) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);

            ByteBuffer image = STBImage.stbi_load_from_memory(
                    ByteBuffer.wrap(texture.getData()), w, h, channels, 4);

            if (image == null) {
                GL11.glDeleteTextures(id);
                throw new IllegalStateException("Failed to load texture: " +
                        STBImage.stbi_failure_reason());
            }

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    w.get(0), h.get(0), 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, image);

            STBImage.stbi_image_free(image);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }

    private void updateProjection(int width, int height) {
        if (height <= 0) return;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / height;
        float near = .1f, far = 100f, fov = 60f;
        float y = (float) (1 / Math.tan(Math.toRadians(fov / 2)));
        float x = y / aspect;

        GL11.glFrustum(-near * x, near * x, -near * y, near * y, near, far);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void drawMesh(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();
        float[] uvs = mesh.getTextureCoordinates();
        Material[] materials = mesh.getVertexMaterials();

        if (vertices == null || indices == null) return;

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int i1 = indices[i], i2 = indices[i + 1], i3 = indices[i + 2];

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

    private void drawVertex(float[] vertex, Material material, float[] uvs,
                            int triangleIndex, int vertexIndex) {
        if (material == null) {
            GL11.glColor4f(.7f, .7f, .7f, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } else {
            float[] color = material.getDiffuseColor();
            GL11.glColor4f(color[0], color[1], color[2], color.length > 3 ? color[3] : 1);

            if (material.hasTexture()) {
                bindTexture(material.getTexture());

                if (uvs != null) {
                    int uv = triangleIndex * 2 + vertexIndex * 2;
                    if (uv + 1 < uvs.length)
                        GL11.glTexCoord2f(uvs[uv], 1 - uvs[uv + 1]);
                }
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
        }

        GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
    }

    private float[] getVertex(float[] vertices, int index) {
        int offset = index * 3;
        if (offset < 0 || offset + 2 >= vertices.length) return new float[]{0, 0, 0};
        return new float[]{vertices[offset], vertices[offset + 1], vertices[offset + 2]};
    }

    private Material getMaterial(Material[] materials, int index) {
        return materials != null && index >= 0 && index < materials.length
                ? materials[index] : null;
    }

    private void bindTexture(Texture texture) {
        Integer id = textureIds.get(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id == null ? 0 : id);
    }

    private void cleanupOpenGL() {
        for (int id : textureIds.values()) GL11.glDeleteTextures(id);
        textureIds.clear();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance = Math.clamp(cameraDistance + amount, .5f, 20f);
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

    @Override
    public boolean isObjectRotationEnabled() {
        return objectRotationMode;
    }

    @Override
    public void setAxesVisible(boolean visible) {
        axesVisible = visible;
    }

    @Override
    public boolean isAxesVisible() {
        return axesVisible;
    }

    @Override
    public void setObjectScalingEnabled(boolean enabled) {
        objectScalingMode = enabled;
    }

    @Override
    public boolean isObjectScalingEnabled() {
        return objectScalingMode;
    }

    @Override
    public void addMeshes(List<ScanMesh> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return;
        }

        objects.addAll(meshes);

        commands.add(() -> {
            for (ScanMesh object : meshes) {
                Material[] materials = object.getVertexMaterials();
                if (materials == null) continue;

                for (Material material : materials) {
                    if (material == null || !material.hasTexture()) continue;

                    Texture texture = material.getTexture();
                    if (!textureIds.containsKey(texture))
                        textureIds.put(texture, createTexture(texture));
                }
            }
        });
    }
}