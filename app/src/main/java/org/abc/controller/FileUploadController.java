package org.abc.controller;

import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class FileUploadController {

    private List<File> selectedFiles;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleChooseFile() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select 3D Scans");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "OBJ/3MF Files",
                        "*.obj",
                        "*.3mf"
                )
        );

        selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            stage.close();
        }
    }

    public List<File> getSelectedFiles() {
        return selectedFiles;
    }
}