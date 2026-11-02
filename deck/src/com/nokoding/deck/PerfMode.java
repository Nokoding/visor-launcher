package com.nokoding.deck;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 2.12 performance mode: when a game starts, close what runs in the background so the game gets the RAM and the
 * CPU. Pure Java: the decisions (when it runs, what it closes, what EXIT GAME closes) live here and are tested, the
 * Android calls are in PerfRunner.
 *
 * What it can really do: ActivityManager.killBackgroundProcesses ends the cached and background processes of other
 * apps. It cannot end the app on screen and it cannot touch the system. Android may start some of them again by
 * itself, so the gain varies. Deck, the home app, the game itself and the protect list are never closed.
 */
final class PerfMode {
    private PerfMode() {}

    static final int OFF = 0, GAMES = 1, CHOSEN = 2;
    static final String[] NAMES = {"OFF", "GAMES", "CHOSEN APPS"};
    /** The same app starting again within this time does not close everything a second time. */
    static final long COOLDOWN_MS = 30000;
    static final int MAX_TARGETS = 60;

    static int clampMode(int m) { return m >= OFF && m <= CHOSEN ? m : OFF; }

    /**
     * Should performance mode run for this app that just came to the front or was started?
     * @param isGame   Android marks the app as a game
     * @param chosen   the app is on the CHOSEN APPS list
     * @param lastPkg  the app it last ran for (or null), lastAt when
     */
    static boolean shouldRun(int mode, boolean isGame, boolean chosen, String pkg, String self, long now, String lastPkg, long lastAt) {
        if (!KillCmd.validPackage(pkg) || pkg.equals(self)) return false;
        mode = clampMode(mode);
        if (mode == OFF) return false;
        if (mode == GAMES && !isGame) return false;
        if (mode == CHOSEN && !chosen) return false;
        if (pkg.equals(lastPkg) && now >= lastAt && now - lastAt < COOLDOWN_MS) return false;
        return true;
    }

    /**
     * The packages to close: the candidates without the game, Deck, anything protected, invalid names and duplicates.
     * Order is kept, and the list is cut to MAX_TARGETS.
     */
    static List<String> targets(List<String> candidates, String game, String self, Set<String> protect) {
        ArrayList<String> out = new ArrayList<>();
        if (candidates == null) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String p : candidates) {
            if (p == null || !KillCmd.validPackage(p) || p.equals(game) || p.equals(self)) continue;
            if (protect != null && protect.contains(p)) continue;
            if (seen.add(p) && out.size() < MAX_TARGETS) out.add(p);
        }
        return out;
    }

    /**
     * Which app EXIT GAME closes: the app in front when it is not Deck, otherwise the game performance mode
     * remembers. Null when there is none.
     */
    static String exitTarget(String foreground, String session, String self) {
        if (foreground != null && !foreground.isEmpty() && !foreground.equals(self)) return KillCmd.validPackage(foreground) ? foreground : null;
        if (session != null && KillCmd.validPackage(session) && !session.equals(self)) return session;
        return null;
    }

    static String summary(int closed) {
        if (closed <= 0) return "NOTHING TO CLOSE";
        return "CLOSED " + closed + (closed == 1 ? " APP" : " APPS");
    }

    /** 2.16: what the free RAM did, in plain capitals. Honest when it did not change or could not be read. */
    static String memoryLine(long beforeMb, long afterMb) {
        if (beforeMb < 0 || afterMb < 0) return "FREE RAM: CANNOT BE READ ON THIS BOX";
        long d = afterMb - beforeMb;
        if (d >= 20) return "FREE RAM " + beforeMb + " -> " + afterMb + " MB  (+" + d + " MB)";
        if (d <= -20) return "FREE RAM " + beforeMb + " -> " + afterMb + " MB  (ANDROID STARTED APPS AGAIN)";
        return "FREE RAM " + beforeMb + " -> " + afterMb + " MB  (NO REAL CHANGE: NOTHING BIG WAS RUNNING)";
    }
}
