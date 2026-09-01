package org.abc.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.abc.component.FileUploadModal;
import org.abc.service.RendererControl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class ToolboxController {

    @FXML
    private VBox root;

    @FXML
    private ToggleButton objectRotationToggle;

    @FXML
    private ToggleButton objectScalingToggle;

    @FXML
    private Slider simplificationSlider;

    @FXML
    private Label simplificationLabel;

    private Consumer<List<File>> filesSelected;
    private RendererControl window;

    @FXML
    private void initialize() {
        if (simplificationSlider != null) {
            simplificationSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (window != null) {
                    int targetVertexCount = (int) Math.round(newValue.doubleValue());
                    System.out.println("[INFO] Slider changed -> simplifying selected object to " + targetVertexCount + " vertices");
                    simplificationLabel.setText("Simplifying to " + String.format("%,d", targetVertexCount) + " vertices...");

                    window.setSelectedObjectTargetVertexCountAsync(targetVertexCount)
                            .thenRun(() -> Platform.runLater(() -> {
                                window.refresh();
                                updateSimplificationLabel();
                                System.out.println("[INFO] Renderer refresh requested after simplification.");
                            }));
                }
            });
        }
    }

    public void setFilesSelected(Consumer<List<File>> filesSelected) {
        this.filesSelected = filesSelected;
    }

    public void setWindow(RendererControl window) {
        this.window = window;

        if (window != null) {
            updateToggleState();
            updateSimplificationState();
        }
    }

    @FXML
    private void handleUpload() {
        try {
            Window owner = root.getScene().getWindow();

            FileUploadModal modal = new FileUploadModal();
            List<File> files = modal.show(owner);

            if (files != null && !files.isEmpty() && filesSelected != null) {
                filesSelected.accept(files);
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to open file upload modal",
                    e
            );
        }
    }

    @FXML
    private void handleClose() {
        if (window != null) {
            window.close();
            window = null;
        }
    }

    @FXML
    private void handleRefresh() {
        if (window != null) {
            window.refresh();
        }
    }

    @FXML
    private void handleResetCamera() {
        if (window != null) {
            window.resetCamera();
        }
    }

    @FXML
    private void handleToggleObjectRotation() {
        if (window == null) {
            return;
        }

        boolean enabled = objectRotationToggle.isSelected();

        window.setObjectRotationEnabled(enabled);
    }

    @FXML
    private void handleToggleObjectScaling() {
        if (window == null) {
            return;
        }

        boolean enabled = objectScalingToggle.isSelected();

        window.setObjectScalingEnabled(enabled);
    }

    private void updateToggleState() {
        if (objectRotationToggle != null) {
            objectRotationToggle.setSelected(
                    window.isObjectRotationEnabled()
            );
        }

        if (objectScalingToggle != null) {
            objectScalingToggle.setSelected(
                    window.isObjectScalingEnabled()
            );
        }
    }

    private void updateSimplificationState() {
        if (window == null || simplificationSlider == null) {
            return;
        }

        int maxVertices = Math.max(1, window.getSelectedObjectMaxVertexCount());
        int currentTarget = Math.max(1, Math.min(maxVertices, window.getSelectedObjectTargetVertexCount()));

        simplificationSlider.setMin(1);
        simplificationSlider.setMax(maxVertices);
        simplificationSlider.setValue(currentTarget);
        updateSimplificationLabel();
    }

    private void updateSimplificationLabel() {
        if (simplificationLabel == null || simplificationSlider == null) {
            return;
        }

        int currentTarget = (int) Math.round(simplificationSlider.getValue());
        int maxVertices = (int) Math.round(simplificationSlider.getMax());

        simplificationLabel.setText(
                String.format("%,d / %,d vertices", currentTarget, maxVertices)
        );
    }
}