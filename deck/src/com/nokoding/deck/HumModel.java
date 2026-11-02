package com.nokoding.deck;

/**
 * Wheel hum envelope: spin speed from detent tick spacing, a ramp up when spinning
 * starts, a decay when it stops, and a throttle so the vibrator is never flooded. Pure Java.
 */
final class HumModel {
    float level, rate;
    /**
     * 2.2: items per second that count as full speed, and the gap that counts as stopped. Deck sets
     * them from the stick's per second cap, so the hum follows the items that really move.
     */
    float fullTps = HapticPatterns.HUM_FULL_TPS;
    int stopMs = HapticPatterns.HUM_STOP_MS;

    /** Full hum at the cap rate, and a stop gap long enough for ticks at that rate. */
    void scaleTo(float capPerSecond) {
        if (!(capPerSecond > 0f)) { fullTps = HapticPatterns.HUM_FULL_TPS; stopMs = HapticPatterns.HUM_STOP_MS; return; }
        fullTps = capPerSecond;
        stopMs = Math.max(HapticPatterns.HUM_STOP_MS, Math.round(2500f / capPerSecond));
    }
    private long lastTick = -100000, lastIssue = -100000;
    private int lastAmp;

    void onTick(long now) {
        long dt = now - lastTick;
        if (dt > 0 && dt < Math.max(600, stopMs * 2)) {
            float r = 1000f / dt;
            rate = rate == 0f ? r : rate * 0.6f + r * 0.4f;
        }
        lastTick = now;
    }

    void update(long now, float dtSec) {
        boolean spinning = now - lastTick < stopMs;
        if (!spinning) rate = 0f;
        float target = spinning ? Math.min(1f, rate / fullTps) : 0f;
        if (level < target) level = Math.min(target, level + dtSec * 1000f / HapticPatterns.HUM_RISE_MS);
        else level = Math.max(target, level - dtSec * 1000f / HapticPatterns.HUM_FALL_MS);
    }

    int amp() {
        if (level < 0.02f) return 0;
        int a = Math.round(HapticPatterns.HUM_MIN + (HapticPatterns.HUM_MAX - HapticPatterns.HUM_MIN) * level);
        return Math.min(HapticPatterns.HUM_MAX, a);
    }

    /** @return amplitude to send now, 0 to cancel, -1 when there is nothing to do. */
    int poll(long now) {
        int a = amp();
        if (a == 0) {
            if (lastAmp != 0) { lastAmp = 0; return 0; }
            return -1;
        }
        if (now - lastIssue >= HapticPatterns.HUM_REISSUE_MS) {
            lastIssue = now;
            lastAmp = a;
            return a;
        }
        return -1;
    }

    int current() { return lastAmp; }

    boolean active() { return level > 0f || lastAmp != 0; }
}
