package com.nokoding.deck;

import java.util.Collections;
import java.util.Set;

/**
 * 2.4: the PS overlay's key decision, pure Java so the JVM tests cover it.
 *
 * PsService asks this for every key the accessibility key filter hands it. The answer is
 * PASS (return false, the app in front gets the key) or something Deck does with it (the
 * key is consumed). The rules:
 *
 * Menu closed: only the PS button (BUTTON_MODE) from a gamepad device can be taken, and only
 * when the app in front is known, is not Deck, is not on the pass-through list and is not
 * bypassed by the kill switch. Every other key passes untouched.
 *
 * Menu open: navigation, select, back, Menu and gamepad buttons drive the menu and never reach
 * the app. Home closes the menu and still goes to the system. Volume, power and anything
 * unknown pass.
 *
 * Pairs stay whole: when a key down is consumed its repeats and its up are consumed too, and
 * an up whose down went to the app goes to the app. So a game never sees half a press.
 *
 * Key codes are Android's KeyEvent values (checked against android.jar at build time by hand,
 * listed in docs/ps-menu.md); this class has no Android imports.
 */
final class PsGate {

    // android.view.KeyEvent key codes
    static final int HOME = 3, BACK = 4, DPAD_UP = 19, DPAD_DOWN = 20, DPAD_LEFT = 21, DPAD_RIGHT = 22,
            DPAD_CENTER = 23, VOLUME_UP = 24, VOLUME_DOWN = 25, POWER = 26, ENTER = 66, MENU = 82,
            BUTTON_A = 96, BUTTON_B = 97, BUTTON_C = 98, BUTTON_X = 99, BUTTON_Y = 100, BUTTON_Z = 101,
            BUTTON_L1 = 102, BUTTON_R1 = 103, BUTTON_L2 = 104, BUTTON_R2 = 105, BUTTON_THUMBL = 106,
            BUTTON_THUMBR = 107, BUTTON_START = 108, BUTTON_SELECT = 109, BUTTON_MODE = 110, ESCAPE = 111,
            NUMPAD_ENTER = 160, VOLUME_MUTE = 164;

    /** Results. Everything but PASS and CLOSE_PASS consumes the key. */
    static final int PASS = 0, OPEN = 1, CLOSE = 2, CLOSE_PASS = 3, SWALLOW = 4,
            MOVE_UP = 5, MOVE_DOWN = 6, LEFT = 7, RIGHT = 8, OK = 9, SETUP = 10, TAB_PREV = 11, TAB_NEXT = 12;

    static boolean consumes(int action) { return action != PASS && action != CLOSE_PASS; }

    /**
     * The pure decision for one key down (first press or repeat).
     *
     * @param first     a first down, not a repeat
     * @param gamepad   the event came from a device with gamepad or joystick sources
     * @param fg        package in front, null if not known yet
     * @param self      Deck's own package
     * @param pass      packages that keep the PS button
     * @param bypass    package the kill switch let go of, or null
     * @param capture   Deck's own switch (Setup, OVERLAY CAPTURE)
     */
    static int decide(int code, boolean first, boolean gamepad, String fg, String self, Set<String> pass,
                      boolean menuOpen, String bypass, boolean capture) {
        if (menuOpen) {
            switch (code) {
                case BUTTON_MODE: return first ? CLOSE : SWALLOW;
                case HOME: return CLOSE_PASS;
                case BACK: case ESCAPE: case BUTTON_B: return first ? CLOSE : SWALLOW;
                case DPAD_UP: return MOVE_UP;
                case BUTTON_L1: return TAB_PREV;   // 2.8: previous control center section
                case DPAD_DOWN: return MOVE_DOWN;
                case BUTTON_R1: return TAB_NEXT;   // 2.8: next control center section
                case DPAD_LEFT: return LEFT;
                case DPAD_RIGHT: return RIGHT;
                case DPAD_CENTER: case ENTER: case NUMPAD_ENTER: case BUTTON_A: return first ? OK : SWALLOW;
                case MENU: case BUTTON_START: return first ? SETUP : SWALLOW;
                case BUTTON_C: case BUTTON_X: case BUTTON_Y: case BUTTON_Z: case BUTTON_L2: case BUTTON_R2:
                case BUTTON_THUMBL: case BUTTON_THUMBR: case BUTTON_SELECT:
                    return SWALLOW;
                default:
                    return PASS;   // volume, power, media and TV keys keep working
            }
        }
        if (code != BUTTON_MODE || !first || !capture || !gamepad) return PASS;
        if (fg == null || fg.isEmpty() || fg.equals(self)) return PASS;
        if (fg.equals(bypass)) return PASS;
        if (pass != null && pass.contains(fg)) return PASS;
        return OPEN;
    }

    // ------------------------------------------------------------------ state

    private static final int MAX_CODE = 512;
    private final boolean[] eaten = new boolean[MAX_CODE];
    final String self;
    final PsKill kill = new PsKill();
    volatile Set<String> pass = Collections.emptySet();
    volatile boolean capture = true;
    boolean menuOpen;
    String fg, bypass;

    PsGate(String self) { this.self = self; }

    /**
     * One key event.
     * @param down   ACTION_DOWN (false for ACTION_UP)
     * @param repeat KeyEvent.getRepeatCount()
     */
    int onKey(int code, boolean down, int repeat, boolean gamepad, long now) {
        boolean known = code >= 0 && code < MAX_CODE;
        if (!down) {
            if (code == BUTTON_MODE) kill.release();
            if (known && eaten[code]) { eaten[code] = false; return SWALLOW; }
            return PASS;
        }
        boolean first = repeat == 0;
        if (!first && !(known && eaten[code])) return PASS;   // its first down went to the app
        int a = decide(code, first, gamepad, fg, self, pass, menuOpen, bypass, capture);
        if (!first && !menuOpen) a = SWALLOW;                  // repeats of a press Deck took
        if (consumes(a) && known) eaten[code] = true;
        if (first && code == BUTTON_MODE && consumes(a)) kill.press(now);
        switch (a) {
            case OPEN: menuOpen = true; break;
            case CLOSE: case CLOSE_PASS: menuOpen = false; break;
            default: break;
        }
        return a;
    }

    /**
     * The app in front changed (from TYPE_WINDOW_STATE_CHANGED). Leaving the bypassed app ends
     * the kill switch. @return true if it is a different package.
     */
    boolean setForeground(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(fg)) return false;
        fg = pkg;
        if (bypass != null && !bypass.equals(pkg)) bypass = null;
        return true;
    }

    /**
     * Kill switch fired (PS held for PsKill.HOLD_MS): the menu closes and every key, the PS
     * button too, goes to the app in front until another app comes to the front.
     */
    void killSwitch() {
        menuOpen = false;
        bypass = fg;
    }

    /** The service closed the menu on its own (launch, disconnect, error). */
    void closed() { menuOpen = false; }

    /** The service opened the menu without a key (Setup's test button). */
    void opened() { menuOpen = true; }

    /** Forget every half press, used after an error so nothing stays swallowed. */
    void reset() {
        java.util.Arrays.fill(eaten, false);
        menuOpen = false;
        kill.release();
    }

    boolean eaten(int code) { return code >= 0 && code < MAX_CODE && eaten[code]; }
}
