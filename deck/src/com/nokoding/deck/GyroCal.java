package com.nokoding.deck;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 2.3: one controller's gyro calibration. Bias (rad/s, per raw sensor axis) and a signed
 * permutation matrix M that turns raw sensor axes into Deck's model axes:
 * X right (pitch, positive tips the far edge up), Y up out of the face (yaw, positive turns
 * left), Z toward the player (roll, positive lifts the right side). model = M * (raw - bias).
 * The accelerometer uses the same M (both sensors share the IMU's axes), times accSign, which
 * the flat step measures. accOk false means gravity did not land on the model's up axis, so
 * the accelerometer is not trusted for tilt correction.
 *
 * The default (not calibrated) is zero bias and the identity, which is exactly the 2.2
 * assumption, so an uncalibrated controller behaves like 2.2. Pure Java.
 */
final class GyroCal {
    static final String PREFIX = "gyrocal.";
    static final float MAX_BIAS = 0.1f;   // rad/s; anything bigger is not a resting gyro

    float bx, by, bz;
    final float[] m = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    int accSign = 1;
    boolean accOk = true;
    /** Wall clock time of SAVE in ms, 0 when not calibrated. */
    long savedAt;
    /** Leftover angle of the CHECK step in degrees, -1 when not measured. */
    float checkErr = -1f;

    boolean calibrated() { return savedAt > 0; }

    void copy(GyroCal o) {
        bx = o.bx; by = o.by; bz = o.bz;
        System.arraycopy(o.m, 0, m, 0, 9);
        accSign = o.accSign;
        accOk = o.accOk;
        savedAt = o.savedAt;
        checkErr = o.checkErr;
    }

    /** out = M * (raw - bias). out has 3 slots. */
    void gyro(float x, float y, float z, float[] out) {
        Mat3D.apply(m, x - bx, y - by, z - bz, out, 0);
    }

    /** out = accSign * M * raw. */
    void accel(float x, float y, float z, float[] out) {
        Mat3D.apply(m, x, y, z, out, 0);
        if (accSign < 0) { out[0] = -out[0]; out[1] = -out[1]; out[2] = -out[2]; }
    }

    float biasMag() { return (float) Math.sqrt(bx * bx + by * by + bz * bz); }

    void setBias(float x, float y, float z) {
        bx = clampBias(x);
        by = clampBias(y);
        bz = clampBias(z);
    }

    static float clampBias(float v) {
        if (!Attitude.finite(v)) return 0f;
        return Math.max(-MAX_BIAS, Math.min(MAX_BIAS, v));
    }

    /**
     * Gravity measured while the controller lay flat (raw accel) must point along +Y after M.
     * Sets accSign and accOk. @return +1 right way up, -1 flipped (fixed by accSign), 0 not on Y.
     */
    int gravityCheck(float gx, float gy, float gz) {
        float[] o = new float[3];
        Mat3D.apply(m, gx, gy, gz, o, 0);
        float mag = (float) Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]);
        if (!(mag > 0.5f * Attitude.G)) { accOk = false; accSign = 1; return 0; }
        float up = o[1] / mag;
        if (up > 0.9f) { accSign = 1; accOk = true; return 1; }
        if (up < -0.9f) { accSign = -1; accOk = true; return -1; }
        accSign = 1;
        accOk = false;
        return 0;
    }

    /** Preference key for one controller: vendor, product and the first part of the device descriptor. */
    static String key(int vendor, int product, String descriptor) {
        String d = descriptor == null ? "" : descriptor.replaceAll("[^A-Za-z0-9]", "");
        if (d.length() > 16) d = d.substring(0, 16);
        return String.format(Locale.ROOT, "%s%04x.%04x.%s", PREFIX, vendor & 0xFFFF, product & 0xFFFF, d.isEmpty() ? "any" : d);
    }

    /** v1;bx;by;bz;m0..m8;accSign;accOk;savedAt;checkErr */
    String encode() {
        StringBuilder s = new StringBuilder("v1");
        s.append(';').append(bx).append(';').append(by).append(';').append(bz);
        for (float v : m) s.append(';').append((int) v);
        s.append(';').append(accSign).append(';').append(accOk ? 1 : 0).append(';').append(savedAt).append(';').append(checkErr);
        return s.toString();
    }

    /** @return the stored calibration, or null when missing, broken or not a valid signed permutation. */
    static GyroCal decode(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length != 17 || !"v1".equals(p[0])) return null;
        try {
            GyroCal c = new GyroCal();
            c.setBias(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]));
            for (int i = 0; i < 9; i++) c.m[i] = Integer.parseInt(p[4 + i]);
            c.accSign = Integer.parseInt(p[13]) < 0 ? -1 : 1;
            c.accOk = "1".equals(p[14]);
            c.savedAt = Long.parseLong(p[15]);
            c.checkErr = Float.parseFloat(p[16]);
            if (!AxisMap.validSignedPerm(c.m)) return null;
            return c;
        } catch (RuntimeException bad) {
            return null;
        }
    }

    /** One status line for Setup and the gyro test. */
    String status() {
        if (!calibrated()) return "NOT CALIBRATED";
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(savedAt));
        StringBuilder s = new StringBuilder("CALIBRATED ").append(date).append("  //  BIAS ");
        s.append(String.format(Locale.ROOT, "%.4f", biasMag())).append(" RAD/S");
        if (!accOk) s.append("  //  TILT CORRECTION OFF");
        return s.toString();
    }
}
