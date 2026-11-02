package com.nokoding.deck;

/**
 * 2.3 options flyout timeline, pure Java. Open (about 270 ms of motion, then the text):
 * the focused tile's icon glides to the dock spot on the side of the screen while the rest
 * dims and pushes back, then the panel grows out of the icon, then the lines type in.
 * Close (about 250 ms, faster than open): the text clears, the panel retracts, the icon glides
 * back to its tile. Every time scales with DURATION SCALE. finish() jumps to the end, so a key
 * press never waits for it.
 *
 * Values: glide 0 at the tile, 1 at the dock. grow 0 closed, 1 full width. dim 0 to 1 for the
 * scrim and the push back. iconAlpha for the flying icon (it fades instead of gliding when
 * ICON GLIDE is off).
 */
final class FlyoutAnim {
    static final int GLIDE_MS = 180, GROW_AT = 100, GROW_MS = 170, TEXT_AT = 170, TEXT_AT_NO_GLIDE = 70;
    static final int CLEAR_MS = 80, RETRACT_AT = 50, RETRACT_MS = 130, BACK_AT = 100, BACK_MS = 150;
    static final int CLOSE_MS = BACK_AT + BACK_MS;

    static final class Params {
        boolean glide = true;
        /** No motion at all (Reduce effects, or the screen opted out). */
        boolean instant;
        float scale = 1f;

        void set(Params o) { glide = o.glide; instant = o.instant; scale = o.scale; }
    }

    final Params p = new Params();
    float glide, grow, dim, iconAlpha;
    boolean opening, running;
    private boolean docked;
    private long t0;

    private float s() { return p.scale > 0.1f ? p.scale : 1f; }

    /** @param alreadyDocked the icon is at the dock already (one flyout replacing another) */
    void open(long now, boolean alreadyDocked) {
        opening = true;
        docked = alreadyDocked;
        t0 = now;
        if (p.instant || alreadyDocked) {
            glide = grow = dim = iconAlpha = 1f;
            running = false;
        } else {
            glide = p.glide ? 0f : 1f;
            grow = 0f;
            dim = 0f;
            iconAlpha = p.glide ? 1f : 0f;
            running = true;
        }
    }

    void close(long now) {
        opening = false;
        t0 = now;
        if (p.instant) {
            glide = grow = dim = iconAlpha = 0f;
            running = false;
        } else {
            running = true;
        }
    }

    /** ms after open() when the text should start. */
    int textDelay() {
        if (p.instant || docked) return 0;
        return Math.round((p.glide ? TEXT_AT : TEXT_AT_NO_GLIDE) * s());
    }

    /** ms the text takes to clear on close. */
    int clearMs() { return p.instant ? 0 : Math.round(CLEAR_MS * s()); }

    /** @return true while it moves. */
    boolean step(long now) {
        if (!running) return false;
        float s = s(), t = now - t0;
        if (opening) {
            float growAt = p.glide ? GROW_AT * s : 0f;
            glide = p.glide ? TextReveal.ease(t / (GLIDE_MS * s)) : 1f;
            grow = TextReveal.ease((t - growAt) / (GROW_MS * s));
            dim = Math.min(1f, Math.max(0f, t / (GLIDE_MS * s)));
            iconAlpha = p.glide ? 1f : grow;
            float end = Math.max(p.glide ? GLIDE_MS * s : 0f, growAt + GROW_MS * s);
            if (t >= end) { glide = grow = dim = iconAlpha = 1f; running = false; }
        } else {
            grow = 1f - TextReveal.ease((t - RETRACT_AT * s) / (RETRACT_MS * s));
            if (p.glide) {
                glide = 1f - inOut((t - BACK_AT * s) / (BACK_MS * s));
                iconAlpha = 1f;
            } else {
                iconAlpha = grow;
            }
            float end = (p.glide ? CLOSE_MS : RETRACT_AT + RETRACT_MS) * s;
            dim = Math.max(0f, 1f - t / end);
            if (t >= end) { glide = p.glide ? 0f : glide; grow = dim = 0f; iconAlpha = p.glide ? 1f : 0f; running = false; }
        }
        return true;
    }

    /** Jump to the end. @return true if it was moving. */
    boolean finish() {
        if (!running) return false;
        running = false;
        if (opening) { glide = grow = dim = iconAlpha = 1f; }
        else { glide = p.glide ? 0f : glide; grow = dim = 0f; iconAlpha = p.glide ? 1f : 0f; }
        return true;
    }

    /** The panel is fully out (the reticle can show on its rows). */
    boolean ready() { return opening && grow >= 1f; }

    /** Closed and done: the panel can be removed. */
    boolean gone() { return !opening && !running; }

    static float inOut(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }
}
