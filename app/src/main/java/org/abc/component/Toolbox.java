package org.abc.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.abc.controller.ToolboxController;
import org.abc.service.RendererControl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class Toolbox {

    private ToolboxController lastController;

    public Parent create(
            Consumer<List<File>> filesSelected,
            RendererControl window
    ) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/org/abc/fxml/toolbox.fxml"
                )
        );

        Parent root = loader.load();

        ToolboxController controller =
                loader.getController();

        controller.setFilesSelected(filesSelected);
        controller.setWindow(window);

        lastController = controller;

        return root;
    }

    public ToolboxController getController() {
        return lastController;
    }
}