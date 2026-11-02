package com.nokoding.deck;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 2.12 performance mode, the Android side (the decisions are PerfMode, pure and tested). When a game starts, from
 * Deck or seen by the overlay service, it closes the background and cached processes of the other recent apps with
 * ActivityManager.killBackgroundProcesses, so the game has more RAM and CPU. EXIT GAME sends the game to the
 * background (Deck's home) and then closes it the same way, which is the only close a normal app can do.
 *
 * Honest limits: it cannot close the app on screen or the system, Android may start some apps again by itself, and
 * whatever was closed starts again when you open it, which takes a moment. The list of apps comes from Android's
 * usage history (the usage access grant); without it, Deck's own recent launches are used.
 */
final class PerfRunner {
    private PerfRunner() {}

    static final String PREF_MODE = "perfMode", PREF_CHOSEN = "perfChosen", PREF_GAME = "perfGame", PREF_LAST = "perfLast";
    private static String lastPkg;
    private static long lastAt;
    private static final Handler H = new Handler(Looper.getMainLooper());

    static int mode(SharedPreferences p) {
        try { return PerfMode.clampMode(p.getInt(PREF_MODE, PerfMode.OFF)); } catch (Throwable t) { return PerfMode.OFF; }
    }

    static Set<String> chosen(SharedPreferences p) { return TaskList.parseProtect(p.getString(PREF_CHOSEN, "")); }

    static void saveChosen(SharedPreferences p, Set<String> s) { p.edit().putString(PREF_CHOSEN, TaskList.saveProtect(s)).apply(); }

    @SuppressWarnings("deprecation")
    static boolean isGame(Context c, String pkg) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            return PsRules.isGame(ai.category, ai.flags);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String homePkg(Context c) {
        try {
            ResolveInfo ri = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            return ri == null || ri.activityInfo == null ? null : ri.activityInfo.packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The apps to close: usage history when granted, otherwise Deck's own recent launches. */
    private static List<String> candidates(Context c, SharedPreferences p) {
        ArrayList<String> out = new ArrayList<>();
        List<String> l = TaskSource.recentPackages(c);
        if (!l.isEmpty()) out.addAll(l);
        // 2.16: every launchable app too. Closing an app that is not running does nothing, and apps the usage history
        // missed (a streaming app left in the background for days) now get closed as well.
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            for (String cat : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
                for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(main.addCategory(cat), 0)) {
                    String pk = ri.activityInfo == null ? null : ri.activityInfo.packageName;
                    if (pk != null && !out.contains(pk)) out.add(pk);
                }
                main = new Intent(Intent.ACTION_MAIN);
            }
        } catch (Throwable ignored) {}
        if (!out.isEmpty()) return out;
        try {
            for (String k : Recents.pick(Recents.load(p.getString("recents", "")).list, Recents.MAX_KEEP, null, null)) {
                ComponentName cn = ComponentName.unflattenFromString(k);
                if (cn != null) out.add(cn.getPackageName());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Called when an app starts or comes to the front. Closes the others if the mode says so.
     * @return a short result for a toast, or null when it did not run
     */
    static synchronized String consider(Context c, SharedPreferences p, String pkg) {
        try {
            if (pkg == null) return null;
            GameProfiles.Profile gp = profileOf(p, pkg);
            bundleVolume(c, p, pkg, gp);
            if (gp != null && gp.perf == GameBundle.PERF_OFF) return null;
            // 2.17 game bundle: a profile can force performance mode on for its game, whatever the global mode is
            boolean forced = gp != null && gp.perf == GameBundle.PERF_ON;
            int m = forced ? PerfMode.GAMES : mode(p);
            if (m == PerfMode.OFF) return null;
            boolean game = forced || isGame(c, pkg), pick = chosen(p).contains(pkg);
            long now = SystemClock.uptimeMillis();
            if (!PerfMode.shouldRun(m, game, pick, pkg, c.getPackageName(), now, lastPkg, lastAt)) return null;
            lastPkg = pkg;
            lastAt = now;
            return closeOthers(c, p, pkg);
        } catch (Throwable t) {
            return null;
        }
    }

    static final String PREF_BVOL = "bundleVol";

    private static GameProfiles.Profile profileOf(SharedPreferences p, String pkg) {
        try { return GameProfiles.parse(p.getString(ProfileRunner.PREF_PROFILES, "")).get(pkg); } catch (Throwable t) { return null; }
    }

    private static boolean transientFront(Context c, String pkg) {
        return pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui");
    }

    /**
     * 2.17 game bundle volume. When a game with a volume comes to the front, remember the music volume and set the
     * game's. When another app takes over (not Deck's own menu, not the system bar), put the old volume back.
     * Saved as "package|index".
     */
    static synchronized void bundleVolume(Context c, SharedPreferences p, String front, GameProfiles.Profile gp) {
        try {
            String saved = p.getString(PREF_BVOL, "");
            String savedPkg = saved.indexOf('|') > 0 ? saved.substring(0, saved.indexOf('|')) : "";
            int act = GameBundle.volumeAction(savedPkg, front, gp != null && gp.volume != GameBundle.VOL_NONE, transientFront(c, front));
            if (act == 0) return;
            android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (act > 0) {
                int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                int want = GameBundle.volumeIndexFor(gp.volume, max);
                if (want < 0) return;
                p.edit().putString(PREF_BVOL, front + "|" + am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)).apply();
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, want, 0);
            } else {
                int old = Integer.parseInt(saved.substring(saved.indexOf('|') + 1));
                p.edit().remove(PREF_BVOL).apply();
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, old, 0);
            }
        } catch (Throwable ignored) {
            try { p.edit().remove(PREF_BVOL).apply(); } catch (Throwable ignored2) {}
        }
    }

    /** Deck's home came to the front: the game is over, so put its volume back. */
    static void bundleHome(Context c, SharedPreferences p) {
        try {
            if (p.getString(PREF_BVOL, "").isEmpty()) return;
            bundleVolume(c, p, "deck.home.marker", null);   // any other app counts as leaving the game
        } catch (Throwable ignored) {}
    }

    /** Closes every candidate except the game, Deck, the home app, the protect list. Remembers the game for EXIT GAME. */
    static String closeOthers(Context c, SharedPreferences p, String game) {
        Set<String> protect = TaskList.withDefaults(TaskList.parseProtect(p.getString(CcHost.PREF_PROTECT, "")), c.getPackageName(), homePkg(c), game);
        List<String> t = PerfMode.targets(candidates(c, p), game, c.getPackageName(), protect);
        final long before = freeMb(c);
        int n = 0;
        for (String pkg : t) if (TaskSource.kill(c, pkg)) n++;
        p.edit().putString(PREF_GAME, game == null ? "" : game).apply();
        final String head = PerfMode.summary(n);
        // 2.16: measure the free RAM again a moment later and say what really changed
        final Context app = c.getApplicationContext();
        H.postDelayed(() -> {
            long after = freeMb(app);
            String line = PerfMode.memoryLine(before, after);
            p.edit().putString(PREF_LAST, head + "  //  " + line).apply();
            try { Toast.makeText(app, line, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
        }, 2000);
        return head;
    }

    /** Free RAM in MB as Android counts it (available, caches included), or -1. */
    static long freeMb(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.availMem / (1024 * 1024);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** The game EXIT GAME would close: the app in front (null for Deck) or the one performance mode remembers. */
    static String exitTarget(Context c, SharedPreferences p, String foreground) {
        return PerfMode.exitTarget(foreground, p.getString(PREF_GAME, ""), c.getPackageName());
    }

    /**
     * EXIT GAME: Deck's home comes to the front (the game goes to the background), then the game's processes are closed.
     * @return a short text, or null when there is no game to exit
     */
    static String exitGame(final Context c, final SharedPreferences p, String foreground) {
        final String g = exitTarget(c, p, foreground);
        if (g == null) return null;
        RemapRunner.deck(c, null);
        H.postDelayed(() -> {
            TaskSource.kill(c, g);
            p.edit().putString(PREF_GAME, "").apply();
        }, 1500);
        return "EXITING THE GAME";
    }

    /** Turns performance mode between OFF and GAMES (the control center's toggle). */
    static void toggle(SharedPreferences p) {
        p.edit().putInt(PREF_MODE, mode(p) == PerfMode.OFF ? PerfMode.GAMES : PerfMode.OFF).apply();
    }


}
