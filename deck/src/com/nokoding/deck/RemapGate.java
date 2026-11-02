package com.nokoding.deck;

import java.util.Collections;
import java.util.Set;

/**
 * 2.6 button remap: the decision, in pure Java so the JVM tests cover every case.
 *
 * Two layers.
 *
 * decide() is one pure function. Given a button, a kind of press (the first press, a completed
 * short press, a long press, the escape), the app in front, the rules, the advanced switches, the
 * pass-through list and the escape state, it answers: take the button or leave it, and which
 * action to run. Its first step is the escape: Back held for ESCAPE_MS clears the navigation
 * remaps, and while that escape is active no navigation button is ever taken. After that:
 *
 *  - a button with no rule is never taken (the service never consumes a key it has no rule for);
 *  - a protected button is never taken;
 *  - in a game (the PS pass-through list) nothing is taken unless REMAP IN GAMES is on;
 *  - an advanced button needs the advanced unlock, a navigation button too, and outside Deck
 *    also the SYSTEM WIDE NAVIGATION REMAP switch;
 *  - a DECK KEY action (read this button as another one) exists inside Deck only, it is a swap
 *    done by Deck itself, never a key sent to Android;
 *  - a long press action next to a DEFAULT short press turns the short press into DO NOTHING,
 *    because Android gives apps no way to hand a swallowed press back to the system.
 *
 * RemapGate (the instance) tracks the key presses around that function: the first down is
 * taken or passed, repeats and the up follow the same way (pairs stay whole: a game never sees
 * half a press, only taken presses need a slot, a passed one leaves no trace), a held button with a long press action waits for the up or LONG_MS, and a
 * repeat guard stops one button from firing again within MIN_GAP_MS. It has no Android types and
 * allocates nothing per key.
 *
 * Both HomeActivity (fg is Deck) and PsService (fg is any other app) own one.
 */
final class RemapGate {

    static final long LONG_MS = 500, ESCAPE_MS = 6000, STALE_MS = 30000, MIN_GAP_MS = 120;
    /** Switch bits. */
    static final int F_ADVANCED = 1, F_NAV = 2, F_GAMES = 4,
            /** 2.17: Home remap accepted (the third warning), and the key filter of the overlay service is the one asking. */
            F_HOME = 8, F_SERVICE = 16;
    /** 2.17 Home escape: this many Home presses within HOME_ESCAPE_MS pass Home through and switch the Home remap off. */
    static final int HOME_ESCAPE_PRESSES = 5;
    static final long HOME_ESCAPE_MS = 4000;
    /** Kinds of press for decide(). */
    static final int P_DOWN = 0, P_SHORT = 1, P_LONG = 2, P_ESCAPE = 3;
    /** Escape chord state: Back is not held, is held, or has been held long enough. */
    static final int ESC_IDLE = 0, ESC_HELD = 1, ESC_FIRED = 2;
    /** What onKey answers. */
    static final int PASS = 0, SWALLOW = 1, ACT = 2, SWAP = 3;
    /** What poll answers. */
    static final int NONE = 0, LONG_ACT = 1, ESCAPE = 2;

    /** The answer of decide(). One instance is reused. */
    static final class Decision {
        boolean consume, immediate, longPending, clearNav;
        int action = RemapAction.DEFAULT;
        String arg = "";
        /** Not 0: Deck reads the button as this key code (a swap). Never consumes. */
        int swapCode;

        void reset() {
            consume = false; immediate = false; longPending = false; clearNav = false;
            action = RemapAction.DEFAULT; arg = ""; swapCode = 0;
        }
    }

    /**
     * The pure decision.
     * @param press  P_DOWN: is this button taken at all. P_SHORT, P_LONG: which action runs. P_ESCAPE: the chord completed.
     * @param fg     package in front, null if not known (nothing is taken then)
     * @param self   Deck's own package
     * @param pass   packages that keep their buttons (games), may be null
     * @param escape ESC_IDLE, ESC_HELD or ESC_FIRED
     */
    static void decide(Decision d, long key, int press, String fg, String self, RemapTable t, int flags, Set<String> pass, int escape) {
        d.reset();
        // first step: the escape. Back held long enough clears the navigation remaps, and until it is let go no navigation button is taken.
        if (press == P_ESCAPE) { d.clearNav = true; return; }
        if (escape == ESC_FIRED && RemapKey.nav(key)) return;
        if (t == null || !RemapKey.remappable(key)) return;
        RemapTable.Rule r = t.get(key);
        if (r == null || !r.active()) return;
        if (fg == null || fg.isEmpty()) return;
        boolean inDeck = fg.equals(self);
        if (!inDeck && (flags & F_GAMES) == 0 && pass != null && pass.contains(fg)) return;
        int cls = RemapKey.cls(key);
        if (cls != RemapKey.STANDARD && (flags & F_ADVANCED) == 0) return;
        // 2.17: Home is taken only by the overlay service's key filter, and only after the Home warning was accepted
        if (key == RemapKey.KC_HOME && ((flags & F_HOME) == 0 || (flags & F_SERVICE) == 0)) return;
        int s = r.shortA, l = r.longA;
        if (RemapAction.deckKey(s)) {
            if (!inDeck || cls != RemapKey.NAV) return;
            d.swapCode = RemapAction.deckKeyCode(s);
            d.action = s;
            return;
        }
        if (RemapAction.deckKey(l) || (l != RemapAction.DEFAULT && !RemapAction.allowedFor(l, key))) l = RemapAction.DEFAULT;
        if (!RemapAction.allowedFor(s, key)) s = RemapAction.DEFAULT;
        if (cls == RemapKey.NAV && !inDeck && (flags & F_NAV) == 0) return;
        if (s == RemapAction.DEFAULT && l == RemapAction.DEFAULT) return;
        switch (press) {
            case P_SHORT:
                d.consume = true;
                d.action = s == RemapAction.DEFAULT ? RemapAction.NOTHING : s;
                d.arg = s == RemapAction.DEFAULT ? "" : r.shortArg;
                break;
            case P_LONG:
                d.consume = true;
                if (l == RemapAction.DEFAULT) { d.action = s; d.arg = r.shortArg; } else { d.action = l; d.arg = r.longArg; }
                break;
            default:   // P_DOWN
                d.consume = true;
                d.longPending = l != RemapAction.DEFAULT;
                d.immediate = !d.longPending;
                if (d.immediate) { d.action = s; d.arg = r.shortArg; }
                break;
        }
    }

    // ------------------------------------------------------------------ state

    private static final int N = 6, FREE = 0, TAKEN = 2, DONE = 3;

    final String self;
    volatile RemapTable table = RemapTable.EMPTY;
    volatile int flags;
    volatile Set<String> pass = Collections.emptySet();
    final Decision d = new Decision();
    /** Results of the last onKey or poll that answered ACT, SWAP or LONG_ACT. */
    int act = RemapAction.DEFAULT, swapCode;
    String arg = "";
    int esc = ESC_IDLE;

    private final long[] sk = new long[N], st = new long[N];
    private final int[] ss = new int[N];
    private long escT0, lastKey, lastT = -1000000;
    private final long[] homeT = new long[HOME_ESCAPE_PRESSES];
    private int homeN;
    private boolean homeOff, homeEscapeFired;

    /** True once after the Home escape fired: the caller switches the Home remap off for good. */
    boolean takeHomeEscape() {
        boolean f = homeEscapeFired;
        homeEscapeFired = false;
        return f;
    }

    RemapGate(String self) { this.self = self; }

    private int find(long key) {
        for (int i = 0; i < N; i++) if (ss[i] != FREE && sk[i] == key) return i;
        return -1;
    }

    private int free() {
        for (int i = 0; i < N; i++) if (ss[i] == FREE) return i;
        return -1;
    }

    /** Frees presses that never ended (a remote that went away), so a slot cannot stay taken for ever. */
    private void sweep(long now) {
        for (int i = 0; i < N; i++) if (ss[i] != FREE && now - st[i] >= STALE_MS) ss[i] = FREE;
    }

    /**
     * One key event.
     * @param key    RemapKey.pack(...)
     * @param down   ACTION_DOWN (false for ACTION_UP)
     * @param repeat KeyEvent.getRepeatCount() (the key filter sends none, Deck's window does)
     * @param fg     package in front
     */
    int onKey(long key, boolean down, int repeat, long now, String fg) {
        act = RemapAction.DEFAULT;
        arg = "";
        swapCode = 0;
        if (!RemapKey.valid(key)) return PASS;
        sweep(now);
        if (key == RemapKey.KC_HOME) {
            // 2.17 Home escape: five quick presses give Home back to the system, whatever the rules say
            if (homeOff) return PASS;
            if (down && repeat == 0) {
                if (homeN == HOME_ESCAPE_PRESSES) { System.arraycopy(homeT, 1, homeT, 0, HOME_ESCAPE_PRESSES - 1); homeN--; }
                homeT[homeN++] = now;
                if (homeN == HOME_ESCAPE_PRESSES && now - homeT[0] <= HOME_ESCAPE_MS) {
                    homeOff = true;
                    homeEscapeFired = true;
                    int hi = find(key);
                    if (hi >= 0) ss[hi] = FREE;
                    return PASS;
                }
            }
        }
        boolean back = key == RemapKey.KC_BACK;
        int i = find(key);
        if (!down) {
            if (back) esc = ESC_IDLE;
            if (i < 0) return swapOf(key, fg);
            int state = ss[i];
            long t0 = st[i];
            ss[i] = FREE;
            if (state == DONE) return SWALLOW;
            decide(d, key, now - t0 >= LONG_MS ? P_LONG : P_SHORT, fg, self, table, flags, pass, esc);
            if (!d.consume) return SWALLOW;   // the rules changed under a held button: it is ours already, end it quietly
            return fire(key, now);
        }
        if (i >= 0 && repeat == 0) { ss[i] = FREE; i = -1; }   // a new first press: the old up never came
        if (back && repeat == 0) { esc = ESC_HELD; escT0 = now; }
        if (i >= 0 && repeat > 0) return SWALLOW;
        decide(d, key, P_DOWN, fg, self, table, flags, pass, esc);
        if (d.swapCode != 0) { swapCode = d.swapCode; act = d.action; return SWAP; }
        if (repeat > 0) return PASS;   // its first down went to the app
        if (!d.consume) return PASS;   // not followed: its repeats and its up find no slot and pass the same way
        int s = free();
        if (s < 0) return PASS;        // no room to follow it: leave the button alone
        sk[s] = key;
        st[s] = now;
        if (d.immediate) {
            ss[s] = DONE;
            return fire(key, now);
        }
        ss[s] = TAKEN;
        return SWALLOW;
    }

    private int swapOf(long key, String fg) {
        decide(d, key, P_DOWN, fg, self, table, flags, pass, esc);
        if (d.swapCode == 0) return PASS;
        swapCode = d.swapCode;
        act = d.action;
        return SWAP;
    }

    /** The decision in d says run an action: guard against a button firing again at once. */
    private int fire(long key, long now) {
        if (key == lastKey && now - lastT < MIN_GAP_MS) return SWALLOW;
        lastKey = key;
        lastT = now;
        act = d.action;
        arg = d.arg;
        return act == RemapAction.NOTHING ? SWALLOW : ACT;
    }

    /**
     * Timers: call when nextDelay says so. @return LONG_ACT (run act and arg), ESCAPE (clear the
     * navigation remaps, the chord completed) or NONE. Call again until NONE.
     */
    int poll(long now, String fg) {
        act = RemapAction.DEFAULT;
        arg = "";
        if (esc == ESC_HELD && now - escT0 >= ESCAPE_MS) {
            decide(d, RemapKey.KC_BACK, P_ESCAPE, fg, self, table, flags, pass, esc);
            if (d.clearNav) { esc = ESC_FIRED; return ESCAPE; }
        }
        for (int i = 0; i < N; i++) {
            if (ss[i] != TAKEN || now - st[i] < LONG_MS) continue;
            ss[i] = DONE;
            decide(d, sk[i], P_LONG, fg, self, table, flags, pass, esc);
            if (!d.consume) continue;
            if (fire(sk[i], now) == ACT) return LONG_ACT;
        }
        sweep(now);
        return NONE;
    }

    /** Milliseconds until poll has something to do, or -1. */
    long nextDelay(long now) {
        long best = -1;
        if (esc == ESC_HELD) best = Math.max(0, escT0 + ESCAPE_MS - now);
        for (int i = 0; i < N; i++) {
            if (ss[i] != TAKEN) continue;
            long w = Math.max(0, st[i] + LONG_MS - now);
            if (best < 0 || w < best) best = w;
        }
        return best;
    }

    /** True while a taken press is waiting for its up or its long press. */
    boolean busy() {
        for (int i = 0; i < N; i++) if (ss[i] == TAKEN) return true;
        return esc == ESC_HELD;
    }

    /** True if a press of this button was taken and has not ended (its up must be taken too). */
    boolean holds(long key) {
        int i = find(key);
        return i >= 0;
    }

    /** Forget every half press. Used after an error so nothing stays swallowed. */
    void reset() {
        for (int i = 0; i < N; i++) ss[i] = FREE;
        esc = ESC_IDLE;
        homeN = 0;
        homeOff = false;
        lastKey = 0;
        lastT = -1000000;
    }
}
