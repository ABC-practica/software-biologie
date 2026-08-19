package org.abc.service;

import org.abc.model.ScanMesh;
import java.util.List;

public interface RenderStrategy extends RendererControl, Movable, Rotatable, Zoomable, Renderer {

    default void embedIn(javafx.scene.layout.Pane container) {
        open();
    }

    default void setObjectRotationMode(boolean enabled) {
    }

    default void setObjectScalingEnabled(boolean enabled) {
    }

    default boolean isObjectScalingEnabled() {
        return false;
    }

    default void addMeshes(List<ScanMesh> meshes) {
        throw new UnsupportedOperationException(
                "addMeshes is not supported by this renderer"
        );
    }
}