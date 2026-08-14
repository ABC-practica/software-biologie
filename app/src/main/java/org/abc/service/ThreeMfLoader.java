package org.abc.service;

import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.abc.model.Texture;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ThreeMfLoader implements Loader {

    @Override
    public ScanMesh load(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry model = findModel(zip);
            if (model == null) throw new IOException("3MF model not found");

            Document document;
            try (InputStream input = zip.getInputStream(model)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                document = factory.newDocumentBuilder().parse(input);
            }

            Map<Integer, Material[]> materials = loadMaterialGroups(document);
            Map<Integer, Texture> textures = loadTextures(document, zip);
            Map<Integer, TextureGroup> textureGroups = loadTextureGroups(document, textures);

            ScanMesh mesh = loadMeshes(document, materials, textureGroups);

            System.out.println("Vertices: " + mesh.getVertices().length);
            System.out.println("Indices: " + mesh.getIndices().length);
            System.out.println("UVs: " +
                    (mesh.getTextureCoordinates() == null
                            ? "null"
                            : mesh.getTextureCoordinates().length));

            Material[] materialsArray = mesh.getVertexMaterials();

            System.out.println("Materials: " +
                    (materialsArray == null ? "null" : materialsArray.length));

            if (materialsArray != null) {
                for (Material material : materialsArray) {
                    if (material != null) {
                        System.out.println("Texture: " + material.hasTexture());
                    }
                }
            }

            return mesh;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load 3MF file", e);
        }
    }

    private ScanMesh loadMeshes(
            Document document,
            Map<Integer, Material[]> materials,
            Map<Integer, TextureGroup> textureGroups
    ) {
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Material> vertexMaterials = new ArrayList<>();

        NodeList meshes = document.getElementsByTagNameNS("*", "mesh");

        for (int m = 0; m < meshes.getLength(); m++) {
            Element mesh = (Element) meshes.item(m);
            List<float[]> meshVertices = loadVertices(mesh);

            NodeList triangles =
                    mesh.getElementsByTagNameNS("*", "triangle");

            for (int i = 0; i < triangles.getLength(); i++) {
                Element triangle = (Element) triangles.item(i);

                int v1 = parseInt(triangle, "v1");
                int v2 = parseInt(triangle, "v2");
                int v3 = parseInt(triangle, "v3");

                int base = vertices.size() / 3;

                addVertex(meshVertices, v1, vertices);
                addVertex(meshVertices, v2, vertices);
                addVertex(meshVertices, v3, vertices);

                indices.add(base);
                indices.add(base + 1);
                indices.add(base + 2);

                addVertexMaterials(
                        triangle,
                        materials,
                        vertexMaterials
                );
            }
        }

        return new ScanMesh(
                toFloatArray(vertices),
                indices.stream().mapToInt(Integer::intValue).toArray(),
                vertexMaterials.toArray(new Material[0])
        );
    }

    private void addVertexMaterials(
            Element triangle,
            Map<Integer, Material[]> materials,
            List<Material> output
    ) {
        String pid = triangle.getAttribute("pid");

        if (pid.isBlank()) {
            output.add(null);
            output.add(null);
            output.add(null);
            return;
        }

        try {
            Material[] group =
                    materials.get(Integer.parseInt(pid));

            output.add(resolveVertexMaterial(
                    triangle, "p1", group
            ));

            output.add(resolveVertexMaterial(
                    triangle, "p2", group
            ));

            output.add(resolveVertexMaterial(
                    triangle, "p3", group
            ));
        } catch (NumberFormatException e) {
            output.add(null);
            output.add(null);
            output.add(null);
        }
    }

    private Material resolveVertexMaterial(
            Element triangle,
            String attribute,
            Material[] materials
    ) {
        if (materials == null) return null;

        int index = parseInt(triangle, attribute);

        if (index < 0 || index >= materials.length)
            return null;

        return materials[index];
    }

    private List<float[]> loadVertices(Element mesh) {
        List<float[]> vertices = new ArrayList<>();
        NodeList nodes = mesh.getElementsByTagNameNS("*", "vertex");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element v = (Element) nodes.item(i);
            vertices.add(new float[]{
                    Float.parseFloat(v.getAttribute("x")),
                    Float.parseFloat(v.getAttribute("y")),
                    Float.parseFloat(v.getAttribute("z"))
            });
        }

        return vertices;
    }

    private void addVertex(
            List<float[]> vertices,
            int index,
            List<Float> output
    ) {
        if (index < 0 || index >= vertices.size())
            throw new IllegalArgumentException("Invalid 3MF vertex index: " + index);

        float[] vertex = vertices.get(index);
        output.add(vertex[0]);
        output.add(vertex[1]);
        output.add(vertex[2]);
    }

    private ZipEntry findModel(ZipFile zip) {
        ZipEntry model = zip.getEntry("3D/3dmodel.model");
        if (model != null) return model;

        for (var entries = zip.entries(); entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".model"))
                return entry;
        }

        return null;
    }

    private Map<Integer, Material[]> loadMaterialGroups(Document document) {
        Map<Integer, Material[]> groups = new HashMap<>();
        loadBaseMaterials(document, groups);
        loadColorGroups(document, groups);
        return groups;
    }

    private void loadBaseMaterials(
            Document document,
            Map<Integer, Material[]> groups
    ) {
        NodeList nodes = document.getElementsByTagNameNS("*", "basematerials");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element group = (Element) nodes.item(i);
            int id = parseId(group);
            if (id < 0) continue;

            NodeList bases = group.getElementsByTagNameNS("*", "base");
            Material[] materials = new Material[bases.getLength()];

            for (int j = 0; j < bases.getLength(); j++) {
                Element base = (Element) bases.item(j);
                String color = base.getAttribute("displaycolor");
                materials[j] = color.isBlank() ? null : new Material(parseColor(color));
            }

            groups.put(id, materials);
        }
    }

    private void loadColorGroups(
            Document document,
            Map<Integer, Material[]> groups
    ) {
        NodeList nodes = document.getElementsByTagNameNS("*", "colorgroup");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element group = (Element) nodes.item(i);
            int id = parseId(group);
            if (id < 0) continue;

            NodeList colors = group.getElementsByTagNameNS("*", "color");
            Material[] materials = new Material[colors.getLength()];

            for (int j = 0; j < colors.getLength(); j++) {
                Element color = (Element) colors.item(j);
                String value = color.getAttribute("color");
                materials[j] = value.isBlank() ? null : new Material(parseColor(value));
            }

            groups.put(id, materials);
        }
    }

    private Map<Integer, Texture> loadTextures(
            Document document,
            ZipFile zip
    ) throws IOException {
        Map<Integer, Texture> textures = new HashMap<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "texture2d");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            int id = parseId(element);
            if (id < 0) continue;

            String path = element.getAttribute("path");
            String type = element.getAttribute("contenttype");
            if (path.isBlank()) continue;

            ZipEntry entry = zip.getEntry(normalizePath(path));
            if (entry == null) continue;

            try (InputStream input = zip.getInputStream(entry)) {
                textures.put(id, new Texture(readAllBytes(input), type));
            }
        }

        return textures;
    }

    private Map<Integer, TextureGroup> loadTextureGroups(
            Document document,
            Map<Integer, Texture> textures
    ) {
        Map<Integer, TextureGroup> groups = new HashMap<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "texture2dgroup");

        for (int i = 0; i < nodes.getLength(); i++) {
            Element group = (Element) nodes.item(i);
            int id = parseId(group);
            if (id < 0) continue;

            int textureId;
            try {
                textureId = Integer.parseInt(group.getAttribute("texid"));
            } catch (NumberFormatException e) {
                continue;
            }

            Texture texture = textures.get(textureId);
            if (texture == null) continue;

            NodeList nodes2 = group.getElementsByTagNameNS("*", "tex2coord");
            List<TextureCoordinates> coordinates = new ArrayList<>();

            for (int j = 0; j < nodes2.getLength(); j++) {
                Element c = (Element) nodes2.item(j);
                coordinates.add(new TextureCoordinates(
                        Float.parseFloat(c.getAttribute("u")),
                        Float.parseFloat(c.getAttribute("v"))
                ));
            }

            groups.put(id, new TextureGroup(texture, coordinates));
        }

        return groups;
    }

    private Material resolveMaterial(
            Element triangle,
            Map<Integer, Material[]> materials,
            Map<Integer, TextureGroup> textureGroups
    ) {
        String pid = triangle.getAttribute("pid");
        String p1 = triangle.getAttribute("p1");

        if (pid.isBlank() || p1.isBlank()) return null;

        try {
            int id = Integer.parseInt(pid);
            int index = Integer.parseInt(p1);

            Material[] group = materials.get(id);

            if (group != null) {
                if (index < 0 || index >= group.length) return null;
                return group[index];
            }

            TextureGroup textureGroup = textureGroups.get(id);

            if (textureGroup != null) {
                return new Material(
                        new float[]{1.0f, 1.0f, 1.0f},
                        textureGroup.texture()
                );
            }

            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TextureCoordinates[] resolveTextureCoordinates(
            Element triangle,
            Map<Integer, TextureGroup> groups
    ) {
        String pid = triangle.getAttribute("pid");
        if (pid.isBlank()) return null;

        try {
            TextureGroup group = groups.get(Integer.parseInt(pid));
            if (group == null) return null;

            return new TextureCoordinates[]{
                    getCoordinate(triangle, "p1", group),
                    getCoordinate(triangle, "p2", group),
                    getCoordinate(triangle, "p3", group)
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TextureCoordinates getCoordinate(
            Element triangle,
            String attribute,
            TextureGroup group
    ) {
        int index = parseInt(triangle, attribute);

        if (index < 0 || index >= group.coordinates().size())
            throw new IllegalArgumentException("Invalid 3MF texture coordinate: " + index);

        return group.coordinates().get(index);
    }

    private int parseId(Element element) {
        return parseInt(element, "id", -1);
    }

    private int parseInt(
            Element element,
            String attribute
    ) {
        return parseInt(element, attribute, Integer.MIN_VALUE);
    }

    private int parseInt(
            Element element,
            String attribute,
            int defaultValue
    ) {
        String value = element.getAttribute(attribute);

        if (value.isBlank()) {
            if (defaultValue != Integer.MIN_VALUE) return defaultValue;
            throw new IllegalArgumentException("Missing 3MF attribute: " + attribute);
        }

        return Integer.parseInt(value);
    }

    private float[] parseColor(String value) {
        String color = value.trim();
        if (color.startsWith("#")) color = color.substring(1);

        if (color.length() != 6 && color.length() != 8)
            throw new IllegalArgumentException("Invalid 3MF color: " + value);

        int offset = color.length() == 8 ? 2 : 0;

        int r = Integer.parseInt(color.substring(offset, offset + 2), 16);
        int g = Integer.parseInt(color.substring(offset + 2, offset + 4), 16);
        int b = Integer.parseInt(color.substring(offset + 4, offset + 6), 16);

        if (color.length() == 6)
            return new float[]{r / 255f, g / 255f, b / 255f};

        int a = Integer.parseInt(color.substring(0, 2), 16);
        return new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;

        while ((read = input.read(buffer)) != -1)
            output.write(buffer, 0, read);

        return output.toByteArray();
    }

    private boolean hasMaterials(List<Material> materials) {
        return materials.stream().anyMatch(Objects::nonNull);
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];

        for (int i = 0; i < values.size(); i++)
            result[i] = values.get(i);

        return result;
    }

    private record TextureGroup(
            Texture texture,
            List<TextureCoordinates> coordinates
    ) {}

    private record TextureCoordinates(float u, float v) {}
}