package com.nokoding.deck;

/**
 * Controller rumble patterns as plain data. Edit the tables, not the code.
 * Each motor row is pairs of {duration ms, amplitude 1..255}; amplitude 0 is a pause.
 * L = left motor (conventionally heavy, low frequency), R = right (light, high frequency).
 * Android lists controller vibrators left first, then right. Pure Java.
 */
final class HapticPatterns {
    private HapticPatterns() {}

    static final int TICK = 0, DETENT_HEAVY = 1, FOCUS = 2, LOCK = 3, SELECT = 4, BACK = 5,
            MENU_OPEN = 6, MENU_CLOSE = 7, TOGGLE = 8, MODE = 9, ERROR = 10;
    static final String[] NAMES = {"wheel_tick", "wheel_detent_heavy", "focus_move", "lock_on", "select", "back",
            "menu_open", "menu_close", "toggle", "mode_switch", "error"};

    static final int[][] L = {
            {},                                                   // wheel_tick: right motor only, subtle on purpose
            {14, 90},                                             // wheel_detent_heavy: every 5th item or on wrap
            {},                                                   // focus_move
            {20, 140},                                            // lock_on, fired when the brackets close
            {28, 150},                                            // select (press is stronger)
            {24, 70},                                             // back (lighter)
            {10, 20, 10, 32, 10, 43, 10, 55, 10, 67, 10, 78, 10, 90}, // menu_open: ramp 20 to 90 over 70 ms
            {10, 90, 10, 76, 10, 62, 10, 48, 10, 34, 10, 20},     // menu_close: ramp 90 to 20 over 60 ms
            {},                                                   // toggle
            {30, 60, 40, 0, 30, 110},                             // mode_switch
            {40, 200, 50, 0, 40, 200},                            // error (negative events feel stronger)
    };
    static final int[][] R = {
            {10, 100},
            {12, 160},
            {16, 55},
            {12, 190},
            {18, 200},
            {},
            {},
            {},
            {14, 130},
            {},
            {30, 120},
    };

    /** Sound event index (SoundMap) to haptic pattern, -1 = none. */
    static final int[] FOR_SOUND = {
            FOCUS,      // move
            TICK,       // wheel_tick
            SELECT,     // select
            BACK,       // back
            MENU_OPEN,  // menu_open
            MENU_CLOSE, // menu_close
            LOCK,       // lock_on
            SELECT,     // launch
            ERROR,      // error
            MODE,       // mode_gamepad
            -1,         // mode_remote (no rumble: the user just put the pad down)
            TOGGLE,     // toggle
            -1,         // pack_loaded
            SELECT,     // test_complete (gamepad test passed)
            -1,         // type_tick (2.3: far too many for rumble)
            MENU_OPEN,  // flyout_open
            MENU_CLOSE, // flyout_close
    };

    // Feel presets: {duration multiplier, left amp multiplier, right amp multiplier, heavy: add left from right}
    static final int CRISP = 0, SOFT = 1, HEAVY = 2;
    static final String[] PRESET_NAMES = {"CRISP", "SOFT", "HEAVY"};
    static final float[][] PRESET = {
            {0.8f, 1.0f, 1.15f, 0f},
            {1.35f, 0.75f, 0.7f, 0f},
            {1.1f, 1.4f, 0.9f, 0.6f},
    };
    static final int[] STRENGTHS = {0, 25, 50, 75, 100};

    // Wheel hum: continuous low hum on L while the dial keeps spinning
    static final int HUM_MIN = 18, HUM_MAX = 110;   // amplitude at spin speed 0..1 (MAX is also the cap)
    static final int HUM_RISE_MS = 250, HUM_FALL_MS = 200, HUM_STOP_MS = 180;
    static final int HUM_REISSUE_MS = 80, HUM_PULSE_MS = 130;
    static final float HUM_FULL_TPS = 8f;           // detent ticks per second that count as full speed
    static final int DUTY_CYCLE_MS = 10;            // fake intensity when amplitude control is missing

    static final class Wave {
        final long[] t;
        final int[] a;
        final long total;
        Wave(long[] t, int[] a) {
            this.t = t;
            this.a = a;
            long s = 0;
            for (long x : t) s += x;
            total = s;
        }
    }

    /** Builds one motor's waveform, scaled by preset and strength (0..100). Null when silent. */
    static Wave build(int pattern, boolean left, int preset, int strength, boolean ampControl) {
        if (pattern < 0 || pattern >= L.length || strength <= 0) return null;
        float[] pr = PRESET[Math.max(0, Math.min(preset, PRESET.length - 1))];
        int[] seg = left ? L[pattern] : R[pattern];
        float ampMul = left ? pr[1] : pr[2];
        if (left && seg.length == 0 && pr[3] > 0f) {
            seg = R[pattern];
            ampMul = pr[3];
        }
        if (seg.length < 2) return null;
        int n = seg.length / 2;
        long[] t = new long[n];
        int[] a = new int[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            t[i] = Math.max(1, Math.round(seg[2 * i] * pr[0]));
            int raw = seg[2 * i + 1];
            int v = raw <= 0 ? 0 : Math.round(raw * ampMul * strength / 100f);
            if (raw > 0) v = Math.max(1, Math.min(255, v));
            a[i] = v;
            if (v > 0) any = true;
        }
        if (!any) return null;
        return ampControl ? new Wave(t, a) : dutyCycle(t, a);
    }

    /** Hum pulse for the left motor at an already scaled amplitude. */
    static Wave hum(int amp, boolean ampControl) {
        if (amp <= 0) return null;
        long[] t = {HUM_PULSE_MS};
        int[] a = {Math.min(HUM_MAX, amp)};
        return ampControl ? new Wave(t, a) : dutyCycle(t, a);
    }

    /** Without amplitude control: full on/off pulses whose on time follows the amplitude. */
    static Wave dutyCycle(long[] t, int[] a) {
        int cap = 0;
        for (long x : t) cap += (int) Math.max(1, (x + DUTY_CYCLE_MS - 1) / DUTY_CYCLE_MS) * 2;
        long[] ot = new long[cap];
        int[] oa = new int[cap];
        int k = 0;
        for (int i = 0; i < t.length; i++) {
            long dur = t[i];
            if (a[i] <= 0) { ot[k] = dur; oa[k++] = 0; continue; }
            float frac = a[i] / 255f;
            long done = 0;
            while (done < dur) {
                long cyc = Math.min(DUTY_CYCLE_MS, dur - done);
                long on = Math.max(Math.min(cyc, 3), Math.min(cyc, Math.round(cyc * frac)));
                ot[k] = on; oa[k++] = 255;
                if (cyc - on > 0) { ot[k] = cyc - on; oa[k++] = 0; }
                done += cyc;
            }
        }
        long[] ft = new long[k];
        int[] fa = new int[k];
        System.arraycopy(ot, 0, ft, 0, k);
        System.arraycopy(oa, 0, fa, 0, k);
        return new Wave(ft, fa);
    }
}
