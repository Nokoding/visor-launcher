package com.nokoding.deck;

/**
 * 2.4 kill switch timer, pure Java. Holding the PS button for HOLD_MS while Deck has taken it
 * fires once: the overlay closes and Deck lets go of the PS button for the app in front until
 * you leave that app. Releasing early cancels it. A press Deck did not take never starts it.
 */
final class PsKill {
    static final long HOLD_MS = 4000;

    private long t0;
    private boolean held, fired;

    void press(long now) {
        t0 = now;
        held = true;
        fired = false;
    }

    void release() { held = false; }

    boolean held() { return held; }

    /** ms until it fires, or -1 if it is not armed. */
    long remaining(long now) {
        if (!held || fired) return -1;
        return Math.max(0, t0 + HOLD_MS - now);
    }

    /** @return true exactly once per press, when the hold reaches HOLD_MS. */
    boolean due(long now) {
        if (!held || fired || now - t0 < HOLD_MS) return false;
        fired = true;
        return true;
    }
}
