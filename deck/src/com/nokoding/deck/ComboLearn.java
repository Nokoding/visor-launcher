package com.nokoding.deck;

/**
 * 2.9 button combos: captures a pair of buttons from raw key events, for the small "make a
 * combo" screen. Pure Java, no Android types.
 *
 * Press and hold the first button, then press the second while the first is held. A button that
 * cannot be in a combo ends it at once (REJECTED, why says the reason). If the first button is
 * let go before the second is pressed it starts over. Nothing happening for more than
 * TIMEOUT_MS ends it (TIMEOUT). After DONE, REJECTED or TIMEOUT keys are ignored until start().
 * Feed it key downs with repeat count 0 only; a repeated down of the held key is ignored anyway.
 */
final class ComboLearn {
    static final int WAIT_FIRST = 0, WAIT_SECOND = 1, DONE = 2, REJECTED = 3, TIMEOUT = 4;
    static final long TIMEOUT_MS = 8000;

    /** The pair after DONE, a smaller than b. */
    long a, b;
    /** The reason after REJECTED, plain capitals. */
    String why = "";

    private int state = WAIT_FIRST;
    private boolean started;
    private long t0, first;

    ComboLearn() {}

    /** Begins a capture. */
    void start(long now) {
        reset();
        started = true;
        t0 = now;
    }

    /** Back to idle: keys are ignored until start(). */
    void reset() {
        state = WAIT_FIRST;
        started = false;
        a = 0;
        b = 0;
        why = "";
        first = 0;
        t0 = 0;
    }

    int state() { return state; }

    boolean finished() { return state == DONE || state == REJECTED || state == TIMEOUT; }

    /** @return the new state */
    int onKey(long key, boolean down, long now) {
        if (!started || finished()) return state;
        if (poll(now) == TIMEOUT) return state;
        if (state == WAIT_SECOND && !down) {
            if (key == first) state = WAIT_FIRST;   // let go before the second: start over
            return state;
        }
        if (!down) return state;
        if (state == WAIT_SECOND && key == first) return state;   // the same button again
        if (!ComboTable.allowedKey(key)) {
            String w = ComboTable.whyNot(key);
            why = w == null ? "NOT A VALID BUTTON" : w;
            state = REJECTED;
            return state;
        }
        if (state == WAIT_FIRST) {
            first = key;
            state = WAIT_SECOND;
            return state;
        }
        a = Math.min(first, key);
        b = Math.max(first, key);
        state = DONE;
        return state;
    }

    /** Timer: more than TIMEOUT_MS since start with no DONE gives TIMEOUT. @return the state */
    int poll(long now) {
        if (started && !finished() && now - t0 > TIMEOUT_MS) state = TIMEOUT;
        return state;
    }
}
