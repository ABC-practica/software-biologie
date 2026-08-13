package org.abc.service;

import org.abc.model.ScanMesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ObjLoader implements Loader {

    public ScanMesh load(Path path) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length == 0) {
                    continue;
                }

                switch (parts[0]) {
                    case "v" -> vertices.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    });

                    case "f" -> {
                        for (int i = 1; i < parts.length; i++) {
                            String[] faceData = parts[i].split("/");
                            indices.add(Integer.parseInt(faceData[0]) - 1);
                        }
                    }
                }
            }
        }

        float[] vertexArray = new float[vertices.size() * 3];

        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i * 3] = vertices.get(i)[0];
            vertexArray[i * 3 + 1] = vertices.get(i)[1];
            vertexArray[i * 3 + 2] = vertices.get(i)[2];
        }

        int[] indexArray = indices.stream()
                .mapToInt(Integer::intValue)
                .toArray();

        return new ScanMesh(vertexArray, indexArray);
    }
}