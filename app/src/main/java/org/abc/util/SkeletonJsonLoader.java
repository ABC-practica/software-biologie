package org.abc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.abc.model.BoneData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SkeletonJsonLoader {

    public static List<BoneData> loadBoneData(File skeletonJsonFile) {
        List<BoneData> rawBones = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(skeletonJsonFile);

            JsonNode bonesNode = root.get("bones");

            if (bonesNode == null || !bonesNode.isArray()) {
                return rawBones;
            }

            for (JsonNode boneNode : bonesNode) {

                JsonNode nameNode = boneNode.get("name");
                JsonNode bboxNode = boneNode.get("bounding_box");

                if (nameNode == null || bboxNode == null) {
                    continue;
                }

                float[] min = readVector3(bboxNode.get("min"));
                float[] max = readVector3(bboxNode.get("max"));
                float[] center = readVector3(bboxNode.get("center"));

                rawBones.add(
                        new BoneData(
                                nameNode.asText(),
                                min,
                                max,
                                center
                        )
                );
            }

        } catch (Exception e) {
            System.err.println(
                    "[ERROR] Failed to load skeleton.json: "
                            + e.getMessage()
            );
            e.printStackTrace();
            return rawBones;
        }

        if (!rawBones.isEmpty()) {
            normalizeBoneCoordinates(rawBones);
        }

        return rawBones;
    }

    private static float[] readVector3(JsonNode node) {
        float[] result = new float[3];

        if (node == null || !node.isArray()) {
            return result;
        }

        for (int i = 0; i < 3 && i < node.size(); i++) {
            result[i] = (float) node.get(i).asDouble();
        }

        return result;
    }

    private static void normalizeBoneCoordinates(List<BoneData> bones) {

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;

        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        for (BoneData bone : bones) {

            float[] min = bone.getBboxMin();
            float[] max = bone.getBboxMax();

            minX = Math.min(minX, min[0]);
            minY = Math.min(minY, min[1]);
            minZ = Math.min(minZ, min[2]);

            maxX = Math.max(maxX, max[0]);
            maxY = Math.max(maxY, max[1]);
            maxZ = Math.max(maxZ, max[2]);
        }

        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        float size = Math.max(
                sizeX,
                Math.max(sizeY, sizeZ)
        );

        if (size == 0.0f) {
            return;
        }

        for (BoneData bone : bones) {

            float[] min = bone.getBboxMin();
            float[] max = bone.getBboxMax();
            float[] center = bone.getBboxCenter();

            min[0] = (min[0] - centerX) / size;
            min[1] = (min[1] - centerY) / size;
            min[2] = (min[2] - centerZ) / size;

            max[0] = (max[0] - centerX) / size;
            max[1] = (max[1] - centerY) / size;
            max[2] = (max[2] - centerZ) / size;

            center[0] = (center[0] - centerX) / size;
            center[1] = (center[1] - centerY) / size;
            center[2] = (center[2] - centerZ) / size;
        }

        System.out.printf(
                "[DEBUG] Skeleton normalization | " +
                "Original bounds min=(%.6f, %.6f, %.6f) " +
                "max=(%.6f, %.6f, %.6f) | " +
                "Center=(%.6f, %.6f, %.6f) | Size=%.6f%n",
                minX, minY, minZ,
                maxX, maxY, maxZ,
                centerX, centerY, centerZ,
                size
        );
    }
}