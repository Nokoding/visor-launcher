package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 2.7: the adb commands that make Deck's screensaver (DeckDream) the system one, and how to read the
 * settings back. Built here so the Setup row, docs/screensaver.md and tools/enable-screensaver.sh all
 * say the same thing (a JVM test compares the script with this class). Pure Java.
 *
 * What the Android 14 sources say (docs/screensaver.md has the long version):
 * - Settings.Secure screensaver_components names the dream, as "package/class" (a comma list, the first
 *   valid entry wins; an invalid one falls back to screensaver_default_component).
 * - Settings.Secure screensaver_enabled must be 1 for dreams to run at all.
 * - PowerManagerService starts a dream when the idle timeout ends only if screensaver_activate_on_sleep
 *   is 1 (or it is docked). Android TV's own screensaver page sets only the first two plus the timeout,
 *   so the TV builds must default this to 1. Setting it is harmless.
 * - The idle timeout is Settings.System screen_off_timeout (the TV page offers 5, 15, 30, 60, 120 minutes).
 */
final class ScreensaverCmd {
    private ScreensaverCmd() {}

    static final String PKG = "com.nokoding.deck", CLS = "com.nokoding.deck.DeckDream";
    static final String COMPONENTS = "screensaver_components", ENABLED = "screensaver_enabled", ON_SLEEP = "screensaver_activate_on_sleep";
    static final String TIMEOUT = "screen_off_timeout";
    static final int MIN_MINUTES = 1, MAX_MINUTES = 120;

    /** The component as the system writes it: package/class. */
    static String component() { return PKG + "/" + CLS; }

    /** package/class with letters, digits, dots, underscores and a dollar only. Refuses anything a shell could act on. */
    static boolean validComponent(String s) {
        if (s == null || s.length() < 3 || s.length() > 200) return false;
        int slash = s.indexOf('/');
        if (slash <= 0 || slash != s.lastIndexOf('/') || slash == s.length() - 1) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '$' || c == '/';
            if (!ok) return false;
        }
        return true;
    }

    static boolean validMinutes(long m) { return m >= MIN_MINUTES && m <= MAX_MINUTES; }

    /**
     * The commands for "on", each as the text after "adb shell ". minutes = 0 leaves the idle timeout alone.
     * Returns null for a bad minutes value.
     */
    static String[] on(long minutes) {
        if (minutes != 0 && !validMinutes(minutes)) return null;
        ArrayList<String> c = new ArrayList<>();
        c.add("settings put secure " + COMPONENTS + " " + component());
        c.add("settings put secure " + ENABLED + " 1");
        c.add("settings put secure " + ON_SLEEP + " 1");
        if (minutes != 0) c.add("settings put system " + TIMEOUT + " " + (minutes * 60000L));
        return c.toArray(new String[0]);
    }

    /** "off": only if Deck's dream is the current one, drop the setting so Android uses its own default. */
    static String off() { return "settings delete secure " + COMPONENTS; }

    /** The reads for "status". */
    static String[] reads() {
        return new String[]{"settings get secure " + COMPONENTS, "settings get secure " + ENABLED,
                "settings get secure " + ON_SLEEP, "settings get system " + TIMEOUT};
    }

    static boolean unset(String v) { return v == null || v.trim().isEmpty() || v.trim().equals("null"); }

    /** The first entry of a components list, or "". */
    static String first(String comps) {
        if (unset(comps)) return "";
        int c = comps.indexOf(',');
        return (c < 0 ? comps : comps.substring(0, c)).trim();
    }

    /** True if an entry is Deck's dream, in the full form or the short ".DeckDream" form. */
    static boolean isDeck(String entry) {
        if (entry == null) return false;
        String e = entry.trim();
        return e.equals(component()) || e.equals(PKG + "/.DeckDream");
    }

    /** Whether Deck's dream is the one Android will use (it is the first entry). */
    static boolean deckIsCurrent(String comps) { return isDeck(first(comps)); }

    /**
     * A short readable verdict for the Setup row. Arguments are the raw setting values (null or "null" for
     * not set). timeoutMs is -1 when unknown.
     */
    static String describe(String comps, String enabled, String onSleep, long timeoutMs) {
        StringBuilder b = new StringBuilder();
        String f = first(comps);
        if (f.isEmpty()) b.append("NONE SET, ANDROID USES ITS OWN DEFAULT");
        else if (isDeck(f)) b.append("VISOR IS THE SCREENSAVER");
        else b.append("NOT VISOR: ").append(f.toUpperCase(Locale.ROOT));
        b.append("  //  ").append(flag("ENABLED", enabled));
        b.append("  //  ").append(flag("STARTS WHEN IDLE", onSleep));
        if (timeoutMs > 0) b.append("  //  AFTER ").append(minutesText(timeoutMs));
        return b.toString();
    }

    private static String flag(String name, String v) {
        if (unset(v)) return name + " NOT SET";
        return name + (v.trim().equals("0") ? " NO" : " YES");
    }

    static String minutesText(long ms) {
        long m = ms / 60000L;
        if (m < 1) return (ms / 1000L) + " S";
        if (m >= 60 && m % 60 == 0) return (m / 60) + " H";
        return m + " MIN";
    }
}
