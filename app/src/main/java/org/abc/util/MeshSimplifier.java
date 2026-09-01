package org.abc.util;

import org.abc.model.ScanMesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class MeshSimplifier {
    private static final double EPSILON = 1.0e-9;
    private static final double BOUNDARY_PENALTY = 1_000_000.0;

    private MeshSimplifier() {
    }

    public static ScanMesh simplify(
            ScanMesh mesh,
            int targetVertexCount
    ) {
        if (mesh == null) {
            return null;
        }

        float[] originalVertices = mesh.getVertices();
        if (originalVertices == null || originalVertices.length == 0) {
            return mesh;
        }

        int[] originalIndices = mesh.getIndices();
        if (originalIndices == null || originalIndices.length == 0) {
            return new ScanMesh(
                    originalVertices.clone(),
                    new int[0],
                    mesh.getTextureCoordinates(),
                    mesh.getVertexMaterials()
            );
        }

        int originalVertexCount = originalVertices.length / 3;
        if (targetVertexCount >= originalVertexCount) {
            return new ScanMesh(
                    originalVertices.clone(),
                    originalIndices.clone(),
                    mesh.getTextureCoordinates(),
                    mesh.getVertexMaterials()
            );
        }

        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < originalVertices.length; i += 3) {
            vertices.add(new Vertex(
                    originalVertices[i],
                    originalVertices[i + 1],
                    originalVertices[i + 2]
            ));
        }

        List<int[]> triangles = new ArrayList<>();
        for (int i = 0; i < originalIndices.length; i += 3) {
            int a = originalIndices[i];
            int b = originalIndices[i + 1];
            int c = originalIndices[i + 2];

            if (a < 0 || b < 0 || c < 0 || a >= vertices.size() || b >= vertices.size() || c >= vertices.size()) {
                continue;
            }

            if (a == b || b == c || c == a) {
                continue;
            }

            triangles.add(new int[]{a, b, c});
        }

        if (triangles.isEmpty()) {
            return new ScanMesh(
                    originalVertices.clone(),
                    new int[0],
                    mesh.getTextureCoordinates(),
                    mesh.getVertexMaterials()
            );
        }

        boolean[] active = new boolean[vertices.size()];
        Arrays.fill(active, true);

        while (usedVertexCount(triangles, active) > targetVertexCount && !triangles.isEmpty()) {
            Candidate candidate = findBestCandidate(vertices, triangles, active);
            if (candidate == null) {
                break;
            }

            applyCollapse(vertices, triangles, active, candidate);
        }

        Map<Integer, Integer> remap = new TreeMap<>();
        int nextIndex = 0;
        for (int i = 0; i < vertices.size(); i++) {
            if (active[i]) {
                remap.put(i, nextIndex++);
            }
        }

        List<Integer> compactIndices = new ArrayList<>();
        for (int[] triangle : triangles) {
            if (!active[triangle[0]] || !active[triangle[1]] || !active[triangle[2]]) {
                continue;
            }

            compactIndices.add(remap.get(triangle[0]));
            compactIndices.add(remap.get(triangle[1]));
            compactIndices.add(remap.get(triangle[2]));
        }

        float[] simplifiedVertices = new float[remap.size() * 3];
        for (Map.Entry<Integer, Integer> entry : remap.entrySet()) {
            int oldIndex = entry.getKey();
            int newIndex = entry.getValue();
            Vertex vertex = vertices.get(oldIndex);
            simplifiedVertices[newIndex * 3] = (float) vertex.x;
            simplifiedVertices[newIndex * 3 + 1] = (float) vertex.y;
            simplifiedVertices[newIndex * 3 + 2] = (float) vertex.z;
        }

        int[] simplifiedIndexArray = new int[compactIndices.size()];
        for (int i = 0; i < compactIndices.size(); i++) {
            simplifiedIndexArray[i] = compactIndices.get(i);
        }

        return new ScanMesh(
                simplifiedVertices,
                simplifiedIndexArray,
                mesh.getTextureCoordinates(),
                mesh.getVertexMaterials()
        );
    }

    private static int usedVertexCount(
            List<int[]> triangles,
            boolean[] active
    ) {
        boolean[] used = new boolean[active.length];
        for (int[] triangle : triangles) {
            if (triangle == null) {
                continue;
            }

            for (int vertexIndex : triangle) {
                if (vertexIndex >= 0 && vertexIndex < active.length && active[vertexIndex]) {
                    used[vertexIndex] = true;
                }
            }
        }

        int count = 0;
        for (boolean value : used) {
            if (value) {
                count++;
            }
        }

        return count;
    }

    private static Candidate findBestCandidate(
            List<Vertex> vertices,
            List<int[]> triangles,
            boolean[] active
    ) {
        Map<Long, Integer> edgeCounts = new HashMap<>();
        for (int[] triangle : triangles) {
            addEdge(edgeCounts, triangle[0], triangle[1]);
            addEdge(edgeCounts, triangle[1], triangle[2]);
            addEdge(edgeCounts, triangle[2], triangle[0]);
        }

        double[][] quadric = buildQuadrics(vertices, triangles, active);

        Candidate best = null;
        for (Map.Entry<Long, Integer> entry : edgeCounts.entrySet()) {
            int a = decodeFirst(entry.getKey());
            int b = decodeSecond(entry.getKey());

            if (!active[a] || !active[b] || a == b) {
                continue;
            }

            double[] merged = addQuadrics(quadric[a], quadric[b]);
            double[] target = optimalPoint(merged, vertices.get(a), vertices.get(b));
            double error = quadricError(merged, target[0], target[1], target[2]);

            if (edgeCounts.get(entry.getKey()) == 1) {
                error += BOUNDARY_PENALTY;
            }

            int keep = a;
            int remove = b;
            if (a > b) {
                keep = b;
                remove = a;
            }

            Candidate candidate = new Candidate(keep, remove, target, error);
            if (best == null || candidate.cost < best.cost) {
                best = candidate;
            }
        }

        return best;
    }

    private static void applyCollapse(
            List<Vertex> vertices,
            List<int[]> triangles,
            boolean[] active,
            Candidate candidate
    ) {
        if (candidate == null || candidate.keep == candidate.remove) {
            return;
        }

        Vertex keepVertex = vertices.get(candidate.keep);
        keepVertex.x = candidate.target[0];
        keepVertex.y = candidate.target[1];
        keepVertex.z = candidate.target[2];

        List<int[]> updated = new ArrayList<>();
        for (int[] triangle : triangles) {
            int[] nextTriangle = triangle.clone();
            for (int i = 0; i < 3; i++) {
                if (nextTriangle[i] == candidate.remove) {
                    nextTriangle[i] = candidate.keep;
                }
            }

            if (nextTriangle[0] == nextTriangle[1]
                    || nextTriangle[1] == nextTriangle[2]
                    || nextTriangle[2] == nextTriangle[0]) {
                continue;
            }

            if (triangleArea(vertices, nextTriangle) <= EPSILON) {
                continue;
            }

            updated.add(nextTriangle);
        }

        triangles.clear();
        triangles.addAll(updated);
        active[candidate.remove] = false;
    }

    private static double[][] buildQuadrics(
            List<Vertex> vertices,
            List<int[]> triangles,
            boolean[] active
    ) {
        double[][] quadrics = new double[vertices.size()][16];

        for (int[] triangle : triangles) {
            if (!active[triangle[0]] || !active[triangle[1]] || !active[triangle[2]]) {
                continue;
            }

            double[] plane = planeForTriangle(vertices, triangle);
            if (plane == null) {
                continue;
            }

            double[] quadric = planeQuadric(plane[0], plane[1], plane[2], plane[3]);
            addQuadric(quadrics[triangle[0]], quadric);
            addQuadric(quadrics[triangle[1]], quadric);
            addQuadric(quadrics[triangle[2]], quadric);
        }

        return quadrics;
    }

    private static double[] planeForTriangle(
            List<Vertex> vertices,
            int[] triangle
    ) {
        Vertex a = vertices.get(triangle[0]);
        Vertex b = vertices.get(triangle[1]);
        Vertex c = vertices.get(triangle[2]);

        double ux = b.x - a.x;
        double uy = b.y - a.y;
        double uz = b.z - a.z;

        double vx = c.x - a.x;
        double vy = c.y - a.y;
        double vz = c.z - a.z;

        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= EPSILON) {
            return null;
        }

        nx /= length;
        ny /= length;
        nz /= length;

        double d = -(nx * a.x + ny * a.y + nz * a.z);
        return new double[]{nx, ny, nz, d};
    }

    private static double[] planeQuadric(
            double nx,
            double ny,
            double nz,
            double d
    ) {
        double[] quadric = new double[16];

        quadric[0] = nx * nx;
        quadric[1] = nx * ny;
        quadric[2] = nx * nz;
        quadric[3] = nx * d;

        quadric[4] = nx * ny;
        quadric[5] = ny * ny;
        quadric[6] = ny * nz;
        quadric[7] = ny * d;

        quadric[8] = nx * nz;
        quadric[9] = ny * nz;
        quadric[10] = nz * nz;
        quadric[11] = nz * d;

        quadric[12] = nx * d;
        quadric[13] = ny * d;
        quadric[14] = nz * d;
        quadric[15] = d * d;

        return quadric;
    }

    private static double[] addQuadrics(
            double[] left,
            double[] right
    ) {
        double[] merged = new double[16];
        for (int i = 0; i < 16; i++) {
            merged[i] = left[i] + right[i];
        }
        return merged;
    }

    private static void addQuadric(
            double[] target,
            double[] source
    ) {
        for (int i = 0; i < 16; i++) {
            target[i] += source[i];
        }
    }

    private static double[] optimalPoint(
            double[] quadric,
            Vertex a,
            Vertex b
    ) {
        double[][] matrix = new double[][]{
                {quadric[0], quadric[1], quadric[2]},
                {quadric[4], quadric[5], quadric[6]},
                {quadric[8], quadric[9], quadric[10]}
        };

        double[] vector = new double[]{
                quadric[3],
                quadric[7],
                quadric[11]
        };

        if (determinant3x3(matrix) > EPSILON) {
            double[] solution = solveLinearSystem(matrix, vector, true);
            if (solution != null) {
                return new double[]{solution[0], solution[1], solution[2]};
            }
        }

        double midpointX = (a.x + b.x) / 2.0;
        double midpointY = (a.y + b.y) / 2.0;
        double midpointZ = (a.z + b.z) / 2.0;
        return new double[]{midpointX, midpointY, midpointZ};
    }

    private static double[] solveLinearSystem(
            double[][] matrix,
            double[] vector,
            boolean negate
    ) {
        double[][] augmented = new double[3][4];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                augmented[row][col] = matrix[row][col];
            }
            augmented[row][3] = negate ? -vector[row] : vector[row];
        }

        for (int pivot = 0; pivot < 3; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < 3; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[maxRow][pivot])) {
                    maxRow = row;
                }
            }

            if (Math.abs(augmented[maxRow][pivot]) <= EPSILON) {
                return null;
            }

            if (maxRow != pivot) {
                double[] tmp = augmented[pivot];
                augmented[pivot] = augmented[maxRow];
                augmented[maxRow] = tmp;
            }

            double pivotValue = augmented[pivot][pivot];
            for (int col = pivot; col < 4; col++) {
                augmented[pivot][col] /= pivotValue;
            }

            for (int row = 0; row < 3; row++) {
                if (row == pivot) {
                    continue;
                }

                double factor = augmented[row][pivot];
                for (int col = pivot; col < 4; col++) {
                    augmented[row][col] -= factor * augmented[pivot][col];
                }
            }
        }

        return new double[]{
                augmented[0][3],
                augmented[1][3],
                augmented[2][3]
        };
    }

    private static double determinant3x3(double[][] matrix) {
        return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
    }

    private static double quadricError(
            double[] quadric,
            double x,
            double y,
            double z
    ) {
        double[] point = new double[]{x, y, z, 1.0};
        double error = 0.0;

        for (int row = 0; row < 4; row++) {
            double sum = 0.0;
            for (int col = 0; col < 4; col++) {
                sum += quadric[row * 4 + col] * point[col];
            }
            error += point[row] * sum;
        }

        return error;
    }

    private static double triangleArea(
            List<Vertex> vertices,
            int[] triangle
    ) {
        Vertex a = vertices.get(triangle[0]);
        Vertex b = vertices.get(triangle[1]);
        Vertex c = vertices.get(triangle[2]);

        double ux = b.x - a.x;
        double uy = b.y - a.y;
        double uz = b.z - a.z;

        double vx = c.x - a.x;
        double vy = c.y - a.y;
        double vz = c.z - a.z;

        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;

        return 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    private static void addEdge(
            Map<Long, Integer> edgeCounts,
            int a,
            int b
    ) {
        if (a == b) {
            return;
        }

        int first = Math.min(a, b);
        int second = Math.max(a, b);
        long key = (((long) first) << 32) | (second & 0xffffffffL);
        edgeCounts.put(key, edgeCounts.getOrDefault(key, 0) + 1);
    }

    private static int decodeFirst(long key) {
        return (int) (key >>> 32);
    }

    private static int decodeSecond(long key) {
        return (int) key;
    }

    private static final class Vertex {
        private double x;
        private double y;
        private double z;

        private Vertex(
                double x,
                double y,
                double z
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Candidate {
        private final int keep;
        private final int remove;
        private final double[] target;
        private final double cost;

        private Candidate(
                int keep,
                int remove,
                double[] target,
                double cost
        ) {
            this.keep = keep;
            this.remove = remove;
            this.target = target;
            this.cost = cost;
        }
    }
}
