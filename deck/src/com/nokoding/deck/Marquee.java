package com.nokoding.deck;

/**
 * 2.5: the math of an endless text marquee (the row help line in the Setup footer).
 *
 * One cycle is: the text rests at the start for pauseMs, then slides left at speed px per second
 * until it has moved one text width plus gap. At that moment a second copy of the text, drawn gap
 * behind the first, sits exactly where the first one started, so the next cycle begins without a
 * jump. Text that fits its view never moves.
 *
 * The caller draws the text at -offset(now), and a second copy at cycle() - offset(now) while
 * showsSecond(now). It owns the clock, the clip and the drawing. No Android types, no allocation.
 */
final class Marquee {
    static final long DEFAULT_PAUSE_MS = 1400;
    /** Design px per second, before the screen scale. Calm enough to read. */
    static final float DEFAULT_SPEED = 56f;

    private float textW, viewW, gap, speed = DEFAULT_SPEED;
    private long pauseMs = DEFAULT_PAUSE_MS, t0;

    /**
     * @param textW   full width of the text
     * @param viewW   width of the window it shows through
     * @param gap     empty space between the end of the text and the start of its next copy
     * @param pxPerSec scroll speed, above 0
     * @param pauseMs rest at the start of every cycle
     */
    void set(float textW, float viewW, float gap, float pxPerSec, long pauseMs) {
        this.textW = clean(textW);
        this.viewW = clean(viewW);
        this.gap = Math.max(1f, clean(gap));
        this.speed = pxPerSec > 0f && pxPerSec < 1e6f ? pxPerSec : DEFAULT_SPEED;
        this.pauseMs = Math.max(0L, pauseMs);
    }

    /** A bad measurement (NaN, negative, infinite) becomes 0 or a large finite number. */
    private static float clean(float x) { return x != x ? 0f : Math.max(0f, Math.min(1e6f, x)); }

    /** Starts a fresh cycle (new text, or the view came back). It rests first. */
    void restart(long now) { t0 = now; }

    /** True when the text is wider than the view, so it has to move. */
    boolean scrolls() { return textW > viewW + 0.5f; }

    /** Distance from one copy's start to the next. */
    float cycle() { return textW + gap; }

    /** Time of one full cycle (rest plus slide) in ms. 0 when the text does not scroll. */
    long periodMs() { return scrolls() ? pauseMs + slideMs() : 0L; }

    private long slideMs() { return Math.max(1L, Math.round(cycle() / speed * 1000.0)); }

    /** How far the text has moved left, 0 up to cycle(). 0 while resting and for text that fits. */
    float offset(long now) {
        if (!scrolls()) return 0f;
        long period = pauseMs + slideMs();
        long t = (now - t0) % period;
        if (t < 0) t += period;
        if (t < pauseMs) return 0f;
        return Math.min(cycle(), (t - pauseMs) * speed / 1000f);
    }

    /** Ms of rest left at the start of this cycle, 0 once it slides (or when it never moves). */
    long pauseLeft(long now) {
        if (!scrolls()) return 0L;
        long period = pauseMs + slideMs();
        long t = (now - t0) % period;
        if (t < 0) t += period;
        return t < pauseMs ? pauseMs - t : 0L;
    }

    /** True while the second copy has come into view. */
    boolean showsSecond(long now) { return scrolls() && cycle() - offset(now) < viewW; }

    /** True when the text is sliding right now (a frame changes something). */
    boolean moving(long now) { return scrolls() && pauseLeft(now) == 0L; }
}
