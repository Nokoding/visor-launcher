package com.nokoding.deck;

/**
 * 2.3: the AXES step of gyro calibration. Three guided gestures; for each, the bias corrected
 * raw rates are integrated per raw axis, and the axis that turned the most (and clearly more
 * than the others) is the raw axis behind that model axis, with its sign. Together they make a
 * signed permutation matrix M (model = M * raw). The model frame is right handed (X right,
 * Y up, Z toward the player) and so is Android's sensor frame, so a valid M also has
 * determinant +1. Anything else means a gesture went the wrong way: redo. Pure Java.
 */
final class AxisMap {
    static final String[] PROMPTS = {"TILT THE FRONT EDGE DOWN", "ROLL IT TO THE RIGHT", "TURN IT TO THE LEFT"};
    /** Model axis each gesture turns about (0 X pitch, 1 Y yaw, 2 Z roll). */
    static final int[] MODEL_AXIS = {0, 2, 1};
    /**
     * Sign of that turn in the model frame: front (far) edge down is negative pitch, rolling
     * to the right (right side down) is negative roll, turning left is positive yaw.
     */
    static final int[] MODEL_SIGN = {-1, -1, 1};
    static final float NEED = (float) Math.toRadians(40);
    static final float DOMINANCE = 2f;
    static final int RUNNING = 0, DONE = 1, MIXED = -1;

    final float[] ang = new float[3];
    int axis = -1, sign;

    void begin() {
        ang[0] = ang[1] = ang[2] = 0f;
        axis = -1;
        sign = 0;
    }

    /** Bias corrected raw rates. @return RUNNING, DONE (axis and sign set) or MIXED (redo this gesture). */
    int feed(float x, float y, float z, float dt) {
        if (axis >= 0) return DONE;
        if (!(dt > 0f) || !Attitude.finite(x) || !Attitude.finite(y) || !Attitude.finite(z)) return RUNNING;
        ang[0] += x * dt;
        ang[1] += y * dt;
        ang[2] += z * dt;
        int d = dominant(ang);
        float a = Math.abs(ang[d]), other = 0f;
        for (int i = 0; i < 3; i++) if (i != d) other = Math.max(other, Math.abs(ang[i]));
        if (a >= NEED && a >= DOMINANCE * other) {
            axis = d;
            sign = ang[d] > 0 ? 1 : -1;
            return DONE;
        }
        if (a >= 2.2f * NEED) return MIXED;   // turned a lot but no axis stands out
        return RUNNING;
    }

    float progress() {
        if (axis >= 0) return 1f;
        return Math.min(1f, Math.abs(ang[dominant(ang)]) / NEED);
    }

    static int dominant(float[] a) {
        int d = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(a[i]) > Math.abs(a[d])) d = i;
        return d;
    }

    /**
     * rawAxis[g] and rawSign[g] are the results of gesture g. Writes M (row major, model = M * raw).
     * @return true if M is a valid right handed signed permutation.
     */
    static boolean build(int[] rawAxis, int[] rawSign, float[] out) {
        for (int i = 0; i < 9; i++) out[i] = 0f;
        for (int g = 0; g < 3; g++) {
            int r = rawAxis[g];
            if (r < 0 || r > 2 || (rawSign[g] != 1 && rawSign[g] != -1)) return false;
            // the gesture turned the model by MODEL_SIGN about MODEL_AXIS and the raw axis read rawSign
            out[MODEL_AXIS[g] * 3 + r] = MODEL_SIGN[g] * rawSign[g];
        }
        return validSignedPerm(out);
    }

    /** Each row and each column holds exactly one +1 or -1, and the determinant is +1. */
    static boolean validSignedPerm(float[] m) {
        if (m == null || m.length != 9) return false;
        for (int r = 0; r < 3; r++) {
            int rowN = 0, colN = 0;
            for (int c = 0; c < 3; c++) {
                float v = m[r * 3 + c], w = m[c * 3 + r];
                if (v != 0f && v != 1f && v != -1f) return false;
                if (v != 0f) rowN++;
                if (w != 0f) colN++;
            }
            if (rowN != 1 || colN != 1) return false;
        }
        return Math.abs(Mat3D.det(m) - 1f) < 1e-4f;
    }
}
