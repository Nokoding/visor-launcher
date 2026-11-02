package com.nokoding.deck;

/**
 * 2.9: the tutorial lessons as plain data, so the screen only has to draw them and the JVM tests can
 * check the text. Pure Java, no Android types: key codes are plain ints (the same numbers as
 * android.view.KeyEvent).
 *
 * Every line is one short sentence, at most 70 characters. Every statement here was checked against
 * the docs and the key handling in HomeActivity, and nothing in 2.9 has run on the TV yet, which the
 * first lesson says out loud.
 *
 * A lesson may have a TRY IT step the screen checks by watching key presses inside the tutorial:
 * PRESS (any one of the listed keys), COMBO (two keys held at the same time) or NONE (no step). The
 * screen passes only first presses and releases to {@link TryState}, never key repeats.
 */
final class TutorialModel {
    private TutorialModel() {}

    // Android key codes, as plain numbers so the JVM tests need no Android classes.
    private static final int DPAD_CENTER = 23;
    private static final int ENTER = 66;
    private static final int BUTTON_A = 96;
    private static final int BUTTON_L1 = 102;
    private static final int BUTTON_X = 99;
    private static final int BUTTON_Y = 100;
    private static final int BUTTON_R1 = 103;
    private static final int BUTTON_MODE = 110;

    static final int PRESS = 0;
    static final int COMBO = 1;
    static final int NONE = 2;

    /** A TRY IT step. prompt says what to do, done is shown when it worked. Both are short capitals. */
    static final class Try {
        final int kind;
        final int[] keys;
        final String prompt;
        final String done;

        Try(int kind, int[] keys, String prompt, String done) {
            this.kind = kind;
            this.keys = keys;
            this.prompt = prompt;
            this.done = done;
        }
    }

    /** One lesson. gamepad is true when the lesson is about a controller. */
    static final class Lesson {
        final String id;
        final String title;
        final String[] lines;
        final Try tryIt;
        final boolean gamepad;

        Lesson(String id, String title, String[] lines, Try tryIt, boolean gamepad) {
            this.id = id;
            this.title = title;
            this.lines = lines;
            this.tryIt = tryIt;
            this.gamepad = gamepad;
        }
    }

    private static Try none() {
        return new Try(NONE, new int[0], "", "");
    }

    private static final Lesson[] LESSONS = {
        new Lesson("welcome", "WELCOME TO VISOR", new String[]{
            "Visor is a launcher. It replaces the home screen on your TV.",
            "This tutorial teaches the controls in short lessons.",
            "Press A or OK to go to the next lesson.",
            "Press B or left to go to the one before.",
            "Press EXIT, or Back on the remote, to leave.",
            "Nothing here has run on the TV yet. Some things may not work."
        }, none(), false),

        new Lesson("move", "MOVING AROUND", new String[]{
            "The D-pad moves the focus.",
            "OK launches the app in focus.",
            "Hold OK to open the app menu for that app.",
            "Menu opens Setup.",
            "Back goes back.",
            "On a controller, A works like OK and B works like Back."
        }, new Try(PRESS, new int[]{DPAD_CENTER, ENTER, BUTTON_A},
            "PRESS THE A BUTTON OR OK", "GOOD. THAT IS HOW YOU SELECT."), false),

        new Lesson("wheel", "THE WHEEL AND THE GRID", new String[]{
            "Your apps sit on a wheel or in a grid.",
            "On a controller, press Y to switch between them.",
            "The right stick spins the wheel like a click wheel.",
            "L1 and R1 jump five apps at a time."
        }, new Try(PRESS, new int[]{BUTTON_L1, BUTTON_R1},
            "PRESS L1 OR R1", "GOOD. THAT JUMPS FIVE APPS."), true),

        new Lesson("psmenu", "THE PS MENU", new String[]{
            "Press the PS button to open the control center.",
            "It has sections: QUICK, APPS, DISPLAY, CONTROLLER, SOUND, SYSTEM.",
            "L1 and R1 change the section. B closes it.",
            "Inside Visor it works with nothing else turned on.",
            "Over other apps it needs the overlay service, set up with adb.",
            "Games can keep the PS button for themselves."
        }, new Try(PRESS, new int[]{BUTTON_MODE},
            "PRESS THE PS BUTTON", "GOOD. THAT OPENS THE CONTROL CENTER."), true),

        new Lesson("apps", "THE TASK MANAGER", new String[]{
            "The APPS section lists the apps you used lately.",
            "The list needs one adb grant. See docs/task-manager.md.",
            "OK on an app opens its actions.",
            "KILL BACKGROUND ends the background part of an app.",
            "FORCE STOP is experimental and may not work on this box.",
            "CLOSE ALL BACKGROUND asks you to press twice."
        }, none(), false),

        new Lesson("remap", "REMAPPING BUTTONS", new String[]{
            "In Setup, REMAP, the spare remote buttons can run actions.",
            "Pick a button, then set a short press and a long press action.",
            "LEARN A BUTTON finds any button, even ones Visor does not know.",
            "A remap runs an action. It never presses another button.",
            "A remapped button stops doing its old job.",
            "In other apps it needs the overlay service."
        }, none(), false),

        new Lesson("combos", "COMBOS", new String[]{
            "A combo uses two controller buttons together.",
            "Hold the first button, then press the second.",
            "The first button still goes to the app. Only the second is taken.",
            "Make one in Setup, REMAP, COMBOS.",
            "Allowed: X, Y, L1, R1, L2, R2, the stick clicks, Create, Options.",
            "Combos follow REMAP IN GAMES, so games keep their buttons."
        }, new Try(COMBO, new int[]{BUTTON_X, BUTTON_Y},
            "HOLD X THEN PRESS Y", "GOOD. THAT IS A COMBO."), true),

        new Lesson("escape", "IF SOMETHING GOES WRONG", new String[]{
            "Do not try these now. They are for when something goes wrong.",
            "Hold Back for 6 seconds. It clears the D-pad, OK and Back remaps.",
            "Hold the PS button for 4 seconds in an app. The app keeps it.",
            "Pad tests: hold B 1.5 s, or Start and Select for 2 seconds.",
            "Setup, REMAP, RESET ALL REMAPS clears every remap. Press OK twice.",
            "Picture looks wrong? Use RESTORE DISPLAY in Setup or the PS menu.",
            "From a phone: adb shell wm size reset, then wm density reset.",
            "Or send deck_cmd restore_display to Visor. See docs/game-profiles.md."
        }, none(), false),

        new Lesson("profiles", "GAME PROFILES", new String[]{
            "A game profile lowers the picture size for one heavy game.",
            "Set one up in Setup, SYSTEM, GAME PROFILES, ADD A PROFILE.",
            "It helps the GPU. It does not help memory.",
            "The first time, a prompt asks KEEP IT or PUT IT BACK.",
            "With no answer in 15 seconds it puts the picture back.",
            "RESTORE DISPLAY always puts the normal size back.",
            "Whether it works on this box is not known yet."
        }, none(), false),

        new Lesson("touchpad", "TOUCHPAD VOLUME", new String[]{
            "Change the volume with the DualSense touchpad.",
            "Turn it on in Setup, PAD, TOUCHPAD VOLUME. It is off by default.",
            "Hold the key you chose, then drag a finger on the touchpad.",
            "Up is louder. You pick the key, direction and speed in Setup.",
            "If the box hides the touchpad, hold the key and press the D-pad.",
            "Whether the touchpad reaches Visor on this box is not known.",
            "No touchpad? Try Setup, PAD, STICK VOLUME: spin the right stick."
        }, none(), true),

        new Lesson("look", "MAKE IT YOURS", new String[]{
            "Setup, LOOK has the wallpaper, the clock and the screensaver.",
            "CLOCK SECONDS adds seconds to the clock.",
            "Pick a wallpaper and a screensaver style there.",
            "On a slow box, turn on REDUCE EFFECTS in MOTION.",
            "It cuts the heavy motion and keeps everything working."
        }, none(), false),

        new Lesson("done", "THAT IS IT", new String[]{
            "You can see this again in Setup, SYSTEM, TUTORIAL.",
            "From a phone you can open it with adb:",
            "adb shell am start -n com.nokoding.deck/.HomeActivity",
            "--es deck_cmd open_tutorial",
            "Longer guides are in the docs folder of the Visor repo.",
            "Press A or OK to finish."
        }, none(), false),
    };

    static int count() {
        return LESSONS.length;
    }

    /** The lesson at index i, or null when i is out of range. */
    static Lesson get(int i) {
        return i >= 0 && i < LESSONS.length ? LESSONS[i] : null;
    }

    /** The lesson with this id, or null when there is none. */
    static Lesson byId(String id) {
        int i = indexOf(id);
        return i < 0 ? null : LESSONS[i];
    }

    /** Index of the lesson with this id, or -1. */
    static int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < LESSONS.length; i++) if (LESSONS[i].id.equals(id)) return i;
        return -1;
    }

    /**
     * Whether the lesson is listed. True for every real lesson: controller lessons stay in the list
     * even with no controller connected (the screen may mark them), so nobody misses what they teach.
     */
    static boolean visible(Lesson l, boolean gamepadPresent) {
        return l != null;
    }

    /** PRESS steps: true when keyCode is one of the listed keys. Other kinds never match. */
    static boolean checkPress(Try t, int keyCode) {
        if (t == null || t.kind != PRESS || t.keys == null) return false;
        for (int k : t.keys) if (k == keyCode) return true;
        return false;
    }

    /**
     * Tracks one TRY IT step. Feed it first presses (down true) and releases (down false) only, never
     * key repeats. onKey returns true once, at the moment the step completes. done() stays true until
     * reset().
     */
    static final class TryState {
        private final Try t;
        private boolean done;
        private boolean heldA;
        private boolean heldB;

        TryState(Try t) {
            this.t = t;
        }

        boolean onKey(int keyCode, boolean down) {
            if (t == null || t.keys == null) return false;
            if (t.kind == PRESS) {
                if (done || !down) return false;
                if (!checkPress(t, keyCode)) return false;
                done = true;
                return true;
            }
            if (t.kind == COMBO && t.keys.length == 2) {
                if (keyCode == t.keys[0]) heldA = down;
                else if (keyCode == t.keys[1]) heldB = down;
                else return false;
                if (!done && heldA && heldB) {
                    done = true;
                    return true;
                }
            }
            return false;
        }

        boolean done() {
            return done;
        }

        private boolean swallowed;

        /**
         * 2.17: true if this key press only counts for the step and must not change the page. It never traps: the
         * first press of a key the step waits for is swallowed, the same key pressed again changes the page, and
         * nothing is swallowed once the step is done or skipped.
         */
        boolean consumes(int keyCode) {
            if (t == null || t.keys == null || done) return false;
            boolean mine = false;
            for (int k : t.keys) if (k == keyCode) mine = true;
            if (!mine) return false;
            if (swallowed) return false;
            swallowed = true;
            return true;
        }

        /** SKIP TRY: counts the step as done without the keys. */
        void skip() {
            done = true;
        }

        void reset() {
            swallowed = false;
            done = false;
            heldA = false;
            heldB = false;
        }
    }
}
