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
    public void start(Stage stage) {
        System.out.println("[INFO] App.start() invoked on thread: " + Thread.currentThread().getName());
        try {
            System.out.println("[INFO] Loading FXML resource: /org/abc/fxml/object-viewer.fxml");
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource(
                            "/org/abc/fxml/object-viewer.fxml"
                    )
            );

            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    App.class.getResource("/org/abc/css/style.css").toExternalForm()
            );
            System.out.println("[INFO] FXML & CSS loaded successfully. Setting up primary Stage...");

            stage.setTitle("3D Scan Studio");
            stage.setScene(scene);
            stage.setWidth(1100);
            stage.setHeight(750);
            stage.setMinWidth(750);
            stage.setMinHeight(500);

            stage.setOnCloseRequest(event -> {
                System.out.println("[INFO] Primary stage close requested.");
                GLFWManager.requestShutdown();
                Platform.exit();
            });

            stage.show();
            System.out.println("[INFO] Primary Stage shown successfully!");
        } catch (Exception e) {
            System.err.println("[FATAL] Error in App.start(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        GLFWManager.requestShutdown();
    }
}