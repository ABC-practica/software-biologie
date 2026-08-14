package org.abc.model;

public class Texture {
    private final byte[] data;
    private final String mimeType;

    public Texture(byte[] data, String mimeType) {
        this.data = data;
        this.mimeType = mimeType;
    }

    public byte[] getData() {
        return data;
    }

    public String getMimeType() {
        return mimeType;
    }
}
