package com.nokoding.deck;

/**
 * 2.3 type_tick budget. Typing owes one tick per 2 characters per active row; this decides
 * which of those really play. At most one tick every 25 ms (40 a second) across every
 * text animation on screen. Ticks over the cap are dropped, not queued, so no tick plays
 * after the typing has stopped. Each tick gets a small pitch jitter so a long burst does not
 * sound machine gunned. Pure Java, one shared instance per activity.
 */
final class TickScheduler {
    static final int MAX_PER_SEC = 40;
    static final long MIN_GAP_MS = 1000 / MAX_PER_SEC;
    /** Playback rate range for SoundPool: 0.9 to 1.1, so the 3.2 kHz built in tick stays in 2.9 to 3.5 kHz. */
    static final float JITTER = 0.10f;

    private long last = Long.MIN_VALUE / 4;
    private int seed = 0x2545F491;
    /** Ticks that were allowed to play since the last reset (for tests and the frame stats). */
    int played;

    /**
     * @param owed ticks the caller would like to play right now (0 or more)
     * @return true if one tick may play now
     */
    boolean offer(int owed, long now) {
        if (owed <= 0) return false;
        if (now - last < MIN_GAP_MS) return false;
        last = now;
        played++;
        return true;
    }

    /** Next playback rate, 1 - JITTER to 1 + JITTER. */
    float rate() {
        seed = seed * 1103515245 + 12345;
        float r = ((seed >>> 8) & 0xFFFF) / 65535f;   // 0..1
        return 1f - JITTER + 2f * JITTER * r;
    }

    void reset() {
        last = Long.MIN_VALUE / 4;
        played = 0;
    }
}
