package com.nokoding.deck;

/**
 * Package name checks and command strings for closing an app. Pure Java.
 *
 * There are three honest routes, and none of them is a full "kill everything" button:
 * 1. KILL: ActivityManager.killBackgroundProcesses with the normal permission
 *    KILL_BACKGROUND_PROCESSES. It only ends background and cached processes. An app that
 *    is in the foreground, or that runs a foreground service, is not touched.
 * 2. FORCE STOP: the accessibility service opens App Info for the package and clicks the
 *    Force stop button and then the confirm button (see ForceStopMatcher). This is
 *    experimental: it depends on the Settings app layout of the TV and it has not been run on it.
 * 3. ADB: the user runs "adb shell am force-stop PKG" from a computer or from a paired
 *    adb session. This always works but needs adb.
 *
 * Every package name that ends up in a command is checked by validPackage first, so a hostile
 * name can never add shell words to a command line.
 */
final class KillCmd {
    private KillCmd() {}

    static final int MAX_LEN = 255;

    /**
     * Letters, digits, dots and underscores only, ASCII only, 1..255 chars, at least one dot,
     * no leading, trailing or doubled dot, and every dot separated segment starts with a letter.
     */
    static boolean validPackage(String s) {
        if (s == null) return false;
        int n = s.length();
        if (n < 1 || n > MAX_LEN) return false;
        boolean dot = false;
        boolean segStart = true;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (c == '.') {
                if (segStart) return false; // leading dot or empty segment
                dot = true;
                segStart = true;
                continue;
            }
            boolean ok = letter || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
            if (segStart && !letter) return false;
            segStart = false;
        }
        return dot && !segStart; // needs a dot, and must not end with one
    }

    /** "am force-stop PKG" (as run by a shell on the device) or null for an invalid name. */
    static String forceStopShell(String pkg) {
        return validPackage(pkg) ? "am force-stop " + pkg : null;
    }

    /** "adb shell am force-stop PKG" (as typed on a computer) or null for an invalid name. */
    static String forceStopAdb(String pkg) {
        return validPackage(pkg) ? "adb shell am force-stop " + pkg : null;
    }

    /** "am kill PKG": the shell cousin of killBackgroundProcesses, or null for an invalid name. */
    static String killBackgroundShell(String pkg) {
        return validPackage(pkg) ? "am kill " + pkg : null;
    }
}
