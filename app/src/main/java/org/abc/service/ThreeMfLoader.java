package org.abc.service;

import org.abc.model.ScanMesh;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ThreeMfLoader implements Loader {

    @Override
    public ScanMesh load(Path path) throws IOException {
        List<float[]> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry model = zip.getEntry("3D/3dmodel.model");

            if (model == null) {
                throw new IOException("3MF model not found");
            }

            try (InputStream input = zip.getInputStream(model)) {
                Document document = DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(input);

                NodeList vertexNodes = document.getElementsByTagName("vertex");

                for (int i = 0; i < vertexNodes.getLength(); i++) {
                    Element vertex = (Element) vertexNodes.item(i);

                    vertices.add(new float[]{
                            Float.parseFloat(vertex.getAttribute("x")),
                            Float.parseFloat(vertex.getAttribute("y")),
                            Float.parseFloat(vertex.getAttribute("z"))
                    });
                }

                NodeList triangleNodes = document.getElementsByTagName("triangle");

                for (int i = 0; i < triangleNodes.getLength(); i++) {
                    Element triangle = (Element) triangleNodes.item(i);

                    indices.add(Integer.parseInt(triangle.getAttribute("v1")));
                    indices.add(Integer.parseInt(triangle.getAttribute("v2")));
                    indices.add(Integer.parseInt(triangle.getAttribute("v3")));
                }
            }
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

        return new ScanMesh(vertexArray, indexArray);
    }
}