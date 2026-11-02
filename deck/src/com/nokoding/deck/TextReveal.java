package com.nokoding.deck;

import java.util.List;

/**
 * 2.3 shared text animation for every menu: TYPE (characters one by one with a block cursor),
 * FADE (each line fades in and slides a few px along the typing direction) or NONE.
 * Lines start one after another (the row stagger) and run at the same time, so they overlap.
 *
 * It only knows line lengths, a clock and a callback, no Android UI types, so it runs in the
 * JVM tests and can be reused by a later overlay service. Views ask it per line how many
 * characters show, the alpha, the slide and whether the cursor sits on that line, and draw
 * that themselves. No allocation after start(): lengths live in a reused int array.
 *
 * Interrupts: finish() jumps to the end at once (any key or click does that, then the key is
 * applied as usual). clear() runs the close: all lines untype (or fade out) together.
 */
final class TextReveal {

    interface Clock {
        long now();

        /**
         * 2.5: a reveal (or a clear) that needs frames just started. The frame loop's clock (Engine)
         * makes sure a frame is coming, so the animation never waits for some other input to wake
         * the loop. Plain clocks (tests, fixed times) ignore it.
         */
        default void wake() {}
    }

    interface Callback {
        /** One type_tick may play now, at this playback rate (pitch jitter). */
        void onRevealTick(float rate);
        /** Something visible changed: redraw. */
        void onRevealFrame();
        /** The reveal (or the clear) reached its end, naturally or through finish(). */
        void onRevealDone();
    }

    static final int TYPE = 0, FADE = 1, NONE = 2;
    private static final int IDLE = 0, RUN = 1, DONE = 2, CLEARING = 3, CLEARED = 4;

    /** Timings already include the duration scale (AnimConfig fills these). */
    static final class Params {
        int mode = TYPE;
        boolean rtl, cursor = true, ticks = true;
        float cps = 90f;
        int staggerMs = 70, fadeMs = 180;
        /** FADE slide distance in design px. */
        float slide = 10f;
        /** Long lists: the stagger shrinks so the last line starts within this. */
        int maxSpanMs = 280;
        /** Long lines: a single line never types for longer than this. */
        int maxRowMs = 320;

        void set(Params o) {
            mode = o.mode; rtl = o.rtl; cursor = o.cursor; ticks = o.ticks; cps = o.cps;
            staggerMs = o.staggerMs; fadeMs = o.fadeMs; slide = o.slide; maxSpanMs = o.maxSpanMs; maxRowMs = o.maxRowMs;
        }
    }

    final Params p = new Params();
    private final Clock clock;
    private final Callback cb;
    private TickScheduler ticks = new TickScheduler();
    private int[] len = new int[24];
    private int n, state = IDLE, stg, clearMs, tickUnits;
    private long t0, c0, now;

    TextReveal(Clock clock, Callback cb) {
        this.clock = clock;
        this.cb = cb;
    }

    /** Use one scheduler for every reveal on screen, so the 40 per second cap is overall. */
    void share(TickScheduler t) { if (t != null) ticks = t; }

    /** True while this reveal (or its clear) still has frames to run. A finished or instant one does not. */
    boolean needsFrames() { return state == RUN || state == CLEARING; }

    /** 2.5: asks the clock for a frame, but only when this reveal needs one. */
    private void wakeUp() {
        if (needsFrames()) clock.wake();
    }

    void start(List<? extends CharSequence> lines, long delayMs) {
        int c = lines == null ? 0 : lines.size();
        ensure(c);
        for (int i = 0; i < c; i++) {
            CharSequence s = lines.get(i);
            len[i] = s == null ? 0 : s.length();
        }
        begin(c, delayMs);
    }

    void startLengths(int[] lens, int count, long delayMs) {
        int c = Math.max(0, Math.min(count, lens == null ? 0 : lens.length));
        ensure(c);
        for (int i = 0; i < c; i++) len[i] = Math.max(0, lens[i]);
        begin(c, delayMs);
    }

    private void ensure(int c) { if (len.length < c) len = new int[Math.max(c, len.length * 2)]; }

    private void begin(int c, long delayMs) {
        n = c;
        now = clock.now();
        t0 = now + Math.max(0, delayMs);
        stg = Math.max(0, p.staggerMs);
        if (n > 1 && (long) stg * (n - 1) > p.maxSpanMs) stg = Math.max(0, p.maxSpanMs) / (n - 1);
        tickUnits = 0;
        state = p.mode == NONE || n == 0 ? DONE : RUN;
        if (state == DONE) cb.onRevealDone();
        cb.onRevealFrame();
        wakeUp();
    }

    /** Close: every line clears at once over ms (TYPE untypes, FADE fades, NONE is instant). */
    void clear(int ms) {
        now = clock.now();
        c0 = now;
        clearMs = Math.max(0, ms);
        state = p.mode == NONE || clearMs == 0 || n == 0 ? CLEARED : CLEARING;
        if (state == CLEARED) cb.onRevealDone();
        cb.onRevealFrame();
        wakeUp();
    }

    /** Advance to the clock's time. @return true if this step changed anything (redraw). */
    boolean step() {
        if (state != RUN && state != CLEARING) return false;
        now = clock.now();
        if (state == RUN) {
            if (p.mode == TYPE && p.ticks) {
                int units = 0;
                for (int i = 0; i < n; i++) units += visible(i) / 2;
                int owed = units - tickUnits;
                tickUnits = units;
                if (ticks.offer(owed, now)) cb.onRevealTick(ticks.rate());
            }
            if (now >= endAt()) state = DONE;
        } else if (now - c0 >= clearMs) {
            state = CLEARED;
        }
        cb.onRevealFrame();
        if (state == DONE || state == CLEARED) cb.onRevealDone();
        return true;
    }

    /** Interrupt: jump to the end now. @return true if something was running. */
    boolean finish() {
        if (state == RUN) state = DONE;
        else if (state == CLEARING) state = CLEARED;
        else return false;
        cb.onRevealFrame();
        cb.onRevealDone();
        return true;
    }

    boolean running() { return state == RUN || state == CLEARING; }

    boolean clearing() { return state == CLEARING || state == CLEARED; }

    int lines() { return n; }

    int length(int i) { return i >= 0 && i < n ? len[i] : 0; }

    /** Effective stagger in ms (shrunk for long lists). */
    int stagger() { return stg; }

    long startOf(int i) { return t0 + (long) i * stg; }

    /** Duration of one line in ms. */
    int rowMs(int i) {
        if (p.mode == FADE) return Math.max(1, p.fadeMs);
        if (p.mode == NONE) return 0;
        int l = length(i);
        if (l == 0) return 0;
        float cps = p.cps > 1f ? p.cps : 1f;
        return Math.max(1, Math.min(p.maxRowMs, Math.round(l * 1000f / cps)));
    }

    /** Time from start() (after its delay) to the last line done, in ms. */
    long totalMs() { return n == 0 ? 0 : endAt() - t0; }

    private long endAt() {
        long e = t0;
        for (int i = 0; i < n; i++) e = Math.max(e, startOf(i) + rowMs(i));
        return e;
    }

    private float lineT(int i) {
        long s = startOf(i);
        if (now < s) return 0f;
        int d = rowMs(i);
        return d <= 0 ? 1f : Math.min(1f, (now - s) / (float) d);
    }

    private float clearT() { return clearMs <= 0 ? 1f : Math.min(1f, (now - c0) / (float) clearMs); }

    /**
     * Characters of line i that show, for the length this line had when the reveal started.
     * Lines this reveal does not know (out of range, or no reveal running) return
     * Integer.MAX_VALUE: the caller clamps to its own length. Prefer visible(i, actual): a row
     * can change after start() (focus adds arrows, a value changes, a confirmation replaces the
     * label), and then its length is no longer the one the reveal was started with.
     */
    int visible(int i) {
        if (i < 0 || i >= n) return state == CLEARED ? 0 : Integer.MAX_VALUE;
        return visible(i, len[i]);
    }

    /**
     * 2.4.1: characters of line i that show, when the line is `actual` characters long right now.
     * While typing, progress is a share of the real length, so a row that grew or shrank since
     * start() still ends complete. Once the reveal is done, interrupted or idle, it is always the
     * full `actual` length. Only a close (CLEARING, CLEARED) takes text away.
     */
    int visible(int i, int actual) {
        if (actual < 0) actual = 0;
        if (i < 0 || i >= n) return state == CLEARED ? 0 : actual;
        switch (state) {
            case RUN:
                if (p.mode != TYPE) return now >= startOf(i) ? actual : 0;
                return Math.min(actual, (int) Math.floor(lineT(i) * actual + 1e-4f));
            case CLEARING:
                if (p.mode != TYPE) return actual;
                return Math.min(actual, (int) Math.ceil((1f - clearT()) * actual - 1e-4f));
            case CLEARED:
                return 0;
            default:
                return actual;
        }
    }

    /** Alpha of line i, 0..1. */
    float alpha(int i) {
        if (state == CLEARED) return 0f;
        if (i < 0 || i >= n) return 1f;
        if (state == RUN) {
            if (p.mode == FADE) return ease(lineT(i));
            return now >= startOf(i) ? 1f : 0f;
        }
        if (state == CLEARING && p.mode == FADE) return 1f - clearT();
        return 1f;
    }

    /** FADE slide of line i in design px: from p.slide behind toward 0, along the typing direction. */
    float slide(int i) {
        if (state != RUN || p.mode != FADE || i < 0 || i >= n) return 0f;
        float k = (1f - ease(lineT(i))) * p.slide;
        return p.rtl ? k : -k;
    }

    /** True while the block cursor sits on line i (TYPE, started, not finished). */
    boolean cursor(int i) {
        if (state != RUN || p.mode != TYPE || !p.cursor || i < 0 || i >= n) return false;
        return now >= startOf(i) && visible(i) < len[i];
    }

    static float ease(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
