package com.nokoding.deck;

import android.content.SharedPreferences;
import android.view.InputDevice;

/**
 * 2.3: saved gyro calibrations, one per controller, in Deck's SharedPreferences under
 * gyrocal.VENDOR.PRODUCT.DESCRIPTOR (see GyroCal.key). The device descriptor is Android's
 * stable id for one physical controller, so two DualSense pads keep separate calibrations.
 */
final class GyroCalStore {
    final SharedPreferences prefs;

    GyroCalStore(SharedPreferences p) { prefs = p; }

    static String key(InputDevice d) {
        String desc = null;
        try { desc = d.getDescriptor(); } catch (Throwable ignored) {}
        return GyroCal.key(d.getVendorId(), d.getProductId(), desc);
    }

    /** The saved calibration, or a fresh not calibrated one (the 2.2 behavior). Never null. */
    GyroCal load(InputDevice d) {
        GyroCal c = null;
        if (d != null) {
            try { c = GyroCal.decode(prefs.getString(key(d), null)); } catch (Throwable ignored) {}
        }
        return c == null ? new GyroCal() : c;
    }

    void save(InputDevice d, GyroCal c) {
        if (d == null || c == null) return;
        try { prefs.edit().putString(key(d), c.encode()).apply(); } catch (Throwable ignored) {}
    }

    void clear(InputDevice d) {
        if (d == null) return;
        try { prefs.edit().remove(key(d)).apply(); } catch (Throwable ignored) {}
    }

    /** The controller to calibrate: the preferred id if it is a gamepad, else the first gamepad. */
    static InputDevice pad(int preferredId) {
        try {
            InputDevice d = preferredId >= 0 ? InputDevice.getDevice(preferredId) : null;
            if (TestScreen.isPad(d)) return d;
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice x = InputDevice.getDevice(id);
                if (TestScreen.isPad(x)) return x;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Status line for Setup: CALIBRATED date and bias, NOT CALIBRATED, or no controller. */
    String status(int preferredId) {
        InputDevice d = pad(preferredId);
        if (d == null) return "NO CONTROLLER CONNECTED";
        return load(d).status();
    }
}
