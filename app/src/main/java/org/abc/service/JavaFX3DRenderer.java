package org.abc.service;

import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import org.abc.model.Material;
import org.abc.model.ScanMesh;

public class JavaFX3DRenderer implements RenderStrategy {

    private final ScanMesh scanMesh;
    private Stage stage;

    private volatile float cameraDistance = 2.5f;
    private volatile float positionX = 0.0f;
    private volatile float positionY = 0.0f;
    private volatile float positionZ = 0.0f;

    private volatile float rotationX = 25.0f;
    private volatile float rotationY = 35.0f;
    private volatile float rotationZ = 0.0f;

    private double lastMouseX;
    private double lastMouseY;

    private Rotate rxTransform;
    private Rotate ryTransform;
    private Rotate rzTransform;
    private Translate tTransform;
    private Translate cameraTranslate;

    public JavaFX3DRenderer(ScanMesh scanMesh) {
        this.scanMesh = scanMesh;
    }

    @Override
    public void open() {
        System.out.println("[INFO] JavaFX3DRenderer.open() called. FX Thread: " + Platform.isFxApplicationThread());
        if (Platform.isFxApplicationThread()) {
            initAndShowStage();
        } else {
            Platform.runLater(this::initAndShowStage);
        }
    }

    private void initAndShowStage() {
        System.out.println("[INFO] JavaFX3DRenderer.initAndShowStage() starting...");
        if (stage != null) {
            stage.show();
            stage.toFront();
            System.out.println("[INFO] JavaFX3DRenderer stage brought to front.");
            return;
        }

        TriangleMesh mesh = buildTriangleMesh(scanMesh);
        MeshView meshView = new MeshView(mesh);

        PhongMaterial material = buildMaterial(scanMesh);
        meshView.setMaterial(material);

        Group meshGroup = new Group(meshView);

        rxTransform = new Rotate(rotationX, Rotate.X_AXIS);
        ryTransform = new Rotate(rotationY, Rotate.Y_AXIS);
        rzTransform = new Rotate(rotationZ, Rotate.Z_AXIS);
        tTransform = new Translate(positionX, positionY, positionZ);

        meshGroup.getTransforms().addAll(tTransform, rxTransform, ryTransform, rzTransform);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(100, 100, 100));
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(2.0);
        pointLight.setTranslateY(-3.0);
        pointLight.setTranslateZ(-4.0);

        Group root = new Group(meshGroup, ambientLight, pointLight);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(100.0);

        cameraTranslate = new Translate(0, 0, -cameraDistance);
        camera.getTransforms().add(cameraTranslate);

        Scene scene = new Scene(root, 800, 600, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.rgb(25, 25, 25));
        scene.setCamera(camera);

        setupMouseHandlers(scene);

        stage = new Stage();
        stage.setTitle("3D Renderer (JavaFX 3D Fallback)");
        stage.setScene(scene);

        stage.setOnCloseRequest(event -> close());

        stage.show();
        System.out.println("[INFO] JavaFX 3D Stage shown successfully!");
    }

    private void setupMouseHandlers(Scene scene) {
        scene.setOnMousePressed(event -> {
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });

        scene.setOnMouseDragged(event -> {
            double dx = event.getSceneX() - lastMouseX;
            double dy = event.getSceneY() - lastMouseY;

            if (event.isPrimaryButtonDown()) {
                rotate((float) -dy * 0.5f, (float) dx * 0.5f, 0.0f);
            } else if (event.isSecondaryButtonDown()) {
                move((float) dx * 0.005f, (float) -dy * 0.005f, 0.0f);
            }

            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });

        scene.setOnScroll(event -> zoom((float) -event.getDeltaY() * 0.02f));
    }

    public static TriangleMesh buildTriangleMesh(ScanMesh mesh) {
        TriangleMesh triangleMesh = new TriangleMesh();

        float[] vertices = mesh.getVertices();
        if (vertices != null) {
            triangleMesh.getPoints().setAll(vertices);
        }

        float[] uvs = mesh.getTextureCoordinates();
        if (uvs != null && uvs.length >= 2) {
            triangleMesh.getTexCoords().setAll(uvs);
        } else {
            triangleMesh.getTexCoords().setAll(0.0f, 0.0f);
        }

        int[] indices = mesh.getIndices();
        if (indices != null) {
            int faceCount = indices.length / 3;
            int[] faces = new int[faceCount * 6];
            boolean hasUvs = uvs != null && uvs.length >= (indices.length * 2);

            for (int i = 0; i < faceCount; i++) {
                int i1 = indices[i * 3];
                int i2 = indices[i * 3 + 1];
                int i3 = indices[i * 3 + 2];

                faces[i * 6] = i1;
                faces[i * 6 + 1] = hasUvs ? i1 : 0;

                faces[i * 6 + 2] = i2;
                faces[i * 6 + 3] = hasUvs ? i2 : 0;

                faces[i * 6 + 4] = i3;
                faces[i * 6 + 5] = hasUvs ? i3 : 0;
            }
            triangleMesh.getFaces().setAll(faces);
        }

        return triangleMesh;
    }

    private PhongMaterial buildMaterial(ScanMesh mesh) {
        PhongMaterial phongMaterial = new PhongMaterial();
        Material[] materials = mesh.getVertexMaterials();

        if (materials != null && materials.length > 0 && materials[0] != null) {
            float[] color = materials[0].getDiffuseColor();
            if (color != null && color.length >= 3) {
                phongMaterial.setDiffuseColor(new Color(
                        Math.clamp(color[0], 0f, 1f),
                        Math.clamp(color[1], 0f, 1f),
                        Math.clamp(color[2], 0f, 1f),
                        color.length > 3 ? Math.clamp(color[3], 0f, 1f) : 1.0
                ));
            } else {
                phongMaterial.setDiffuseColor(Color.LIGHTGRAY);
            }
        } else {
            phongMaterial.setDiffuseColor(Color.SILVER);
        }

        phongMaterial.setSpecularColor(Color.WHITE);
        return phongMaterial;
    }

    @Override
    public void close() {
        Runnable closeTask = () -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        };

        if (Platform.isFxApplicationThread()) {
            closeTask.run();
        } else {
            Platform.runLater(closeTask);
        }
    }

    @Override
    public void reload() {
        resetCamera();
        refresh();
    }

    @Override
    public void resetCamera() {
        cameraDistance = 2.5f;
        positionX = 0.0f;
        positionY = 0.0f;
        positionZ = 0.0f;

        rotationX = 25.0f;
        rotationY = 35.0f;
        rotationZ = 0.0f;

        updateTransforms();
    }

    @Override
    public void refresh() {
        updateTransforms();
    }

    @Override
    public void render() {
        updateTransforms();
    }

    @Override
    public void move(float x, float y, float z) {
        positionX += x;
        positionY += y;
        positionZ += z;
        updateTransforms();
    }

    @Override
    public void rotate(float x, float y, float z) {
        rotationX += x;
        rotationY += y;
        rotationZ += z;
        updateTransforms();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance = Math.clamp(cameraDistance + amount, 0.5f, 20.0f);
        updateTransforms();
    }

    private void updateTransforms() {
        Runnable updateTask = () -> {
            if (rxTransform != null) rxTransform.setAngle(rotationX);
            if (ryTransform != null) ryTransform.setAngle(rotationY);
            if (rzTransform != null) rzTransform.setAngle(rotationZ);

            if (tTransform != null) {
                tTransform.setX(positionX);
                tTransform.setY(positionY);
                tTransform.setZ(positionZ);
            }

            if (cameraTranslate != null) {
                cameraTranslate.setZ(-cameraDistance);
            }
        };

        if (Platform.isFxApplicationThread()) {
            updateTask.run();
        } else {
            Platform.runLater(updateTask);
        }
    }

    public Stage getStage() {
        return stage;
    }
}
