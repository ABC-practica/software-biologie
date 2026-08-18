package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.abc.component.Toolbox;
import org.abc.model.ScanMesh;
import org.abc.service.Loader;
import org.abc.service.ObjLoader;
import org.abc.service.RenderStrategy;
import org.abc.service.RenderStrategyFactory;
import org.abc.service.ThreeMfLoader;
import org.abc.util.MeshNormalizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectViewerController {

    @FXML
    private StackPane viewportContainer;

    @FXML
    private VBox emptyStateView;

    @FXML
    private VBox toolbox;

    @FXML
    private Label activeFileLabel;

    @FXML
    private Label statusLabel;

    private RenderStrategy renderer;

    private ToolboxController toolboxController;

    @FXML
    private void initialize() {
        System.out.println("[INFO] ObjectViewerController initialized successfully.");

        if (toolbox != null) {
            Toolbox component = new Toolbox();

            try {
                toolbox.getChildren().add(
                        component.create(
                                this::handleFilesSelected,
                                null
                        )
                );

                toolboxController = component.getController();

            } catch (IOException e) {
                throw new RuntimeException("Failed to load toolbox", e);
            }
        }

        updateUIState(null, "System Ready. No model loaded.");
    }

    @FXML
    private void handleUpload() {
        Window owner = viewportContainer != null && viewportContainer.getScene() != null
                ? viewportContainer.getScene().getWindow()
                : null;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select 3D Scan File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "3D Mesh Files (*.obj, *.3mf)",
                        "*.obj",
                        "*.3mf"
                )
        );

        File file = fileChooser.showOpenDialog(owner);

        if (file != null) {
            handleFileSelected(file);
        }
    }

    public void handleFileSelected(File file) {
        if (file == null) {
            return;
        }

        System.out.println("[INFO] Selected 3D file: " + file.getAbsolutePath());

        try {
            Loader loader = getLoader(file);
            ScanMesh mesh = loader.load(file.toPath());
            mesh = MeshNormalizer.normalize(mesh);

            startRenderer(mesh, file);

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load 3D file: " + e.getMessage());
            e.printStackTrace();

            if (statusLabel != null) {
                statusLabel.setText("Error loading file: " + e.getMessage());
            }
        }
    }

    private void handleFilesSelected(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        for (File file : files) {
            try {
                Loader loader = getLoader(file);
                ScanMesh mesh = loader.load(file.toPath());
                mesh = MeshNormalizer.normalize(mesh);

                startRenderer(mesh, file);
                return;

            } catch (Exception e) {
                System.err.println(
                        "[ERROR] Failed to load 3D file: "
                                + file.getName()
                                + ": "
                                + e.getMessage()
                );
            }
        }
    }

    private Loader getLoader(File file) {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".obj")) {
            return new ObjLoader();
        }

        if (fileName.endsWith(".3mf")) {
            return new ThreeMfLoader();
        }

        throw new IllegalArgumentException(
                "Unsupported file format: " + fileName
        );
    }

    private void startRenderer(ScanMesh mesh, File file) {
        stopRenderer();

        if (emptyStateView != null) {
            emptyStateView.setVisible(false);
            emptyStateView.setManaged(false);
        }

        RenderStrategy newRenderer =
                RenderStrategyFactory.createRenderer(mesh);

        renderer = newRenderer;

        newRenderer.embedIn(viewportContainer);

        if (toolboxController != null) {
            toolboxController.setWindow(newRenderer);
        }

        int vertexCount =
                mesh.getVertices() != null
                        ? mesh.getVertices().length / 3
                        : 0;

        int faceCount =
                mesh.getIndices() != null
                        ? mesh.getIndices().length / 3
                        : 0;

        updateUIState(
                file.getName(),
                String.format(
                        "Active: %s | Vertices: %,d | Faces: %,d | Controls: Drag to rotate, Right drag to pan, Scroll to zoom",
                        file.getName(),
                        vertexCount,
                        faceCount
                )
        );
    }

    @FXML
    private void handleClose() {
        stopRenderer();

        if (emptyStateView != null) {
            emptyStateView.setVisible(true);
            emptyStateView.setManaged(true);
        }

        updateUIState(
                null,
                "System Ready. No model loaded."
        );
    }

    @FXML
    private void handleRefresh() {
        if (renderer != null) {
            renderer.refresh();

            if (statusLabel != null) {
                statusLabel.setText("Viewport refreshed.");
            }
        }
    }

    @FXML
    private void handleResetCamera() {
        if (renderer != null) {
            renderer.resetCamera();

            if (statusLabel != null) {
                statusLabel.setText("Camera reset to default view.");
            }
        }
    }

    private void stopRenderer() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    private void updateUIState(
            String fileName,
            String statusMessage
    ) {
        if (activeFileLabel != null) {
            activeFileLabel.setText(
                    fileName != null
                            ? fileName
                            : "No model loaded"
            );
        }

        if (statusLabel != null && statusMessage != null) {
            statusLabel.setText(statusMessage);
        }
    }

    public RenderStrategy getRenderer() {
        return renderer;
    }
}