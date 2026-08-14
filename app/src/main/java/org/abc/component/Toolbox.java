package org.abc.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.abc.controller.ToolboxController;

import java.io.IOException;
import java.util.function.Consumer;

public class Toolbox {

    public Parent create(Consumer<java.io.File> onFileSelected) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/abc/fxml/toolbox.fxml")
        );

        Parent root = loader.load();

        ToolboxController controller = loader.getController();
        controller.setOnFileSelected(onFileSelected);

        return root;
    }
}