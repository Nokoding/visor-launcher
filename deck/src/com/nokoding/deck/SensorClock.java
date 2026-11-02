package com.nokoding.deck;

/**
 * 2.3: time step for controller sensor samples, from SensorEvent.timestamp (nanoseconds),
 * never from the wall clock or the frame clock. The first few events are dropped because
 * Bluetooth controllers often send a stale batch right after registering. A step that goes
 * backwards or is zero is skipped. A gap longer than GAP_S (the sensor paused) restarts
 * without integrating the gap. Normal steps are clamped to MAX_DT_S, so a late sample cannot
 * make a big jump, and uneven sample rates integrate correctly because each sample uses its
 * own step. Pure Java.
 */
final class SensorClock {
    static final int SKIP = 3;
    static final float MAX_DT_S = 0.05f;
    static final float GAP_S = 0.25f;

    private long last;
    private int seen;
    /** Smoothed sample rate in Hz, for the readout. 0 until known. */
    float hz;

    /** @return seconds to integrate for this sample, 0 to skip it. */
    float dt(long tsNs) {
        if (seen < SKIP) {
            seen++;
            last = tsNs;
            return 0f;
        }
        long d = tsNs - last;
        if (d <= 0) return 0f;           // out of order or duplicate: keep the newer time
        last = tsNs;
        float s = d / 1e9f;
        if (s > GAP_S) return 0f;         // paused: restart cleanly, do not integrate the gap
        float inst = 1f / s;
        hz = hz == 0f ? inst : hz * 0.95f + inst * 0.05f;
        return Math.min(s, MAX_DT_S);
    }

    void reset() {
        last = 0;
        seen = 0;
        hz = 0f;
    }
}
