package org.abc.util;

import org.abc.model.ScanMesh;

public class MeshNormalizer {

    public static ScanMesh normalize(ScanMesh mesh) {
        return normalizeWithData(mesh).getMesh();
    }

    public static NormalizedMesh normalizeWithData(ScanMesh mesh) {

        float[] vertices = mesh.getVertices();

        if (vertices == null || vertices.length == 0) {
            return new NormalizedMesh(
                    mesh,
                    new NormalizationData(0, 0, 0, 1)
            );
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

        float size = Math.max(
                maxX - minX,
                Math.max(
                        maxY - minY,
                        maxZ - minZ
                )
        );

        if (size == 0) {
            size = 1;
        }

        float[] normalizedVertices =
                new float[vertices.length];

        for (int i = 0; i < vertices.length; i += 3) {

            normalizedVertices[i] =
                    (vertices[i] - centerX) / size;

            normalizedVertices[i + 1] =
                    (vertices[i + 1] - centerY) / size;

            normalizedVertices[i + 2] =
                    (vertices[i + 2] - centerZ) / size;
        }

        return new NormalizedMesh(
                new ScanMesh(
                        normalizedVertices,
                        mesh.getIndices(),
                        mesh.getTextureCoordinates(),
                        mesh.getVertexMaterials()
                ),
                new NormalizationData(
                        centerX,
                        centerY,
                        centerZ,
                        size
                )
        );
    }

    public static ScanMesh normalize(
            ScanMesh mesh,
            NormalizationData data
    ) {

        float[] vertices = mesh.getVertices();

        if (vertices == null || vertices.length == 0) {
            return mesh;
        }

        float[] normalized =
                new float[vertices.length];

        for (int i = 0; i < vertices.length; i += 3) {

            normalized[i] =
                    (vertices[i] - data.centerX) / data.size;

            normalized[i + 1] =
                    (vertices[i + 1] - data.centerY) / data.size;

            normalized[i + 2] =
                    (vertices[i + 2] - data.centerZ) / data.size;
        }

        return new ScanMesh(
                normalized,
                mesh.getIndices(),
                mesh.getTextureCoordinates(),
                mesh.getVertexMaterials()
        );
    }

    public static ScanMesh translate(
            ScanMesh mesh,
            float x,
            float y,
            float z
    ) {

        float[] vertices = mesh.getVertices();

        if (vertices == null) {
            return mesh;
        }

        float[] translated = vertices.clone();

        for (int i = 0; i < translated.length; i += 3) {

            translated[i] += x;
            translated[i + 1] += y;
            translated[i + 2] += z;
        }

        return new ScanMesh(
                translated,
                mesh.getIndices(),
                mesh.getTextureCoordinates(),
                mesh.getVertexMaterials()
        );
    }

    public static class NormalizedMesh {

        private final ScanMesh mesh;
        private final NormalizationData data;

        public NormalizedMesh(
                ScanMesh mesh,
                NormalizationData data
        ) {
            this.mesh = mesh;
            this.data = data;
        }

        public ScanMesh getMesh() {
            return mesh;
        }

        public NormalizationData getData() {
            return data;
        }
    }

    public static class NormalizationData {

        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float size;

        public NormalizationData(
                float centerX,
                float centerY,
                float centerZ,
                float size
        ) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.size = size;
        }
    }
}