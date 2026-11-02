package com.nokoding.deck;

import java.util.Locale;

/**
 * Pure logic for Setup > PAD > GAMEPAD TEST: controller type from ids and name, the
 * buttons each type has, FULL and QUICK state, completion and the exit hold timer.
 * Key codes are the android.view.KeyEvent values, copied so the JVM tests can run.
 */
final class PadTest {

    // controller types
    static final int GENERIC = 0, PLAYSTATION = 1, XBOX = 2, SWITCH = 3;
    static final String[] TYPE_NAMES = {"GENERIC GAMEPAD", "PLAYSTATION", "XBOX",
            "NINTENDO SWITCH (LAYOUT NOT FULLY SUPPORTED)"};
    static final int SONY = 0x054c, MICROSOFT = 0x045e, NINTENDO = 0x057e;

    // logical buttons (bit index)
    static final int SOUTH = 0, EAST = 1, WEST = 2, NORTH = 3, L1 = 4, R1 = 5, L2 = 6, R2 = 7,
            L3 = 8, R3 = 9, UP = 10, DOWN = 11, LEFT = 12, RIGHT = 13, START = 14, SELECT = 15,
            HOME = 16, TOUCHPAD = 17;
    static final int COUNT = 18;
    /** Every type: four face buttons, shoulders, triggers, stick clicks, D-pad, Start and Select. */
    static final int REQUIRED = (1 << 16) - 1;

    // android.view.KeyEvent key codes
    static final int K_DPAD_UP = 19, K_DPAD_DOWN = 20, K_DPAD_LEFT = 21, K_DPAD_RIGHT = 22,
            K_BUTTON_A = 96, K_BUTTON_B = 97, K_BUTTON_X = 99, K_BUTTON_Y = 100,
            K_BUTTON_L1 = 102, K_BUTTON_R1 = 103, K_BUTTON_L2 = 104, K_BUTTON_R2 = 105,
            K_BUTTON_THUMBL = 106, K_BUTTON_THUMBR = 107, K_BUTTON_START = 108, K_BUTTON_SELECT = 109,
            K_BUTTON_MODE = 110;

    private static final String[] PS = {"CROSS", "CIRCLE", "SQUARE", "TRIANGLE", "L1", "R1", "L2", "R2",
            "L3", "R3", "UP", "DOWN", "LEFT", "RIGHT", "OPTIONS", "CREATE", "PS", "TOUCHPAD"};
    private static final String[] XB = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT",
            "LEFT STICK", "RIGHT STICK", "UP", "DOWN", "LEFT", "RIGHT", "MENU", "VIEW", "GUIDE", ""};
    private static final String[] GEN = {"A", "B", "X", "Y", "L1", "R1", "L2", "R2",
            "L3", "R3", "UP", "DOWN", "LEFT", "RIGHT", "START", "SELECT", "HOME", ""};

    private PadTest() {}

    /** Controller type from the USB or Bluetooth ids, with the name as a fallback. */
    static int detect(int vendor, int product, String name) {
        if (vendor == SONY) return PLAYSTATION;
        if (vendor == MICROSOFT) return XBOX;
        if (vendor == NINTENDO) return SWITCH;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("dualsense") || n.contains("dualshock") || n.contains("playstation")
                || n.contains("ps5") || n.contains("ps4") || n.equals("wireless controller")) return PLAYSTATION;
        if (n.contains("xbox") || n.contains("x-box") || n.contains("microsoft")) return XBOX;
        if (n.contains("nintendo") || n.contains("pro controller") || n.contains("joy-con") || n.contains("switch")) return SWITCH;
        return GENERIC;
    }

    /** Short model name for known product ids, or "" when unknown. */
    static String model(int vendor, int product) {
        if (vendor == SONY) {
            switch (product) {
                case 0x0ce6: return "DUALSENSE";
                case 0x0df2: return "DUALSENSE EDGE";
                case 0x05c4: return "DUALSHOCK 4";
                case 0x09cc: return "DUALSHOCK 4 (V2)";
                default: return "";
            }
        }
        if (vendor == MICROSOFT) {
            switch (product) {
                case 0x02e0: case 0x02fd: case 0x0b05: case 0x0b13: case 0x0b20: case 0x0b22: return "XBOX WIRELESS CONTROLLER";
                case 0x02ea: case 0x0b12: return "XBOX CONTROLLER (USB)";
                default: return "";
            }
        }
        if (vendor == NINTENDO) {
            switch (product) {
                case 0x2009: return "PRO CONTROLLER";
                case 0x2006: return "JOY-CON (L)";
                case 0x2007: return "JOY-CON (R)";
                default: return "";
            }
        }
        return "";
    }

    /** Layout used for drawing. Switch falls back to the generic layout. */
    static int layout(int type) { return type == SWITCH ? GENERIC : type; }

    static int required(int type) { return REQUIRED; }

    /** Shown but not counted: many boxes swallow the PS or Guide button and the touchpad click. */
    static int optional(int type) {
        int m = 1 << HOME;
        if (type == PLAYSTATION) m |= 1 << TOUCHPAD;
        return m;
    }

    static boolean has(int type, int button) {
        return button >= 0 && button < COUNT && ((required(type) | optional(type)) & (1 << button)) != 0;
    }

    /** Label on screen. DualShock 4 calls Create "SHARE". */
    static String label(int type, int product, int button) {
        if (button < 0 || button >= COUNT) return "";
        if (type == PLAYSTATION) {
            if (button == SELECT && (product == 0x05c4 || product == 0x09cc)) return "SHARE";
            return PS[button];
        }
        if (type == XBOX) return XB[button];
        return GEN[button];
    }

    /** Logical button for a key code, or -1 when the key is not part of any layout. */
    static int buttonFor(int keyCode) {
        switch (keyCode) {
            case K_BUTTON_A: return SOUTH;
            case K_BUTTON_B: return EAST;
            case K_BUTTON_X: return WEST;
            case K_BUTTON_Y: return NORTH;
            case K_BUTTON_L1: return L1;
            case K_BUTTON_R1: return R1;
            case K_BUTTON_L2: return L2;
            case K_BUTTON_R2: return R2;
            case K_BUTTON_THUMBL: return L3;
            case K_BUTTON_THUMBR: return R3;
            case K_DPAD_UP: return UP;
            case K_DPAD_DOWN: return DOWN;
            case K_DPAD_LEFT: return LEFT;
            case K_DPAD_RIGHT: return RIGHT;
            case K_BUTTON_START: return START;
            case K_BUTTON_SELECT: return SELECT;
            case K_BUTTON_MODE: return HOME;
            default: return -1;
        }
    }

    /** Hysteresis for analog triggers and the hat: press above 0.5, release below 0.3. */
    static boolean analogHeld(float v, boolean wasHeld) {
        float a = Math.abs(v);
        if (Float.isNaN(a)) return false;
        return wasHeld ? a > 0.3f : a > 0.5f;
    }

    /** Test run state. A key and an analog axis can both hold a button (L2 key and L2 axis). */
    static final class State {
        static final int SRC_KEY = 0, SRC_AXIS = 1, SRC_POINTER = 2;
        int type = GENERIC;
        boolean full = true;
        int seen;
        /** 2.17: buttons the player marked as SKIPPED because the box never reports them. They count as done. */
        int skipped;
        private final int[] holdBy = new int[3];
        private boolean done, announced;

        void setType(int t) { type = t; reset(); }

        /** Changing mode starts a fresh run. */
        void setFull(boolean f) { full = f; reset(); }

        void reset() {
            seen = 0;
            skipped = 0;
            holdBy[0] = holdBy[1] = holdBy[2] = 0;
            done = false;
            announced = false;
        }

        void set(int button, boolean down, int src) {
            if (button < 0 || button >= COUNT || src < 0 || src > 2) return;
            int bit = 1 << button;
            if (down) {
                holdBy[src] |= bit;
                seen |= bit;
                if (full && (seen & required(type)) == required(type)) done = true;
            } else {
                holdBy[src] &= ~bit;
            }
        }

        /**
         * 2.17 SKIP NEXT: counts the first required button that has not been seen as done, marked skipped.
         * @return the button, or -1 when there is none to skip (or a quick test)
         */
        int skipNext() {
            if (!full) return -1;
            int req = required(type);
            for (int b = 0; b < COUNT; b++) {
                int bit = 1 << b;
                if ((req & bit) != 0 && (seen & bit) == 0) {
                    seen |= bit;
                    skipped |= bit;
                    if ((seen & req) == req) done = true;
                    return b;
                }
            }
            return -1;
        }

        int skippedCount() { return Integer.bitCount(skipped & required(type)); }

        boolean skipped(int button) { return (skipped & (1 << button)) != 0; }

        /** The first required button not seen yet, or -1. */
        int nextWanted() {
            int req = required(type);
            for (int b = 0; b < COUNT; b++) if ((req & (1 << b)) != 0 && (seen & (1 << b)) == 0) return b;
            return -1;
        }

        int held() { return holdBy[0] | holdBy[1] | holdBy[2]; }

        boolean held(int button) { return (held() & (1 << button)) != 0; }

        boolean seen(int button) { return (seen & (1 << button)) != 0; }

        /** FULL: lit once pressed. QUICK: lit only while held. */
        boolean lit(int button) { return full ? seen(button) || held(button) : held(button); }

        int count() { return Integer.bitCount(seen & required(type)); }

        int total() { return Integer.bitCount(required(type)); }

        boolean complete() { return full && done; }

        /** True exactly once per run, the moment the last required button is pressed. */
        boolean takeCompletion() {
            if (!complete() || announced) return false;
            announced = true;
            return true;
        }
    }

    /**
     * Hold Start and Select together for HOLD_MS, or hold B (Circle) alone for EAST_MS, to exit (2.17: Steam's
     * input test also exits on a held B). Never traps: any release restarts it.
     */
    static final class ExitHold {
        static final long HOLD_MS = 2000, EAST_MS = 1500;
        private boolean start, select, east;
        private long since = -1, eastSince = -1;

        void set(boolean startHeld, boolean selectHeld, long now) {
            boolean was = start && select;
            start = startHeld;
            select = selectHeld;
            boolean both = start && select;
            if (both && !was) since = now;
            if (!both) since = -1;
        }

        void setEast(boolean eastHeld, long now) {
            if (eastHeld && !east) eastSince = now;
            east = eastHeld;
            if (!east) eastSince = -1;
        }

        boolean holding() { return since >= 0 || eastSince >= 0; }

        /** True when the B hold is the one filling the ring (the ring then sits on the B button). */
        boolean eastLeads(long now) { return eastSince >= 0 && eastProgress(now) >= comboProgress(now); }

        private float comboProgress(long now) {
            if (since < 0) return 0f;
            float p = (now - since) / (float) HOLD_MS;
            return p < 0f ? 0f : (p > 1f ? 1f : p);
        }

        private float eastProgress(long now) {
            if (eastSince < 0) return 0f;
            float p = (now - eastSince) / (float) EAST_MS;
            return p < 0f ? 0f : (p > 1f ? 1f : p);
        }

        float progress(long now) { return Math.max(comboProgress(now), eastProgress(now)); }

        boolean done(long now) {
            return (since >= 0 && now - since >= HOLD_MS) || (eastSince >= 0 && now - eastSince >= EAST_MS);
        }

        void clear() { start = false; select = false; east = false; since = -1; eastSince = -1; }
    }

    /** Hex id like 054C for the screen. */
    static String hex4(int v) {
        String s = Integer.toHexString(v & 0xFFFF).toUpperCase(Locale.ROOT);
        while (s.length() < 4) s = "0" + s;
        return s;
    }
}
