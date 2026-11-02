package com.nokoding.deck;

/** Turns controller angular velocity into a small bounded tile sway that decays to rest. Pure Java. */
final class GyroFilter {
    static final float GAIN = 9f;    // degrees of tilt per radian turned
    static final float MAX = 7f;     // degrees
    static final float DECAY = 3f;   // per second

    float rx, ry;

    /**
     * Maps raw controller gyro values (rad/s, sensor axes 0..2) to the sway inputs:
     * out[0] drives rotationX (pitch), out[1] drives rotationY and the small sideways shift
     * (yaw plus roll). Negative out[0] tips the tile's top edge away (the sway points up),
     * negative out[1] tips the left edge away and shifts left (the sway points left).
     *
     * Field reports on the onn box with a DualSense:
     * - 2.0 (rx = -v0, ry = v1 + v2): left and right were mirrored.
     * - 2.1 (both axes of 2.0 inverted): left and right were right, up and down were mirrored.
     * Together that says a tilt up gives v0 > 0 and a tilt left gives v1 + v2 > 0, so 2.2 Follow
     * keeps the 2.1 horizontal sign and goes back to the 2.0 vertical sign. Opposite is Follow
     * with both axes flipped, so the setting still flips both together.
     */
    static void axes(float v0, float v1, float v2, boolean follow, float[] out) {
        float s = follow ? 1f : -1f;
        float x = -v0, y = -(v1 + v2);        // 2.2 Follow
        out[0] = s * x;
        out[1] = s * y;
        if (Float.isNaN(out[0]) || Float.isInfinite(out[0])) out[0] = 0f;
        if (Float.isNaN(out[1]) || Float.isInfinite(out[1])) out[1] = 0f;
    }

    /** Gyro strength setting as a factor (1 = the 2.1 feel). Scales both the gain and the limit. */
    float scale = 1f;

    static float clampScale(int percent) { return Math.max(25, Math.min(200, percent)) / 100f; }

    void update(float wx, float wy, float dt) {
        if (!(dt > 0f)) return;
        if (dt > 0.1f) dt = 0.1f;
        float sc = scale > 0f && scale <= 2f ? scale : 1f, max = MAX * sc;
        rx += wx * dt * GAIN * sc;
        ry += wy * dt * GAIN * sc;
        float k = (float) Math.exp(-DECAY * dt);
        rx *= k;
        ry *= k;
        rx = rx > max ? max : (rx < -max ? -max : rx);
        ry = ry > max ? max : (ry < -max ? -max : ry);
        if (Float.isNaN(rx) || Float.isNaN(ry)) { rx = 0f; ry = 0f; }
    }

    /** Tiny sideways shift in design px that goes with the sway. */
    float shiftX() { return ry * 0.9f; }

    boolean resting() { return Math.abs(rx) + Math.abs(ry) < 0.02f; }
}
