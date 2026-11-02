package com.nokoding.deck;

/**
 * Anime style camera pan over a wide wrapping panorama. Every step adds to an
 * unbounded target. The camera chases it with a slow critically damped spring, so
 * it trails behind during fast spins and catches up in about 1.5 s. Pure Java.
 */
final class PanModel {
    static final double STEP_X = 60.0, STEP_Y = 34.0, Y_LIMIT = 110.0; // design px
    static final double OMEGA = 3.4; // rad/s, critically damped: settles in about 1.7 s

    double tx, ty, x, y, vx, vy;
    int dir = 1;          // +1 follow (picture moves with the press), -1 opposite
    double scale = 1.0;   // reduce effects sets this close to 0
    boolean moving;

    void step(int dx, int dy) {
        if (dx != 0) tx += dir * dx * STEP_X;
        if (dy != 0) ty = soft(ty + dir * dy * STEP_Y, Y_LIMIT);
        moving = true;
    }

    /** Advance the camera. @return true while it still moves. */
    boolean update(double dt) {
        if (!moving) return false;
        if (!(dt > 0)) return true;
        if (dt > 0.1) dt = 0.1;
        int n = (int) Math.ceil(dt / 0.008);
        double h = dt / n, w2 = OMEGA * OMEGA, c = 2.0 * OMEGA;
        for (int i = 0; i < n; i++) {
            vx += (-w2 * (x - tx) - c * vx) * h;
            x += vx * h;
            vy += (-w2 * (y - ty) - c * vy) * h;
            y += vy * h;
        }
        if (Math.abs(x - tx) < 0.5 && Math.abs(vx) < 1.5 && Math.abs(y - ty) < 0.5 && Math.abs(vy) < 1.5) {
            x = tx;
            y = ty;
            vx = 0;
            vy = 0;
            moving = false;
        }
        return moving;
    }

    /** Soft limit: linear near zero, then eases toward +-lim and never reaches it. */
    static double soft(double v, double lim) {
        double a = Math.abs(v), knee = lim * 0.6;
        if (a <= knee) return v;
        double r = knee + (lim - knee) * Math.tanh((a - knee) / (lim - knee));
        return v < 0 ? -r : r;
    }

    /** floorMod for doubles: result in [0, period). */
    static double wrap(double v, double period) {
        if (!(period > 0)) return 0;
        double r = v - period * Math.floor(v / period);
        return r >= period || r < 0 ? 0 : r;
    }
}
