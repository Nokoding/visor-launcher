package com.nokoding.deck;

/**
 * Click wheel style dial on a thumbstick. Rotating the stick around its rim adds
 * angle; every detent radians emits one tick (clockwise = next). A quick push and
 * release without rotation is a flick that steps one item. Pure Java, allocation free.
 * 2.2: the default detent is 60 degrees (6 ticks per turn), half the 2.1 speed. The
 * WHEEL STICK SPEED setting scales it (StickRoute.detent), and StickRoute caps items per second.
 */
final class StickDial {
    static final float DEAD_IN = 0.35f, DEAD_OUT = 0.30f;
    static final double DETENT = Math.PI / 3.0;     // 2.2 default: 60 degrees, 6 ticks per turn
    static final double DETENT_2_1 = Math.PI / 6.0; // 2.1: 30 degrees, 12 ticks per turn (200 percent now)
    double detent = DETENT;
    static final long FLICK_MS = 260;

    boolean active;
    private double last, acc;
    private int ticksThisPush;
    private long entryT;
    private float peakX, peakY, peakR;

    // results of the last update (reused, no allocation)
    int ticks, flickX, flickY;

    void update(float x, float y, long tMs) {
        ticks = 0;
        flickX = 0;
        flickY = 0;
        float r = (float) Math.hypot(x, y);
        if (r < (active ? DEAD_OUT : DEAD_IN)) {
            if (active) {
                if (ticksThisPush == 0 && tMs - entryT <= FLICK_MS) {
                    if (Math.abs(peakX) >= Math.abs(peakY)) flickX = peakX > 0 ? 1 : -1;
                    else flickY = peakY > 0 ? 1 : -1;
                }
                active = false;
                acc = 0;
            }
            return;
        }
        double a = Math.atan2(y, x);
        if (!active) {
            active = true;
            last = a;
            acc = 0;
            ticksThisPush = 0;
            entryT = tMs;
            peakX = x; peakY = y; peakR = r;
            return;
        }
        if (r > peakR) { peakR = r; peakX = x; peakY = y; }
        double d = a - last;
        if (d > Math.PI) d -= 2 * Math.PI;
        else if (d <= -Math.PI) d += 2 * Math.PI;
        last = a;
        if (Math.abs(d) > 2.6) return; // ambiguous jump through the center, ignore
        if (acc != 0 && (d > 0) != (acc > 0)) acc = 0; // direction change: start a fresh detent
        acc += d;
        double dt = detent > 0.05 ? detent : DETENT;
        while (acc >= dt) { ticks++; acc -= dt; }
        while (acc <= -dt) { ticks--; acc += dt; }
        ticksThisPush += Math.abs(ticks);
    }
}
