package org.abc.model;

public class Material {

    private final float[] diffuseColor;
    private final float[] specularColor;
    private final float shininess;
    private final Texture texture;

    public Material(
            float[] diffuseColor,
            float[] specularColor,
            float shininess,
            Texture texture
    ) {
        this.diffuseColor = diffuseColor;
        this.specularColor = specularColor;
        this.shininess = shininess;
        this.texture = texture;
    }

    public Material(float[] diffuseColor) {
        this(
                diffuseColor,
                new float[]{1.0f, 1.0f, 1.0f},
                32.0f,
                null
        );
    }

    public Material(float[] diffuseColor, Texture texture) {
        this(
                diffuseColor,
                new float[]{1.0f, 1.0f, 1.0f},
                32.0f,
                texture
        );
    }

    public float[] getDiffuseColor() {
        return diffuseColor;
    }

    public float[] getSpecularColor() {
        return specularColor;
    }

    public float getShininess() {
        return shininess;
    }

    public Texture getTexture() {
        return texture;
    }

    public boolean hasTexture() {
        return texture != null;
    }
}