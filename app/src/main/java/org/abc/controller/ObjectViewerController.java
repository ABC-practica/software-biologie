package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.abc.component.FileUploadModal;
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

    private final List<RenderStrategy> renderers = new ArrayList<>();

    private ToolboxController toolboxController;
    private ScanMesh skeletonMesh;

    @FXML
    private void initialize() {
        System.out.println(
                "[INFO] ObjectViewerController initialized successfully."
        );

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
                throw new RuntimeException(
                        "Failed to load toolbox",
                        e
                );
            }
        }
        
        // Preload skeleton
        preloadSkeleton();
    }
    
    private void preloadSkeleton() {
        try {
            String skeletonPath = "org/abc/models/skeleton.obj";
            var resource = getClass().getClassLoader().getResource(skeletonPath);
            
            if (resource != null) {
                File skeletonFile = new File(resource.getPath());
                if (skeletonFile.exists()) {
                    System.out.println("[INFO] Preloading skeleton from: " + skeletonFile.getAbsolutePath());
                    
                    Loader loader = new ObjLoader();
                    skeletonMesh = loader.load(skeletonFile.toPath());
                    skeletonMesh = MeshNormalizer.normalize(skeletonMesh);
                    skeletonMesh.setLocked(true); // Mark as locked (non-editable)
                    
                    List<ScanMesh> meshes = new ArrayList<>();
                    meshes.add(skeletonMesh);
                    
                    try {
                        RenderStrategy newRenderer = RenderStrategyFactory.createRenderer(meshes);
                        renderer = newRenderer;
                        renderers.add(newRenderer);
                        newRenderer.embedIn(viewportContainer);
                        
                        if (toolboxController != null) {
                            toolboxController.setWindow(newRenderer);
                        }
                        
                        if (emptyStateView != null) {
                            emptyStateView.setVisible(false);
                            emptyStateView.setManaged(false);
                        }
                        
                        updateUIState(
                                "skeleton.obj",
                                "Skeleton preloaded. Ready for additional models."
                        );
                        
                        System.out.println("[INFO] Skeleton preloaded successfully.");
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to render preloaded skeleton: " + e.getMessage());
                        e.printStackTrace();
                        updateUIState(
                                null,
                                "System Ready. No model loaded."
                        );
                    }
                } else {
                    System.out.println("[WARNING] Skeleton file not found at: " + skeletonFile.getAbsolutePath());
                    updateUIState(
                            null,
                            "System Ready. No model loaded."
                    );
                }
            } else {
                System.out.println("[WARNING] Skeleton resource not found.");
                updateUIState(
                        null,
                        "System Ready. No model loaded."
                );
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to preload skeleton: " + e.getMessage());
            e.printStackTrace();
            updateUIState(
                    null,
                    "System Ready. No model loaded."
            );
        }
    }

    @FXML
    private void handleUpload() {
        try {
            Window owner = viewportContainer.getScene().getWindow();

            FileUploadModal modal = new FileUploadModal();
            List<File> files = modal.show(owner);

            if (files != null && !files.isEmpty()) {
                handleFilesSelected(files);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to open file upload modal",
                    e
            );
        }
    }
    public void handleFileSelected(File file) {
        if (file == null) {
            return;
        }

        handleFilesSelected(List.of(file));
    }

    private void handleFilesSelected(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        if (emptyStateView != null) {
            emptyStateView.setVisible(false);
            emptyStateView.setManaged(false);
        }

        List<ScanMesh> userMeshes = new ArrayList<>();
        List<File> loadedFiles = new ArrayList<>();

        for (File file : files) {
            try {
                System.out.println(
                        "[INFO] Loading 3D file: "
                                + file.getAbsolutePath()
                );

                Loader loader = getLoader(file);

                ScanMesh mesh = loader.load(file.toPath());
                mesh = MeshNormalizer.normalize(mesh);

                userMeshes.add(mesh);
                loadedFiles.add(file);

                System.out.println(
                        "[INFO] Loaded: "
                                + file.getName()
                );

            } catch (Exception e) {
                System.err.println(
                        "[ERROR] Failed to load 3D file: "
                                + file.getName()
                                + ": "
                                + e.getMessage()
                );
                e.printStackTrace();
            }
        }

        if (userMeshes.isEmpty()) {
            updateUIState(
                    null,
                    "No models could be loaded."
            );
            return;
        }

        // Position only the uploaded meshes so skeleton remains fixed
        positionMeshes(userMeshes);

        try {
            if (renderer != null) {
                renderer.addMeshes(userMeshes);

                if (toolboxController != null) {
                    toolboxController.setWindow(renderer);
                }

            } else {
                List<ScanMesh> renderMeshes = new ArrayList<>();
                if (skeletonMesh != null) {
                    renderMeshes.add(skeletonMesh);
                }

                renderMeshes.addAll(userMeshes);

                RenderStrategy newRenderer = RenderStrategyFactory.createRenderer(renderMeshes);

                renderer = newRenderer;
                renderers.add(newRenderer);

                newRenderer.embedIn(viewportContainer);

                if (toolboxController != null) {
                    toolboxController.setWindow(newRenderer);
                }

                if (emptyStateView != null) {
                    emptyStateView.setVisible(false);
                    emptyStateView.setManaged(false);
                }
            }

            int vertexCount = 0;
            int faceCount = 0;

            for (ScanMesh mesh : userMeshes) {
                if (mesh.getVertices() != null) {
                    vertexCount += mesh.getVertices().length / 3;
                }

                if (mesh.getIndices() != null) {
                    faceCount += mesh.getIndices().length / 3;
                }
            }

            String fileLabel;

            if (loadedFiles.size() == 1) {
                fileLabel = loadedFiles.get(0).getName();
            } else {
                fileLabel = loadedFiles.size() + " models loaded";
            }

            updateUIState(
                    fileLabel,
                    String.format(
                            "Loaded %d of %d selected models | Vertices: %,d | Faces: %,d | Left drag: rotate object, Right drag: pan, Scroll: zoom",
                            loadedFiles.size(),
                            files.size(),
                            vertexCount,
                            faceCount
                    )
            );

        } catch (Exception e) {
            System.err.println(
                    "[ERROR] Failed to process renderer: "
                            + e.getMessage()
            );
            e.printStackTrace();

            updateUIState(
                    null,
                    "Failed to process renderer: "
                            + e.getMessage()
            );
        }
    }

    private void positionMeshes(List<ScanMesh> meshes) {
        float spacing = 0.5f;
        float currentX = 0.0f;

        for (ScanMesh mesh : meshes) {
            float width = getMeshWidth(mesh);

            mesh.setPosition(
                    currentX + width / 2.0f,
                    0.0f,
                    0.0f
            );

            currentX += width + spacing;
        }

        float totalWidth = currentX - spacing;

        for (ScanMesh mesh : meshes) {
            mesh.move(
                    -totalWidth / 2.0f,
                    0.0f,
                    0.0f
            );
        }
    }

    private float getMeshWidth(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();

        if (vertices == null || vertices.length < 3) {
            return 1.0f;
        }

        float minX = vertices[0];
        float maxX = vertices[0];

        for (int i = 3; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
        }

        return Math.max(0.5f, maxX - minX);
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

    @FXML
    private void handleClose() {
        stopRenderers();

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
        for (RenderStrategy currentRenderer : renderers) {
            currentRenderer.refresh();
        }

        if (statusLabel != null) {
            statusLabel.setText(
                    "Viewport refreshed."
            );
        }
    }

    @FXML
    private void handleResetCamera() {
        for (RenderStrategy currentRenderer : renderers) {
            currentRenderer.resetCamera();
        }

        if (statusLabel != null) {
            statusLabel.setText(
                    "Camera reset to default view."
            );
        }
    }

    private void stopRenderers() {
        for (RenderStrategy currentRenderer : renderers) {
            try {
                currentRenderer.close();
            } catch (Exception e) {
                System.err.println(
                        "[WARN] Failed to close renderer: "
                                + e.getMessage()
                );
            }
        }

        renderers.clear();
        renderer = null;
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

        if (statusLabel != null
                && statusMessage != null) {
            statusLabel.setText(statusMessage);
        }
    }

    public RenderStrategy getRenderer() {
        return renderer;
    }
}