package org.abc.controller;

import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class FileUploadController {

    private File selectedFile;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleChooseFile() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select 3D Scan");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OBJ/3MF Files", "*.obj", "*.3mf")
        );

        selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            stage.close();
        }
    }

    public File getSelectedFile() {
        return selectedFile;
    }
}