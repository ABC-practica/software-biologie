package org.abc.service;

import javafx.scene.shape.TriangleMesh;
import org.abc.model.ScanMesh;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaFX3DRendererTest {

    @Test
    public void testBuildTriangleMeshConversion() {
        float[] vertices = new float[]{
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        int[] indices = new int[]{0, 1, 2};
        ScanMesh scanMesh = new ScanMesh(vertices, indices);

        TriangleMesh triangleMesh = JavaFX3DRenderer.buildTriangleMesh(scanMesh);

        assertNotNull(triangleMesh);
        assertEquals(9, triangleMesh.getPoints().size());
        assertEquals(6, triangleMesh.getFaces().size()); // 1 triangle face = 6 ints (p0, t0, p1, t1, p2, t2)
    }
}
