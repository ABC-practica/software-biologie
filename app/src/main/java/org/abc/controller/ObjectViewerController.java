package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import org.abc.component.Toolbox;
import org.abc.model.ScanMesh;
import org.abc.service.Loader;
import org.abc.service.ObjLoader;
import org.abc.service.OpenGLRenderer;
import org.abc.service.RendererControl;
import org.abc.service.ThreeMfLoader;
import org.abc.util.MeshNormalizer;

import java.io.File;
import java.io.IOException;

public class ObjectViewerController {

    @FXML
    private VBox toolbox;

    private RendererControl renderer;

    private ToolboxController toolboxController;

    @FXML
    private void initialize() {

        Toolbox component = new Toolbox();

        try {

            toolbox.getChildren().add(
                    component.create(
                            this::handleFileSelected,
                            null
                    )
            );

            toolboxController =
                    component.getController();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to load toolbox",
                    e
            );
        }
    }

    private void handleFileSelected(File file) {

        try {

            Loader loader =
                    getLoader(file);

            ScanMesh mesh =
                    loader.load(file.toPath());

            mesh =
                    MeshNormalizer.normalize(mesh);

            startRenderer(mesh);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    private Loader getLoader(File file) {

        String fileName =
                file.getName().toLowerCase();

        if (fileName.endsWith(".obj")) {
            return new ObjLoader();
        }

        if (fileName.endsWith(".3mf")) {
            return new ThreeMfLoader();
        }

        throw new IllegalArgumentException(
                "Unsupported file format: "
                        + fileName
        );
    }

    private void startRenderer(ScanMesh mesh) {

        stopRenderer();

        OpenGLRenderer newRenderer =
                new OpenGLRenderer(mesh);

        renderer = newRenderer;

        if (toolboxController != null) {
            toolboxController.setWindow(
                    newRenderer
            );
        }

        newRenderer.open();
    }

    private void stopRenderer() {

        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }
}