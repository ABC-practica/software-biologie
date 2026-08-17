package org.abc.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.abc.controller.FileUploadController;

import java.io.File;
import java.io.IOException;

public class FileUploadModal {

    public File show(Window owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/abc/fxml/file-upload.fxml")
        );

        Parent root = loader.load();

        FileUploadController controller = loader.getController();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Upload 3D Scan");
        stage.setScene(new Scene(root));

        controller.setStage(stage);
        stage.showAndWait();

        return controller.getSelectedFile();
    }
}