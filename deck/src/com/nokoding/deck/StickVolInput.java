package com.nokoding.deck;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * 2.10 stick volume, the Android side: hold a controller key and spin the right thumbstick round its rim and the
 * volume changes. The spin math is StickVol (pure, tested). This class tracks the hold key, reads the right stick
 * from joystick events, and changes the volume the same way touchpad volume does (TouchVolInput.step).
 *
 * The overlay service asks Android for joystick events only while the hold key is down, so a game keeps its
 * sticks the rest of the time. While the key is held, the game does not get the stick either.
 * The hold key itself still goes to the app. It uses the same key list as touchpad volume (TouchVol.validHoldKey).
 */
final class StickVolInput {
    static final String K_KEY = "svKey", K_SPEED = "svSpeed", K_INV = "svInv";

    int holdKey, speed;
    boolean invert;
    private final StickVol vol = new StickVol();
    private final HoldKey key = new HoldKey();

    void load(SharedPreferences p) {
        int k = 0, s = StickVol.SPEED_DEFAULT;
        boolean inv = false;
        try {
            k = p.getInt(K_KEY, 0);
            s = p.getInt(K_SPEED, StickVol.SPEED_DEFAULT);
            inv = p.getBoolean(K_INV, false);
        } catch (Throwable ignored) {}
        holdKey = TouchVol.validHoldKey(k) ? k : 0;
        speed = s >= 0 && s < StickVol.SPEED_DEG.length ? s : StickVol.SPEED_DEFAULT;
        invert = inv;
        vol.config(speed, invert);
        if (holdKey == 0) key.reset();
    }

    boolean enabled() { return holdKey != 0; }

    /** True while the hold key is down (and its up has not been lost, see HoldKey). */
    boolean held() { return key.held(android.os.SystemClock.uptimeMillis()); }

    /** The window lost the keyboard or the service restarted: the up may never come, so let go. @return true if it was held. */
    boolean release() {
        boolean was = key.held(android.os.SystemClock.uptimeMillis());
        key.reset();
        vol.reset();
        return was;
    }

    /** A key event. The hold key is tracked and never taken. @return true when the held state changed. */
    boolean onKey(KeyEvent e) {
        if (holdKey == 0 || e == null || e.getKeyCode() != holdKey) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down && e.getRepeatCount() > 0) return false;
        // press and hold: held exactly while the key is down, let go the moment it comes up
        boolean changed = down ? key.press(android.os.SystemClock.uptimeMillis()) : key.release();
        if (changed) vol.reset();
        return changed;
    }

    private static float axis(MotionEvent e, InputDevice d, int a) {
        if (d != null && d.getMotionRange(a, e.getSource()) == null) return 0f;
        float v = e.getAxisValue(a);
        return Float.isNaN(v) ? 0f : v;
    }

    /**
     * A joystick event while the key is held. @return true when it was a stick event that counted (the caller
     * then does not use it for anything else).
     */
    boolean onMotion(Context c, MotionEvent e) {
        if (holdKey == 0 || e == null || !held()) return false;
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        if (e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        InputDevice d = e.getDevice();
        boolean zr = d == null || d.getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
        float x = axis(e, d, zr ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX);
        float y = axis(e, d, zr ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
        key.touch(android.os.SystemClock.uptimeMillis());
        int st = vol.feed(x, y, e.getEventTime());
        if (st != 0) TouchVolInput.step(c, st);
        return true;
    }
}
