package org.abc.controller;

import javafx.fxml.FXML;
import javafx.stage.Window;
import org.abc.component.FileUploadModal;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class ToolboxController {

    private final FileUploadModal fileUploadModal = new FileUploadModal();

    private Consumer<File> onFileSelected;

    public void setOnFileSelected(Consumer<File> onFileSelected) {
        this.onFileSelected = onFileSelected;
    }

    @FXML
    private void handleUpload() {
        try {
            Window window = uploadButton.getScene().getWindow();

            File file = fileUploadModal.show(window);

            if (file != null && onFileSelected != null) {
                onFileSelected.accept(file);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private javafx.scene.control.Button uploadButton;
}