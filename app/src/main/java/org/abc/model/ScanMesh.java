package org.abc.model;

public class ScanMesh {

    private final float[] vertices;
    private final int[] indices;

    public ScanMesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }
}
