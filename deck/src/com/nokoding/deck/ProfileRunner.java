package com.nokoding.deck;

import android.annotation.SuppressLint;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.Set;

/**
 * 2.8 game profiles, the Android side. Only the DECK DISPLAY method runs here: Deck changes the whole
 * display size and density itself (DisplayControl, needs the one adb grant, experimental). The other
 * methods (GAME MODE, PHONE HELPER, COPY COMMANDS) cannot be run by an app, so Deck only shows their
 * commands (see GameProfileScreen in HomeActivity and docs/game-profiles.md).
 *
 * Safety rules, in the order they matter:
 *  - the ORIGINAL size and density are saved (DisplayState) before the first change, and a change is
 *    refused when another override is already in place and nothing was saved;
 *  - the first time a profile value is applied it asks KEEP or REVERT, and reverts by itself after
 *    DisplayRules.KEEP_SECONDS;
 *  - restore happens when the profile app has been gone longer than the grace period, when Deck or the
 *    service starts, from RESTORE DISPLAY, from deck_cmd restore_display, and the last resort is
 *    "adb shell wm size reset; adb shell wm density reset". Restore never reads the saved values: it
 *    clears the override, so a corrupt prefs file cannot stop it.
 * The state machine itself (ProfileMachine) is pure and tested. This class only feeds it the front app,
 * a clock and prefs, and carries out what it says.
 *
 * The front app comes from PsService window events when the service runs, otherwise from a usage stats
 * poll every 2 seconds, which runs only while a profile is applied.
 */
@SuppressLint("ApplySharedPref")   // the marker and the saved original must be on disk before the display changes
final class ProfileRunner {

    interface Ui {
        /** Show the KEEP or REVERT prompt. */
        void showKeep(GameProfiles.Profile p);
        void keepTick(int secondsLeft);
        void hideKeep();
        void toast(String s);
    }

    static final String PREF_PROFILES = "gameProfiles", PREF_ORIG = "dispOrig", PREF_ACTIVE = "dispActive", PREF_CONFIRMED = "profConfirmed", PREF_BLOCKED = "profBlocked";
    private static final long POLL_MS = 2000;

    @SuppressLint("StaticFieldLeak")   // holds the application context only
    private static ProfileRunner inst;

    static synchronized ProfileRunner get(Context c) {
        if (inst == null) inst = new ProfileRunner(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private GameProfiles all = new GameProfiles();
    private ProfileMachine machine = new ProfileMachine(new GameProfiles());
    private Ui deckUi, svcUi;
    private boolean ticking;
    /** Set by the host that owns the front app events (the service). */
    volatile boolean serviceFeeds;
    private Runnable afterKeep;

    private ProfileRunner(Context c) {
        ctx = c;
        prefs = c.getSharedPreferences("deck", Context.MODE_PRIVATE);
        reload();
    }

    void setDeckUi(Ui u) { deckUi = u; }

    void setServiceUi(Ui u) { svcUi = u; }

    private Ui ui() { return deckUi != null ? deckUi : svcUi; }

    GameProfiles profiles() { return all; }

    /** Reads the saved profiles and rebuilds the machine, keeping what was confirmed. */
    synchronized void reload() {
        all = GameProfiles.parse(prefs.getString(PREF_PROFILES, ""));
        GameProfiles deckOnly = new GameProfiles();
        for (GameProfiles.Profile p : all.list()) if (p.method == GameProfiles.M_DISPLAY_DECK) deckOnly.put(p);
        Set<String> conf = machine.confirmed();
        if (conf.isEmpty()) conf = loadConfirmed();
        int st = machine.state();
        machine = new ProfileMachine(deckOnly);
        machine.setConfirmed(conf);
        // a change of the profile list while one is applied: put the display back, it is the safe choice
        if (st != ProfileMachine.IDLE) restoreNow();
    }

    void save() {
        prefs.edit().putString(PREF_PROFILES, all.save()).apply();
        reload();
    }

    private Set<String> loadConfirmed() {
        HashSet<String> s = new HashSet<>();
        String raw = prefs.getString(PREF_CONFIRMED, "");
        if (raw != null) for (String k : raw.split("\n")) if (!k.isEmpty()) s.add(k);
        return s;
    }

    private void saveConfirmed() {
        prefs.edit().putString(PREF_CONFIRMED, String.join("\n", machine.confirmed())).apply();
    }

    /** 2.17: true once DECK DISPLAY was tried on this box and Android refused the call. */
    static boolean deckDisplayBlocked(SharedPreferences p) {
        try { return p.getBoolean(PREF_BLOCKED, false); } catch (Throwable t) { return false; }
    }

    boolean canApply() { return DisplayControl.granted(ctx); }

    // ---------------------------------------------------------------- feeding it

    /** The app in front changed (service) or was polled. */
    synchronized void onForeground(String pkg) {
        act(machine.onForeground(pkg, SystemClock.uptimeMillis()));
        schedule();
    }

    /** The chord or the quick switch: toggle the profile of this app. */
    synchronized String toggle(String pkg) {
        GameProfiles.Profile p = all.get(pkg);
        if (p == null) return "NO PROFILE FOR THIS APP";
        if (p.method != GameProfiles.M_DISPLAY_DECK) return "THIS PROFILE USES " + GameProfiles.methodName(p.method) + ": SEE ITS COMMANDS";
        if (!canApply()) return "NEEDS THE GRANT COMMAND";
        int a = machine.toggle(pkg, SystemClock.uptimeMillis());
        String r = act(a);
        schedule();
        return r == null ? (machine.state() == ProfileMachine.IDLE ? "PROFILE OFF" : "PROFILE ON") : r;
    }

    private String act(int a) {
        switch (a) {
            case ProfileMachine.APPLY: {
                GameProfiles.Profile p = machine.active();
                if (p == null) return null;
                String err = applyDisplay(p);
                if (err != null) {
                    if (err.startsWith("THIS TV BOX HIDES")) prefs.edit().putBoolean(PREF_BLOCKED, true).apply();   // 2.17: remember it
                    machine.forceRestore();
                    toast("PROFILE NOT APPLIED: " + err);
                    return err;
                }
                if (machine.state() == ProfileMachine.CONFIRMING && ui() != null) ui().showKeep(p);
                return null;
            }
            case ProfileMachine.RESTORE:
            case ProfileMachine.REVERT: {
                String err = restoreDisplay();
                hideKeep();
                if (a == ProfileMachine.REVERT) toast("DISPLAY PUT BACK");
                return err;
            }
            default:
                return null;
        }
    }

    private String applyDisplay(GameProfiles.Profile p) {
        DisplayState orig = DisplayState.parse(prefs.getString(PREF_ORIG, ""));
        int[] panel = DisplayControl.panelSize(ctx), cur = DisplayControl.currentSize(ctx);
        int dpi = DisplayControl.currentDpi(ctx);
        if (!orig.valid) {
            // nothing saved: only trust the current values when no size override is in place
            if (panel == null || cur == null || panel[0] != cur[0] || panel[1] != cur[1]) return "DISPLAY ALREADY CHANGED, RESTORE FIRST";
            orig = DisplayState.of(panel[0], panel[1], dpi);
            if (!orig.valid) return "COULD NOT READ THE DISPLAY";
            prefs.edit().putString(PREF_ORIG, orig.save()).commit();
        }
        int[] sz = DisplayRules.scaledSize(orig.w, orig.h, p.scalePct);
        int d = DisplayRules.scaledDensity(orig.dpi, p.scalePct);
        if (sz == null || d < 0 || !DisplayRules.validSize(sz[0], sz[1], orig.w, orig.h) || !DisplayRules.validDensity(d)) return "VALUES NOT ALLOWED";
        // remember that a change is in place before making it, so a crash still gets restored at start
        prefs.edit().putString(PREF_ACTIVE, p.pkg).commit();
        String err = DisplayControl.apply(ctx, sz[0], sz[1], d);
        if (err != null) prefs.edit().remove(PREF_ACTIVE).commit();
        return err;
    }

    /** Puts the display back to the panel's own values. @return null on success, otherwise a short reason. */
    synchronized String restoreDisplay() {
        String err = DisplayControl.restore(ctx);
        if (err == null) prefs.edit().remove(PREF_ACTIVE).remove(PREF_ORIG).commit();
        return err;
    }

    /** RESTORE DISPLAY, deck_cmd restore_display and the keep prompt's revert. Text for the label. */
    synchronized String restoreNow() {
        machine.forceRestore();
        String err = restoreDisplay();
        hideKeep();
        h.removeCallbacks(tick);
        ticking = false;
        if (err == null) return "DISPLAY RESTORED";
        return err.startsWith("NEEDS") ? "RUN: " + DisplayState.DOC_RESTORE : "NOT RESTORED: " + err;
    }

    /** Deck or the service started: if a change was left in place, undo it. */
    synchronized void startupRestore() {
        String active = prefs.getString(PREF_ACTIVE, "");
        if (active != null && !active.isEmpty() && machine.state() == ProfileMachine.IDLE) {
            String err = restoreDisplay();
            if (err == null) toast("DISPLAY PUT BACK");
        }
    }

    // ---------------------------------------------------------------- keep prompt

    synchronized void keep() {
        machine.confirm();
        saveConfirmed();
        hideKeep();
        Runnable r = afterKeep;
        afterKeep = null;
        if (r != null) h.postDelayed(r, 600);
    }

    synchronized void revert() {
        afterKeep = null;
        act(machine.cancel());
    }

    boolean keepShowing() { return machine.state() == ProfileMachine.CONFIRMING; }

    /**
     * LAUNCH WITH PROFILE: apply first, wait, then run launch. A new value asks KEEP or REVERT in front of Deck
     * before the app starts, which is the safest place to ask.
     * @return false when the profile does not apply here (the caller launches at once)
     */
    synchronized boolean launchWith(String pkg, Runnable launch) {
        GameProfiles.Profile p = all.get(pkg);
        if (p == null || !p.launchWith || p.method != GameProfiles.M_DISPLAY_DECK || !canApply()) return false;
        int a = machine.onForeground(pkg, SystemClock.uptimeMillis());
        if (a != ProfileMachine.APPLY) return false;
        String r = act(a);
        schedule();
        if (r != null) return false;
        if (machine.state() == ProfileMachine.CONFIRMING) afterKeep = launch;
        else h.postDelayed(launch, 1500);
        return true;
    }

    // ---------------------------------------------------------------- clock

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            synchronized (ProfileRunner.this) {
                if (machine.state() == ProfileMachine.IDLE) { ticking = false; return; }
                long now = SystemClock.uptimeMillis();
                if (!serviceFeeds && machine.state() != ProfileMachine.CONFIRMING) {
                    String fg = foregroundByUsage(ctx);
                    if (fg != null) act(machine.onForeground(fg, now));
                }
                act(machine.tick(now));
                if (ui() != null && machine.state() == ProfileMachine.CONFIRMING) ui().keepTick(machine.secondsLeft(now));
                if (machine.state() == ProfileMachine.IDLE) { ticking = false; return; }
                h.postDelayed(this, machine.state() == ProfileMachine.CONFIRMING ? 500 : POLL_MS);
            }
        }
    };

    private void schedule() {
        if (ticking || machine.state() == ProfileMachine.IDLE) return;
        ticking = true;
        h.postDelayed(tick, 500);
    }

    /** The last app that moved to the foreground in the past minute, or null. */
    static String foregroundByUsage(Context c) {
        try {
            if (!SysUsage.granted(c)) return null;
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (um == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 60000, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev != null && ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last = e.getPackageName();
            }
            return last;
        } catch (Throwable t) {
            return null;
        }
    }

    private void hideKeep() {
        if (deckUi != null) deckUi.hideKeep();
        if (svcUi != null) svcUi.hideKeep();
    }

    private void toast(String s) {
        Ui u = ui();
        if (u != null) u.toast(s);
    }
}
