package com.nokoding.deck;

import java.util.Collections;
import java.util.Set;

/**
 * 2.9 button combos: tracks the key presses around the combo table. Pure Java, allocates
 * nothing per key.
 *
 * Android gives an app no way to hold a key press back, so the FIRST key of a pair always
 * reaches the app. Only the SECOND key (the one that completes the pair while its partner is
 * held) is taken, together with its repeats and its up. The partner's up passes as usual.
 * Whichever key comes second completes the pair.
 *
 * Rules:
 *  - a key that is in no combo is always passed and leaves no trace;
 *  - only keys that ComboTable.allowedKey accepts are ever followed or taken;
 *  - a combo fires only when the app in front is known; in a game (the PS pass-through list)
 *    it fires only when REMAP IN GAMES (RemapGate.F_GAMES) is on, Deck in front always counts;
 *  - the same pair does not fire again within MIN_GAP_MS (the second press is still taken);
 *  - a held key that never got its up (a remote that went away) is forgotten after STALE_MS;
 *  - a new first press of a followed key clears its old entry, so nothing stays stuck.
 *
 * Both HomeActivity (fg is Deck) and PsService (fg is any other app) own one.
 */
final class ComboGate {
    static final int PASS = 0, SWALLOW = 1, ACT = 2;
    static final long STALE_MS = 30000, MIN_GAP_MS = 300;

    private static final int N = 8, FREE = 0, HELD = 1, TAKEN = 2;

    final String self;
    volatile ComboTable table = ComboTable.EMPTY;
    volatile int flags;
    volatile Set<String> pass = Collections.emptySet();
    /** The action to run after onKey answered ACT (a RemapAction number and its argument). */
    int act = RemapAction.DEFAULT;
    String arg = "";

    private final long[] hk = new long[N], ht = new long[N];
    private final int[] hs = new int[N];
    private long lastA, lastB, lastT;

    ComboGate(String self) { this.self = self; }

    /** Forgets every held key and the repeat guard. */
    void reset() {
        for (int i = 0; i < N; i++) hs[i] = FREE;
        lastA = 0;
        lastB = 0;
        lastT = 0;
        act = RemapAction.DEFAULT;
        arg = "";
    }

    private int find(long key) {
        for (int i = 0; i < N; i++) if (hs[i] != FREE && hk[i] == key) return i;
        return -1;
    }

    private int free() {
        for (int i = 0; i < N; i++) if (hs[i] == FREE) return i;
        return -1;
    }

    private void sweep(long now) {
        for (int i = 0; i < N; i++) if (hs[i] != FREE && now - ht[i] >= STALE_MS) hs[i] = FREE;
    }

    /**
     * One key event.
     * @param key    RemapKey.pack(...)
     * @param down   ACTION_DOWN (false for ACTION_UP)
     * @param repeat KeyEvent.getRepeatCount()
     * @param fg     package in front, null or empty if unknown
     * @return PASS (leave the key alone), SWALLOW (take it) or ACT (take it and run act and arg)
     */
    int onKey(long key, boolean down, int repeat, long now, String fg) {
        act = RemapAction.DEFAULT;
        arg = "";
        if (!RemapKey.valid(key)) return PASS;
        sweep(now);
        int i = find(key);
        if (!down) {
            if (i < 0) return PASS;
            boolean taken = hs[i] == TAKEN;
            hs[i] = FREE;
            return taken ? SWALLOW : PASS;
        }
        if (repeat > 0) {
            if (i < 0) return PASS;
            ht[i] = now;   // still held: not stale
            return hs[i] == TAKEN ? SWALLOW : PASS;
        }
        if (i >= 0) { hs[i] = FREE; i = -1; }   // a new first press: the old up never came
        if (!ComboTable.allowedKey(key)) return PASS;
        ComboTable t = table;
        if (t.size() == 0 || !t.involves(key)) return PASS;   // no combo: no trace
        boolean ctx = context(fg);
        int hit = -1;
        long partner = 0;
        if (ctx) {
            for (int j = 0; j < N && hit < 0; j++) {
                if (hs[j] == FREE || !ComboTable.allowedKey(hk[j])) continue;
                int h = t.indexOf(key, hk[j]);
                if (h >= 0 && ComboTable.validAction(t.actionAt(h), t.argAt(h), key, hk[j])) { hit = h; partner = hk[j]; }
            }
        }
        int s = free();
        if (s < 0) return PASS;   // no room to follow it: leave the key alone
        hk[s] = key;
        ht[s] = now;
        if (hit < 0) {
            hs[s] = HELD;
            return PASS;
        }
        hs[s] = TAKEN;
        long a = Math.min(key, partner), b = Math.max(key, partner);
        if (a == lastA && b == lastB && now - lastT >= 0 && now - lastT < MIN_GAP_MS) return SWALLOW;
        lastA = a;
        lastB = b;
        lastT = now;
        act = t.actionAt(hit);
        arg = t.argAt(hit);
        return ACT;
    }

    private boolean context(String fg) {
        if (fg == null || fg.isEmpty()) return false;
        if (fg.equals(self)) return true;
        Set<String> p = pass;
        return !(p != null && p.contains(fg) && (flags & RemapGate.F_GAMES) == 0);
    }
}
