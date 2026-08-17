package org.abc;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.abc.service.GLFWManager;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                App.class.getResource(
                        "/org/abc/fxml/object-viewer.fxml"
                )
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("3D Scan Toolbox");
        stage.setScene(scene);
        stage.setWidth(220);
        stage.setHeight(300);

        stage.setOnCloseRequest(event -> {
            GLFWManager.requestShutdown();
            Platform.exit();
        });

        stage.show();
    }

    @Override
    public void stop() {
        GLFWManager.requestShutdown();
    }
}