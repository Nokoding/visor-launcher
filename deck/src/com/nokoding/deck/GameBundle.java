package com.nokoding.deck;

/**
 * 2.17 game bundle: the extra settings of a game profile, apart from the display scale. Pure, tested.
 * A profile can say whether performance mode runs for this game (FOLLOW the global mode, always ON, always OFF)
 * and a volume (a percent, or none). The volume is set when the game comes to the front and put back when
 * another app takes over.
 */
final class GameBundle {
    private GameBundle() {}

    static final int PERF_FOLLOW = 0, PERF_ON = 1, PERF_OFF = 2;
    static final int VOL_NONE = -1;
    /** The volume choices in the Setup row, in percent. */
    static final int[] VOLUMES = {VOL_NONE, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    static boolean validPerf(int p) { return p >= PERF_FOLLOW && p <= PERF_OFF; }

    static boolean validVolume(int v) { return v == VOL_NONE || (v >= 0 && v <= 100); }

    static String perfName(int p) {
        switch (p) {
            case PERF_ON: return "ALWAYS ON";
            case PERF_OFF: return "ALWAYS OFF";
            default: return "FOLLOW GLOBAL";
        }
    }

    static String volumeName(int v) { return v == VOL_NONE ? "NO CHANGE" : v + " PERCENT"; }

    /** Index in VOLUMES of the nearest choice (for the row). */
    static int volumeIndex(int v) {
        if (v == VOL_NONE || !validVolume(v)) return 0;
        int bi = 1;
        for (int i = 1; i < VOLUMES.length; i++) if (Math.abs(VOLUMES[i] - v) < Math.abs(VOLUMES[bi] - v)) bi = i;
        return bi;
    }

    /**
     * Should performance mode run for this game?
     * @param perf        the profile's choice
     * @param globalRuns  what the global mode would do for this app (PerfMode.shouldRun result)
     */
    static boolean perfRuns(int perf, boolean globalRuns) {
        if (perf == PERF_OFF) return false;
        if (perf == PERF_ON) return true;
        return globalRuns;
    }

    /** The stream index for a percent of the maximum (0 to max). Never louder than max. */
    static int volumeIndexFor(int pct, int max) {
        if (max <= 0 || !validVolume(pct) || pct == VOL_NONE) return -1;
        int i = Math.round(pct * max / 100f);
        return Math.max(0, Math.min(max, i));
    }

    /**
     * Decides what to do with the volume when the app in front changes.
     * @return 1 set the game's volume (and remember the old one), -1 put the old one back, 0 nothing
     * @param savedFor  the game whose volume was set earlier, or null/empty when none is
     * @param front     the app that is now in front
     * @param frontIsTransient the front window is Deck's own overlay or the system UI, which never counts as leaving
     */
    static int volumeAction(String savedFor, String front, boolean profileHasVolume, boolean frontIsTransient) {
        if (front == null || front.isEmpty() || frontIsTransient) return 0;
        boolean saved = savedFor != null && !savedFor.isEmpty();
        if (saved && !savedFor.equals(front)) return -1;
        if (!saved && profileHasVolume) return 1;
        return 0;
    }
}
