package org.abc.model;

public class BoneData {
    private String name;
    private float[] bboxMin;
    private float[] bboxMax;
    private float[] bboxCenter;

    public BoneData(String name, float[] bboxMin, float[] bboxMax, float[] bboxCenter) {
        this.name = name;
        this.bboxMin = bboxMin;
        this.bboxMax = bboxMax;
        this.bboxCenter = bboxCenter;
    }

    public String getName() {
        return name;
    }

    public float[] getBboxMin() {
        return bboxMin;
    }

    public float[] getBboxMax() {
        return bboxMax;
    }

    public float[] getBboxCenter() {
        return bboxCenter;
    }
}
