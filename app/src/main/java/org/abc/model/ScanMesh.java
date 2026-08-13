package org.abc.model;

public class ScanMesh {

    private final float[] vertices;
    private final int[] indices;
    private final Material[] triangleMaterials;

    public ScanMesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
        this.triangleMaterials = null;
    }

    public ScanMesh(float[] vertices, int[] indices, Material material) {
        this.vertices = vertices;
        this.indices = indices;

        if (material == null) {
            this.triangleMaterials = null;
        } else {
            this.triangleMaterials = new Material[indices.length / 3];

            for (int i = 0; i < triangleMaterials.length; i++) {
                triangleMaterials[i] = material;
            }
        }
    }

    public ScanMesh(
            float[] vertices,
            int[] indices,
            Material[] triangleMaterials
    ) {
        this.vertices = vertices;
        this.indices = indices;
        this.triangleMaterials = triangleMaterials;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public Material getMaterial() {
        if (triangleMaterials == null || triangleMaterials.length == 0) {
            return null;
        }

        return triangleMaterials[0];
    }

    public Material[] getTriangleMaterials() {
        return triangleMaterials;
    }
}