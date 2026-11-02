package com.nokoding.deck;

/**
 * 2.8 touchpad volume: hold a controller key and drag a finger on the touchpad. Pure Java.
 *
 * Positions are normalized (0..1 across the pad; the Android side divides raw axis values by the
 * device's motion range), so any pad size works. Only movement along the chosen axis counts.
 */
final class TouchVol {
    static final int VERTICAL = 0, HORIZONTAL = 1;
    /** Volume steps for a swipe across the whole pad. */
    static final int[] SENS_STEPS = {8, 12, 16, 24};
    static final int SENS_DEFAULT = 1;
    /** The finger must move this far (normalized) from where it touched before anything happens. */
    static final float DEADZONE = 0.02f;
    /** Most steps one move() call may return; a bigger jump is a glitch and the rest is dropped. */
    static final int MAX_JUMP = 6;

    // android.view.KeyEvent values, as plain ints so this class stays pure
    private static final int DPAD_UP = 19, DPAD_DOWN = 20, DPAD_LEFT = 21, DPAD_RIGHT = 22;
    private static final int L1 = 102, R1 = 103, L2 = 104, R2 = 105, START = 108, SELECT = 109;

    private int direction = VERTICAL;
    private boolean invert;
    private int sens = SENS_DEFAULT;

    private boolean active, left;
    private double startPos, lastPos, acc;

    TouchVol() {}

    /** Bad values fall back to VERTICAL and the default sensitivity. Also resets the gesture. */
    void config(int direction, boolean invert, int sensIndex) {
        this.direction = direction == HORIZONTAL ? HORIZONTAL : VERTICAL;
        this.invert = invert;
        this.sens = sensIndex >= 0 && sensIndex < SENS_STEPS.length ? sensIndex : SENS_DEFAULT;
        end();
    }

    private static boolean bad(float v) { return Float.isNaN(v) || Float.isInfinite(v); }

    private double axis(float x, float y) { return direction == HORIZONTAL ? x : y; }

    /** Finger down, or the hold key pressed with the finger already down. */
    void begin(float x, float y) {
        end();
        if (bad(x) || bad(y)) return;
        active = true;
        startPos = lastPos = axis(x, y);
    }

    /** Whole volume steps to apply since the last call (positive is louder). */
    int move(float x, float y) {
        if (bad(x) || bad(y)) return 0;
        if (!active) { begin(x, y); return 0; }
        double pos = axis(x, y);
        if (!left) {
            if (Math.abs(pos - startPos) < DEADZONE) return 0;
            left = true;
            lastPos = pos;
            return 0;
        }
        double delta = pos - lastPos;
        lastPos = pos;
        if (direction == VERTICAL) delta = -delta;
        if (invert) delta = -delta;
        acc += delta * SENS_STEPS[sens];
        int steps = (int) (acc + (acc >= 0 ? 1e-6 : -1e-6));
        acc -= steps;
        if (steps > MAX_JUMP) { steps = MAX_JUMP; acc = 0; }
        else if (steps < -MAX_JUMP) { steps = -MAX_JUMP; acc = 0; }
        return steps;
    }

    /** Finger up, or the hold key released. */
    void end() { active = false; left = false; startPos = 0; lastPos = 0; acc = 0; }

    /** The fallback when the touchpad is not visible: hold the key and press the D-pad. Returns +1, -1 or 0. */
    static int dpadSteps(int direction, boolean invert, int keyCode) {
        int s = 0;
        if (direction == HORIZONTAL) {
            if (keyCode == DPAD_RIGHT) s = 1; else if (keyCode == DPAD_LEFT) s = -1;
        } else {
            if (keyCode == DPAD_UP) s = 1; else if (keyCode == DPAD_DOWN) s = -1;
        }
        return invert ? -s : s;
    }

    static String directionName(int d) { return d == HORIZONTAL ? "HORIZONTAL" : "VERTICAL"; }

    static String sensName(int idx) {
        return SENS_STEPS[idx >= 0 && idx < SENS_STEPS.length ? idx : SENS_DEFAULT] + " STEPS";
    }

    /** L1, R1, L2, R2, Create and Options may be the hold key; the PS button, Home, Back and the rest may not. */
    static boolean validHoldKey(int keyCode) { return !"NONE".equals(holdKeyName(keyCode)); }

    static String holdKeyName(int keyCode) {
        switch (keyCode) {
            case L1: return "L1";
            case R1: return "R1";
            case L2: return "L2";
            case R2: return "R2";
            case SELECT: return "CREATE";
            case START: return "OPTIONS";
            default: return "NONE";
        }
    }
}
