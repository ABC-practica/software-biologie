package org.abc.service;

import org.abc.model.ScanMesh;

import java.io.IOException;
import java.nio.file.Path;

public interface Loader {
    ScanMesh load(Path path) throws IOException;
}
