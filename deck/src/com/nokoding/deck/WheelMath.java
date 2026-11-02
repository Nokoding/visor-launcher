package com.nokoding.deck;

/** Index math for the infinite wheel. Virtual indices are unbounded; apps repeat with floorMod. */
final class WheelMath {
    private WheelMath() {}

    static int wrap(long v, int n) {
        return n <= 0 ? 0 : (int) Math.floorMod(v, (long) n);
    }

    /** How many distinct tiles to show: never more than there are apps, so no duplicates. */
    static int window(int n, int slots) {
        return Math.max(0, Math.min(n, slots));
    }

    /** First virtual index of the half open window (pos - w/2, pos + w/2]. */
    static int first(float pos, int n, int slots) {
        int w = window(n, slots);
        return (int) Math.floor(pos - w / 2.0) + 1;
    }

    /** Fade for tiles near the window edge when there are fewer apps than slots. */
    static float edgeAlpha(float d, int n, int slots) {
        int w = window(n, slots);
        if (w >= slots) return 1f;
        float a = (float) ((w / 2.0 - Math.abs(d)) / 0.5);
        return a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    /** Multiple of n to subtract so the target stays small (float precision). 0 if not needed. */
    static int rebase(int target, int n) {
        if (n <= 0 || Math.abs(target) < 100000) return 0;
        return target - wrap(target, n);
    }
}
