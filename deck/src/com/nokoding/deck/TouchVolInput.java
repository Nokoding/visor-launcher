package com.nokoding.deck;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Locale;

/**
 * 2.8 touchpad volume, the Android side: hold a controller key and drag a finger on the DualSense touchpad
 * to change the volume. The gesture math is TouchVol (pure, tested). This class turns motion events into
 * normalized touchpad positions, tracks the hold key, and changes the volume.
 *
 * Whether a DualSense on the onn box delivers its touchpad as SOURCE_TOUCHPAD, as a mouse like pointer, or
 * not at all is not known. Both shapes are handled (a touchpad: absolute position over the device's range; a
 * mouse like device: relative movement over one screen), and the raw source, device and last values are kept
 * in lastSeen so Setup, PAD, TOUCHPAD SEEN can show them. When the service cannot see the touchpad at all,
 * the FALLBACK works: hold the key and press the D-pad (see TouchVol.dpadSteps).
 *
 * Nothing is swallowed except, while the hold key is down and the fallback is in use, the D-pad press that
 * steps the volume. A touchpad click is never taken.
 */
final class TouchVolInput {
    static final String K_KEY = "tvKey", K_DIR = "tvDir", K_SENS = "tvSens", K_INV = "tvInv";

    int holdKey, dir, sens;
    boolean invert;
    private final TouchVol tv = new TouchVol();
    private boolean held, active, motionSeen;
    private float rx, ry;
    /** The last touchpad event Deck saw, for the PAD screen. Empty until one arrives. */
    String lastSeen = "";
    int events;

    void load(SharedPreferences p) {
        int k = 0, d = 0, s = TouchVol.SENS_DEFAULT;
        boolean inv = false;
        try {
            k = p.getInt(K_KEY, 0);
            d = p.getInt(K_DIR, 0);
            s = p.getInt(K_SENS, TouchVol.SENS_DEFAULT);
            inv = p.getBoolean(K_INV, false);
        } catch (Throwable ignored) {}
        holdKey = TouchVol.validHoldKey(k) ? k : 0;
        dir = d == TouchVol.HORIZONTAL ? TouchVol.HORIZONTAL : TouchVol.VERTICAL;
        sens = s >= 0 && s < TouchVol.SENS_STEPS.length ? s : TouchVol.SENS_DEFAULT;
        invert = inv;
        tv.config(dir, invert, sens);
        if (holdKey == 0) { held = false; active = false; }
    }

    boolean enabled() { return holdKey != 0; }

    /** The source flags to ask the accessibility service for (0 when off). */
    static int sources() {
        return InputDevice.SOURCE_TOUCHPAD | InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    /**
     * A key event. @return true to consume it: only the D-pad press of the fallback, while the hold key is down.
     */
    boolean onKey(Context c, KeyEvent e) {
        if (holdKey == 0 || e == null) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == holdKey) {
            held = down;
            if (!down) { tv.end(); active = false; }
            return false;   // the key still goes to the app: Deck never takes it
        }
        if (!held || motionSeen) return false;
        int st = TouchVol.dpadSteps(dir, invert, code);
        if (st == 0) return false;
        if (down && e.getRepeatCount() == 0) step(c, st);
        return true;
    }

    /** A motion event from the touchpad or a mouse like device. @return true if it fed the gesture. */
    boolean onMotion(Context c, MotionEvent e) {
        if (holdKey == 0 || e == null) return false;
        int src = e.getSource();
        boolean padSrc = (src & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD;
        boolean mouse = (src & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE || (src & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
        if (!padSrc && !mouse) return false;
        float nx, ny;
        try {
            if (padSrc) {
                InputDevice d = e.getDevice();
                InputDevice.MotionRange mx = d == null ? null : d.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD);
                InputDevice.MotionRange my = d == null ? null : d.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD);
                if (mx == null || my == null || mx.getRange() <= 0 || my.getRange() <= 0) return false;
                nx = (e.getX() - mx.getMin()) / mx.getRange();
                ny = (e.getY() - my.getMin()) / my.getRange();
            } else {
                // a mouse like device: relative movement, one screen width or height is the whole pad
                rx += e.getAxisValue(MotionEvent.AXIS_RELATIVE_X) / 1920f;
                ry += e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) / 1080f;
                nx = rx;
                ny = ry;
            }
        } catch (Throwable t) {
            return false;
        }
        events++;
        InputDevice dev = e.getDevice();
        lastSeen = (padSrc ? "TOUCHPAD" : "MOUSE LIKE") + " " + (dev == null ? "?" : dev.getName().toUpperCase(Locale.ROOT)) + "  X " + fmt(nx) + "  Y " + fmt(ny);
        if (padSrc) motionSeen = true;
        if (!held) { active = false; return false; }
        if (!active) { tv.begin(nx, ny); active = true; return true; }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) { tv.end(); active = false; return true; }
        int st = tv.move(nx, ny);
        if (st != 0) step(c, st);
        return true;
    }

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** Whole volume steps through AudioManager, or the volume key route on a box with fixed volume (HDMI-CEC). */
    static void step(Context c, int steps) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int n = Math.abs(steps);
            for (int i = 0; i < n; i++) {
                if (am.isVolumeFixed()) RemapRunner.volume(c, steps > 0 ? RemapAction.VOL_UP : RemapAction.VOL_DOWN);
                else am.adjustStreamVolume(AudioManager.STREAM_MUSIC, steps > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, i == n - 1 ? AudioManager.FLAG_SHOW_UI : 0);
            }
        } catch (Throwable ignored) {}
    }
}
