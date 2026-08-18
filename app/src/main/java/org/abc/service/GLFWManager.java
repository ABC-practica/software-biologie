package org.abc.service;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GLFWManager {

    private static boolean initialized = false;
    private static Thread ownerThread;
    private static AnimationTimer animationTimer;

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

        startEventPump();
    }

    private static void startEventPump() {
        if (Platform.isFxApplicationThread()) {
            setupAnimationTimer();
        } else {
            Platform.runLater(GLFWManager::setupAnimationTimer);
        }
    }

    private static void setupAnimationTimer() {
        if (animationTimer != null) {
            return;
        }
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                pollEvents();
            }
        };
        animationTimer.start();
    }

    public static void pollEvents() {
        if (!initialized) {
            return;
        }

        processCommands();

        GLFW.glfwPollEvents();

        for (OpenGLRenderer renderer : renderers) {
            if (renderer.isRenderFinished()) {
                renderer.destroyWindowOnMainThread();
                renderers.remove(renderer);
            }
        }

        if (shutdownRequested && renderers.isEmpty()) {
            terminate();
        }
    }

    public static void runMainLoop() {
        // Event polling is handled asynchronously via FX AnimationTimer loop
    }

    public static void execute(Runnable command) {
        commands.add(command);

        if (initialized) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public static void register(OpenGLRenderer renderer) {
        renderers.add(renderer);
    }

    public static void unregister(OpenGLRenderer renderer) {
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

        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }

        processCommands();

        for (OpenGLRenderer renderer : renderers) {
            if (renderer.isRenderFinished()) {
                renderer.destroyWindowOnMainThread();
            }
        }

        renderers.clear();

        GLFW.glfwTerminate();

        initialized = false;
        ownerThread = null;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    public static synchronized boolean isOwnerThread() {
        return initialized
                && (ownerThread == null || Thread.currentThread() == ownerThread || Platform.isFxApplicationThread());
    }

    private static void processCommands() {
        Runnable command;

        while ((command = commands.poll()) != null) {
            command.run();
        }
    }
}