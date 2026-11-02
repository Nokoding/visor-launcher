package com.nokoding.deck;

/**
 * 2.2 stick routing. The stick picked in Setup > MOTION > WHEEL STICK is the spinner: it only
 * scrolls items inside what has focus (the wheel, the grid, or the open list) and can never
 * change zone, tab or screen. The other stick is the navigator and acts like the D-pad.
 * Also holds the WHEEL STICK SPEED steps, the per second cap and the edge rules. Pure Java.
 */
final class StickRoute {
    private StickRoute() {}

    // which job a stick has
    static final int SPINNER = 0, NAVIGATOR = 1;
    // where focus is
    static final int CTX_WHEEL = 0, CTX_GRID = 1, CTX_LIST = 2, CTX_OTHER_ZONE = 3;
    // what a stick may do there
    static final int A_NONE = 0, A_STEP_ITEMS = 1, A_STEP_LIST = 2, A_DPAD = 3;

    /** WHEEL STICK SPEED steps in percent of the 2.2 default. 2.11 added 300, 400 and 500: 12 degrees of stick per item at 500, and up to 25 items a second on the wheel. */
    static final int[] SPEEDS = {25, 50, 75, 100, 150, 200, 300, 400, 500};
    static final String[] SPEED_NAMES = {"25%", "50%", "75%", "100%", "150%", "200%", "300%", "400%", "500%"};
    static final int DEFAULT_SPEED = 100;
    /** Items per second at 100 percent: about 5 on the wheel, a bit more in the grid and lists. */
    static final float WHEEL_RATE = 5f, GRID_RATE = 6f, LIST_RATE = 5f;

    /** The spinner is the right stick unless WHEEL STICK is set to left. */
    static int role(boolean isRightStick, boolean spinnerIsRight) {
        return isRightStick == spinnerIsRight ? SPINNER : NAVIGATOR;
    }

    static int action(int role, int ctx) {
        if (role == NAVIGATOR) return A_DPAD;
        switch (ctx) {
            case CTX_WHEEL:
            case CTX_GRID: return A_STEP_ITEMS;
            case CTX_LIST: return A_STEP_LIST;
            default: return A_NONE;            // top bar or dock: the spinner does nothing there
        }
    }

    /** Only D-pad style input can move focus to another zone, a tab or another screen. */
    static boolean mayChangeZone(int action) { return action == A_DPAD; }

    static int clampSpeed(int pct) {
        int best = DEFAULT_SPEED, d = Integer.MAX_VALUE;
        for (int s : SPEEDS) if (Math.abs(s - pct) < d) { d = Math.abs(s - pct); best = s; }
        return best;
    }

    static int speedIndex(int pct) {
        int s = clampSpeed(pct);
        for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == s) return i;
        return 3;
    }

    /** Stick angle per item: twice the 2.1 angle at 100 percent, so 200 percent is the 2.1 angle. */
    static double detent(int pct) { return StickDial.DETENT * 100.0 / clampSpeed(pct); }

    static float rate(int ctx, int pct) {
        float base = ctx == CTX_GRID ? GRID_RATE : (ctx == CTX_LIST ? LIST_RATE : WHEEL_RATE);
        return base * clampSpeed(pct) / 100f;
    }

    /** Hard cap on items per second. At most one item of burst, so a fast spin cannot blast through. */
    static final class Limiter {
        private float tokens = 1f;
        private long last = Long.MIN_VALUE;

        /** @return how many of the asked items (signed) may move now. */
        int take(int items, long now, float perSecond) {
            if (last != Long.MIN_VALUE && now > last) tokens = Math.min(1f, tokens + (now - last) * perSecond / 1000f);
            last = now;
            if (items == 0 || !(perSecond > 0f)) return 0;
            int want = Math.abs(items), ok = 0;
            while (ok < want && tokens >= 1f) { tokens -= 1f; ok++; }
            return items > 0 ? ok : -ok;
        }

        void reset() { tokens = 1f; last = Long.MIN_VALUE; }
    }

    /** Spinner rotation in the grid: next or previous item, stopping at the first and last item. */
    static int gridStep(int idx, int n, int s) {
        if (n <= 0) return idx;
        return Math.max(0, Math.min(n - 1, idx + s));
    }

    /** Spinner flick in the grid: one cell in that direction, or stay put at the grid edge. */
    static int gridMove(int idx, int n, int cols, int dx, int dy) {
        if (n <= 0 || cols <= 0) return idx;
        int r = idx / cols, c = idx % cols;
        if (dx != 0) {
            int nc = c + dx, ni = idx + dx;
            return nc >= 0 && nc < cols && ni >= 0 && ni < n ? ni : idx;
        }
        if (dy != 0) {
            int nr = r + dy;
            if (nr < 0 || nr * cols >= n) return idx;
            return Math.min(nr * cols + c, n - 1);
        }
        return idx;
    }

    /** Spinner in a list: rows 0..count-1 only. Focus -1 is a tab strip, which the spinner may leave but never enter. */
    static int listStep(int focus, int count, int s) {
        if (count <= 0) return focus;
        int nf = focus + s;
        if (nf < 0 || nf >= count) return focus;
        return nf;
    }
}
