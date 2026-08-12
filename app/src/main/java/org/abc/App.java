package org.abc;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.abc.model.ScanMesh;
import org.abc.service.OpenGLRenderer;

import java.io.IOException;

public class App extends Application {
    private ScanMesh createPrism(int sides, float radius, float height) {
        float[] vertices = new float[sides * 2 * 3];
        int[] indices = new int[sides * 6];

        float halfHeight = height / 2.0f;

        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;

            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;

            vertices[i * 3] = x;
            vertices[i * 3 + 1] = -halfHeight;
            vertices[i * 3 + 2] = z;

            int topIndex = sides + i;

            vertices[topIndex * 3] = x;
            vertices[topIndex * 3 + 1] = halfHeight;
            vertices[topIndex * 3 + 2] = z;
        }

        int index = 0;

        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;

            int bottomCurrent = i;
            int bottomNext = next;
            int topCurrent = sides + i;
            int topNext = sides + next;

            indices[index++] = bottomCurrent;
            indices[index++] = bottomNext;
            indices[index++] = topNext;

            indices[index++] = topNext;
            indices[index++] = topCurrent;
            indices[index++] = bottomCurrent;
        }

        return new ScanMesh(vertices, indices);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/org/abc/fxml/object-viewer.fxml")
        );

        Scene scene = new Scene(loader.load());

        stage.setScene(scene);
        stage.show();

        ScanMesh mesh = createPrism(6, 1.5f, 2.0f);
        OpenGLRenderer renderer = new OpenGLRenderer(mesh);
        
        Thread renderThread = new Thread(renderer);
        renderThread.setDaemon(true);
        renderThread.start();
    }
}