package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.abc.component.Toolbox;
import org.abc.model.ScanMesh;
import org.abc.service.Loader;
import org.abc.service.ObjLoader;
import org.abc.service.OpenGLRenderer;
import org.abc.service.ThreeMfLoader;
import org.abc.util.MeshNormalizer;

import java.io.File;
import java.io.IOException;

public class ObjectViewerController {

    @FXML
    private StackPane toolboxContainer;

    @FXML
    private StackPane viewerContainer;

    private OpenGLRenderer renderer;
    private Thread renderThread;

    @FXML
    private void initialize() {
        Toolbox toolbox = new Toolbox();

        try {
            toolboxContainer.getChildren().add(
                    toolbox.create(this::handleFileSelected)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load toolbox", e);
        }
    }

    private void handleFileSelected(File file) {
        try {
            Loader loader = getLoader(file);

            ScanMesh mesh = loader.load(file.toPath());
            mesh = MeshNormalizer.normalize(mesh);

            startRenderer(mesh);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFile(File file) {
        try {
            Loader loader = getLoader(file);

            ScanMesh mesh = loader.load(file.toPath());
            mesh = MeshNormalizer.normalize(mesh);

            startRenderer(mesh);

        } catch (IOException e) {
            e.printStackTrace();
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

    private void startRenderer(ScanMesh mesh) {
        if (renderThread != null && renderThread.isAlive()) {
            renderThread.interrupt();
        }

        renderer = new OpenGLRenderer(mesh);

        renderThread = new Thread(renderer);
        renderThread.setDaemon(true);
        renderThread.start();
    }
}