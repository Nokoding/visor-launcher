package com.nokoding.deck;

import android.annotation.TargetApi;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;

import java.util.Locale;

/**
 * 2.3: a controller's gyroscope and accelerometer for the gyro test and gyro calibration.
 * Looks the sensors up (Android 12+), registers on the main looper only while asked, and
 * hands raw samples to a Sink with a time step from SensorEvent.timestamp (SensorClock),
 * never from the wall clock. status says what was found, in the words the docs use.
 */
final class PadSensors implements SensorEventListener {

    interface Sink {
        void onGyro(float x, float y, float z, float dt, long tsNs);
        void onAccel(float x, float y, float z, float dt, long tsNs);
    }

    private final Sink sink;
    private SensorManager sm;
    private Sensor gyroS, accS;
    boolean registered;
    String status = "NO CONTROLLER CONNECTED. PRESS A BUTTON ON A CONTROLLER.";
    final SensorClock gyroClock = new SensorClock(), accClock = new SensorClock();
    /** uptimeMillis of the last sample, 0 before any. */
    long lastMs;

    PadSensors(Sink sink) { this.sink = sink; }

    boolean hasGyro() { return gyroS != null; }

    boolean hasAccel() { return accS != null; }

    void lookup(InputDevice d) {
        unregister();
        sm = null;
        gyroS = accS = null;
        lastMs = 0;
        if (d == null) { status = "NO CONTROLLER CONNECTED. PRESS A BUTTON ON A CONTROLLER."; return; }
        if (Build.VERSION.SDK_INT < 31) { status = "NEEDS ANDROID 12 FOR CONTROLLER SENSORS"; return; }
        lookup31(d);
    }

    @TargetApi(31)
    private void lookup31(InputDevice d) {
        SensorManager m;
        try {
            m = d.getSensorManager();
        } catch (Throwable t) {
            status = "NO SENSORMANAGER (" + t.getClass().getSimpleName().toUpperCase(Locale.ROOT) + ")";
            return;
        }
        if (m == null) { status = "NO SENSORMANAGER FOR THIS CONTROLLER"; return; }
        try {
            gyroS = m.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accS = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } catch (Throwable t) {
            status = "SENSOR LOOKUP FAILED (" + t.getClass().getSimpleName().toUpperCase(Locale.ROOT) + ")";
            return;
        }
        sm = m;
        if (gyroS == null && accS == null) status = "NO GYROSCOPE AND NO ACCELEROMETER ON THIS CONTROLLER";
        else if (gyroS == null) status = "NO GYROSCOPE (ACCELEROMETER ONLY: TILT WORKS, NO YAW)";
        else if (accS == null) status = "NO ACCELEROMETER (GYRO ONLY: NO DRIFT CORRECTION)";
        else status = "GYROSCOPE AND ACCELEROMETER FOUND, WAITING FOR DATA";
    }

    void register() {
        if (registered || sm == null) return;
        Handler main = new Handler(Looper.getMainLooper());
        boolean any = false;
        gyroClock.reset();
        accClock.reset();
        try {
            if (gyroS != null) any |= sm.registerListener(this, gyroS, SensorManager.SENSOR_DELAY_GAME, main);
            if (accS != null) any |= sm.registerListener(this, accS, SensorManager.SENSOR_DELAY_GAME, main);
        } catch (Throwable t) {
            status = "SENSOR REGISTER FAILED (" + t.getClass().getSimpleName().toUpperCase(Locale.ROOT) + ")";
        }
        registered = any;
        if (!any && (gyroS != null || accS != null)) status = "SENSORS FOUND BUT REGISTER FAILED";
    }

    void unregister() {
        try { if (sm != null && registered) sm.unregisterListener(this); } catch (Throwable ignored) {}
        registered = false;
    }

    void release() {
        unregister();
        sm = null;
    }

    boolean live(long nowMs, long within) { return registered && lastMs != 0 && nowMs - lastMs < within; }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e == null || e.values == null || e.values.length < 3 || e.sensor == null) return;
        int st = e.sensor.getType();
        if (st == Sensor.TYPE_GYROSCOPE) {
            sink.onGyro(e.values[0], e.values[1], e.values[2], gyroClock.dt(e.timestamp), e.timestamp);
        } else if (st == Sensor.TYPE_ACCELEROMETER) {
            sink.onAccel(e.values[0], e.values[1], e.values[2], accClock.dt(e.timestamp), e.timestamp);
        }
        if (lastMs == 0) status = "LIVE";
        lastMs = SystemClock.uptimeMillis();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
