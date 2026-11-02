package com.nokoding.deck;

/**
 * 2.3: the OPTIONS FLYOUT animation's schema, and how its options plus Reduce effects plus
 * the per screen switches turn into the parameters TextReveal and FlyoutAnim run with.
 * One spec drives every menu, so one setting changes them all. Pure Java.
 */
final class AnimConfig {
    private AnimConfig() {}

    static final String FLYOUT_ID = "flyout", FLYOUT_NAME = "OPTIONS FLYOUT";

    /** Screens that can opt out, by name. Panel kinds map onto these in screenFor(). */
    static final String SETUP = "setup", APP_MENU = "appmenu", QUICK = "quick", INSTALL = "install",
            REPORT = "report", HOME_CMDS = "homecmds", CONFIRM = "confirm", TOAST = "toast";
    static final String[] SCREENS = {SETUP, APP_MENU, QUICK, INSTALL, REPORT, HOME_CMDS, CONFIRM, TOAST};
    static final String[] SCREEN_LABELS = {"SETUP SCREENS", "APP MENUS (FLYOUT)", "PS QUICK MENU", "INSTALL PACK",
            "CONTROLLER REPORT", "HOME ADB COMMANDS", "CONFIRMATIONS", "TOASTS"};

    static final String FLYOUT_SCHEMA =
            "text    | TEXT ANIMATION   | CHOICE | TYPE, FADE, NONE            | TYPE\n"
          + "dir     | TYPING DIRECTION | CHOICE | LEFT TO RIGHT, RIGHT TO LEFT | LEFT TO RIGHT\n"
          + "speed   | TYPING SPEED     | INT    | 40..200/10 CPS               | 90\n"
          + "stagger | ROW STAGGER      | INT    | 0..200/10 MS                 | 70\n"
          + "cursor  | CURSOR           | BOOL   |                              | on\n"
          + "sound   | SOUND            | BOOL   |                              | on\n"
          + "glide   | ICON GLIDE       | BOOL   |                              | on\n"
          + "side    | FLYOUT SIDE      | CHOICE | LEFT, RIGHT                  | LEFT\n"
          + "scale   | DURATION SCALE   | FLOAT  | 0.5..2/0.25 X                | 1\n"
          + "style   | PANEL STYLE      | CHOICE | SOLID, GLASS                 | SOLID\n";

    /** Base times at DURATION SCALE 1, in ms. */
    static final int FADE_MS = 180, MAX_SPAN_MS = 280, MAX_ROW_MS = 320, CLEAR_MS = 80;
    /** Reduce effects: text appears at once, at most this fade. */
    static final int REDUCE_FADE_MS = 120;
    static final float SLIDE_PX = 10f;

    static AnimSpec flyout() {
        StringBuilder s = new StringBuilder(FLYOUT_SCHEMA);
        for (int i = 0; i < SCREENS.length; i++) s.append("screen.").append(SCREENS[i]).append(" | ").append(SCREEN_LABELS[i]).append(" | BOOL |  | on\n");
        return AnimSpec.parse(FLYOUT_ID, FLYOUT_NAME, s.toString());
    }

    /** Which opt out switch a Panel kind (or the quick menu and toasts) answers to. */
    static String screenFor(String kind) {
        if (kind == null) return SETUP;
        switch (kind) {
            case "tile": case "dock": case "icon": case "preview": return APP_MENU;
            case "quick": return QUICK;
            case "install": return INSTALL;
            case "report": return REPORT;
            case "homecmds": return HOME_CMDS;
            case "confirm": return CONFIRM;
            case "toast": return TOAST;
            default: return SETUP;
        }
    }

    /** The flyout layout (icon dock plus side panel) is for menus that belong to one item. */
    static boolean isFlyout(String kind) { return APP_MENU.equals(screenFor(kind)); }

    static boolean enabled(AnimSpec s, String screen) {
        AnimOption o = s == null ? null : s.get("screen." + screen);
        return o == null || o.on();
    }

    static float scale(AnimSpec s) {
        AnimOption o = s == null ? null : s.get("scale");
        float k = o == null ? 1f : o.f();
        return k < 0.5f ? 0.5f : (k > 2f ? 2f : k);
    }

    static int textMode(AnimSpec s) {
        AnimOption o = s == null ? null : s.get("text");
        int m = o == null ? TextReveal.TYPE : o.i();
        return m < 0 || m > 2 ? TextReveal.TYPE : m;
    }

    static boolean rtl(AnimSpec s) { AnimOption o = s == null ? null : s.get("dir"); return o != null && o.i() == 1; }

    static boolean rightSide(AnimSpec s) { AnimOption o = s == null ? null : s.get("side"); return o != null && o.i() == 1; }

    static boolean glass(AnimSpec s) { AnimOption o = s == null ? null : s.get("style"); return o != null && o.i() == 1; }

    static boolean sound(AnimSpec s) { AnimOption o = s == null ? null : s.get("sound"); return o == null || o.on(); }

    private static boolean bool(AnimSpec s, String k, boolean def) { AnimOption o = s == null ? null : s.get(k); return o == null ? def : o.on(); }

    private static int num(AnimSpec s, String k, int def) { AnimOption o = s == null ? null : s.get(k); return o == null ? def : o.i(); }

    /** Text animation for one screen. Reduce effects wins over everything, then the opt out. */
    static void reveal(AnimSpec s, String screen, boolean reduce, TextReveal.Params out) {
        float k = scale(s);
        out.mode = textMode(s);
        out.rtl = rtl(s);
        out.cps = Math.max(1, num(s, "speed", 90)) / k;
        out.staggerMs = Math.round(Math.max(0, num(s, "stagger", 70)) * k);
        out.fadeMs = Math.round(FADE_MS * k);
        out.maxSpanMs = Math.round(MAX_SPAN_MS * k);
        out.maxRowMs = Math.round(MAX_ROW_MS * k);
        out.slide = SLIDE_PX;
        out.cursor = bool(s, "cursor", true);
        out.ticks = sound(s);
        if (!enabled(s, screen)) {
            out.mode = TextReveal.NONE;
            out.cursor = false;
            out.ticks = false;
        } else if (reduce) {
            if (out.mode != TextReveal.NONE) out.mode = TextReveal.FADE;
            out.staggerMs = 0;
            out.fadeMs = REDUCE_FADE_MS;
            out.slide = 0f;
            out.cursor = false;
            out.ticks = false;
        }
    }

    /** Icon glide and panel grow for one screen. */
    static void flyout(AnimSpec s, String screen, boolean reduce, FlyoutAnim.Params out) {
        boolean on = enabled(s, screen);
        out.scale = scale(s);
        out.instant = reduce || !on;
        out.glide = on && !reduce && bool(s, "glide", true);
    }

    /** Close: how long the text takes to clear (0 when it does not animate). */
    static int clearMs(AnimSpec s, String screen, boolean reduce) {
        if (reduce || !enabled(s, screen) || textMode(s) == TextReveal.NONE) return 0;
        return Math.round(CLEAR_MS * scale(s));
    }
}
