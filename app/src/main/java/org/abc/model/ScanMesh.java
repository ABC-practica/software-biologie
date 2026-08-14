package org.abc.model;

public class ScanMesh {

    private final float[] vertices;
    private final int[] indices;
    private final Material[] vertexMaterials;
    private final float[] textureCoordinates;

    public ScanMesh(
            float[] vertices,
            int[] indices,
            float[] textureCoordinates,
            Material[] vertexMaterials
    ) {
        this.vertices = vertices;
        this.indices = indices;
        this.textureCoordinates = textureCoordinates;
        this.vertexMaterials = vertexMaterials;
    }

    public ScanMesh(float[] vertices, int[] indices) {
        this(vertices, indices, null, null);
    }

    public ScanMesh(
            float[] vertices,
            int[] indices,
            Material[] vertexMaterials
    ) {
        this(vertices, indices, null,vertexMaterials);
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
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
}