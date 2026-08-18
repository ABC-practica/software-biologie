package org.abc.service;

import org.abc.model.ScanMesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ObjLoader implements Loader {

    @Override
    public ScanMesh load(Path path) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");

                switch (parts[0]) {
                    case "v" -> {
                        vertices.add(new float[]{
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        });
                    }

                    case "f" -> {
                        if (parts.length < 4) {
                            continue;
                        }

                        // Convert the face vertices to OBJ vertex indices.
                        int[] face = new int[parts.length - 1];

                        for (int i = 1; i < parts.length; i++) {
                            String[] faceData = parts[i].split("/");

                            int index = Integer.parseInt(faceData[0]);

                            // OBJ supports negative indices.
                            if (index < 0) {
                                index = vertices.size() + index;
                            } else {
                                // OBJ indices are 1-based.
                                index--;
                            }

                            face[i - 1] = index;
                        }

                        // Triangulate the face.
                        for (int i = 1; i < face.length - 1; i++) {
                            indices.add(face[0]);
                            indices.add(face[i]);
                            indices.add(face[i + 1]);
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