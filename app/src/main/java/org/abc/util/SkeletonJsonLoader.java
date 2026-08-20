package org.abc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.abc.model.BoneData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SkeletonJsonLoader {

    public static List<BoneData> loadBoneData(File skeletonJsonFile) {
        List<BoneData> bones = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(skeletonJsonFile);

            if (root.has("bones") && root.get("bones").isArray()) {
                for (JsonNode boneNode : root.get("bones")) {
                    String boneName = boneNode.get("name").asText();

                    JsonNode bboxNode = boneNode.get("bounding_box");
                    if (bboxNode != null) {
                        float[] minArray = new float[3];
                        float[] maxArray = new float[3];
                        float[] centerArray = new float[3];

                        // Parse min
                        JsonNode minNode = bboxNode.get("min");
                        if (minNode != null && minNode.isArray()) {
                            for (int i = 0; i < 3 && i < minNode.size(); i++) {
                                minArray[i] = (float) minNode.get(i).asDouble();
                            }
                        }

                        // Parse max
                        JsonNode maxNode = bboxNode.get("max");
                        if (maxNode != null && maxNode.isArray()) {
                            for (int i = 0; i < 3 && i < maxNode.size(); i++) {
                                maxArray[i] = (float) maxNode.get(i).asDouble();
                            }
                        }

                        // Parse center
                        JsonNode centerNode = bboxNode.get("center");
                        if (centerNode != null && centerNode.isArray()) {
                            for (int i = 0; i < 3 && i < centerNode.size(); i++) {
                                centerArray[i] = (float) centerNode.get(i).asDouble();
                            }
                        }

                        bones.add(new BoneData(boneName, minArray, maxArray, centerArray));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load skeleton.json: " + e.getMessage());
            e.printStackTrace();
        }

        return bones;
    }
}
