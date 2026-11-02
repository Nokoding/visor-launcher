package com.nokoding.deck;

/**
 * Controller orientation for the gyro test: a complementary filter. The gyroscope (rad/s)
 * is integrated for pitch, roll and yaw. The accelerometer (m/s^2, it reads +g straight up
 * when the controller is still) pulls pitch and roll back toward gravity with time constant
 * TAU, so gyro drift cannot build up. Yaw has no gravity reference, so it drifts slowly;
 * Recenter zeroes it. Axes: X right, Y up out of the face, Z toward the player, the same
 * axes GyroFilter uses. 2.2 assumed the raw sensor axes were already these; 2.3 feeds it
 * values mapped by the controller's GyroCal (bias removed, axes measured), and only lets the
 * accelerometer correct while the reading is close to 1 g and the controller turns slowly.
 * settleYaw() slowly recenters yaw, only while the controller is still. Pure Java.
 */
final class Attitude {
    static final float G = 9.81f;
    /** Seconds. Short enough to remove drift, long enough to ignore hand shake. */
    static final float TAU = 0.6f;
    /** 2.3 accelerometer gate: only within 0.9 to 1.1 g ... */
    static final float ACC_LO = 0.9f, ACC_HI = 1.1f;
    /** ... and only while the gyro turns slower than this (rad/s, about 29 degrees a second). */
    static final float RATE_GATE = 0.5f;
    /** Seconds for the still-only yaw recenter. */
    static final float YAW_TAU = 8f;

    /** Absolute angles in radians (pitch and roll against gravity, yaw from the start). */
    float pitch, roll, yaw;
    /** Neutral pose set by recenter(). */
    float pitch0, roll0, yaw0;
    /** Last raw rates in rad/s for the readout. */
    float rateX, rateY, rateZ;
    boolean seeded;

    static float accelPitch(float ax, float ay, float az) {
        return (float) Math.atan2(-az, Math.sqrt(ax * ax + ay * ay));
    }

    static float accelRoll(float ax, float ay) {
        return (float) Math.atan2(ax, ay);
    }

    static float wrap(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }

    static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }

    /** Gyro step. dt in seconds, clamped so a pause does not jump. */
    void gyro(float gx, float gy, float gz, float dt) {
        if (!finite(gx) || !finite(gy) || !finite(gz) || !(dt > 0f)) return;
        if (dt > 0.1f) dt = 0.1f;
        rateX = gx;
        rateY = gy;
        rateZ = gz;
        pitch = wrap(pitch + gx * dt);
        yaw = wrap(yaw + gy * dt);
        roll = wrap(roll + gz * dt);
    }

    /**
     * Accelerometer step: nudges pitch and roll toward what gravity says. Ignored while the
     * controller is shaken (reading far from 1 g). The first good reading seeds the angles.
     * @return true if the reading was used.
     */
    boolean accel(float ax, float ay, float az, float dt) {
        if (!finite(ax) || !finite(ay) || !finite(az)) return false;
        float mag = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (mag < ACC_LO * G || mag > ACC_HI * G) return false;
        if (seeded && rate() > RATE_GATE) return false;   // fast motion: the reading is not just gravity
        float p = accelPitch(ax, ay, az), r = accelRoll(ax, ay);
        boolean rollOk = Math.cos(p) > 0.35;   // near vertical, roll from gravity is unstable
        if (!seeded) {
            pitch = p;
            if (rollOk) roll = r;
            seeded = true;
            return true;
        }
        if (!(dt > 0f)) return false;
        if (dt > 0.1f) dt = 0.1f;
        float k = dt / (TAU + dt);
        pitch = wrap(pitch + k * wrap(p - pitch));
        if (rollOk) roll = wrap(roll + k * wrap(r - roll));
        return true;
    }

    /** Magnitude of the last gyro rates, rad/s. */
    float rate() { return (float) Math.sqrt(rateX * rateX + rateY * rateY + rateZ * rateZ); }

    /**
     * 2.3: yaw has no reference, so while the controller is still the neutral yaw slowly
     * follows the current yaw (time constant YAW_TAU). Call only when Stillness says still.
     */
    void settleYaw(float dt) {
        if (!(dt > 0f)) return;
        if (dt > 0.1f) dt = 0.1f;
        float k = dt / (YAW_TAU + dt);
        yaw0 = wrap(yaw0 + k * wrap(yaw - yaw0));
    }

    /** The current pose becomes neutral. */
    void recenter() {
        pitch0 = pitch;
        roll0 = roll;
        yaw0 = yaw;
    }

    float relPitch() { return wrap(pitch - pitch0); }

    float relRoll() { return wrap(roll - roll0); }

    float relYaw() { return wrap(yaw - yaw0); }

    static float deg(float rad) { return (float) Math.toDegrees(rad); }

    /** Tilt to the right in degrees (rolling the right side down), for the level and corners. */
    float tiltRight() { return -deg(relRoll()); }

    /** Tilt up in degrees (far edge up). */
    float tiltUp() { return deg(relPitch()); }
}
