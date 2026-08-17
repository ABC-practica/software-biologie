package org.abc.service;

import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GLFWManager {

    private static boolean initialized = false;
    private static Thread ownerThread;

    private static final Queue<Runnable> commands =
            new ConcurrentLinkedQueue<>();

    private static final Set<OpenGLRenderer> renderers =
            ConcurrentHashMap.newKeySet();

    private static volatile boolean shutdownRequested;

    private GLFWManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        ownerThread = Thread.currentThread();

        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "GLFW must be initialized on the JVM main thread"
            );
        }

        GLFW.glfwInitHint(
                GLFW.GLFW_WAYLAND_LIBDECOR,
                GLFW.GLFW_WAYLAND_DISABLE_LIBDECOR
        );

        if (!GLFW.glfwInit()) {
            ownerThread = null;

            throw new IllegalStateException(
                    "Unable to initialize GLFW"
            );
        }

        initialized = true;
        shutdownRequested = false;
    }

    public static void runMainLoop() {

        requireOwnerThread();

        while (!shutdownRequested || !renderers.isEmpty()) {

            processCommands();

            GLFW.glfwPollEvents();

            for (OpenGLRenderer renderer : renderers) {

                if (renderer.isRenderFinished()) {
                    renderer.destroyWindowOnMainThread();
                    renderers.remove(renderer);
                }
            }

            if (shutdownRequested && renderers.isEmpty()) {
                break;
            }

            Thread.yield();
        }

        processCommands();

        for (OpenGLRenderer renderer : renderers) {

            if (renderer.isRenderFinished()) {
                renderer.destroyWindowOnMainThread();
            }
        }

        renderers.clear();

        terminate();
    }

    public static void execute(Runnable command) {

        commands.add(command);

        if (initialized) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public static void register(
            OpenGLRenderer renderer
    ) {
        requireOwnerThread();

        renderers.add(renderer);
    }

    public static void unregister(
            OpenGLRenderer renderer
    ) {
        renderers.remove(renderer);
    }

    public static void requestShutdown() {

        shutdownRequested = true;

        for (OpenGLRenderer renderer : renderers) {
            renderer.requestCloseFromManager();
        }

        if (initialized) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public static synchronized void terminate() {

        if (!initialized) {
            return;
        }

        requireOwnerThread();

        GLFW.glfwTerminate();

        initialized = false;
        ownerThread = null;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    public static synchronized boolean isOwnerThread() {
        return initialized
                && Thread.currentThread() == ownerThread;
    }

    private static void processCommands() {

        Runnable command;

        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    private static void requireOwnerThread() {

        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "GLFW operation must run on the JVM main thread. "
                            + "Owner: "
                            + ownerThread
                            + ", current: "
                            + Thread.currentThread()
            );
        }
    }
}