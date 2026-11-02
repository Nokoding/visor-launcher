package com.nokoding.deck;

/**
 * 2.8 game profiles: what display sizes and densities are allowed, and how a scale in percent
 * turns into them. Pure Java, no Android classes.
 *
 * Facts about what Android can do (Android 14, researched before this was written):
 * - There is NO per app density. Density belongs to the whole display.
 * - GAME MODE downscale exists, but only through the shell: `cmd game set --mode 4 --downscale 0.67 PKG`
 *   and `cmd game reset PKG`. It needs MANAGE_GAME_MODE, which only the shell holds. It does nothing
 *   for apps whose manifest category is not "game". The scaling applies when the game restarts.
 *   It never changes the display itself, so it cannot make the TV unusable.
 * - Display wide `wm size WxH`, `wm density N`, `wm size reset`, `wm density reset` also need the shell.
 *   Deck itself might run them after `adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS`
 *   through a hidden API (unverified, experimental), or the user runs them from a phone helper script,
 *   or Deck only shows the command to copy.
 *
 * Safety is the priority: a bad size or density can leave the TV unusable. So every value is checked
 * here against hard limits, and the size must keep the panel's aspect ratio.
 */
final class DisplayRules {
    private DisplayRules() {}

    static final int DENSITY_MIN = 120, DENSITY_MAX = 640;
    static final int MIN_W = 640, MIN_H = 360;
    /** The scales a profile may use, in percent. 100 means no change. */
    static final int[] SCALES = {100, 85, 75, 67, 50};
    /** How long a first time change waits for the user to press KEEP before it is taken back. */
    static final int KEEP_SECONDS = 15;
    /** How long the changed display stays after the app was left, in milliseconds. */
    static final long GRACE_MS = 8000;
    /** No real panel is bigger than this in either direction. */
    static final int MAX_DIM = 8192;

    static boolean validDensity(int dpi) { return dpi >= DENSITY_MIN && dpi <= DENSITY_MAX; }

    static boolean validScale(int pct) {
        for (int s : SCALES) if (s == pct) return true;
        return false;
    }

    /**
     * True if w x h is a safe size for a panel of origW x origH: at least MIN_W x MIN_H, not bigger
     * than the panel in either direction, the same aspect ratio (within 1 percent), and the panel
     * values themselves are plausible (positive, at most 8192).
     */
    static boolean validSize(int w, int h, int origW, int origH) {
        if (origW <= 0 || origH <= 0 || origW > MAX_DIM || origH > MAX_DIM) return false;
        if (w < MIN_W || h < MIN_H) return false;
        if (w > origW || h > origH) return false;
        // |w/h - origW/origH| <= 1% of origW/origH, cross multiplied in long arithmetic
        long lhs = (long) w * origH, rhs = (long) h * origW;
        return Math.abs(lhs - rhs) * 100 <= rhs;
    }

    /**
     * The size for a scale: width and height each rounded to the nearest even number, aspect kept.
     * At 100 it is the original. Null if pct is not in SCALES or the result is not a valid size.
     */
    static int[] scaledSize(int origW, int origH, int pct) {
        if (!validScale(pct)) return null;
        if (origW <= 0 || origH <= 0 || origW > MAX_DIM || origH > MAX_DIM) return null;
        int w, h;
        if (pct == 100) { w = origW; h = origH; }
        else { w = evenRound(origW, pct); h = evenRound(origH, pct); }
        if (!validSize(w, h, origW, origH)) return null;
        return new int[]{w, h};
    }

    /** v * pct / 100 rounded to the nearest even number (halves go up). */
    private static int evenRound(int v, int pct) {
        long x = (long) v * pct;
        return (int) ((x + 100) / 200) * 2;
    }

    /**
     * The density for a scale: origDpi * pct / 100 rounded, then clamped to DENSITY_MIN..DENSITY_MAX.
     * Scaling it with the size keeps the interface the same relative size. -1 if origDpi is not valid
     * or pct is not in SCALES.
     */
    static int scaledDensity(int origDpi, int pct) {
        if (!validDensity(origDpi) || !validScale(pct)) return -1;
        int d = (int) (((long) origDpi * pct + 50) / 100);
        return Math.max(DENSITY_MIN, Math.min(DENSITY_MAX, d));
    }

    /**
     * Text for a row: "75 PERCENT  1440X810  240 DPI", "100 PERCENT  NORMAL", or "NOT AVAILABLE"
     * when the size or the density cannot be worked out (bad scale, bad panel values).
     */
    static String label(int pct, int origW, int origH, int origDpi) {
        int[] s = scaledSize(origW, origH, pct);
        int d = scaledDensity(origDpi, pct);
        if (s == null || d < 0) return "NOT AVAILABLE";
        if (pct == 100) return "100 PERCENT  NORMAL";
        return pct + " PERCENT  " + s[0] + "X" + s[1] + "  " + d + " DPI";
    }
}
