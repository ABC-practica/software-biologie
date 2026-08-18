package org.abc.service;

import org.abc.model.ScanMesh;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RenderStrategyFactoryTest {

    @AfterEach
    public void tearDown() {
        RenderStrategyFactory.setForceFallback(false);
    }

    @Test
    public void testForceFallbackCreatesJavaFX3DRenderer() {
        RenderStrategyFactory.setForceFallback(true);

        ScanMesh mesh = new ScanMesh(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                new int[]{0, 1, 2}
        );

        RenderStrategy strategy = RenderStrategyFactory.createRenderer(mesh);

        assertNotNull(strategy);
        assertTrue(strategy instanceof JavaFX3DRenderer);
    }

    @Test
    public void testPrimaryRendererCreation() {
        RenderStrategyFactory.setForceFallback(false);

        ScanMesh mesh = new ScanMesh(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                new int[]{0, 1, 2}
        );

        RenderStrategy strategy = RenderStrategyFactory.createRenderer(mesh);

        assertNotNull(strategy);
        if (RenderStrategyFactory.isMacOs()) {
            assertTrue(strategy instanceof JavaFX3DRenderer);
        } else {
            assertTrue(strategy instanceof OpenGLRenderer || strategy instanceof JavaFX3DRenderer);
        }
    }
}
