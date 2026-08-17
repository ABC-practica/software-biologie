package org.abc.util;

public class LightNormalizer {

    public static float[] calculateNormal(
            float[] p1,
            float[] p2,
            float[] p3
    ) {
        float ux = p2[0] - p1[0];
        float uy = p2[1] - p1[1];
        float uz = p2[2] - p1[2];

        float vx = p3[0] - p1[0];
        float vy = p3[1] - p1[1];
        float vz = p3[2] - p1[2];

        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;

        float length = (float) Math.sqrt(
                nx * nx + ny * ny + nz * nz
        );

        if (length == 0.0f) {
            return new float[]{
                    0.0f,
                    0.0f,
                    0.0f
            };
        }

        return new float[]{
                nx / length,
                ny / length,
                nz / length
        };
    }
}