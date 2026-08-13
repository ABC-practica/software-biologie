package org.abc.service;

import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ThreeMfLoader implements Loader {

    @Override
    public ScanMesh load(Path path) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Material> triangleMaterials = new ArrayList<>();

        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry model = findModel(zip);

            if (model == null) {
                throw new IOException("3MF model not found");
            }

            try (InputStream input = zip.getInputStream(model)) {
                DocumentBuilderFactory factory =
                        DocumentBuilderFactory.newInstance();

                factory.setNamespaceAware(true);

                Document document = factory
                        .newDocumentBuilder()
                        .parse(input);

                Map<Integer, Material[]> materialGroups =
                        loadMaterialGroups(document);

                NodeList vertexNodes =
                        document.getElementsByTagNameNS("*", "vertex");

                for (int i = 0; i < vertexNodes.getLength(); i++) {
                    Element vertex = (Element) vertexNodes.item(i);

                    vertices.add(new float[]{
                            Float.parseFloat(vertex.getAttribute("x")),
                            Float.parseFloat(vertex.getAttribute("y")),
                            Float.parseFloat(vertex.getAttribute("z"))
                    });
                }

                NodeList triangleNodes =
                        document.getElementsByTagNameNS("*", "triangle");

                for (int i = 0; i < triangleNodes.getLength(); i++) {
                    Element triangle = (Element) triangleNodes.item(i);

                    indices.add(
                            Integer.parseInt(triangle.getAttribute("v1"))
                    );

                    indices.add(
                            Integer.parseInt(triangle.getAttribute("v2"))
                    );

                    indices.add(
                            Integer.parseInt(triangle.getAttribute("v3"))
                    );

                    triangleMaterials.add(
                            resolveMaterial(triangle, materialGroups)
                    );
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load 3MF file", e);
        }

        float[] vertexArray = new float[vertices.size() * 3];

        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i * 3] = vertices.get(i)[0];
            vertexArray[i * 3 + 1] = vertices.get(i)[1];
            vertexArray[i * 3 + 2] = vertices.get(i)[2];
        }

        int[] indexArray = indices.stream()
                .mapToInt(Integer::intValue)
                .toArray();

        Material[] materialArray = triangleMaterials.toArray(
                new Material[0]
        );

        boolean hasMaterials = false;

        for (Material material : materialArray) {
            if (material != null) {
                hasMaterials = true;
                break;
            }
        }

        if (!hasMaterials) {
            return new ScanMesh(vertexArray, indexArray);
        }

        return new ScanMesh(
                vertexArray,
                indexArray,
                materialArray
        );
    }

    private ZipEntry findModel(ZipFile zip) {
        ZipEntry model = zip.getEntry("3D/3dmodel.model");

        if (model != null) {
            return model;
        }

        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();

            if (!entry.isDirectory()
                    && entry.getName().toLowerCase().endsWith(".model")) {
                return entry;
            }
        }

        return null;
    }

    private Map<Integer, Material[]> loadMaterialGroups(
            Document document
    ) {
        Map<Integer, Material[]> groups = new HashMap<>();

        NodeList baseMaterialNodes =
                document.getElementsByTagNameNS("*", "basematerials");

        for (int i = 0; i < baseMaterialNodes.getLength(); i++) {
            Element group =
                    (Element) baseMaterialNodes.item(i);

            int id = parseId(group);

            if (id < 0) {
                continue;
            }

            NodeList bases =
                    group.getElementsByTagNameNS("*", "base");

            List<Material> materials = new ArrayList<>();

            for (int j = 0; j < bases.getLength(); j++) {
                Element base = (Element) bases.item(j);

                String color = base.getAttribute("displaycolor");

                if (color == null || color.isBlank()) {
                    materials.add(null);
                } else {
                    materials.add(
                            new Material(parseColor(color))
                    );
                }
            }

            groups.put(
                    id,
                    materials.toArray(new Material[0])
            );
        }

        NodeList colorGroupNodes =
                document.getElementsByTagNameNS("*", "colorgroup");

        for (int i = 0; i < colorGroupNodes.getLength(); i++) {
            Element group =
                    (Element) colorGroupNodes.item(i);

            int id = parseId(group);

            if (id < 0) {
                continue;
            }

            NodeList colorNodes =
                    group.getElementsByTagNameNS("*", "color");

            List<Material> materials = new ArrayList<>();

            for (int j = 0; j < colorNodes.getLength(); j++) {
                Element color =
                        (Element) colorNodes.item(j);

                String value = color.getAttribute("color");

                if (value == null || value.isBlank()) {
                    materials.add(null);
                } else {
                    materials.add(
                            new Material(parseColor(value))
                    );
                }
            }

            groups.put(
                    id,
                    materials.toArray(new Material[0])
            );
        }

        return groups;
    }

    private Material resolveMaterial(
            Element triangle,
            Map<Integer, Material[]> materialGroups
    ) {
        String pidValue = triangle.getAttribute("pid");
        String p1Value = triangle.getAttribute("p1");

        if (pidValue == null
                || pidValue.isBlank()
                || p1Value == null
                || p1Value.isBlank()) {
            return null;
        }

        try {
            int pid = Integer.parseInt(pidValue);
            int p1 = Integer.parseInt(p1Value);

            Material[] materials = materialGroups.get(pid);

            if (materials == null) {
                return null;
            }

            if (p1 < 0 || p1 >= materials.length) {
                return null;
            }

            return materials[p1];

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseId(Element element) {
        String value = element.getAttribute("id");

        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float[] parseColor(String value) {
        String color = value.trim();

        if (color.startsWith("#")) {
            color = color.substring(1);
        }

        if (color.length() == 6) {
            int red = Integer.parseInt(
                    color.substring(0, 2),
                    16
            );

            int green = Integer.parseInt(
                    color.substring(2, 4),
                    16
            );

            int blue = Integer.parseInt(
                    color.substring(4, 6),
                    16
            );

            return new float[]{
                    red / 255.0f,
                    green / 255.0f,
                    blue / 255.0f
            };
        }

        if (color.length() == 8) {
            int alpha = Integer.parseInt(
                    color.substring(0, 2),
                    16
            );

            int red = Integer.parseInt(
                    color.substring(2, 4),
                    16
            );

            int green = Integer.parseInt(
                    color.substring(4, 6),
                    16
            );

            int blue = Integer.parseInt(
                    color.substring(6, 8),
                    16
            );

            return new float[]{
                    red / 255.0f,
                    green / 255.0f,
                    blue / 255.0f,
                    alpha / 255.0f
            };
        }

        throw new IllegalArgumentException(
                "Invalid 3MF color: " + value
        );
    }
}