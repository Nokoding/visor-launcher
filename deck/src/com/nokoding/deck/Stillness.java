package com.nokoding.deck;

/**
 * 2.3: is the controller lying still? Running (exponentially weighted) mean and variance of
 * the gyro on each axis, and of the accelerometer magnitude. Still means: the gyro variance
 * is tiny, every gyro axis is close to its own mean, no axis turns faster than any real bias,
 * and (when there is an accelerometer) the reading is within 3 percent of 1 g and steady.
 * A bias is a constant offset, so it does not count as motion; noise and real turns do.
 * Pure Java, timestamps in nanoseconds from the sensor events.
 */
final class Stillness {
    static final float G = Attitude.G;
    /** Seconds of memory for the running mean and variance. */
    static final float TAU = 0.25f;
    /** Sum of the three gyro axis variances, (rad/s)^2. 0.02 rad/s of noise per axis passes. */
    static final float GYRO_VAR = 3 * 0.02f * 0.02f;
    /** A single sample this far from the running mean (rad/s) is motion. */
    static final float GYRO_JUMP = 0.08f;
    /** Larger than any real gyro bias, smaller than a slow deliberate turn (rad/s). */
    static final float GYRO_MAX = 0.15f;
    /** Accelerometer magnitude must be within this fraction of 1 g. */
    static final float ACC_TOL = 0.03f;
    /** Standard deviation of the accelerometer magnitude (m/s^2) for "steady". */
    static final float ACC_SD = 0.12f;
    /** Seconds of samples needed before it can say still. */
    static final float WARM_S = 0.3f;

    boolean hasAccel = true;
    final float[] mean = new float[3];
    private final float[] var = new float[3];
    private float accMean, accVar, warm;
    private boolean accSeen, gyroSeen, still, lastSampleOk = true, accOk;
    private long stillSince;

    void reset() {
        for (int i = 0; i < 3; i++) { mean[i] = 0f; var[i] = 0f; }
        accMean = 0f;
        accVar = 0f;
        warm = 0f;
        accSeen = gyroSeen = still = false;
        lastSampleOk = true;
        accOk = false;
        stillSince = 0;
    }

    void feedGyro(float x, float y, float z, float dt, long tsNs) {
        if (!Attitude.finite(x) || !Attitude.finite(y) || !Attitude.finite(z) || !(dt > 0f)) return;
        float k = dt / (TAU + dt);
        boolean jump = false;
        if (!gyroSeen) {
            mean[0] = x; mean[1] = y; mean[2] = z;
            gyroSeen = true;
        } else {
            jump = upd(0, x, k) | upd(1, y, k) | upd(2, z, k);
        }
        warm += dt;
        float v = var[0] + var[1] + var[2];
        boolean slow = Math.abs(mean[0]) < GYRO_MAX && Math.abs(mean[1]) < GYRO_MAX && Math.abs(mean[2]) < GYRO_MAX;
        lastSampleOk = !jump;
        boolean now = warm >= WARM_S && v < GYRO_VAR && !jump && slow && (!hasAccel || accOk);
        if (now && !still) stillSince = tsNs;
        still = now;
    }

    private boolean upd(int i, float x, float k) {
        float d = x - mean[i];
        mean[i] += k * d;
        var[i] = (1f - k) * (var[i] + k * d * d);
        return Math.abs(d) > GYRO_JUMP;
    }

    void feedAccel(float x, float y, float z, float dt, long tsNs) {
        if (!Attitude.finite(x) || !Attitude.finite(y) || !Attitude.finite(z)) return;
        float m = (float) Math.sqrt(x * x + y * y + z * z);
        if (!accSeen) {
            accMean = m;
            accVar = 0f;
            accSeen = true;
        } else if (dt > 0f) {
            float k = dt / (TAU + dt), d = m - accMean;
            accMean += k * d;
            accVar = (1f - k) * (accVar + k * d * d);
        }
        accOk = Math.abs(m - G) < ACC_TOL * G && Math.abs(accMean - G) < ACC_TOL * G && accVar < ACC_SD * ACC_SD;
        if (!accOk && still) still = false;
    }

    boolean still() { return still; }

    /** Seconds the controller has been still, 0 while moving. */
    float stillFor(long tsNs) { return still ? Math.max(0f, (tsNs - stillSince) / 1e9f) : 0f; }

    /** Running gyro variance, (rad/s)^2, for the readout. */
    float gyroVar() { return var[0] + var[1] + var[2]; }
}
