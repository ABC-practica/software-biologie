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
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import org.abc.model.BoneData;
import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.abc.util.SkeletonJsonLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaFX3DRenderer implements RenderStrategy {

    private final List<ScanMesh> scanMeshes;
    private final List<ScanMesh> lockedMeshes = new ArrayList<>();
    private final List<ScanMesh> editableMeshes = new ArrayList<>();

    private Stage stage;
    private SubScene subScene;
    private Pane embeddedContainer;
    private Group lockedMeshGroup;
    private Group editableMeshGroup;

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
    private volatile boolean boneLabelsVisible = false;

    private double lastMouseX;
    private double lastMouseY;

    private Rotate rxTransform;
    private Rotate ryTransform;
    private Rotate rzTransform;
    private Translate tTransform;
    private Translate cameraTranslate;

    private Group boneLabelsGroup;

    public JavaFX3DRenderer(ScanMesh scanMesh) {
        this.scanMeshes = new ArrayList<>();
        this.scanMeshes.add(scanMesh);

        if (scanMesh.isLocked()) {
            this.lockedMeshes.add(scanMesh);
        } else {
            this.editableMeshes.add(scanMesh);
        }
    }

    public JavaFX3DRenderer(List<ScanMesh> scanMeshes) {
        if (scanMeshes == null || scanMeshes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one mesh is required"
            );
        }

        this.scanMeshes = new ArrayList<>(scanMeshes);
        
        // Separate locked and editable meshes
        for (ScanMesh mesh : scanMeshes) {
            if (mesh.isLocked()) {
                lockedMeshes.add(mesh);
            } else {
                editableMeshes.add(mesh);
            }
        }
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
        // Build locked meshes (no transforms applied)
        List<Node> lockedNodes = new ArrayList<>();
        for (ScanMesh scanMesh : lockedMeshes) {
            TriangleMesh mesh = buildTriangleMesh(scanMesh);
            MeshView meshView = new MeshView(mesh);
            meshView.setMaterial(buildMaterial(scanMesh));
            // Make locked meshes non-interactive and semi-transparent
            meshView.setMouseTransparent(true);
            meshView.setPickOnBounds(false);
            meshView.setOpacity(0.35);
            lockedNodes.add(meshView);
        }
        
        lockedMeshGroup = new Group();
        lockedMeshGroup.getChildren().addAll(lockedNodes);

        // Build editable meshes (transforms applied)
        List<Node> editableNodes = new ArrayList<>();
        for (ScanMesh scanMesh : editableMeshes) {
            TriangleMesh mesh = buildTriangleMesh(scanMesh);
            MeshView meshView = new MeshView(mesh);
            meshView.setMaterial(buildMaterial(scanMesh));
            editableNodes.add(meshView);
        }

        editableMeshGroup = new Group();
        editableMeshGroup.getChildren().addAll(editableNodes);

        rxTransform = new Rotate(rotationX, Rotate.X_AXIS);
        ryTransform = new Rotate(rotationY, Rotate.Y_AXIS);
        rzTransform = new Rotate(rotationZ, Rotate.Z_AXIS);
        tTransform = new Translate(
                positionX,
                positionY,
                positionZ
        );

        editableMeshGroup.getTransforms().addAll(
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

        boneLabelsGroup = new Group();
        
        if (!lockedMeshes.isEmpty()) {
            loadBoneLabels();
        }

        return new Group(
                lockedMeshGroup,
                editableMeshGroup,
                ambientLight,
                pointLight,
                boneLabelsGroup,
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

                    // Only scale editable meshes, never locked/reference meshes
                    if (objectScalingMode && !editableMeshes.isEmpty()) {
                        ScanMesh firstMesh = editableMeshes.get(0);
                        firstMesh.setScale(Math.max(0.1f, firstMesh.getScale() + amount));
                        // We don't call updateTransforms on locked meshes so they stay unchanged
                        updateTransforms();
                    } else {
                        zoom(amount);
                    }
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
        updateTransforms();
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

    private void loadBoneLabels() {
        try {
            String skeletonJsonPath = "org/abc/models/skeleton.json";
            var resource = getClass().getClassLoader().getResource(skeletonJsonPath);

            if (resource != null) {
                File skeletonJsonFile = new File(resource.getPath());
                if (skeletonJsonFile.exists()) {
                    List<BoneData> bones = SkeletonJsonLoader.loadBoneData(skeletonJsonFile);

                    for (BoneData bone : bones) {
                        float[] center = bone.getBboxCenter();
                        if (center != null && center.length >= 3) {
                            Text label = createBoneLabel(bone.getName(), center[0], center[1], center[2]);
                            boneLabelsGroup.getChildren().add(label);
                        }
                    }

                    System.out.println("[INFO] Loaded " + bones.size() + " bone labels");
                    boneLabelsGroup.setVisible(boneLabelsVisible);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load bone labels: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Text createBoneLabel(String boneName, float x, float y, float z) {
        Text text = new Text(boneName);
        text.setTranslateX(x);
        text.setTranslateY(y);
        text.setTranslateZ(z);
        text.setStyle("-fx-font-size: 10px; -fx-fill: #FFD700;");
        text.setPickOnBounds(false);
        return text;
    }

    @Override
    public void setBoneLabelsVisible(boolean visible) {
        boneLabelsVisible = visible;

        Runnable updateTask = () -> {
            if (boneLabelsGroup != null) {
                boneLabelsGroup.setVisible(visible);
            }
        };

        if (Platform.isFxApplicationThread()) {
            updateTask.run();
        } else {
            Platform.runLater(updateTask);
        }
    }

    @Override
    public boolean isBoneLabelsVisible() {
        return boneLabelsVisible;
    }

    @Override
    public void addMeshes(List<ScanMesh> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return;
        }

        Runnable addTask = () -> {
            // Add to master list and to editable meshes (locked meshes remain separate)
            scanMeshes.addAll(meshes);
            for (ScanMesh m : meshes) {
                if (m.isLocked()) {
                    lockedMeshes.add(m);
                } else {
                    editableMeshes.add(m);
                }
            }

            // Add MeshView nodes to the editable mesh group so locked/reference meshes stay untouched
            Runnable uiAdd = () -> {
                Group root = null;
                if (subScene != null) {
                    root = (Group) subScene.getRoot();
                } else if (stage != null && stage.getScene() != null) {
                    root = (Group) stage.getScene().getRoot();
                }

                if (root != null) {
                    // editableMeshGroup should have been created in buildSceneRoot
                    if (editableMeshGroup == null) {
                        // Fallback: find second child if layout differs
                        if (root.getChildren().size() > 1 && root.getChildren().get(1) instanceof Group) {
                            editableMeshGroup = (Group) root.getChildren().get(1);
                        }
                    }

                    if (editableMeshGroup != null) {
                        for (ScanMesh scanMesh : meshes) {
                            if (scanMesh.isLocked()) {
                                continue;
                            }

                            TriangleMesh mesh = buildTriangleMesh(scanMesh);
                            MeshView meshView = new MeshView(mesh);
                            meshView.setMaterial(buildMaterial(scanMesh));
                            editableMeshGroup.getChildren().add(meshView);
                        }
                    }
                }
            };

            if (Platform.isFxApplicationThread()) {
                uiAdd.run();
            } else {
                Platform.runLater(uiAdd);
            }
        };

        if (Platform.isFxApplicationThread()) {
            addTask.run();
        } else {
            Platform.runLater(addTask);
        }
    }
}