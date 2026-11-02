package com.nokoding.deck;

/**
 * 2.8 game profiles: builds the shell command lines. Every argument is checked and a bad one gives
 * null, so a command line never carries anything but a validated package name and numbers. Pure Java.
 *
 * See DisplayRules for what the commands do and which of them Deck can run itself. These lines are
 * for `sh -c`, `adb shell`, or showing to the user; nothing here runs anything.
 */
final class ProfileCmd {
    private ProfileCmd() {}

    /** wm size 1440x810. Only the absolute limits are checked here (MIN_W x MIN_H, at most 8192); DisplayRules.scaledSize checks the aspect. */
    static String wmSize(int w, int h) {
        if (w < DisplayRules.MIN_W || h < DisplayRules.MIN_H || w > DisplayRules.MAX_DIM || h > DisplayRules.MAX_DIM) return null;
        return "wm size " + w + "x" + h;
    }

    /** wm density 240, DENSITY_MIN..DENSITY_MAX only. */
    static String wmDensity(int dpi) {
        if (!DisplayRules.validDensity(dpi)) return null;
        return "wm density " + dpi;
    }

    /** cmd game set --mode 4 --downscale 0.67 PKG, for 85, 75, 67 and 50 only. 100 (no change) gives null. */
    static String gameModeSet(String pkg, int pct) {
        if (!validPkg(pkg)) return null;
        String f;
        switch (pct) {
            case 85: f = "0.85"; break;
            case 75: f = "0.75"; break;
            case 67: f = "0.67"; break;
            case 50: f = "0.5"; break;
            default: return null;
        }
        return "cmd game set --mode 4 --downscale " + f + " " + pkg;
    }

    static String gameModeReset(String pkg) {
        if (!validPkg(pkg)) return null;
        return "cmd game reset " + pkg;
    }

    static String wmSizeReset() { return "wm size reset"; }

    static String wmDensityReset() { return "wm density reset"; }

    /**
     * The lines that apply a profile. GAME MODE: one line. The display methods (DECK DISPLAY, PHONE
     * HELPER, COPY COMMANDS): the size line and the density line, from the scaled values of the
     * original display. Null when anything is invalid, an unknown method, or the original is not
     * valid for a display method.
     */
    static String[] on(int method, String pkg, int pct, DisplayState orig) {
        if (!validPkg(pkg)) return null;
        switch (method) {
            case GameProfiles.M_GAME_MODE: {
                String c = gameModeSet(pkg, pct);
                return c == null ? null : new String[]{c};
            }
            case GameProfiles.M_DISPLAY_DECK:
            case GameProfiles.M_DISPLAY_HELPER:
            case GameProfiles.M_COPY: {
                if (orig == null || !orig.valid) return null;
                int[] s = DisplayRules.scaledSize(orig.w, orig.h, pct);
                int d = DisplayRules.scaledDensity(orig.dpi, pct);
                if (s == null || d < 0) return null;
                String a = wmSize(s[0], s[1]), b = wmDensity(d);
                if (a == null || b == null) return null;
                return new String[]{a, b};
            }
            default:
                return null;
        }
    }

    /**
     * The lines that take a profile back. GAME MODE: the reset for that package (null for a bad
     * package). The display methods: the two wm reset lines, which need no package and no saved
     * values, so they can never be refused. Null only for an unknown method.
     */
    static String[] off(int method, String pkg) {
        switch (method) {
            case GameProfiles.M_GAME_MODE: {
                String c = gameModeReset(pkg);
                return c == null ? null : new String[]{c};
            }
            case GameProfiles.M_DISPLAY_DECK:
            case GameProfiles.M_DISPLAY_HELPER:
            case GameProfiles.M_COPY:
                return new String[]{wmSizeReset(), wmDensityReset()};
            default:
                return null;
        }
    }

    /** "adb shell " plus the line. Null stays null. */
    static String adb(String shellLine) { return shellLine == null ? null : "adb shell " + shellLine; }

    /**
     * Package name check: ASCII letters, digits, dots, underscores; 1..255 long; at least one dot;
     * no leading or trailing dot; no empty segment; every segment starts with a letter.
     * (Package private so GameProfiles can use the same rule.)
     */
    static boolean validPkg(String p) {
        if (p == null || p.isEmpty() || p.length() > 255) return false;
        boolean dot = false, segStart = true;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (c == '.') {
                if (segStart) return false;
                dot = true; segStart = true;
            } else if (segStart) {
                if (!letter) return false;
                segStart = false;
            } else if (!(letter || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return dot && !segStart;
    }
}
