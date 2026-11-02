package com.nokoding.deck;

/** Volume steps and the segmented bar in the PS quick menu. Pure Java. */
final class VolumeMath {
    private VolumeMath() {}

    /** One step up (dir 1) or down (dir -1), clamped to 0..max. */
    static int step(int cur, int max, int dir) {
        if (max <= 0) return 0;
        int v = cur + (dir > 0 ? 1 : (dir < 0 ? -1 : 0));
        return Math.max(0, Math.min(max, v));
    }

    /** Lit segments for a bar of segs segments. Any volume above 0 lights at least one. */
    static int lit(int cur, int max, int segs) {
        if (max <= 0 || segs <= 0 || cur <= 0) return 0;
        if (cur >= max) return segs;
        int n = Math.round(cur * segs / (float) max);
        return Math.max(1, Math.min(segs, n));
    }

    static int percent(int cur, int max) {
        if (max <= 0) return 0;
        return Math.round(100f * Math.max(0, Math.min(max, cur)) / max);
    }

    /** Readout next to the bar. */
    static String label(int cur, int max, boolean muted, boolean fixed) {
        if (fixed) return "TV CONTROLS VOLUME";
        if (muted) return "MUTED";
        return percent(cur, max) + "%  " + Math.max(0, Math.min(max, cur)) + "/" + Math.max(0, max);
    }
}
