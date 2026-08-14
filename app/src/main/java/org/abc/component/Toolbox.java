package org.abc.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.abc.controller.ToolboxController;
import org.abc.service.OpenGLWindow;

import java.io.IOException;
import java.util.function.Consumer;
import java.io.File;

public class Toolbox {

    private ToolboxController lastController;

    public Parent create(
            Consumer<File> fileSelected,
            OpenGLWindow window
    ) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/org/abc/fxml/toolbox.fxml"
                )
        );

        Parent root = loader.load();

        ToolboxController controller =
                loader.getController();

        controller.setFileSelected(fileSelected);
        controller.setWindow(window);

        lastController = controller;

        return root;
    }

    public ToolboxController getController() {
        return lastController;
    }
}