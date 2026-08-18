package org.abc.service;

public interface RenderStrategy extends RendererControl, Movable, Rotatable, Zoomable, Renderer {

    default void embedIn(javafx.scene.layout.Pane container) {
        open();
    }

    default void setObjectRotationMode(boolean enabled) {
    }
}