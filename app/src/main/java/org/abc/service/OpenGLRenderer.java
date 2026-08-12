package org.abc.service;

public class OpenGLRenderer implements Renderer, Runnable{
    @Override
    public void run() {
        initialize();

        while (!Thread.currentThread().isInterrupted()) {
            render();
        }

        cleanup();
    }

    @Override
    public void initialize() {
    }

    @Override
    public void render() {
    }

    @Override
    public void cleanup() {
    }
}