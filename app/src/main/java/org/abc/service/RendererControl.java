package org.abc.service;

public interface RendererControl {

    void open();

    void close();

    void reload();

    void resetCamera();

    void refresh();

    void setObjectRotationEnabled(boolean enabled);

    boolean isObjectRotationEnabled();

    void setAxesVisible(boolean visible);

    boolean isAxesVisible();

    void setObjectScalingEnabled(boolean enabled);

    boolean isObjectScalingEnabled();

    void setSelectedObjectTargetVertexCount(int targetVertexCount);

    default java.util.concurrent.CompletableFuture<Void> setSelectedObjectTargetVertexCountAsync(int targetVertexCount) {
        setSelectedObjectTargetVertexCount(targetVertexCount);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    int getSelectedObjectTargetVertexCount();

    int getSelectedObjectMaxVertexCount();
}