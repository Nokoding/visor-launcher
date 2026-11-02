package com.nokoding.deck;

/**
 * 2.11: a modifier key that works like a modifier: held while the key is down, not held the moment it comes up.
 * Used by stick volume (and usable by any hold-a-key feature). Pure Java, no allocation.
 *
 * The up of a key can get lost: when the accessibility service changes what it listens for, Android rebuilds its
 * input filter and the up that follows can go straight to the app. Without a guard the key would then stay
 * "held" until the next press, which looks like a toggle. So a key that has not been seen or touched for
 * STALE_MS counts as let go, and every stick event while it is held refreshes the time.
 */
final class HoldKey {
    static final long STALE_MS = 20000;

    private boolean down;
    private long last;

    /** The key went down (a first press, repeats are ignored by the caller). @return true when the state changed. */
    boolean press(long now) {
        last = now;
        if (down) return false;
        down = true;
        return true;
    }

    /** The key came up. @return true when the state changed. */
    boolean release() {
        if (!down) return false;
        down = false;
        return true;
    }

    /** Something is still going on while the key is held (a stick event): keeps it from going stale. */
    void touch(long now) { if (down) last = now; }

    /** True while the key is down and not stale. */
    boolean held(long now) {
        if (down && now - last > STALE_MS) down = false;   // the up was lost
        if (down && now < last) last = now;                // a clock that went backwards must not keep it for ever
        return down;
    }

    void reset() { down = false; }
}
