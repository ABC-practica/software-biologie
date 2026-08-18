package org.abc.model;

public class ScanMesh {

    private final float[] vertices;
    private final int[] indices;
    private final Material[] vertexMaterials;
    private final float[] textureCoordinates;

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
        this.vertices = vertices;
        this.indices = indices;
        this.textureCoordinates = textureCoordinates;
        this.vertexMaterials = vertexMaterials;
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