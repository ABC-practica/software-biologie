package org.abc.util;

import org.abc.model.ScanMesh;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MeshSimplifierTest {

    @Test
    public void testSimplifyReducesMeshSizeAndKeepsValidTriangles() {
        float[] vertices = new float[]{
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 0.0f,
                1.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f
        };

        int[] indices = new int[]{
                0, 1, 2,
                0, 1, 3,
                0, 2, 3,
                1, 2, 3,
                4, 5, 6,
                4, 5, 7,
                4, 6, 7,
                5, 6, 7,
                0, 1, 4,
                0, 2, 4,
                0, 3, 5,
                0, 2, 6,
                1, 3, 5,
                1, 4, 5,
                2, 3, 6,
                2, 4, 6,
                3, 5, 7,
                3, 6, 7,
                4, 5, 7,
                4, 6, 7
        };

        ScanMesh mesh = new ScanMesh(vertices, indices);

        ScanMesh simplified = MeshSimplifier.simplify(mesh, 5);

        assertNotNull(simplified);
        assertNotSame(mesh, simplified);
        assertTrue(simplified.getVertices().length / 3 <= 5,
                "simplified mesh should meet the target vertex count");
        assertEquals(0, simplified.getIndices().length % 3,
                "triangle indices must stay grouped in triples");
        assertTrue(simplified.getIndices().length > 0,
                "result should still contain visible triangles");

        for (int i = 0; i < simplified.getIndices().length; i += 3) {
            int a = simplified.getIndices()[i];
            int b = simplified.getIndices()[i + 1];
            int c = simplified.getIndices()[i + 2];

            assertNotEquals(a, b);
            assertNotEquals(a, c);
            assertNotEquals(b, c);
        }
    }
}
