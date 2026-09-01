package org.abc.service;

import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import org.abc.model.Material;
import org.abc.model.ScanMesh;

import java.util.ArrayList;
import java.util.List;

public class JavaFX3DRenderer implements RenderStrategy {

    private final List<ScanMesh> scanMeshes;

    private Stage stage;
    private SubScene subScene;
    private Pane embeddedContainer;

    private volatile float cameraDistance = 3.5f;
    private volatile float positionX = 0.0f;
    private volatile float positionY = 0.0f;
    private volatile float positionZ = 0.0f;

    private volatile float rotationX = 25.0f;
    private volatile float rotationY = 35.0f;
    private volatile float rotationZ = 0.0f;
    private volatile boolean objectRotationMode;
    private volatile boolean axesVisible;
    private volatile boolean objectScalingMode;

    private double lastMouseX;
    private double lastMouseY;

    private Rotate rxTransform;
    private Rotate ryTransform;
    private Rotate rzTransform;
    private Translate tTransform;
    private Translate cameraTranslate;

    public JavaFX3DRenderer(ScanMesh scanMesh) {
        this.scanMeshes = List.of(scanMesh);
    }

    public JavaFX3DRenderer(List<ScanMesh> scanMeshes) {
        if (scanMeshes == null || scanMeshes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one mesh is required"
            );
        }

        this.scanMeshes = new ArrayList<>(scanMeshes);
    }

    @Override
    public void open() {
        if (Platform.isFxApplicationThread()) {
            initAndShowStage();
        } else {
            Platform.runLater(this::initAndShowStage);
        }
    }

    @Override
    public void embedIn(Pane container) {
        if (Platform.isFxApplicationThread()) {
            initAndEmbedInContainer(container);
        } else {
            Platform.runLater(
                    () -> initAndEmbedInContainer(container)
            );
        }
    }

    private void initAndEmbedInContainer(Pane container) {
        this.embeddedContainer = container;

        PerspectiveCamera camera = buildPerspectiveCamera();
        Group root = buildSceneRoot(camera);

        subScene = new SubScene(
                root,
                Math.max(100, container.getWidth()),
                Math.max(100, container.getHeight()),
                true,
                SceneAntialiasing.BALANCED
        );

        subScene.setFill(Color.rgb(15, 23, 42));
        subScene.setCamera(camera);

        subScene.widthProperty().bind(container.widthProperty());
        subScene.heightProperty().bind(container.heightProperty());

        setupNodeMouseHandlers(subScene);

        container.getChildren().add(subScene);
    }

    private void initAndShowStage() {
        if (stage != null) {
            stage.show();
            stage.toFront();
            return;
        }

        PerspectiveCamera camera = buildPerspectiveCamera();
        Group root = buildSceneRoot(camera);

        Scene scene = new Scene(
                root,
                800,
                600,
                true,
                SceneAntialiasing.BALANCED
        );

        scene.setFill(Color.rgb(15, 23, 42));
        scene.setCamera(camera);

        setupNodeMouseHandlers(scene.getRoot());

        stage = new Stage();
        stage.setTitle("3D Renderer (JavaFX 3D)");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> close());
        stage.show();
    }

    private Group buildSceneRoot(PerspectiveCamera camera) {
        List<Node> nodes = new ArrayList<>();

        for (ScanMesh scanMesh : scanMeshes) {
            TriangleMesh mesh = buildTriangleMesh(scanMesh);
            MeshView meshView = new MeshView(mesh);

            meshView.setMaterial(
                    buildMaterial(scanMesh)
            );

            nodes.add(meshView);
        }

        Group meshGroup = new Group();
        meshGroup.getChildren().addAll(nodes);

        rxTransform = new Rotate(rotationX, Rotate.X_AXIS);
        ryTransform = new Rotate(rotationY, Rotate.Y_AXIS);
        rzTransform = new Rotate(rotationZ, Rotate.Z_AXIS);
        tTransform = new Translate(
                positionX,
                positionY,
                positionZ
        );

        meshGroup.getTransforms().addAll(
                rxTransform,
                ryTransform,
                rzTransform,
                tTransform
        );

        AmbientLight ambientLight =
                new AmbientLight(Color.rgb(120, 120, 120));

        PointLight pointLight =
                new PointLight(Color.WHITE);

        pointLight.setTranslateX(2.0);
        pointLight.setTranslateY(-3.0);
        pointLight.setTranslateZ(-4.0);

        return new Group(
                meshGroup,
                ambientLight,
                pointLight,
                camera
        );
    }

    private PerspectiveCamera buildPerspectiveCamera() {
        PerspectiveCamera camera =
                new PerspectiveCamera(true);

        camera.setNearClip(0.1);
        camera.setFarClip(100.0);

        cameraTranslate =
                new Translate(0, 0, -cameraDistance);

        camera.getTransforms().add(cameraTranslate);

        return camera;
    }

    private void setupNodeMouseHandlers(Node node) {
        node.setOnMousePressed(event -> {
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            double dx =
                    event.getSceneX() - lastMouseX;

            double dy =
                    event.getSceneY() - lastMouseY;

            if (event.isPrimaryButtonDown()) {
                rotate(
                        (float) -dy * 0.5f,
                        (float) dx * 0.5f,
                        0.0f
                );
            } else if (event.isSecondaryButtonDown()) {
                move(
                        (float) dx * 0.005f,
                        (float) -dy * 0.005f,
                        0.0f
                );
            }

            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });

        node.setOnScroll(
                event -> {
                    float amount = (float) -Math.signum(
                            event.getDeltaY()
                    ) * 0.15f;

                    zoom(amount);
                }
        );
    }

    public static TriangleMesh buildTriangleMesh(
            ScanMesh mesh
    ) {
        TriangleMesh triangleMesh =
                new TriangleMesh();

        float[] vertices =
                mesh.getVertices();

        if (vertices != null) {
            triangleMesh.getPoints().setAll(vertices);
        }

        float[] uvs =
                mesh.getTextureCoordinates();

        if (uvs != null && uvs.length >= 2) {
            triangleMesh.getTexCoords().setAll(uvs);
        } else {
            triangleMesh.getTexCoords().setAll(
                    0.0f,
                    0.0f
            );
        }

        int[] indices =
                mesh.getIndices();

        if (indices != null) {
            int faceCount =
                    indices.length / 3;

            int[] faces =
                    new int[faceCount * 6];

            boolean hasUvs =
                    uvs != null
                            && uvs.length >= indices.length * 2;

            for (int i = 0; i < faceCount; i++) {
                int i1 = indices[i * 3];
                int i2 = indices[i * 3 + 1];
                int i3 = indices[i * 3 + 2];

                faces[i * 6] = i1;
                faces[i * 6 + 1] =
                        hasUvs ? i1 : 0;

                faces[i * 6 + 2] = i2;
                faces[i * 6 + 3] =
                        hasUvs ? i2 : 0;

                faces[i * 6 + 4] = i3;
                faces[i * 6 + 5] =
                        hasUvs ? i3 : 0;
            }

            triangleMesh.getFaces().setAll(faces);
        }

        return triangleMesh;
    }

    private PhongMaterial buildMaterial(
            ScanMesh mesh
    ) {
        PhongMaterial phongMaterial =
                new PhongMaterial();

        Material[] materials =
                mesh.getVertexMaterials();

        if (materials != null
                && materials.length > 0
                && materials[0] != null) {

            float[] color =
                    materials[0].getDiffuseColor();

            if (color != null
                    && color.length >= 3) {

                phongMaterial.setDiffuseColor(
                        new Color(
                                Math.clamp(
                                        color[0],
                                        0f,
                                        1f
                                ),
                                Math.clamp(
                                        color[1],
                                        0f,
                                        1f
                                ),
                                Math.clamp(
                                        color[2],
                                        0f,
                                        1f
                                ),
                                color.length > 3
                                        ? Math.clamp(
                                        color[3],
                                        0f,
                                        1f
                                )
                                        : 1.0
                        )
                );

            } else {
                phongMaterial.setDiffuseColor(
                        Color.LIGHTGRAY
                );
            }

        } else {
            phongMaterial.setDiffuseColor(
                    Color.SILVER
            );
        }

        phongMaterial.setSpecularColor(
                Color.WHITE
        );

        return phongMaterial;
    }

    @Override
    public void close() {
        Runnable closeTask = () -> {
            if (subScene != null) {
                subScene.widthProperty().unbind();
                subScene.heightProperty().unbind();

                if (embeddedContainer != null) {
                    embeddedContainer
                            .getChildren()
                            .remove(subScene);

                    embeddedContainer = null;
                }

                subScene = null;
            }

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
        cameraDistance = 3.5f;

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
        Runnable refreshTask = () -> {
            if (subScene == null && stage == null) {
                updateTransforms();
                return;
            }

            Group root = subScene != null ? (Group) subScene.getRoot() : (Group) stage.getScene().getRoot();
            if (root == null || root.getChildren().isEmpty()) {
                updateTransforms();
                return;
            }

            Group meshGroup = (Group) root.getChildren().get(0);
            meshGroup.getChildren().clear();

            for (ScanMesh scanMesh : scanMeshes) {
                TriangleMesh mesh = buildTriangleMesh(scanMesh);
                MeshView meshView = new MeshView(mesh);
                meshView.setMaterial(buildMaterial(scanMesh));
                meshGroup.getChildren().add(meshView);
            }

            updateTransforms();
        };

        if (Platform.isFxApplicationThread()) {
            refreshTask.run();
        } else {
            Platform.runLater(refreshTask);
        }
    }

    @Override
    public void render() {
        updateTransforms();
    }

    @Override
    public void move(
            float x,
            float y,
            float z
    ) {
        positionX += x;
        positionY += y;
        positionZ += z;

        updateTransforms();
    }

    @Override
    public void rotate(
            float x,
            float y,
            float z
    ) {
        rotationX += x;
        rotationY += y;
        rotationZ += z;

        updateTransforms();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance =
                Math.clamp(
                        cameraDistance + amount,
                        0.5f,
                        20.0f
                );

        updateTransforms();
    }

    private void updateTransforms() {
        Runnable updateTask = () -> {
            if (rxTransform != null) {
                rxTransform.setAngle(rotationX);
            }

            if (ryTransform != null) {
                ryTransform.setAngle(rotationY);
            }

            if (rzTransform != null) {
                rzTransform.setAngle(rotationZ);
            }

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

    @Override
    public void setObjectRotationEnabled(boolean enabled) {
        objectRotationMode = enabled;
    }

    @Override
    public boolean isObjectRotationEnabled() {
        return objectRotationMode;
    }

    @Override
    public void setAxesVisible(boolean visible) {
        axesVisible = visible;
    }

    @Override
    public boolean isAxesVisible() {
        return axesVisible;
    }

    @Override
    public void setObjectScalingEnabled(boolean enabled) {
        objectScalingMode = enabled;
    }

    @Override
    public boolean isObjectScalingEnabled() {
        return objectScalingMode;
    }

    @Override
    public void setSelectedObjectTargetVertexCount(int targetVertexCount) {
        if (scanMeshes.isEmpty()) {
            return;
        }

        ScanMesh mesh = scanMeshes.get(0);
        mesh.setSimplificationScale(targetVertexCount);
    }

    @Override
    public int getSelectedObjectTargetVertexCount() {
        if (scanMeshes.isEmpty()) {
            return 0;
        }

        return Math.max(1, scanMeshes.get(0).getCurrentVertexCount());
    }

    @Override
    public int getSelectedObjectMaxVertexCount() {
        if (scanMeshes.isEmpty()) {
            return 1;
        }

        return Math.max(1, scanMeshes.get(0).getOriginalVertexCount());
    }

    @Override
    public void addMeshes(List<ScanMesh> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return;
        }

        Runnable addTask = () -> {
            scanMeshes.addAll(meshes);

            if (subScene != null) {
                Group root = (Group) subScene.getRoot();
                Group meshGroup = (Group) root.getChildren().get(0);

                for (ScanMesh scanMesh : meshes) {
                    TriangleMesh mesh = buildTriangleMesh(scanMesh);
                    MeshView meshView = new MeshView(mesh);
                    meshView.setMaterial(buildMaterial(scanMesh));
                    meshGroup.getChildren().add(meshView);
                }
            } else if (stage != null) {
                Scene scene = stage.getScene();
                if (scene != null) {
                    Group root = (Group) scene.getRoot();
                    Group meshGroup = (Group) root.getChildren().get(0);

                    for (ScanMesh scanMesh : meshes) {
                        TriangleMesh mesh = buildTriangleMesh(scanMesh);
                        MeshView meshView = new MeshView(mesh);
                        meshView.setMaterial(buildMaterial(scanMesh));
                        meshGroup.getChildren().add(meshView);
                    }
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            addTask.run();
        } else {
            Platform.runLater(addTask);
        }
    }
}