package com.nokoding.deck;

/**
 * 2.3: gyro zero rate (bias) estimation. A resting gyro reads a small steady rate; integrated
 * that becomes drift, worst on yaw because gravity cannot correct it.
 *
 * Collector: the BIAS step. Averages COLLECT_S seconds of raw rates while Stillness says the
 * controller is still. Any motion restarts it, so a bump never ends up in the bias.
 * Also averages the accelerometer over the same samples, the gravity direction for the AXES
 * sanity check.
 *
 * online(): after the controller has been still for ONLINE_AFTER_S, nudges the bias toward the
 * current reading with a slow average (ONLINE_TAU_S). Never while moving, and never by a big
 * step, so a slow steady turn cannot be mistaken for bias. Pure Java.
 */
final class GyroBias {
    static final float COLLECT_S = 3f;
    static final float ONLINE_AFTER_S = 1f;
    static final float ONLINE_TAU_S = 20f;
    /** Online updates only accept readings this close to the current bias (rad/s per axis). */
    static final float ONLINE_NEAR = 0.03f;

    private double sx, sy, sz, ax, ay, az;
    private int n, na;
    private float collected;
    /** How many times motion restarted the collection (for the "moved, starting over" prompt). */
    int restarts;
    boolean done;

    void reset() {
        sx = sy = sz = ax = ay = az = 0;
        n = na = 0;
        collected = 0f;
        done = false;
    }

    /** Feed one gyro sample. @return true once COLLECT_S of still samples are in. */
    boolean feed(float x, float y, float z, float dt, boolean still) {
        if (done) return true;
        if (!still) {
            if (n > 0) restarts++;
            reset();
            return false;
        }
        if (!(dt > 0f)) return false;
        sx += x; sy += y; sz += z;
        n++;
        collected += dt;
        if (collected >= COLLECT_S && n > 0) done = true;
        return done;
    }

    void feedAccel(float x, float y, float z) {
        if (n == 0 || done) return;
        ax += x; ay += y; az += z;
        na++;
    }

    float progress() { return done ? 1f : Math.min(1f, collected / COLLECT_S); }

    float bx() { return n == 0 ? 0f : (float) (sx / n); }

    float by() { return n == 0 ? 0f : (float) (sy / n); }

    float bz() { return n == 0 ? 0f : (float) (sz / n); }

    boolean hasGravity() { return na > 0; }

    float gx() { return na == 0 ? 0f : (float) (ax / na); }

    float gy() { return na == 0 ? 0f : (float) (ay / na); }

    float gz() { return na == 0 ? 0f : (float) (az / na); }

    /**
     * Online zero rate tracking. raw rates in rad/s, dt in seconds.
     * @return true if the bias was nudged.
     */
    static boolean online(GyroCal c, float x, float y, float z, float dt, float stillFor) {
        if (c == null || stillFor < ONLINE_AFTER_S || !(dt > 0f)) return false;
        if (!Attitude.finite(x) || !Attitude.finite(y) || !Attitude.finite(z)) return false;
        if (Math.abs(x - c.bx) > ONLINE_NEAR || Math.abs(y - c.by) > ONLINE_NEAR || Math.abs(z - c.bz) > ONLINE_NEAR) return false;
        float k = Math.min(dt, 0.05f) / (ONLINE_TAU_S + dt);
        c.setBias(c.bx + k * (x - c.bx), c.by + k * (y - c.by), c.bz + k * (z - c.bz));
        return true;
    }
}
