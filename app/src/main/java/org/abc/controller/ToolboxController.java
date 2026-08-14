package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.abc.component.FileUploadModal;
import org.abc.service.OpenGLWindow;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class ToolboxController {

    @FXML
    private VBox root;

    private Consumer<File> fileSelected;
    private OpenGLWindow window;

    public void setFileSelected(Consumer<File> fileSelected) {
        this.fileSelected = fileSelected;
    }

    public void setWindow(OpenGLWindow window) {
        this.window = window;
    }

    @FXML
    private void handleUpload() {
        try {
            Window owner = root.getScene().getWindow();

            FileUploadModal modal = new FileUploadModal();
            File file = modal.show(owner);

            if (file != null && fileSelected != null) {
                fileSelected.accept(file);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to open file upload modal",
                    e
            );
        }
    }

    @FXML
    private void handleClose() {
        if (window != null) {
            window.close();

            window = null;
        }
    }

    @FXML
    private void handleRefresh() {
        if (window != null) {
            window.refresh();
        }
    }

    @FXML
    private void handleResetCamera() {
        if (window != null) {
            window.resetCamera();
        }
    }
}