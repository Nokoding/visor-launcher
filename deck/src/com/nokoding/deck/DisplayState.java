package com.nokoding.deck;

/**
 * 2.8 game profiles: the ORIGINAL display values, remembered before the first change, saved as one
 * plain line in prefs ("v1;1920x1080;320") and read back with a strict parser that survives a
 * corrupt file. Pure Java.
 *
 * Restoring never depends on this file. `wm size reset` and `wm density reset` put back the
 * panel's own values, whatever was saved, so restore works even when the saved line is corrupt or
 * missing. The saved values are only used to notice that the display differs from the original.
 */
final class DisplayState {
    static final String VERSION = "v1";
    /** The same two commands as text for the user to run from a phone. */
    static final String DOC_RESTORE = "adb shell wm size reset; adb shell wm density reset";

    final int w, h, dpi;
    final boolean valid;

    private DisplayState(int w, int h, int dpi, boolean valid) {
        this.w = w; this.h = h; this.dpi = dpi; this.valid = valid;
    }

    private static final DisplayState INVALID = new DisplayState(0, 0, 0, false);

    /** Valid only for a plausible display: w and h in 320..8192, dpi in DENSITY_MIN..DENSITY_MAX. */
    static DisplayState of(int w, int h, int dpi) {
        if (w < 320 || w > DisplayRules.MAX_DIM || h < 320 || h > DisplayRules.MAX_DIM || !DisplayRules.validDensity(dpi)) return INVALID;
        return new DisplayState(w, h, dpi, true);
    }

    /**
     * Reads "v1;1920x1080;320". Anything else gives an invalid state and never throws: null, empty,
     * another version, a wrong number of fields, spaces, signs, non digits, numbers that are too
     * long, long junk.
     */
    static DisplayState parse(String s) {
        if (s == null || s.length() < 10 || s.length() > 30) return INVALID;
        String[] p = s.split(";", -1);
        if (p.length != 3 || !VERSION.equals(p[0])) return INVALID;
        int x = p[1].indexOf('x');
        if (x < 0 || p[1].indexOf('x', x + 1) >= 0) return INVALID;
        int w = digits(p[1].substring(0, x)), h = digits(p[1].substring(x + 1)), d = digits(p[2]);
        if (w < 0 || h < 0 || d < 0) return INVALID;
        return of(w, h, d);
    }

    /** A number of 1 to 4 ASCII digits, or -1. */
    private static int digits(String t) {
        if (t.isEmpty() || t.length() > 4) return -1;
        int v = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        return v;
    }

    /** The saved line, or "" for an invalid state. */
    String save() { return valid ? VERSION + ";" + w + "x" + h + ";" + dpi : ""; }

    /**
     * The shell lines that put the display back. ALWAYS the same two reset lines, whatever the
     * original is (valid, invalid or null): resetting goes to the panel's own values, so this works
     * when the saved file is corrupt.
     */
    static String[] restoreShell(DisplayState original) {
        return new String[]{"wm size reset", "wm density reset"};
    }

    /** The same lines with the adb prefix, for the user to run from a phone. */
    static String[] restoreAdb(DisplayState original) {
        return new String[]{"adb shell wm size reset", "adb shell wm density reset"};
    }

    /** True if the current values differ from a valid original. An invalid original gives false (the caller can still run restoreShell). */
    static boolean needsRestore(DisplayState original, int curW, int curH, int curDpi) {
        if (original == null || !original.valid) return false;
        return curW != original.w || curH != original.h || curDpi != original.dpi;
    }
}
