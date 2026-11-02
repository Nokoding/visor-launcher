package com.nokoding.deck;

/**
 * Small allocation free 3D helpers for the gyro test: 3x3 rotation matrices (row major,
 * float[9]), perspective projection, the painter's sort and the tilt corner markers.
 * Axes follow the controller: X right, Y up out of the face, Z toward the player. Pure Java.
 */
final class Mat3D {
    private Mat3D() {}

    static void identity(float[] m) {
        m[0] = 1; m[1] = 0; m[2] = 0;
        m[3] = 0; m[4] = 1; m[5] = 0;
        m[6] = 0; m[7] = 0; m[8] = 1;
    }

    /** Rotation about X by a radians (right hand: Y turns toward Z). */
    static void rotX(float a, float[] m) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[0] = 1; m[1] = 0; m[2] = 0;
        m[3] = 0; m[4] = c; m[5] = -s;
        m[6] = 0; m[7] = s; m[8] = c;
    }

    /** Rotation about Y by a radians (Z turns toward X). */
    static void rotY(float a, float[] m) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[0] = c; m[1] = 0; m[2] = s;
        m[3] = 0; m[4] = 1; m[5] = 0;
        m[6] = -s; m[7] = 0; m[8] = c;
    }

    /** Rotation about Z by a radians (X turns toward Y). */
    static void rotZ(float a, float[] m) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[0] = c; m[1] = -s; m[2] = 0;
        m[3] = s; m[4] = c; m[5] = 0;
        m[6] = 0; m[7] = 0; m[8] = 1;
    }

    /** out = a * b. out may not be a or b. */
    static void mul(float[] a, float[] b, float[] out) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
    }

    /**
     * Pose from yaw (about Y, positive turns left), pitch (about X, positive tips the far edge
     * up) and roll (about Z, positive lifts the right side): R = Ry(yaw) * Rx(pitch) * Rz(roll).
     * t1 and t2 are scratch matrices.
     */
    static void euler(float yaw, float pitch, float roll, float[] out, float[] t1, float[] t2) {
        rotY(yaw, t1);
        rotX(pitch, t2);
        mul(t1, t2, out);
        rotZ(roll, t1);
        mul(out, t1, t2);
        System.arraycopy(t2, 0, out, 0, 9);
    }

    /** v' = m * (x, y, z), written to out[at..at+2]. */
    static void apply(float[] m, float x, float y, float z, float[] out, int at) {
        out[at] = m[0] * x + m[1] * y + m[2] * z;
        out[at + 1] = m[3] * x + m[4] * y + m[5] * z;
        out[at + 2] = m[6] * x + m[7] * y + m[8] * z;
    }

    static float det(float[] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    /**
     * Perspective: the camera sits at z = dist looking toward -z. Screen y grows downward.
     * Writes screen x and y to out[at], out[at+1]. @return false when the point is behind the camera.
     */
    static boolean project(float x, float y, float z, float dist, float focal, float cx, float cy, float[] out, int at) {
        float d = dist - z;
        if (d < 0.01f) { out[at] = cx; out[at + 1] = cy; return false; }
        float k = focal / d;
        out[at] = cx + x * k;
        out[at + 1] = cy - y * k;
        return true;
    }

    /** Sorts order[0..n) so the largest depth (farthest away) comes first. Insertion sort, no allocation. */
    static void sortFarFirst(float[] depth, int[] order, int n) {
        for (int i = 1; i < n; i++) {
            int o = order[i];
            float d = depth[o];
            int j = i - 1;
            while (j >= 0 && depth[order[j]] < d) { order[j + 1] = order[j]; j--; }
            order[j + 1] = o;
        }
    }

    static final int TL = 0, TR = 1, BL = 2, BR = 3, NO_CORNER = -1;

    /**
     * Which corner the tilt points into. right and up are tilt angles in degrees. The tilt must
     * be at least threshold degrees and lean clearly diagonal (each part at least 38 percent of it).
     */
    static int corner(float right, float up, float threshold) {
        if (Float.isNaN(right) || Float.isNaN(up)) return NO_CORNER;
        float mag = (float) Math.sqrt(right * right + up * up);
        if (mag < threshold || mag < 1e-6f) return NO_CORNER;
        if (Math.abs(right) < mag * 0.38f || Math.abs(up) < mag * 0.38f) return NO_CORNER;
        if (up > 0) return right < 0 ? TL : TR;
        return right < 0 ? BL : BR;
    }
}
