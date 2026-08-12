package org.abc;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.abc.service.OpenGLRenderer;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/org/abc/fxml/object-viewer.fxml")
        );

        Scene scene = new Scene(loader.load());

        stage.setScene(scene);
        stage.show();

        OpenGLRenderer renderer = new OpenGLRenderer();

        Thread renderThread = new Thread(renderer);
        renderThread.setDaemon(true);
        renderThread.start();
    }
}