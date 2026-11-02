package com.nokoding.deck;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 2.8 game profiles: decides when a profile is applied and when the display is put back. Pure Java,
 * no threads, no clock of its own: the caller passes `now` in milliseconds and carries out the
 * action each call returns. Use a monotonic clock (SystemClock.elapsedRealtime) for `now`.
 *
 * States:
 *   IDLE        nothing applied.
 *   ACTIVE      a profile is applied and its app is in front.
 *   GRACE       a profile is applied, its app was left; waiting GRACE_MS in case the user comes back.
 *   CONFIRMING  a profile was just applied for a combination (package, scale, method) that was never
 *               kept before; waiting KEEP_SECONDS for KEEP, otherwise it is taken back (REVERT).
 *
 * Actions: NONE; APPLY (apply the profile now in active(); when a profile was already applied the new
 * values REPLACE the old ones, and replaced() says which profile was in place so the caller can undo a
 * per app change such as a game mode downscale); RESTORE (put everything back); REVERT (put everything
 * back because the user did not confirm).
 *
 * Safety choices:
 * - A clock that jumps backwards by more than BACK_TOLERANCE_MS counts as expired, so a restore or
 *   revert is never held up by a bad clock.
 * - After the user turned a profile off by hand (toggle, cancel, forceRestore while the app was in
 *   front, a timeout revert) the same app does not get the profile again until another app was seen
 *   in front. Otherwise a revert would apply again at once.
 * - An unknown front app (null or "") never applies anything. While ACTIVE it counts like an app
 *   without a profile (GRACE starts), so a broken app detection cannot leave the display changed.
 */
final class ProfileMachine {
    static final int IDLE = 0, ACTIVE = 1, GRACE = 2, CONFIRMING = 3;
    static final int NONE = 0, APPLY = 1, RESTORE = 2, REVERT = 3;
    static final long BACK_TOLERANCE_MS = 2000;
    private static final int MAX_CONFIRMED = 400, MAX_KEY = 300;

    private final GameProfiles profiles;
    private int state = IDLE;
    private GameProfiles.Profile active;       // copy taken when it was applied
    private GameProfiles.Profile replaced;     // what the last APPLY replaced
    private long since, deadline;
    private String suppressed;
    private final Set<String> confirmed = new HashSet<>();

    ProfileMachine(GameProfiles profiles) {
        this.profiles = profiles == null ? new GameProfiles() : profiles;
    }

    // ---------------------------------------------------------------- confirmed combinations

    /** Combination key: package, scale and method. */
    static String key(GameProfiles.Profile p) { return p.pkg + "|" + p.scalePct + "|" + p.method; }

    /** True if this combination was never kept before, so the user must confirm it. */
    boolean needsConfirm(GameProfiles.Profile p) { return !confirmed.contains(key(p)); }

    /** A copy of the keys the user confirmed (for saving). */
    Set<String> confirmed() { return Collections.unmodifiableSet(new HashSet<>(confirmed)); }

    /** Loads saved keys. Null is ignored; too many or too long entries are dropped. */
    void setConfirmed(Set<String> keys) {
        confirmed.clear();
        if (keys == null) return;
        for (String k : keys) {
            if (confirmed.size() >= MAX_CONFIRMED) break;
            if (k != null && !k.isEmpty() && k.length() <= MAX_KEY) confirmed.add(k);
        }
    }

    // ---------------------------------------------------------------- events

    /**
     * The app now in front. pkg null or "" is unknown.
     * IDLE: a package with a profile is applied (APPLY; CONFIRMING when it needs confirming, else ACTIVE).
     * ACTIVE or GRACE: the same app again goes back to ACTIVE (NONE). Another app with a profile
     * switches directly: APPLY for the new profile (see replaced()). An app without a profile starts
     * GRACE with the deadline now + GRACE_MS, but only if the profile has autoRestore; without it the
     * display stays changed until forceRestore. Deck itself in front is just an app without a profile.
     * CONFIRMING: nothing changes (the countdown keeps running) except that another app with a
     * profile switches directly like above.
     */
    int onForeground(String pkg, long now) {
        replaced = null;
        boolean known = pkg != null && !pkg.isEmpty();
        if (known && suppressed != null && !pkg.equals(suppressed)) suppressed = null;
        switch (state) {
            case IDLE: {
                if (!known || pkg.equals(suppressed)) return NONE;
                GameProfiles.Profile p = profileFor(pkg);
                return p == null ? NONE : begin(p, now);
            }
            case CONFIRMING: {
                if (known && !pkg.equals(active.pkg)) {
                    GameProfiles.Profile p = profileFor(pkg);
                    if (p != null) return begin(p, now);
                }
                return NONE;
            }
            case ACTIVE:
            case GRACE: {
                if (known && pkg.equals(active.pkg)) { state = ACTIVE; return NONE; }
                if (known) {
                    GameProfiles.Profile p = profileFor(pkg);
                    if (p != null) return begin(p, now);
                }
                if (state == ACTIVE && active.autoRestore) {
                    state = GRACE; since = now; deadline = add(now, DisplayRules.GRACE_MS);
                }
                return NONE;
            }
            default:
                return NONE;
        }
    }

    /** The profile of an app in front, or null (none, or the user turned it off by hand and the app has not left yet). */
    private GameProfiles.Profile profileFor(String pkg) {
        return pkg.equals(suppressed) ? null : profiles.get(pkg);
    }

    private int begin(GameProfiles.Profile p, long now) {
        replaced = state == IDLE ? null : active;
        active = p;
        since = now;
        if (needsConfirm(p)) {
            state = CONFIRMING; deadline = add(now, DisplayRules.KEEP_SECONDS * 1000L);
        } else {
            state = ACTIVE; deadline = 0;
        }
        return APPLY;
    }

    /** Call about once a second (or when a timer is due). GRACE past its deadline: RESTORE. CONFIRMING past its deadline: REVERT (the key is not stored). */
    int tick(long now) {
        replaced = null;
        if (state == GRACE && expired(now)) {
            state = IDLE; deadline = 0;
            return RESTORE;
        }
        if (state == CONFIRMING && expired(now)) {
            suppressed = active.pkg;
            state = IDLE; deadline = 0;
            return REVERT;
        }
        return NONE;
    }

    /** The user pressed KEEP: CONFIRMING becomes ACTIVE and the combination is remembered. Any other state: NONE, nothing changes. */
    int confirm() {
        replaced = null;
        if (state == CONFIRMING) {
            confirmed.add(key(active));
            state = ACTIVE; deadline = 0;
        }
        return NONE;
    }

    /**
     * The user pressed REVERT NOW. CONFIRMING: REVERT. ACTIVE or GRACE: RESTORE. Either way the
     * machine goes IDLE and the same app is not applied again until another app was in front.
     * IDLE: NONE.
     */
    int cancel() {
        replaced = null;
        if (state == IDLE) return NONE;
        int r = state == CONFIRMING ? REVERT : RESTORE;
        suppressed = active.pkg;
        state = IDLE; deadline = 0;
        return r;
    }

    /**
     * Restore now, whatever the state (RESTORE DISPLAY row, Deck starting, the adb command). From
     * IDLE it also returns RESTORE, always safe because the reset commands only put back the panel's
     * own values, and the state stays IDLE. From ACTIVE or CONFIRMING the app is in front, so it is
     * not applied again until another app was seen; from GRACE it can be applied when the app returns.
     */
    int forceRestore() {
        replaced = null;
        if (state != IDLE) {
            if (state == ACTIVE || state == CONFIRMING) suppressed = active.pkg;
            state = IDLE; deadline = 0;
        }
        return RESTORE;
    }

    /**
     * The chord. IDLE and pkg has a profile: APPLY like onForeground (and a suppression of that app
     * is lifted, the user asked for it). Not IDLE: RESTORE and IDLE, and the profile's app is not
     * applied again until another app was seen in front.
     */
    int toggle(String pkg, long now) {
        replaced = null;
        if (state != IDLE) {
            suppressed = active.pkg;
            state = IDLE; deadline = 0;
            return RESTORE;
        }
        if (pkg == null || pkg.isEmpty()) return NONE;
        GameProfiles.Profile p = profiles.get(pkg);
        if (p == null) return NONE;
        if (pkg.equals(suppressed)) suppressed = null;
        return begin(p, now);
    }

    // ---------------------------------------------------------------- reading

    int state() { return state; }

    /** A copy of the profile now applied, or null when IDLE. */
    GameProfiles.Profile active() { return state == IDLE || active == null ? null : active.copy(); }

    /** The profile the last APPLY replaced (a direct switch), else null. Valid until the next call. */
    GameProfiles.Profile replaced() { return replaced == null ? null : replaced.copy(); }

    /** The deadline of GRACE or CONFIRMING in the caller's clock, else 0. */
    long deadline() { return state == GRACE || state == CONFIRMING ? deadline : 0; }

    /** Seconds left of the countdown (rounded up, never negative, never more than the countdown's length). 0 outside GRACE and CONFIRMING. */
    int secondsLeft(long now) {
        if (state != GRACE && state != CONFIRMING) return 0;
        if (expired(now)) return 0;
        long ms = deadline - now;
        long max = state == GRACE ? DisplayRules.GRACE_MS : DisplayRules.KEEP_SECONDS * 1000L;
        if (ms > max) ms = max;
        return (int) ((ms + 999) / 1000);
    }

    // ---------------------------------------------------------------- helpers

    private boolean expired(long now) {
        if (now >= deadline) return true;
        long back = since - now;   // positive when the clock went backwards
        return now < since && (back < 0 || back > BACK_TOLERANCE_MS);
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
