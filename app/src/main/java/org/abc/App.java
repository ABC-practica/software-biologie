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

        scene.getStylesheets().add(
                App.class.getResource("/org/abc/css/style.css").toExternalForm()
        );

        stage.setTitle("3D Scan Studio");
        stage.setScene(scene);
        stage.setWidth(1100);
        stage.setHeight(750);
        stage.setMinWidth(750);
        stage.setMinHeight(500);

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