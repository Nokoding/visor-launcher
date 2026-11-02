package com.nokoding.deck;

/**
 * 2.6 button remap: who a button is, what it is called, and how risky it is to change. Pure Java,
 * no Android types, so the JVM tests cover all of it.
 *
 * Identity. A button is named by its key code: id "kc:166". A few vendor buttons arrive as key
 * code 0 (KEYCODE_UNKNOWN) with only a scan code; those are named by scan code plus the device's
 * vendor and product ids: id "sc:233:4012:9001". The id is what goes into the saved rules.
 * Inside the key filter the same identity is packed into one long (no allocation per key press).
 *
 * Classes. STANDARD buttons are the unused ones (channel, TV, input, account switcher, color
 * buttons, guide, info and so on, plus any button this table does not know). ADVANCED buttons
 * are the ordinary system buttons (Menu, the assistant, media and volume keys) and need the
 * advanced unlock. NAV buttons (D-pad, OK, Back) need the unlock too and, outside Deck, a second
 * switch. PROTECTED buttons can never be remapped (Home, Power, the PS button, game buttons,
 * typing keys).
 *
 * The code numbers are android.view.KeyEvent values. tools/check-keycodes.sh compares every line
 * of the table below with the constants in android.jar.
 */
final class RemapKey {
    private RemapKey() {}

    static final int STANDARD = 0, ADVANCED = 1, NAV = 2, PROTECTED = 3;
    static final long INVALID = -1L;
    static final int MAX_CODE = 0xFFFF;
    private static final long SCAN_TAG = 1L << 50;   // above the scan code (bits 32 to 47)

    // key codes used by logic elsewhere (android.view.KeyEvent)
    static final int KC_HOME = 3, KC_BACK = 4, KC_DPAD_UP = 19, KC_DPAD_DOWN = 20, KC_DPAD_LEFT = 21, KC_DPAD_RIGHT = 22,
            KC_DPAD_CENTER = 23, KC_POWER = 26, KC_MENU = 82, KC_BUTTON_MODE = 110;

    /** One known button. */
    static final class Def {
        final int code;
        final String konst, name, help;
        final int cls;
        Def(int code, String konst, String name, int cls, String help) {
            this.code = code; this.konst = konst; this.name = name; this.cls = cls; this.help = help;
        }
    }

    // ---------------------------------------------------------------- the table
    // k(code, KEYCODE_ constant without the prefix, shown name, class, one sentence for the Setup help line)
    // tools/check-keycodes.sh reads the k( lines below, keep one per line.
    private static final java.util.ArrayList<Def> DEFS = new java.util.ArrayList<>();

    private static void k(int code, String konst, String name, int cls, String help) {
        DEFS.add(new Def(code, konst, name, cls, help));
    }

    static {
        // the four buttons at the bottom of the onn remote, and the rest of the usual spare buttons
        k(166, "CHANNEL_UP", "CHANNEL UP", STANDARD, "Sets what the channel up button does, on a short press and on a long press.");
        k(167, "CHANNEL_DOWN", "CHANNEL DOWN", STANDARD, "Sets what the channel down button does, on a short press and on a long press.");
        k(170, "TV", "TV BUTTON", STANDARD, "Sets what the TV button does, on a short press and on a long press.");
        k(178, "TV_INPUT", "INPUT BUTTON", STANDARD, "Sets what the input button does, on a short press and on a long press.");
        k(288, "PROFILE_SWITCH", "ACCOUNT SWITCHER", STANDARD, "Sets what the account switcher button does, on a short press and on a long press.");
        k(172, "GUIDE", "GUIDE", STANDARD, "Sets what the guide button does, on a short press and on a long press.");
        k(165, "INFO", "INFO", STANDARD, "Sets what the info button does, on a short press and on a long press.");
        k(183, "PROG_RED", "RED BUTTON", STANDARD, "Sets what the red color button does, on a short press and on a long press.");
        k(184, "PROG_GREEN", "GREEN BUTTON", STANDARD, "Sets what the green color button does, on a short press and on a long press.");
        k(185, "PROG_YELLOW", "YELLOW BUTTON", STANDARD, "Sets what the yellow color button does, on a short press and on a long press.");
        k(186, "PROG_BLUE", "BLUE BUTTON", STANDARD, "Sets what the blue color button does, on a short press and on a long press.");
        k(229, "LAST_CHANNEL", "LAST CHANNEL", STANDARD, "Sets what the last channel button does, on a short press and on a long press.");
        k(173, "DVR", "DVR", STANDARD, "Sets what the DVR button does, on a short press and on a long press.");
        k(174, "BOOKMARK", "BOOKMARK", STANDARD, "Sets what the bookmark button does, on a short press and on a long press.");
        k(175, "CAPTIONS", "CAPTIONS", STANDARD, "Sets what the captions button does, on a short press and on a long press.");
        k(176, "SETTINGS", "SETTINGS BUTTON", STANDARD, "Sets what the settings button on the remote does, on a short press and on a long press.");
        k(230, "TV_DATA_SERVICE", "TV DATA SERVICE", STANDARD, "Sets what the TV data service button does, on a short press and on a long press.");
        k(234, "TV_NUMBER_ENTRY", "TV NUMBER ENTRY", STANDARD, "Sets what the number entry button does, on a short press and on a long press.");
        k(284, "ALL_APPS", "ALL APPS", STANDARD, "Sets what the all apps button does, on a short press and on a long press.");
        k(187, "APP_SWITCH", "APP SWITCH", STANDARD, "Sets what the app switch button does, on a short press and on a long press.");
        k(286, "THUMBS_UP", "THUMBS UP", STANDARD, "Sets what the thumbs up button does, on a short press and on a long press.");
        k(287, "THUMBS_DOWN", "THUMBS DOWN", STANDARD, "Sets what the thumbs down button does, on a short press and on a long press.");
        k(285, "REFRESH", "REFRESH", STANDARD, "Sets what the refresh button does, on a short press and on a long press.");
        k(259, "HELP", "HELP", STANDARD, "Sets what the help button does, on a short press and on a long press.");
        k(222, "MEDIA_AUDIO_TRACK", "AUDIO TRACK", STANDARD, "Sets what the audio track button does, on a short press and on a long press.");
        k(226, "MEDIA_TOP_MENU", "TOP MENU", STANDARD, "Sets what the top menu button does, on a short press and on a long press.");
        k(233, "TV_TELETEXT", "TELETEXT", STANDARD, "Sets what the teletext button does, on a short press and on a long press.");
        k(256, "TV_CONTENTS_MENU", "CONTENTS MENU", STANDARD, "Sets what the contents menu button does, on a short press and on a long press.");
        k(257, "TV_MEDIA_CONTEXT_MENU", "MEDIA CONTEXT MENU", STANDARD, "Sets what the media context menu button does, on a short press and on a long press.");
        k(171, "WINDOW", "WINDOW", STANDARD, "Sets what the window button does, on a short press and on a long press.");
        k(168, "ZOOM_IN", "ZOOM IN", STANDARD, "Sets what the zoom in button does, on a short press and on a long press.");
        k(169, "ZOOM_OUT", "ZOOM OUT", STANDARD, "Sets what the zoom out button does, on a short press and on a long press.");
        k(207, "CONTACTS", "CONTACTS", STANDARD, "Sets what the contacts button does, on a short press and on a long press.");
        k(208, "CALENDAR", "CALENDAR", STANDARD, "Sets what the calendar button does, on a short press and on a long press.");
        k(209, "MUSIC", "MUSIC", STANDARD, "Sets what the music button does, on a short press and on a long press.");
        k(210, "CALCULATOR", "CALCULATOR", STANDARD, "Sets what the calculator button does, on a short press and on a long press.");
        k(289, "VIDEO_APP_1", "VIDEO APP 1", STANDARD, "Sets what the first video app button does, on a short press and on a long press.");
        k(290, "VIDEO_APP_2", "VIDEO APP 2", STANDARD, "Sets what the second video app button does, on a short press and on a long press.");
        k(291, "VIDEO_APP_3", "VIDEO APP 3", STANDARD, "Sets what the third video app button does, on a short press and on a long press.");
        k(292, "VIDEO_APP_4", "VIDEO APP 4", STANDARD, "Sets what the fourth video app button does, on a short press and on a long press.");
        k(293, "VIDEO_APP_5", "VIDEO APP 5", STANDARD, "Sets what the fifth video app button does, on a short press and on a long press.");
        k(294, "VIDEO_APP_6", "VIDEO APP 6", STANDARD, "Sets what the sixth video app button does, on a short press and on a long press.");
        k(295, "VIDEO_APP_7", "VIDEO APP 7", STANDARD, "Sets what the seventh video app button does, on a short press and on a long press.");
        k(296, "VIDEO_APP_8", "VIDEO APP 8", STANDARD, "Sets what the eighth video app button does, on a short press and on a long press.");
        k(297, "FEATURED_APP_1", "FEATURED APP 1", STANDARD, "Sets what the first featured app button does, on a short press and on a long press.");
        k(298, "FEATURED_APP_2", "FEATURED APP 2", STANDARD, "Sets what the second featured app button does, on a short press and on a long press.");
        k(299, "FEATURED_APP_3", "FEATURED APP 3", STANDARD, "Sets what the third featured app button does, on a short press and on a long press.");
        k(300, "FEATURED_APP_4", "FEATURED APP 4", STANDARD, "Sets what the fourth featured app button does, on a short press and on a long press.");
        k(301, "DEMO_APP_1", "DEMO APP 1", STANDARD, "Sets what the first demo app button does, on a short press and on a long press.");
        k(302, "DEMO_APP_2", "DEMO APP 2", STANDARD, "Sets what the second demo app button does, on a short press and on a long press.");
        k(303, "DEMO_APP_3", "DEMO APP 3", STANDARD, "Sets what the third demo app button does, on a short press and on a long press.");
        k(304, "DEMO_APP_4", "DEMO APP 4", STANDARD, "Sets what the fourth demo app button does, on a short press and on a long press.");
        k(313, "MACRO_1", "MACRO 1", STANDARD, "Sets what the first macro button does, on a short press and on a long press.");
        k(314, "MACRO_2", "MACRO 2", STANDARD, "Sets what the second macro button does, on a short press and on a long press.");
        k(315, "MACRO_3", "MACRO 3", STANDARD, "Sets what the third macro button does, on a short press and on a long press.");
        k(316, "MACRO_4", "MACRO 4", STANDARD, "Sets what the fourth macro button does, on a short press and on a long press.");

        // the ordinary system buttons: advanced
        k(82, "MENU", "MENU", ADVANCED, "Sets what the menu button does, which is to open Setup inside Visor until you change it.");
        k(219, "ASSIST", "ASSISTANT", ADVANCED, "Sets what the assistant button does, on a short press and on a long press.");
        k(231, "VOICE_ASSIST", "VOICE ASSISTANT", ADVANCED, "Sets what the voice assistant button does, on a short press and on a long press.");
        k(84, "SEARCH", "SEARCH", ADVANCED, "Sets what the search button does, on a short press and on a long press.");
        k(85, "MEDIA_PLAY_PAUSE", "PLAY PAUSE", ADVANCED, "Sets what the play pause button does, on a short press and on a long press.");
        k(126, "MEDIA_PLAY", "PLAY", ADVANCED, "Sets what the play button does, on a short press and on a long press.");
        k(127, "MEDIA_PAUSE", "PAUSE", ADVANCED, "Sets what the pause button does, on a short press and on a long press.");
        k(86, "MEDIA_STOP", "STOP", ADVANCED, "Sets what the stop button does, on a short press and on a long press.");
        k(87, "MEDIA_NEXT", "NEXT TRACK", ADVANCED, "Sets what the next track button does, on a short press and on a long press.");
        k(88, "MEDIA_PREVIOUS", "PREVIOUS TRACK", ADVANCED, "Sets what the previous track button does, on a short press and on a long press.");
        k(89, "MEDIA_REWIND", "REWIND", ADVANCED, "Sets what the rewind button does, on a short press and on a long press.");
        k(90, "MEDIA_FAST_FORWARD", "FAST FORWARD", ADVANCED, "Sets what the fast forward button does, on a short press and on a long press.");
        k(130, "MEDIA_RECORD", "RECORD", ADVANCED, "Sets what the record button does, on a short press and on a long press.");
        k(272, "MEDIA_SKIP_FORWARD", "SKIP FORWARD", ADVANCED, "Sets what the skip forward button does, on a short press and on a long press.");
        k(273, "MEDIA_SKIP_BACKWARD", "SKIP BACKWARD", ADVANCED, "Sets what the skip backward button does, on a short press and on a long press.");
        k(24, "VOLUME_UP", "VOLUME UP", ADVANCED, "Sets what the volume up button does, on a short press and on a long press.");
        k(25, "VOLUME_DOWN", "VOLUME DOWN", ADVANCED, "Sets what the volume down button does, on a short press and on a long press.");
        k(164, "VOLUME_MUTE", "MUTE BUTTON", ADVANCED, "Sets what the mute button does, on a short press and on a long press.");
        k(177, "TV_POWER", "TV POWER", ADVANCED, "Sets what the TV power button does, on a short press and on a long press.");
        k(179, "STB_POWER", "BOX POWER", ADVANCED, "Sets what the set top box power button does, on a short press and on a long press.");
        k(181, "AVR_POWER", "RECEIVER POWER", ADVANCED, "Sets what the receiver power button does, on a short press and on a long press.");
        k(180, "STB_INPUT", "BOX INPUT", ADVANCED, "Sets what the set top box input button does, on a short press and on a long press.");
        k(182, "AVR_INPUT", "RECEIVER INPUT", ADVANCED, "Sets what the receiver input button does, on a short press and on a long press.");

        // navigation: advanced, and outside Deck a second switch
        k(19, "DPAD_UP", "UP", NAV, "Sets what the D-pad up key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(20, "DPAD_DOWN", "DOWN", NAV, "Sets what the D-pad down key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(21, "DPAD_LEFT", "LEFT", NAV, "Sets what the D-pad left key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(22, "DPAD_RIGHT", "RIGHT", NAV, "Sets what the D-pad right key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(23, "DPAD_CENTER", "OK", NAV, "Sets what the OK key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(4, "BACK", "BACK", NAV, "Sets what the Back key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(66, "ENTER", "ENTER", NAV, "Sets what the Enter key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(160, "NUMPAD_ENTER", "NUMPAD ENTER", NAV, "Sets what the numpad Enter key does inside Visor, and outside Visor only if the system wide switch is on.");
        k(111, "ESCAPE", "ESCAPE", NAV, "Sets what the Escape key does inside Visor, and outside Visor only if the system wide switch is on.");

        // never remappable
        k(3, "HOME", "HOME", ADVANCED, "Home can be remapped only if the overlay service sees it, so check with HOME KEY CHECK first.");
        k(26, "POWER", "POWER", PROTECTED, "Power is handled by the system and stays as it is.");
        k(223, "SLEEP", "SLEEP", PROTECTED, "Sleep is handled by the system and stays as it is.");
        k(224, "WAKEUP", "WAKEUP", PROTECTED, "Wakeup is handled by the system and stays as it is.");
        k(276, "SOFT_SLEEP", "SOFT SLEEP", PROTECTED, "Soft sleep is handled by the system and stays as it is.");
        k(110, "BUTTON_MODE", "PS BUTTON", PROTECTED, "The PS button belongs to the PS menu, set it in the PAD tab.");
    }

    private static final java.util.HashMap<Integer, Def> BY_CODE = new java.util.HashMap<>();

    static {
        for (Def d : DEFS) BY_CODE.put(d.code, d);
    }

    /** Every known button, in table order. */
    static java.util.List<Def> all() { return java.util.Collections.unmodifiableList(DEFS); }

    /** The five buttons Setup always lists first, in this order. */
    static final int[] STANDARD_FIRST = {166, 167, 170, 178, 288};

    /** The advanced screen's buttons, in the order shown. */
    static final int[] ADVANCED_LIST = {19, 20, 21, 22, 23, 4, 82, 219, 231, 84, 85, 126, 127, 86, 87, 88, 89, 90, 130, 272, 273, 24, 25, 164, 177, 179, 181, 180, 182};

    static Def def(int code) { return BY_CODE.get(code); }

    // ---------------------------------------------------------------- identity

    /** Packs a button's identity into one long. Key code 0 (unknown) is named by scan code, vendor and product. */
    static long pack(int code, int scan, int vendor, int product) {
        if (code < 0 || code > MAX_CODE) return INVALID;
        if (code > 0) return code;
        if (scan <= 0 || scan > MAX_CODE) return INVALID;   // code 0 with no scan code is nothing a rule could name
        return SCAN_TAG | ((long) scan << 32) | ((long) (vendor & 0xFFFF) << 16) | (product & 0xFFFF);
    }

    static boolean valid(long key) { return key > 0; }

    static boolean isScan(long key) { return key > 0 && (key & SCAN_TAG) != 0; }

    /** The key code of a "kc:" identity, or 0. */
    static int code(long key) { return key > 0 && !isScan(key) ? (int) key : 0; }

    static int scan(long key) { return isScan(key) ? (int) ((key >> 32) & 0xFFFF) : 0; }

    static int vendor(long key) { return isScan(key) ? (int) ((key >> 16) & 0xFFFF) : 0; }

    static int product(long key) { return isScan(key) ? (int) (key & 0xFFFF) : 0; }

    /** "kc:166" or "sc:233:4012:9001". Empty for an invalid key. */
    static String id(long key) {
        if (!valid(key)) return "";
        if (!isScan(key)) return "kc:" + code(key);
        return "sc:" + scan(key) + ":" + vendor(key) + ":" + product(key);
    }

    /** Reads an id back. @return the packed key or INVALID for anything that is not exactly one of the two forms. */
    static long parse(String id) {
        if (id == null || id.length() < 4 || id.length() > 24) return INVALID;
        if (id.startsWith("kc:")) {
            int c = digits(id, 3, id.length());
            return c >= 1 && c <= MAX_CODE ? c : INVALID;
        }
        if (id.startsWith("sc:")) {
            int a = id.indexOf(':', 3);
            if (a < 0) return INVALID;
            int b = id.indexOf(':', a + 1);
            if (b < 0) return INVALID;
            int scan = digits(id, 3, a), vendor = digits(id, a + 1, b), product = digits(id, b + 1, id.length());
            if (scan < 0 || scan > MAX_CODE || vendor < 0 || vendor > MAX_CODE || product < 0 || product > MAX_CODE) return INVALID;
            return pack(0, scan, vendor, product);
        }
        return INVALID;
    }

    /** Plain decimal digits only (no sign, no space), at most 6. @return -1 if it is not that. */
    private static int digits(String s, int from, int to) {
        if (to <= from || to - from > 6) return -1;
        int v = 0;
        for (int i = from; i < to; i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') return -1;
            v = v * 10 + (ch - '0');
        }
        return v;
    }

    // ---------------------------------------------------------------- names and classes

    /** Shown name. Known codes use the table, others read "KEY 345" or "SCAN 233 (4012:9001)". */
    static String name(long key) {
        if (!valid(key)) return "";
        if (!isScan(key)) {
            Def d = BY_CODE.get(code(key));
            return d != null ? d.name : "KEY " + code(key);
        }
        return "SCAN " + scan(key) + " (" + vendor(key) + ":" + product(key) + ")";
    }

    /** True if the table knows this button by name. */
    static boolean known(long key) { return valid(key) && !isScan(key) && BY_CODE.containsKey(code(key)); }

    /** The sentence for the Setup help line. */
    static String help(long key) {
        Def d = isScan(key) ? null : BY_CODE.get(code(key));
        if (d != null) return d.help;
        return "Sets what this learned button does, on a short press and on a long press.";
    }

    static int cls(long key) {
        if (!valid(key)) return PROTECTED;
        if (isScan(key)) return STANDARD;
        return clsOfCode(code(key));
    }

    static int clsOfCode(int c) {
        Def d = BY_CODE.get(c);
        if (d != null) return d.cls;
        // codes the table does not name: typing keys and game buttons stay out, anything else is a spare button
        if ((c >= 7 && c <= 18) || (c >= 29 && c <= 78) || c == 81 || c == 92 || c == 93 || (c >= 96 && c <= 109)
                || (c >= 112 && c <= 125) || (c >= 131 && c <= 163) || (c >= 188 && c <= 203)) return PROTECTED;
        return STANDARD;
    }

    static boolean nav(long key) { return cls(key) == NAV; }

    static boolean protectedKey(long key) { return cls(key) == PROTECTED; }

    /** Why a protected button cannot be remapped, for the learn screen. */
    static String protectedReason(long key) {
        int c = code(key);
        if (c == 110) return "THE PS BUTTON OPENS THE PS MENU. SET IT IN THE PAD TAB.";
        if (c == 3) return "HOME NEEDS THE OVERLAY SERVICE AND THE HOME WARNING IN ADVANCED REMAP.";
        if (c == 26 || c == 223 || c == 224 || c == 276) return "POWER AND SLEEP STAY WITH THE SYSTEM.";
        if ((c >= 96 && c <= 109) || (c >= 188 && c <= 203)) return "GAME BUTTONS BELONG TO THE GAMES AND THE PS MENU.";
        return "TYPING KEYS STAY AS THEY ARE.";
    }

    /** Every name, for the help guard test. */
    static boolean hasName(String upper) {
        for (Def d : DEFS) if (d.name.equals(upper)) return true;
        return false;
    }

    /** Help id used by Setup rows: one per known code, one for everything else. */
    static String helpId(long key) {
        return "remap.key." + (known(key) ? String.valueOf(code(key)) : "other");
    }

    /** Where this button is allowed in the lists: false for protected keys. */
    static boolean remappable(long key) { return valid(key) && cls(key) != PROTECTED; }
}
