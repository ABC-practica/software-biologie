package org.abc;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("Hello JavaFX");

        Scene scene = new Scene(
                new StackPane(label),
                800,
                600
        );

        stage.setTitle("My JavaFX App");
        stage.setScene(scene);
        stage.show();
    }
}