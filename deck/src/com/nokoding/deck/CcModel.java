package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2.8 control center: the sections and the tiles in them, as data. Pure Java.
 *
 * Adding a tile later is one line in the table below. App rows under APPS are built at run time
 * and are not part of this model. Tile ids are stable lowercase strings "section.name" and are
 * what CcPrefs saves, so never rename one.
 */
final class CcModel {
    private CcModel() {}

    static final int QUICK = 0, APPS = 1, DISPLAY = 2, CONTROLLER = 3, SOUND = 4, SYSTEM = 5, COUNT = 6;
    static final String[] SECTION_NAMES = {"QUICK", "APPS", "DISPLAY", "CONTROLLER", "SOUND", "SYSTEM"};

    static final int BUTTON = 0, TOGGLE = 1, SLIDER = 2, TILE = 3, HEADER = 4;

    /** One control center tile. span 1 is half width, span 2 is full width. */
    static final class Tile {
        final String id;
        final int section, kind, span;
        final String label, help;
        /** Needs the accessibility service when used outside Deck (global actions); greyed out when it is off. */
        final boolean needsService;

        Tile(String id, int section, int kind, int span, String label, String help, boolean needsService) {
            this.id = id; this.section = section; this.kind = kind; this.span = span;
            this.label = label; this.help = help; this.needsService = needsService;
        }
    }

    private static final List<List<Tile>> BY_SECTION = new ArrayList<>();
    private static final Map<String, Tile> BY_ID = new HashMap<>();
    private static int total = 0;

    static {
        List<List<Tile>> work = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) work.add(new ArrayList<Tile>());
        // QUICK
        add(work, "q.volume", QUICK, SLIDER, 2, "VOLUME", "Change the TV volume with left and right.", false);
        add(work, "q.mute", QUICK, TOGGLE, 1, "MUTE", "Turn the sound off or back on.", false);
        add(work, "q.playpause", QUICK, BUTTON, 1, "PLAY PAUSE", "Play or pause the music or video that is running.", false);
        add(work, "q.next", QUICK, BUTTON, 1, "NEXT", "Skip to the next track or video.", false);
        add(work, "q.prev", QUICK, BUTTON, 1, "PREVIOUS", "Go back to the previous track or video.", false);
        add(work, "q.shot", QUICK, BUTTON, 1, "SCREENSHOT", "Take a picture of the screen.", true);
        add(work, "q.sleep", QUICK, BUTTON, 1, "SLEEP", "Open the power menu so you can put the box to sleep.", true);
        add(work, "q.home", QUICK, BUTTON, 1, "HOME", "Go to the Android home screen.", true);
        add(work, "q.back", QUICK, BUTTON, 1, "BACK", "Press the Android back button for you.", true);
        add(work, "q.recents", QUICK, BUTTON, 1, "RECENTS", "Open the list of recent apps.", true);
        add(work, "q.deck", QUICK, BUTTON, 1, "VISOR HOME", "Go back to the Visor home screen.", false);
        add(work, "q.exitgame", QUICK, BUTTON, 1, "EXIT GAME", "Close the game you are playing and go back to Visor.", false);
        // APPS (the app rows are added at run time after the header)
        add(work, "a.header", APPS, HEADER, 2, "RUNNING APPS", "The apps that are open in the background.", false);
        add(work, "a.closeall", APPS, BUTTON, 2, "CLOSE ALL BACKGROUND", "Close every app in the background to free memory.", false);
        // DISPLAY
        add(work, "d.profile", DISPLAY, BUTTON, 1, "GAME PROFILE", "Pick a display mode for the app that is in front.", false);
        add(work, "d.restore", DISPLAY, BUTTON, 1, "RESTORE DISPLAY", "Put the display back to how it was before a game profile.", false);
        add(work, "d.reduce", DISPLAY, TOGGLE, 1, "REDUCE EFFECTS", "Turn off animations in Visor to save power and memory.", false);
        add(work, "d.ram", DISPLAY, HEADER, 2, "MEMORY", "How much memory is free on the box right now.", false);
        add(work, "d.saver", DISPLAY, BUTTON, 1, "SCREENSAVER NOW", "Start the Visor screensaver right away.", false);
        add(work, "d.perf", DISPLAY, TOGGLE, 1, "PERFORMANCE MODE", "Close background apps when a game starts, to free memory.", false);
        // CONTROLLER
        add(work, "c.gyro", CONTROLLER, TOGGLE, 1, "GYRO SWAY", "Let the home screen tilt when you move the controller.", false);
        add(work, "c.mode", CONTROLLER, BUTTON, 1, "INPUT MODE", "Switch how the controller moves around the screen.", false);
        add(work, "c.rumble", CONTROLLER, BUTTON, 1, "RUMBLE TEST", "Shake the controller once to check the vibration.", false);
        add(work, "c.leds", CONTROLLER, TOGGLE, 1, "PLAYER LEDS", "Turn the player lights on the controller on or off.", false);
        add(work, "c.battery", CONTROLLER, HEADER, 2, "CONTROLLER BATTERY", "How much charge the controller has left.", false);
        add(work, "c.remap", CONTROLLER, TOGGLE, 1, "REMAPS ON", "Turn all your button remaps on or off at once.", false);
        // SOUND
        add(work, "s.volume", SOUND, SLIDER, 2, "VOLUME", "Change the TV volume with left and right.", false);
        add(work, "s.pack", SOUND, BUTTON, 1, "SOUND PACK", "Pick the set of click and menu sounds Visor uses.", false);
        add(work, "s.ui", SOUND, TOGGLE, 1, "UI SOUNDS", "Turn the click and menu sounds on or off.", false);
        // SYSTEM
        add(work, "y.setup", SYSTEM, BUTTON, 2, "VISOR SETUP", "Open the Visor settings.", false);
        add(work, "y.android", SYSTEM, BUTTON, 1, "ANDROID SETTINGS", "Open the settings of the Android system.", false);
        add(work, "y.power", SYSTEM, BUTTON, 1, "POWER MENU", "Open the power menu to restart or turn off the box.", true);
        add(work, "y.restart", SYSTEM, BUTTON, 1, "RESTART VISOR", "Close Visor and start it again fresh.", false);
        add(work, "y.launcher", SYSTEM, HEADER, 2, "GOOGLE TV LAUNCHER", "Shows whether the Google TV launcher is on the box.", false);
        for (int i = 0; i < COUNT; i++) BY_SECTION.add(Collections.unmodifiableList(work.get(i)));
    }

    private static void add(List<List<Tile>> work, String id, int section, int kind, int span, String label, String help,
                            boolean needsService) {
        Tile t = new Tile(id, section, kind, span, label, help, needsService);
        work.get(section).add(t);
        BY_ID.put(id, t);
        total++;
    }

    /** The default tiles of a section in their default order (unmodifiable; empty for a bad section). */
    static List<Tile> defaults(int section) {
        if (section < 0 || section >= COUNT) return Collections.emptyList();
        return BY_SECTION.get(section);
    }

    /** The tile with this id, or null if there is none. */
    static Tile byId(String id) { return id == null ? null : BY_ID.get(id); }

    static boolean valid(String id) { return byId(id) != null; }

    /** Number of tiles in all sections. */
    static int count() { return total; }
}
