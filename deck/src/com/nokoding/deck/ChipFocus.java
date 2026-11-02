package com.nokoding.deck;

/**
 * 2.4.1: which top chip of a full screen test has the focus ring, and how the sticks, the
 * D-pad and the remote move it. Pure Java, no Android types.
 *
 * Rules:
 * - Chip focus starts only on purpose. Pushing the navigator stick up (the left stick, or the
 *   right one when WHEEL STICK is swapped) starts it. The tested pad's own D-pad starts it only
 *   where D-pad presses are not test inputs (padDpadStarts, the Gyro test). The remote's D-pad
 *   up always starts it, the remote is never a tested button.
 * - With a chip focused the navigator stick or the D-pad moves left and right along the chips
 *   (no wrap), and the stick or D-pad moving down leaves the chips again.
 * - activate() only says which chip A or OK would press, it never changes the focus.
 * - Never a trap: the focus lets go on its own after IDLE_MS without any input for the chips,
 *   so a drifting stick or a forgotten focus cannot keep eating A in the Gamepad test.
 */
final class ChipFocus {
    static final int NONE = -1;
    static final float UP = 0.6f, UP_OFF = 0.35f;
    static final long IDLE_MS = 6000;

    private final boolean padDpadStarts;
    private int count, focus = NONE, last;
    private boolean up, down;
    private long usedAt;
    private final PsStick horiz = new PsStick();

    ChipFocus(int count, boolean padDpadStarts) {
        this.count = Math.max(0, count);
        this.padDpadStarts = padDpadStarts;
    }

    void setCount(int n) {
        count = Math.max(0, n);
        if (focus >= count) focus = count == 0 ? NONE : count - 1;
        if (last >= count) last = Math.max(0, count - 1);
    }

    int focus() { return focus; }

    boolean active() { return focus != NONE; }

    /** The chip A, Cross or OK would press now, or NONE. */
    int activate() { return focus; }

    /** Let go of the chips. */
    void clear() {
        focus = NONE;
        up = down = false;
        horiz.reset();
    }

    private boolean start(long now) {
        if (count == 0 || focus != NONE) return false;
        focus = Math.max(0, Math.min(last, count - 1));
        usedAt = now;
        horiz.reset();
        return true;
    }

    private boolean leave() {
        if (focus == NONE) return false;
        last = focus;
        focus = NONE;
        horiz.reset();
        return true;
    }

    private boolean step(int s, long now) {
        if (focus == NONE || s == 0) return false;
        usedAt = now;
        int nf = Math.max(0, Math.min(count - 1, focus + s));
        if (nf == focus) return false;
        focus = nf;
        return true;
    }

    /**
     * The navigator stick moved. @return true if the focus changed (redraw).
     * Up starts, down leaves, left and right step with the D-pad's repeat (380 ms, then 110 ms).
     * Mostly vertical pushes never step sideways.
     */
    boolean stick(float x, float y, long now) {
        if (Float.isNaN(x)) x = 0f;
        if (Float.isNaN(y)) y = 0f;
        boolean nowUp = up ? y < -UP_OFF : y <= -UP, nowDown = down ? y > UP_OFF : y >= UP;
        boolean changed = false;
        if (nowUp && !up) changed |= start(now);
        if (nowDown && !down) changed |= leave();
        up = nowUp;
        down = nowDown;
        float hx = focus != NONE && Math.abs(x) >= Math.abs(y) ? x : 0f;
        changed |= step(horiz.feed(hx, now), now);
        return changed;
    }

    /** Repeat while the stick is held left or right. @return true if the focus changed. */
    boolean poll(long now) {
        if (focus == NONE) return false;
        return step(horiz.poll(now), now);
    }

    /** ms until poll() is due again, -1 when no repeat is running. */
    long waitMs(long now) { return focus == NONE ? -1 : horiz.waitMs(now); }

    /**
     * One D-pad press (dx or dy is -1, 0 or 1). fromPad: it came from the tested controller.
     * @return true if the chips used the press (the remote's keys then skip their old shortcut).
     */
    boolean key(int dx, int dy, boolean fromPad, long now) {
        if (dy < 0) {
            if (focus != NONE) return false;   // already there, up does nothing more
            if (fromPad && !padDpadStarts) return false;
            return start(now);
        }
        if (dy > 0) return leave();
        if (dx != 0 && focus != NONE) { step(dx, now); return true; }
        return false;
    }

    /** Idle release. @return true if the focus was dropped (redraw). */
    boolean expire(long now) {
        if (focus != NONE && now - usedAt >= IDLE_MS) { leave(); return true; }
        return false;
    }

    /** ms until the idle release, -1 when nothing is focused. */
    long expireIn(long now) { return focus == NONE ? -1 : Math.max(0, usedAt + IDLE_MS - now); }
}
