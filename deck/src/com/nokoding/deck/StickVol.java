package com.nokoding.deck;

/**
 * 2.10 stick volume: hold a controller key and spin the right thumbstick round its rim, like the wheel
 * dial, and the volume goes up (clockwise) or down. The angle math is StickDial's, the same detent logic
 * as the wheel stick. This class adds the speed choices, the invert switch and a cap on steps per call.
 * A quick flick without spinning does nothing (a stick bumped while holding the key must not move the
 * volume). Pure Java, allocation free.
 */
final class StickVol {
    /** Degrees of rotation for one volume step, slowest to fastest. */
    static final int[] SPEED_DEG = {90, 60, 45, 30};
    static final String[] SPEED_NAMES = {"SLOW", "NORMAL", "FAST", "FASTER"};
    static final int SPEED_DEFAULT = 1, MAX_JUMP = 4;

    private final StickDial dial = new StickDial();
    private boolean invert;

    StickVol() { config(SPEED_DEFAULT, false); }

    /** Bad values fall back to the defaults. Also drops a spin in progress. */
    void config(int speedIndex, boolean invert) {
        int i = speedIndex >= 0 && speedIndex < SPEED_DEG.length ? speedIndex : SPEED_DEFAULT;
        dial.detent = Math.toRadians(SPEED_DEG[i]);
        this.invert = invert;
        reset();
    }

    /**
     * One stick position (-1 to 1 on each axis, y down as Android sends it).
     * @return signed volume steps: positive louder (clockwise, unless inverted), at most MAX_JUMP either way
     */
    int feed(float x, float y, long tMs) {
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) return 0;
        dial.update(x, y, tMs);
        int t = dial.ticks;
        if (t == 0) return 0;
        if (t > MAX_JUMP) t = MAX_JUMP;
        if (t < -MAX_JUMP) t = -MAX_JUMP;
        return invert ? -t : t;
    }

    /** Stick released, key let go or the settings changed. */
    void reset() { dial.update(0f, 0f, 0L); }

    static String speedName(int i) { return i >= 0 && i < SPEED_NAMES.length ? SPEED_NAMES[i] : SPEED_NAMES[SPEED_DEFAULT]; }
}
