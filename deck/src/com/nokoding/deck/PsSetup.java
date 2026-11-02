package com.nokoding.deck;

import java.util.ArrayList;
import java.util.List;

/**
 * 2.4: turning the PS overlay service on and off over adb, and the Setup status line.
 * Pure Java, mirrored by deck/tools/enable-ps-overlay.sh.
 *
 * enabled_accessibility_services is one colon separated list for the whole TV. A plain
 * "settings put" with only Deck's name would switch off every other service the user runs,
 * so the commands here read the current value, add or remove only Deck's entry and write the
 * rest back unchanged.
 */
final class PsSetup {
    private PsSetup() {}

    static final String PKG = "com.nokoding.deck";
    static final String SERVICE = PKG + "/" + PKG + ".PsService";
    /** Android also accepts the short form; treat it as Deck's entry too. */
    static final String SHORT = PKG + "/.PsService";
    static final String SETTING = "enabled_accessibility_services";

    static final int NOT_ENABLED = 0, OFF = 1, ON = 2;
    static final String[] STATUS = {"NOT ENABLED", "OFF", "ON"};

    static boolean isDeck(String entry) { return SERVICE.equals(entry) || SHORT.equals(entry); }

    /** The list, without blanks, without the literal "null" adb prints for an unset value, deduped. */
    static List<String> parse(String value) {
        ArrayList<String> out = new ArrayList<>();
        if (value == null) return out;
        String v = value.trim();
        if (v.isEmpty() || v.equals("null")) return out;
        for (String part : v.split(":")) {
            String p = part.trim();
            if (p.isEmpty() || p.equals("null") || out.contains(p)) continue;
            out.add(p);
        }
        return out;
    }

    static boolean listed(String value) {
        for (String s : parse(value)) if (isDeck(s)) return true;
        return false;
    }

    /** Current value plus Deck's entry (once, at the end). Other services keep their order. */
    static String withDeck(String value) {
        ArrayList<String> out = new ArrayList<>();
        boolean have = false;
        for (String s : parse(value)) {
            if (isDeck(s)) { if (have) continue; have = true; out.add(SERVICE); continue; }
            out.add(s);
        }
        if (!have) out.add(SERVICE);
        return String.join(":", out);
    }

    /** Current value without Deck's entry. Empty when Deck was the only one. */
    static String withoutDeck(String value) {
        ArrayList<String> out = new ArrayList<>();
        for (String s : parse(value)) if (!isDeck(s)) out.add(s);
        return String.join(":", out);
    }

    /** Characters a component list can hold. Anything else is refused, not quoted around. */
    static boolean safeValue(String v) {
        if (v == null) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '/' || c == ':' || c == '$' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    /** Single quoted for the TV's shell, so a $ in an inner class name stays literal. */
    static String quote(String v) { return "'" + v + "'"; }

    /**
     * adb commands that switch the service on and keep every other service.
     * @param current what the TV holds now, or null if unknown (then the plain version)
     */
    static String[] enableCommands(String current) {
        String v = withDeck(current);
        if (!safeValue(v)) v = SERVICE;
        return new String[]{
                "adb shell settings put secure " + SETTING + " " + quote(v),
                "adb shell settings put secure accessibility_enabled 1"};
    }

    /** adb commands that switch only Deck's service off. */
    static String[] disableCommands(String current) {
        String v = withoutDeck(current);
        if (v.isEmpty() || !safeValue(v)) {
            if (!v.isEmpty()) return new String[]{"adb shell settings get secure " + SETTING,
                    "(remove " + SERVICE + " from that list by hand and put the rest back)"};
            return new String[]{
                    "adb shell settings delete secure " + SETTING,
                    "adb shell settings put secure accessibility_enabled 0"};
        }
        return new String[]{"adb shell settings put secure " + SETTING + " " + quote(v)};
    }

    /**
     * @param listed  Deck's entry is in enabled_accessibility_services
     * @param a11yOn  accessibility_enabled is 1
     * @param running PsService is connected right now
     */
    static int status(boolean listed, boolean a11yOn, boolean running) {
        if (running) return ON;
        return listed ? OFF : NOT_ENABLED;
    }

    static String statusLine(int st, boolean listed, boolean a11yOn) {
        switch (st) {
            case ON: return "ON. THE SERVICE IS RUNNING";
            case OFF:
                if (!a11yOn) return "OFF. LISTED, BUT ACCESSIBILITY IS OFF. RUN THE ENABLE COMMANDS AGAIN";
                return "OFF. LISTED BUT NOT RUNNING. ANDROID STOPS A SERVICE THAT CRASHED. RUN DISABLE, THEN ENABLE";
            default: return "NOT ENABLED. RUN THE ENABLE COMMANDS FROM A PHONE (SHOW ENABLE COMMANDS)";
        }
    }

    /**
     * The plain answer to "does the PS overlay work on this box", from what was seen.
     * @param seenDeck  the PS button reached Deck's own screen at least once
     * @param seenSvc   the PS button reached the overlay service's key filter at least once
     */
    static String verdict(int st, boolean seenDeck, boolean seenSvc) {
        if (st != ON) return "TURN THE SERVICE ON FIRST, THEN PRESS PS ONCE ON THE HOME SCREEN";
        if (seenSvc) return "WORKS: THE BOX PASSES THE PS BUTTON TO THE OVERLAY SERVICE";
        if (seenDeck) return "NOT YET SEEN BY THE SERVICE. PRESS PS ON THE HOME SCREEN NOW. IF VISOR'S OWN MENU OPENS AND THIS STAYS NO, THE BOX ROUTES IT AROUND THE KEY FILTER";
        return "NOT SEEN YET. PRESS PS ONCE ON THE HOME SCREEN. IF NOTHING EVER ARRIVES, THE BOX EATS THE PS BUTTON AND THE OVERLAY CANNOT WORK HERE";
    }
}
