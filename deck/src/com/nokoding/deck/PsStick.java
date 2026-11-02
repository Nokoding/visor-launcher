package com.nokoding.deck;

/**
 * 2.4: one stick or hat axis turned into menu steps for the PS overlay, which only gets raw
 * joystick MotionEvents (no D-pad keys from a DualSense, its D-pad is the hat axes). One step
 * on the push, then repeats like the D-pad (380 ms, then every 110 ms) while held. Hysteresis
 * on the threshold so a stick resting near the edge does not chatter. Pure Java.
 */
final class PsStick {
    static final float PRESS = 0.5f, RELEASE = 0.3f;
    static final int FIRST_MS = 380, REPEAT_MS = 110;

    private int dir;
    private long next;

    /** New axis value. @return -1, 0 or 1: the step to apply now. */
    int feed(float v, long now) {
        int d;
        if (v >= PRESS) d = 1;
        else if (v <= -PRESS) d = -1;
        else if (Math.abs(v) <= RELEASE) d = 0;
        else d = dir;   // between the thresholds: keep what it was
        if (d != dir) {
            dir = d;
            if (d != 0) { next = now + FIRST_MS; return d; }
            return 0;
        }
        return poll(now);
    }

    /** Repeat while held. @return the step due now, or 0. */
    int poll(long now) {
        if (dir != 0 && now >= next) { next = now + REPEAT_MS; return dir; }
        return 0;
    }

    int dir() { return dir; }

    /** ms until the next repeat, -1 when released. */
    long waitMs(long now) { return dir == 0 ? -1 : Math.max(0, next - now); }

    void reset() { dir = 0; }
}
