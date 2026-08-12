package org.abc.util;

import org.abc.model.ScanMesh;

public class MeshNormalizer {

    public static ScanMesh normalize(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();

        if (vertices.length == 0) {
            return mesh;
        }

        float minX = vertices[0];
        float minY = vertices[1];
        float minZ = vertices[2];

        float maxX = vertices[0];
        float maxY = vertices[1];
        float maxZ = vertices[2];

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);

            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        float size = Math.max(sizeX, Math.max(sizeY, sizeZ));

        if (size == 0.0f) {
            return mesh;
        }

        float[] normalizedVertices = new float[vertices.length];

        for (int i = 0; i < vertices.length; i += 3) {
            normalizedVertices[i] =
                    (vertices[i] - centerX) / size;

            normalizedVertices[i + 1] =
                    (vertices[i + 1] - centerY) / size;

            normalizedVertices[i + 2] =
                    (vertices[i + 2] - centerZ) / size;
        }

        return new ScanMesh(
                normalizedVertices,
                mesh.getIndices()
        );
    }
}