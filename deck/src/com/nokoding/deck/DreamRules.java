package com.nokoding.deck;

/**
 * 2.7: the rules of Deck's screensaver (DeckDream and the preview inside Deck): what the three
 * styles show, how the settings are limited, and what wakes it. Pure Java.
 *
 * Wake rules (docs/screensaver.md): any key, any pointer movement, any touch and any gamepad button
 * or stick push beyond a small deadzone wake it. The dream is interactive (DreamService's own
 * non interactive mode would wake on every stick drift sample), so these rules are Deck's own.
 * Nothing wakes it in the first 600 ms, so the key up of the press that started a preview cannot end it.
 */
final class DreamRules {
    private DreamRules() {}

    static final int CLOCK = 0, CLOCK_AND_WALLPAPER = 1, WALLPAPER_ONLY = 2;
    static final String[] STYLE_NAMES = {"CLOCK", "CLOCK AND WALLPAPER", "WALLPAPER ONLY"};
    static final int DIM_MIN = 30, DIM_MAX = 95, DIM_DEFAULT = 75, DIM_STEP = 5;
    static final long GRACE_MS = 600;
    static final float STICK_DEADZONE = 0.30f, HAT_DEADZONE = 0.5f, TRIGGER_DEADZONE = 0.30f;
    static final float POINTER_SLOP_PX = 2f;

    static int clampStyle(int s) { return Math.max(0, Math.min(STYLE_NAMES.length - 1, s)); }

    static int clampDim(int d) {
        d = Math.max(DIM_MIN, Math.min(DIM_MAX, d));
        return Math.round(d / (float) DIM_STEP) * DIM_STEP;
    }

    static boolean hasClock(int style) { return clampStyle(style) != WALLPAPER_ONLY; }

    static boolean hasWallpaper(int style) { return clampStyle(style) != CLOCK; }

    /** True while input is still ignored after the screensaver started. A negative time (clock went back) counts as ignored. */
    static boolean grace(long sinceStartMs) { return sinceStartMs < GRACE_MS; }

    /** A gamepad push that counts: a stick, the hat or a trigger beyond its deadzone. */
    static boolean stickWakes(float x, float y, float z, float rz, float hatX, float hatY, float lt, float rt) {
        return big(x, STICK_DEADZONE) || big(y, STICK_DEADZONE) || big(z, STICK_DEADZONE) || big(rz, STICK_DEADZONE)
                || big(hatX, HAT_DEADZONE) || big(hatY, HAT_DEADZONE) || big(lt, TRIGGER_DEADZONE) || big(rt, TRIGGER_DEADZONE);
    }

    private static boolean big(float v, float dead) { return !Float.isNaN(v) && Math.abs(v) > dead; }

    /**
     * Pointer movement: the first sample only records where the pointer is (some devices send one when
     * they appear). After that a move of more than POINTER_SLOP_PX in either direction wakes it.
     */
    static final class Pointer {
        private float x, y;
        private boolean seen;

        void reset() { seen = false; }

        boolean moved(float nx, float ny) {
            if (Float.isNaN(nx) || Float.isNaN(ny)) return false;
            if (!seen) { x = nx; y = ny; seen = true; return false; }
            boolean m = Math.abs(nx - x) > POINTER_SLOP_PX || Math.abs(ny - y) > POINTER_SLOP_PX;
            x = nx;
            y = ny;
            return m;
        }
    }
}
