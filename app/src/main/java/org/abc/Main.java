package org.abc;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.abc.service.GLFWManager;

public class Main {

    public static void main(String[] args) {
        GLFWManager.initialize();

        Platform.startup(() -> {
            try {
                App app = new App();
                app.start(new Stage());
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to start JavaFX",
                        e
                );
            }
        });

        GLFWManager.runMainLoop();
    }
}