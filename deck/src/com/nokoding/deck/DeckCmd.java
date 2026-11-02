package com.nokoding.deck;

/**
 * Commands that can arrive as the deck_cmd string extra on the HomeActivity intent, for
 * example from tools/install-soundpack.sh:
 *   adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reload_packs
 * Only this short list is accepted. Anything else is ignored. Every command only touches
 * Deck's own files or opens Deck's own Setup, so a stray intent from another app cannot do harm. Pure Java.
 */
final class DeckCmd {
    private DeckCmd() {}

    static final String EXTRA = "deck_cmd";
    static final int NONE = 0, RELOAD_PACKS = 1, RELOAD_ICONS = 2, OPEN_SETUP = 3, RESET_REMAP = 4, OPEN_INPUTS = 5,
            RELOAD_WALLPAPERS = 6, PREVIEW_SAVER = 7, RESTORE_DISPLAY = 8, OPEN_TUTORIAL = 9, EXIT_GAME = 10, RELOAD_THEMES = 11;
    /**
     * 2.4: open_setup is how the PS overlay's OPEN SETUP row brings Deck forward with Setup open.
     * 2.6: reset_remap clears every button remap and turns the advanced switches off, even when
     * the D-pad is broken or Deck is in safe mode (docs/button-remap.md). open_inputs is how a
     * remapped button brings Deck forward with its list of TV inputs open.
     * 2.7: reload_wallpapers unpacks new wallpaper pack zips and reloads the wallpaper (the same as
     * RELOAD WALLPAPERS in Setup). preview_screensaver starts the screensaver preview inside Deck, the
     * same as PREVIEW SCREENSAVER in Setup, so it can be tried from a phone. Neither touches anything
     * outside Deck's own files and screens.
     * 2.8: restore_display puts the display size and density back to the panel's own values (wm size
     * reset and wm density reset, done by Deck when it can, otherwise it shows the adb lines) and drops any
     * active game profile. It is the same as RESTORE DISPLAY in Setup and the PS menu.
     * 2.9: open_tutorial opens Deck's tutorial, the same as Setup, SYSTEM, TUTORIAL.
     * 2.12: exit_game brings Deck to the front and closes the game performance mode remembers, the same as EXIT GAME in
     * the control center. It only closes background processes of one app, through the same call as KILL BACKGROUND.
     * 2.17: reload_themes rescans the themes folder after a theme file was pushed with adb and says how many it found.
     */
    static final String[] NAMES = {"", "reload_packs", "reload_icons", "open_setup", "reset_remap", "open_inputs",
            "reload_wallpapers", "preview_screensaver", "restore_display", "open_tutorial", "exit_game", "reload_themes"};

    static int parse(Object extra) {
        if (!(extra instanceof String)) return NONE;
        String s = (String) extra;
        if (s.isEmpty() || s.length() > 32) return NONE;
        for (int i = 1; i < NAMES.length; i++) if (NAMES[i].equals(s)) return i;
        return NONE;
    }
}
