package org.abc.model;

public class BoneData {

    private final String name;
    private final float[] bboxMin;
    private final float[] bboxMax;
    private final float[] bboxCenter;

    public BoneData(
            String name,
            float[] bboxMin,
            float[] bboxMax,
            float[] bboxCenter
    ) {
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