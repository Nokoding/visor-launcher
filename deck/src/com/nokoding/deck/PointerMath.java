package com.nokoding.deck;

/** Opera GX style hover push: tiles near the cursor move away from it. Pure Java, no allocation. */
final class PointerMath {
    private PointerMath() {}

    /**
     * Displacement for a tile whose center is (dx, dy) away from the cursor.
     * Falls off smoothly to 0 at radius; out[0], out[1] receive the push.
     */
    static void push(float dx, float dy, float radius, float max, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        if (!(radius > 0f) || !(max > 0f)) return;
        float d = (float) Math.hypot(dx, dy);
        if (!(d < radius) || d < 1e-3f) return; // outside, or exactly under the cursor: no direction
        float f = 1f - d / radius;
        float s = max * f * f * (3f - 2f * f); // smoothstep falloff
        out[0] = dx / d * s;
        out[1] = dy / d * s;
    }

    /** Ease toward a target; returns the new value. k in 0..1 per frame at 60 fps. */
    static float ease(float cur, float target, float k, float dt) {
        float kk = 1f - (float) Math.pow(1f - k, dt * 60f);
        return cur + (target - cur) * kk;
    }

    /** Angle in degrees from a center to a point, 0 = right, 90 = down (screen coordinates). */
    static float angle(float cx, float cy, float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
    }
}
