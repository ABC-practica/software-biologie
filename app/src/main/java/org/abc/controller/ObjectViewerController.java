package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import org.abc.component.Toolbox;
import org.abc.model.ScanMesh;
import org.abc.service.Loader;
import org.abc.service.ObjLoader;
import org.abc.service.RenderStrategy;
import org.abc.service.RenderStrategyFactory;
import org.abc.service.RendererControl;
import org.abc.service.ThreeMfLoader;
import org.abc.util.MeshNormalizer;

import java.io.File;
import java.io.IOException;

public class ObjectViewerController {

    @FXML
    private VBox toolbox;

    private RenderStrategy renderer;

    private ToolboxController toolboxController;

    @FXML
    private void initialize() {
        System.out.println("[INFO] ObjectViewerController initializing...");
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

            System.out.println("[INFO] ObjectViewerController initialized successfully.");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load toolbox component: " + e.getMessage());
            throw new RuntimeException(
                    "Failed to load toolbox",
                    e
            );
        }
    }

    private void handleFileSelected(File file) {
        System.out.println("[INFO] Selected 3D file: " + (file != null ? file.getAbsolutePath() : "null"));
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

        RenderStrategy newRenderer =
                RenderStrategyFactory.createRenderer(mesh);

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