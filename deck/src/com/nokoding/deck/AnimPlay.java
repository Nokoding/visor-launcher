package com.nokoding.deck;

/**
 * 2.7: the timing of an animated wallpaper. Android's AnimatedImageDrawable plays at the file's own
 * speed and has no speed or frame rate setting, so Deck does both from the outside:
 * - FRAME RATE CAP: the drawable asks for a redraw whenever its frame changes. Deck lets a redraw
 *   through only when the cap's interval has passed since the last one, and posts one for the
 *   rest of the interval otherwise. The picture still plays at the file's speed, with frames skipped.
 * - PLAYBACK SPEED below 100 percent: Deck stops the drawable and starts it again in a short cycle,
 *   running for speed percent of each cycle. That can only slow a file down, never speed it up, and
 *   it looks steppy. Docs say so. Pure Java.
 */
final class AnimPlay {
    private AnimPlay() {}

    static final int FPS_MIN = 5, FPS_MAX = 30, FPS_DEFAULT = 20;
    static final int SPEED_MIN = 25, SPEED_MAX = 100;
    static final long CYCLE_MS = 300;

    static int clampFps(int fps) { return Math.max(FPS_MIN, Math.min(FPS_MAX, fps)); }

    static int clampSpeed(int pct) { return Math.max(SPEED_MIN, Math.min(SPEED_MAX, pct)); }

    /** Least milliseconds between two redraws for a frame rate cap. */
    static long interval(int fps) { return 1000L / clampFps(fps); }

    /** 0 when a redraw is due now, else the milliseconds to wait. A clock that went backwards counts as due. */
    static long wait(long now, long lastDraw, int fps) {
        long since = now - lastDraw;
        if (since < 0) return 0;
        long iv = interval(fps);
        return since >= iv ? 0 : iv - since;
    }

    /** True for full speed: no duty cycle needed. */
    static boolean full(int speedPct) { return clampSpeed(speedPct) >= SPEED_MAX; }

    /** How long the drawable runs in each cycle. */
    static long runMs(int speedPct) { return Math.max(1, CYCLE_MS * clampSpeed(speedPct) / 100); }

    /** How long it is held still in each cycle. */
    static long holdMs(int speedPct) { return CYCLE_MS - runMs(speedPct); }

    /** The effective speed share over one cycle, 0.25 to 1.0. */
    static double share(int speedPct) { return runMs(speedPct) / (double) CYCLE_MS; }
}
