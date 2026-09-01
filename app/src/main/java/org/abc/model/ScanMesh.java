package org.abc.model;

import org.abc.util.MeshSimplifier;

import java.util.concurrent.CompletableFuture;

public class ScanMesh {

    private volatile float[] vertices;
    private volatile int[] indices;
    private final Material[] vertexMaterials;
    private final float[] textureCoordinates;

    private final float[] originalVertices;
    private final int[] originalIndices;
    private final int originalVertexCount;
    private volatile float simplificationScale;
    private volatile int simplificationRequestVersion;

    private float positionX;
    private float positionY;
    private float positionZ;

    private float rotationX;
    private float rotationY;
    private float rotationZ;

    private float scale = 1.0f;

    public ScanMesh(
            float[] vertices,
            int[] indices,
            float[] textureCoordinates,
            Material[] vertexMaterials
    ) {
        this.vertices = vertices != null ? vertices.clone() : null;
        this.indices = indices != null ? indices.clone() : null;
        this.originalVertices = this.vertices != null ? this.vertices.clone() : null;
        this.originalIndices = this.indices != null ? this.indices.clone() : null;
        this.originalVertexCount = this.vertices != null ? this.vertices.length / 3 : 0;
        this.textureCoordinates = textureCoordinates;
        this.vertexMaterials = vertexMaterials;
        this.simplificationScale = this.originalVertexCount > 0 ? this.originalVertexCount : 1.0f;
    }

    public ScanMesh(
            float[] vertices,
            int[] indices
    ) {
        this(vertices, indices, null, null);
    }

    public ScanMesh(
            float[] vertices,
            int[] indices,
            Material[] vertexMaterials
    ) {
        this(vertices, indices, null, vertexMaterials);
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getOriginalVertexCount() {
        return originalVertexCount;
    }

    public int getCurrentVertexCount() {
        return vertices == null ? 0 : vertices.length / 3;
    }

    public float getSimplificationScale() {
        return simplificationScale;
    }

    public CompletableFuture<Void> setSimplificationScale(float targetVertexCount) {
        if (originalVertices == null || originalIndices == null) {
            return CompletableFuture.completedFuture(null);
        }

        float maxVertices = Math.max(1.0f, originalVertexCount);
        float clamped = Math.max(1.0f, Math.min(maxVertices, targetVertexCount));
        simplificationScale = clamped;

        int requestedVertexCount = Math.max(1, Math.round(clamped));
        System.out.println("[INFO] Mesh simplification requested: original=" + originalVertexCount + ", target=" + requestedVertexCount);

        if (requestedVertexCount >= originalVertexCount) {
            vertices = originalVertices.clone();
            indices = originalIndices.clone();
            System.out.println("[INFO] Mesh simplification reset to original geometry.");
            return CompletableFuture.completedFuture(null);
        }

        final int expectedVersion = ++simplificationRequestVersion;

        return CompletableFuture.runAsync(() -> {
            System.out.println("[INFO] Simplifying mesh in background: " + originalVertexCount + " -> " + requestedVertexCount + " vertices");

            ScanMesh simplified = MeshSimplifier.simplify(
                    new ScanMesh(
                            originalVertices.clone(),
                            originalIndices.clone(),
                            textureCoordinates,
                            vertexMaterials
                    ),
                    requestedVertexCount
            );

            if (simplified == null || expectedVersion != simplificationRequestVersion) {
                System.out.println("[INFO] Simplification skipped: stale job or null result.");
                return;
            }

            synchronized (this) {
                if (expectedVersion != simplificationRequestVersion) {
                    return;
                }

                vertices = simplified.getVertices();
                indices = simplified.getIndices();
                System.out.println("[INFO] Mesh simplified successfully: " + vertices.length / 3 + " vertices, " + indices.length / 3 + " triangles");
            }
        });
    }

    public Material[] getVertexMaterials() {
        return vertexMaterials;
    }

    public float[] getTextureCoordinates() {
        return textureCoordinates;
    }

    public boolean hasVertexMaterials() {
        return vertexMaterials != null;
    }

    public boolean hasTextureCoordinates() {
        return textureCoordinates != null;
    }

    public float getPositionX() {
        return positionX;
    }

    public float getPositionY() {
        return positionY;
    }

    public float getPositionZ() {
        return positionZ;
    }

    public void setPosition(
            float x,
            float y,
            float z
    ) {
        positionX = x;
        positionY = y;
        positionZ = z;
    }

    public void move(
            float x,
            float y,
            float z
    ) {
        positionX += x;
        positionY += y;
        positionZ += z;
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public float getRotationZ() {
        return rotationZ;
    }

    public void rotate(
            float x,
            float y,
            float z
    ) {
        rotationX += x;
        rotationY += y;
        rotationZ += z;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }
}