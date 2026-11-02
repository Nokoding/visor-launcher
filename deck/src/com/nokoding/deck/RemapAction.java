package com.nokoding.deck;

/**
 * 2.6 button remap: the list of things a button can be made to do. Pure Java.
 *
 * Android gives an app no way to inject a key press, so a remap never makes one button "act as"
 * another. It runs an action from this list. The only exception is inside Deck's own screens,
 * where the DECK KEY actions let Deck read a button as a different one (swap OK and Back, swap
 * left and right), because Deck is the one reading the keys there.
 *
 * Each action has a short stable id (what is saved), a name for the list, and one plain sentence
 * for the Setup help line.
 */
final class RemapAction {
    private RemapAction() {}

    static final int DEFAULT = 0, NOTHING = 1, APP = 2, DECK_HOME = 3, DECK_PS = 4, DECK_SETUP = 5, HOME = 6, BACK = 7,
            RECENTS = 8, QUICK = 9, POWER_MENU = 10, SCREENSHOT = 11, PREV_APP = 12, PLAY_PAUSE = 13, NEXT = 14,
            PREV = 15, STOP = 16, VOL_UP = 17, VOL_DOWN = 18, MUTE = 19, ANDROID_SETTINGS = 20, INPUT_PICKER = 21,
            KEY_UP = 22, KEY_DOWN = 23, KEY_LEFT = 24, KEY_RIGHT = 25, KEY_OK = 26, KEY_BACK = 27, TOGGLE_PROFILE = 28,
            EXIT_GAME = 29, PERF_TOGGLE = 30;
    static final int COUNT = 31;

    private static final String[] ID = {"default", "nothing", "app", "deck_home", "deck_ps", "deck_setup", "home", "back",
            "recents", "quick", "power", "shot", "prev_app", "play_pause", "next", "prev", "stop", "vol_up", "vol_down", "mute",
            "settings", "inputs", "key_up", "key_down", "key_left", "key_right", "key_ok", "key_back", "profile", "exit_game", "perf"};

    private static final String[] NAME = {"DEFAULT", "DO NOTHING", "LAUNCH APP", "OPEN VISOR HOME", "OPEN VISOR PS MENU",
            "OPEN VISOR SETUP", "HOME", "BACK", "RECENTS", "QUICK SETTINGS", "POWER MENU", "SCREENSHOT", "PREVIOUS APP",
            "MEDIA PLAY PAUSE", "MEDIA NEXT", "MEDIA PREVIOUS", "MEDIA STOP", "VOLUME UP", "VOLUME DOWN", "MUTE",
            "ANDROID SETTINGS", "OPEN INPUT PICKER", "INSIDE VISOR: UP", "INSIDE VISOR: DOWN", "INSIDE VISOR: LEFT",
            "INSIDE VISOR: RIGHT", "INSIDE VISOR: OK", "INSIDE VISOR: BACK", "TOGGLE GAME PROFILE", "EXIT GAME", "TOGGLE PERFORMANCE MODE"};

    private static final String[] HELP = {
            "Leaves the button to the system, so it does what it always did.",
            "Swallows the button so nothing happens when you press it.",
            "Opens one app that you pick, which you choose on the next screen.",
            "Brings Visor's home screen to the front.",
            "Opens Visor's PS menu, over other apps too when the overlay service is on.",
            "Opens Visor's Setup screen.",
            "Goes to the home screen, like the Home button.",
            "Goes back one step, like the Back button.",
            "Opens Android's list of recent apps, which needs the overlay service outside Visor.",
            "Opens Android's quick settings panel, which needs the overlay service outside Visor.",
            "Opens Android's power menu, which needs the overlay service outside Visor.",
            "Takes a screenshot, which needs the overlay service outside Visor.",
            "Jumps to the app you used before this one, taken from the apps you opened with Visor.",
            "Sends play pause to whatever is playing media.",
            "Sends next track to whatever is playing media.",
            "Sends previous track to whatever is playing media.",
            "Sends stop to whatever is playing media.",
            "Raises the volume one step.",
            "Lowers the volume one step.",
            "Mutes or unmutes the sound.",
            "Opens the box's Android settings.",
            "Opens Visor's list of TV inputs such as HDMI, and is only offered when the box lists some.",
            "Inside Visor this button reads as the D-pad up key, and in other apps it does its normal job.",
            "Inside Visor this button reads as the D-pad down key, and in other apps it does its normal job.",
            "Inside Visor this button reads as the D-pad left key, and in other apps it does its normal job.",
            "Inside Visor this button reads as the D-pad right key, and in other apps it does its normal job.",
            "Inside Visor this button reads as the OK key, and in other apps it does its normal job.",
            "Inside Visor this button reads as the Back key, and in other apps it does its normal job.",
            "Turns the game profile of the app in front on or off, which needs the overlay service and the Visor display method.",
            "Goes back to Visor and closes the game that is in front, the same as the EXIT GAME tile.",
            "Turns performance mode between off and games, the same as its tile in the control center."};

    /** What the LONG PRESS list shows in place of DEFAULT. */
    static final String SAME_NAME = "SAME AS SHORT PRESS";
    static final String SAME_HELP = "Does the same as the short press, so the button acts the moment you press it.";

    static boolean valid(int a) { return a >= 0 && a < COUNT; }

    static String id(int a) { return valid(a) ? ID[a] : ID[0]; }

    /** @return the action number, or -1 for an id this version does not know. */
    static int byId(String id) {
        if (id == null) return -1;
        for (int i = 0; i < COUNT; i++) if (ID[i].equals(id)) return i;
        return -1;
    }

    static String name(int a) { return valid(a) ? NAME[a] : NAME[0]; }

    static String help(int a) { return valid(a) ? HELP[a] : HELP[0]; }

    /** The explicit Setup help id of an action row. */
    static String helpId(int a) { return "remap.act." + id(a); }

    static boolean needsArg(int a) { return a == APP; }

    /** DECK KEY actions: only meaningful inside Deck, and only for navigation buttons. */
    static boolean deckKey(int a) { return a >= KEY_UP && a <= KEY_BACK; }

    /** The key code Deck reads the button as for a DECK KEY action, or 0. */
    static int deckKeyCode(int a) {
        switch (a) {
            case KEY_UP: return RemapKey.KC_DPAD_UP;
            case KEY_DOWN: return RemapKey.KC_DPAD_DOWN;
            case KEY_LEFT: return RemapKey.KC_DPAD_LEFT;
            case KEY_RIGHT: return RemapKey.KC_DPAD_RIGHT;
            case KEY_OK: return RemapKey.KC_DPAD_CENTER;
            case KEY_BACK: return RemapKey.KC_BACK;
            default: return 0;
        }
    }

    /** Global actions only an accessibility service can perform (outside Deck there is no other way). */
    static boolean needsService(int a) {
        return a == HOME || a == BACK || a == RECENTS || a == QUICK || a == POWER_MENU || a == SCREENSHOT;
    }

    /**
     * The actions the picker offers, in order.
     * @param nav    the button is a navigation button (D-pad, OK, Back): the DECK KEY actions are offered
     * @param inputs the box lists TV inputs: OPEN INPUT PICKER is offered, otherwise it is hidden
     */
    static int[] picker(boolean nav, boolean inputs) {
        int[] all = {DEFAULT, NOTHING, APP, DECK_HOME, DECK_PS, DECK_SETUP, HOME, BACK, RECENTS, QUICK, POWER_MENU, SCREENSHOT,
                PREV_APP, PLAY_PAUSE, NEXT, PREV, STOP, VOL_UP, VOL_DOWN, MUTE, ANDROID_SETTINGS, INPUT_PICKER, TOGGLE_PROFILE, EXIT_GAME, PERF_TOGGLE,
                KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT, KEY_OK, KEY_BACK};
        int n = 0;
        int[] out = new int[all.length];
        for (int a : all) {
            if (a == INPUT_PICKER && !inputs) continue;
            if (deckKey(a) && !nav) continue;
            out[n++] = a;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** True if this action may be set for this button at all (the picker only offers allowed ones; storage re-checks). */
    static boolean allowedFor(int a, long key) {
        if (!valid(a)) return false;
        if (deckKey(a)) return RemapKey.nav(key);
        return true;
    }

    /**
     * PREVIOUS APP: from Deck's recents (newest first, component keys such as "com.foo/.Main"),
     * the newest app that is not the one in front and not Deck. Null when there is none.
     */
    static String previousApp(java.util.List<String> keys, String fg, String self) {
        if (keys == null) return null;
        for (String k : keys) {
            if (k == null || k.isEmpty()) continue;
            int slash = k.indexOf('/');
            String pkg = slash > 0 ? k.substring(0, slash) : k;
            if (pkg.equals(fg) || pkg.equals(self)) continue;
            return k;
        }
        return null;
    }
}
