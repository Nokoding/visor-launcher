package com.nokoding.deck;

/**
 * 2.3: the CHECK step of gyro calibration. Hold still (the pose becomes the start), turn the
 * controller about 90 degrees any way, bring it back and hold still. The leftover angle
 * between the start pose and the end pose is what calibration did not fix. Pure Java.
 */
final class CalCheck {
    static final int HOLD = 0, ROTATE = 1, RETURN = 2, DONE = 3;
    static final float HOLD_S = 0.5f, BACK_S = 0.8f;
    static final float TURN_DEG = 60f, BACK_DEG = 40f;

    int state = HOLD;
    float maxDeg, errDeg = -1f;
    private final float[] r = new float[9], t1 = new float[9], t2 = new float[9];

    void reset() {
        state = HOLD;
        maxDeg = 0f;
        errDeg = -1f;
    }

    /**
     * @param angleDeg rotation from the start pose, from angleDeg(), ignored while HOLD
     * @return true when the caller should recenter (the start pose is taken now)
     */
    boolean feed(float angleDeg, boolean still, float stillFor) {
        switch (state) {
            case HOLD:
                if (still && stillFor >= HOLD_S) { state = ROTATE; maxDeg = 0f; return true; }
                return false;
            case ROTATE:
                maxDeg = Math.max(maxDeg, angleDeg);
                if (maxDeg >= TURN_DEG) state = RETURN;
                return false;
            case RETURN:
                maxDeg = Math.max(maxDeg, angleDeg);
                if (still && stillFor >= BACK_S && angleDeg < BACK_DEG) { errDeg = angleDeg; state = DONE; }
                return false;
            default:
                return false;
        }
    }

    /** Total rotation angle in degrees of the pose Ry(yaw) Rx(pitch) Rz(roll) (radians in). */
    float angleDeg(float yaw, float pitch, float roll) {
        Mat3D.euler(yaw, pitch, roll, r, t1, t2);
        double c = (r[0] + r[4] + r[8] - 1.0) / 2.0;
        c = Math.max(-1.0, Math.min(1.0, c));
        return (float) Math.toDegrees(Math.acos(c));
    }
}
