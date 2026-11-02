package com.nokoding.deck;

import android.annotation.TargetApi;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.InputDevice;

/**
 * Controller gyro sway (Android 12+, InputDevice.getSensorManager). Only created on
 * API 31 and up. Registered while in gamepad mode and resumed; everything is guarded
 * so a box or controller without sensors just reports why.
 *
 * 2.3: raw rates go through the controller's saved GyroCal (bias removed, measured axes)
 * before GyroFilter.axes. The time step comes from SensorEvent.timestamp. While the controller
 * lies still for a second or more (gyro only Stillness, no accelerometer is registered here),
 * the bias is nudged online; a calibrated controller keeps that on stop().
 */
@TargetApi(31)
final class Gyro implements SensorEventListener {
    final GyroFilter filter = new GyroFilter();
    private SensorManager sm;
    int deviceId = -1;
    boolean active;
    String status = "NO GAMEPAD YET";
    private volatile float wx, wy;
    private volatile long lastEvent;
    /** Gyro direction setting: true = Follow (default), false = Opposite (both axes flipped). */
    volatile boolean follow = true;
    private final float[] mapped = new float[2], mv = new float[3];
    /** Saved calibrations; set by HomeActivity. */
    GyroCalStore store;
    volatile GyroCal cal = new GyroCal();
    private InputDevice dev;
    private final SensorClock clock = new SensorClock();
    private final Stillness still = new Stillness();
    private boolean calDirty;

    /** Why a device does or does not give gyro data, as one short status line. */
    static String check(InputDevice d) {
        if (d == null) return "NO GAMEPAD CONNECTED";
        SensorManager m;
        try {
            m = d.getSensorManager();
        } catch (Throwable t) {
            return "NO SENSORMANAGER (" + t.getClass().getSimpleName() + ")";
        }
        if (m == null) return "NO SENSORMANAGER FOR THIS CONTROLLER";
        try {
            if (m.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null) {
                boolean accel = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
                return accel ? "NO GYRO SENSOR (ACCELEROMETER ONLY)" : "NO GYRO SENSOR EXPOSED";
            }
        } catch (Throwable t) {
            return "NO GYRO SENSOR (LOOKUP FAILED)";
        }
        return "FOUND";
    }

    static boolean isPad(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int s = d.getSources();
        return InputMode.has(s, InputMode.SRC_GAMEPAD) || InputMode.has(s, InputMode.SRC_JOYSTICK);
    }

    /** Status for settings without registering anything. */
    String probe(int preferredId) {
        if (active) return "FOUND, ACTIVE";
        try {
            InputDevice pref = preferredId >= 0 ? InputDevice.getDevice(preferredId) : null;
            if (isPad(pref)) return check(pref) + "  " + name(pref);
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (isPad(d)) return check(d) + "  " + name(d);
            }
        } catch (Throwable ignored) {}
        return "NO GAMEPAD CONNECTED";
    }

    private static String name(InputDevice d) {
        String n = d.getName();
        return n == null ? "" : "(" + n.toUpperCase(java.util.Locale.ROOT) + ")";
    }

    boolean start(int id) {
        stop();
        try {
            InputDevice d = InputDevice.getDevice(id);
            status = check(d);
            if (!"FOUND".equals(status)) return false;
            SensorManager m = d.getSensorManager();
            Sensor s = m.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (!m.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)) {
                status = "GYRO FOUND BUT REGISTER FAILED";
                return false;
            }
            sm = m;
            deviceId = id;
            dev = d;
            cal = store != null ? store.load(d) : new GyroCal();
            calDirty = false;
            clock.reset();
            still.reset();
            still.hasAccel = false;
            active = true;
            return true;
        } catch (Throwable t) {
            status = "GYRO ERROR (" + t.getClass().getSimpleName() + ")";
            return false;
        }
    }

    void stop() {
        try { if (sm != null) sm.unregisterListener(this); } catch (Throwable ignored) {}
        sm = null;
        active = false;
        wx = 0f;
        wy = 0f;
        if (calDirty && store != null && dev != null && cal.calibrated()) store.save(dev, cal);
        calDirty = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e == null || e.values == null || e.values.length < 3) return;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float dt = clock.dt(e.timestamp);
        still.feedGyro(x, y, z, dt, e.timestamp);
        GyroCal c = cal;
        if (GyroBias.online(c, x, y, z, dt, still.stillFor(e.timestamp))) calDirty = true;
        c.gyro(x, y, z, mv);
        // pitch drives rotationX; yaw and roll together drive the sideways sway (signs in GyroFilter.axes)
        GyroFilter.axes(mv[0], mv[1], mv[2], follow, mapped);
        wx = mapped[0];
        wy = mapped[1];
        lastEvent = SystemClock.uptimeMillis();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    void frame(float dt, long now) {
        boolean fresh = now - lastEvent < 120;
        filter.update(fresh ? wx : 0f, fresh ? wy : 0f, dt);
    }

    boolean moving(long now) {
        return active && (now - lastEvent < 150 || !filter.resting());
    }
}
