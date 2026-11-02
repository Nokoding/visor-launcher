package com.nokoding.deck;

/**
 * 2.7: burn-in protection for the screensaver clock. The clock block sits at a new place every 60
 * seconds, along a slow figure-eight (a Lissajous curve, one sine across and one at double speed down), and with MOTION on it
 * glides there over 10 seconds with an ease at each end. With MOTION off it jumps. Pure functions of
 * time, so the screensaver keeps no state and allocates nothing. Pure Java.
 */
final class BurnIn {
    private BurnIn() {}

    static final long PERIOD_MS = 60000L, GLIDE_MS = 10000L;
    /**
     * Radians along the curve per slot. Not a fraction of a full turn, so the places visited never repeat
     * exactly. Checked by the tests: two minutes in a row are always at least 14 percent of the range apart,
     * at most 59 percent, and in two hours the block visits every ninth of the screen.
     */
    static final double STEP = 0.55;

    /** The block's place in slot k as shares (0 to 1) of the free range on each axis. */
    static void slot(long k, double[] out) {
        double t = k * STEP;
        out[0] = 0.5 + 0.5 * Math.sin(t + 0.7);
        out[1] = 0.5 + 0.5 * Math.sin(2 * t + 1.4);
    }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * Where the block is elapsedMs after the screensaver started, if it began in slot startSlot (a number
     * from the wall clock, so each start is somewhere else). glide = false jumps at each minute.
     */
    static void at(long startSlot, long elapsedMs, boolean glide, double[] out) {
        if (elapsedMs < 0) elapsedMs = 0;
        long n = elapsedMs / PERIOD_MS, t = elapsedMs % PERIOD_MS;
        slot(startSlot + n, out);
        if (!glide || n == 0 || t >= GLIDE_MS) return;
        double cx = out[0], cy = out[1];
        slot(startSlot + n - 1, out);
        double p = smooth(t / (double) GLIDE_MS);
        out[0] += (cx - out[0]) * p;
        out[1] += (cy - out[1]) * p;
    }

    /** The slot to start in, from the wall clock: a different one each minute. */
    static long startSlot(long wallMs) { return Math.floorDiv(wallMs, PERIOD_MS); }

    /**
     * The top left corner in px for a block of bw by bh inside a view of vw by vh, keeping margin px
     * from every edge. A block too big for the view sits at the margin (or at 0 if even that is too big).
     */
    static void place(double fx, double fy, int vw, int vh, int bw, int bh, int margin, int[] out) {
        out[0] = axis(fx, vw, bw, margin);
        out[1] = axis(fy, vh, bh, margin);
    }

    private static int axis(double f, int v, int b, int margin) {
        double free = v - b - 2.0 * margin;
        if (!(free > 0)) return Math.max(0, (v - b) / 2);
        if (Double.isNaN(f)) f = 0.5;
        f = Math.max(0, Math.min(1, f));
        return (int) Math.round(margin + free * f);
    }
}
