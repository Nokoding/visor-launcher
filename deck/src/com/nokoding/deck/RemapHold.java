package com.nokoding.deck;

/**
 * 2.6: a hold-to-continue timer for the ADVANCED REMAP warnings (hold OK or A for 3 seconds).
 * Pure Java. Key repeats keep the same start; letting go starts over.
 */
final class RemapHold {
    final long ms;
    private long t0 = -1;
    private boolean fired;

    RemapHold(long ms) { this.ms = Math.max(1, ms); }

    void down(long now) { if (t0 < 0) t0 = now; }

    void up() { t0 = -1; fired = false; }

    boolean holding() { return t0 >= 0 && !fired; }

    /** 0..1 */
    float progress(long now) {
        if (t0 < 0) return 0f;
        return Math.max(0f, Math.min(1f, (now - t0) / (float) ms));
    }

    /** @return true exactly once per hold, when it reaches ms. */
    boolean due(long now) {
        if (t0 < 0 || fired || now - t0 < ms) return false;
        fired = true;
        return true;
    }
}
