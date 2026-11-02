package com.nokoding.deck;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.8 group B tests: display rules, saved display, shell commands, game profiles, the apply/restore machine. */
final class Tests28B {
    private Tests28B() {}

    private static final String G = "com.game.one", H = "com.game.two", D = "com.nokoding.deck", X = "com.other.app";

    static void run() {
        rules();
        rulesScales();
        state();
        stateHostile();
        cmd();
        cmdHostile();
        cmdLists();
        profilesGood();
        profilesHostile();
        profilesEdit();
        machineLife();
        machineSwitch();
        machineManual();
        machineClock();
    }

    // ================================================================== DisplayRules

    static void rules() {
        section("2.8 display rules: limits");
        check(DisplayRules.validDensity(120), "density 120 ok");
        check(!DisplayRules.validDensity(119), "density 119 too low");
        check(DisplayRules.validDensity(640), "density 640 ok");
        check(!DisplayRules.validDensity(641), "density 641 too high");
        check(!DisplayRules.validDensity(0) && !DisplayRules.validDensity(-320) && !DisplayRules.validDensity(Integer.MAX_VALUE), "density 0, negative, huge not ok");
        check(DisplayRules.validSize(640, 360, 1920, 1080), "640x360 is the smallest ok size");
        check(!DisplayRules.validSize(639, 360, 1920, 1080), "639x360 too narrow");
        check(!DisplayRules.validSize(640, 359, 1920, 1080), "640x359 too low");
        check(!DisplayRules.validSize(640, 360, 1280, 1000), "wrong aspect rejected");
        check(DisplayRules.validSize(1920, 1080, 1920, 1080), "the original size is valid");
        check(!DisplayRules.validSize(1922, 1080, 1920, 1080), "wider than the original rejected");
        check(!DisplayRules.validSize(1920, 1082, 1920, 1080), "taller than the original rejected");
        check(!DisplayRules.validSize(3840, 2160, 1920, 1080), "4K on a 1080 panel rejected");
        check(DisplayRules.validSize(1440, 810, 1920, 1080), "1440x810 ok");
        check(DisplayRules.validSize(1440, 816, 1920, 1080), "aspect off by under 1 percent ok");
        check(!DisplayRules.validSize(1440, 830, 1920, 1080), "aspect off by more than 1 percent rejected");
        check(!DisplayRules.validSize(1440, 810, 0, 1080) && !DisplayRules.validSize(1440, 810, 1920, 0), "zero original rejected");
        check(!DisplayRules.validSize(1440, 810, -1920, 1080), "negative original rejected");
        check(!DisplayRules.validSize(1440, 810, 8193, 1080) && !DisplayRules.validSize(1440, 810, 1920, 8193), "original over 8192 rejected");
        check(DisplayRules.validSize(1440, 810, 8192, 8192) == false, "wrong aspect on a square original rejected");
        check(DisplayRules.validSize(3840, 2160, 3840, 2160), "a 4K original is valid");
        check(!DisplayRules.validSize(Integer.MAX_VALUE, Integer.MAX_VALUE, 1920, 1080), "huge size no overflow, rejected");
        check(!DisplayRules.validSize(-1440, -810, 1920, 1080), "negative size rejected");
        check(DisplayRules.KEEP_SECONDS == 15 && DisplayRules.GRACE_MS == 8000, "constants");
        check(Arrays.equals(DisplayRules.SCALES, new int[]{100, 85, 75, 67, 50}), "scales");
    }

    static void rulesScales() {
        section("2.8 display rules: scales for 1920x1080 at 320 dpi");
        int[][] want = {{100, 1920, 1080, 320}, {85, 1632, 918, 272}, {75, 1440, 810, 240}, {67, 1286, 724, 214}, {50, 960, 540, 160}};
        for (int[] w : want) {
            int[] s = DisplayRules.scaledSize(1920, 1080, w[0]);
            check(s != null && s[0] == w[1] && s[1] == w[2], "size at " + w[0] + " is " + w[1] + "x" + w[2] + " got " + (s == null ? "null" : s[0] + "x" + s[1]));
            check(s != null && s[0] % 2 == 0 && s[1] % 2 == 0, "even numbers at " + w[0]);
            check(DisplayRules.scaledDensity(320, w[0]) == w[3], "density at " + w[0] + " is " + w[3]);
        }
        check(DisplayRules.scaledSize(1920, 1080, 60) == null, "60 is not a scale");
        check(DisplayRules.scaledSize(1920, 1080, 0) == null && DisplayRules.scaledSize(1920, 1080, -75) == null && DisplayRules.scaledSize(1920, 1080, 200) == null, "0, negative, 200 are not scales");
        check(DisplayRules.scaledDensity(320, 60) == -1, "density with a bad scale is -1");
        check(DisplayRules.scaledDensity(119, 75) == -1 && DisplayRules.scaledDensity(641, 75) == -1, "density with a bad original is -1");
        check(DisplayRules.scaledDensity(120, 50) == 120, "density clamps to the minimum");
        check(DisplayRules.scaledDensity(640, 100) == 640, "density 640 at 100 stays");
        check(DisplayRules.scaledDensity(160, 50) == 120, "160 at 50 clamps to 120");
        // small panels: 50 percent of 1280x720 is 640x360, still valid
        int[] s = DisplayRules.scaledSize(1280, 720, 50);
        check(s != null && s[0] == 640 && s[1] == 360, "1280x720 at 50 is 640x360");
        check(DisplayRules.scaledSize(1000, 562, 50) == null, "1000x562 at 50 is under the minimum, null");
        check(DisplayRules.scaledSize(0, 0, 75) == null && DisplayRules.scaledSize(-1920, 1080, 75) == null && DisplayRules.scaledSize(9000, 1080, 75) == null, "bad original gives null");
        check(DisplayRules.scaledSize(1920, 1080, 100)[0] == 1920, "100 returns the original");
        // 4K
        s = DisplayRules.scaledSize(3840, 2160, 67);
        check(s != null && s[0] == 2572 && s[1] == 1448, "4K at 67: " + (s == null ? "null" : s[0] + "x" + s[1]));
        // every result of every scale for some odd panels keeps the aspect and stays valid, or is null
        int[][] panels = {{1920, 1080}, {1280, 720}, {3840, 2160}, {2560, 1440}, {1366, 768}, {1920, 1200}, {1600, 900}, {1920, 1081}};
        for (int[] p : panels) for (int pct : DisplayRules.SCALES) {
            int[] r = DisplayRules.scaledSize(p[0], p[1], pct);
            check(r == null || DisplayRules.validSize(r[0], r[1], p[0], p[1]), "scaled size valid or null for " + p[0] + "x" + p[1] + " at " + pct);
        }
        check("75 PERCENT  1440X810  240 DPI".equals(DisplayRules.label(75, 1920, 1080, 320)), "label 75: " + DisplayRules.label(75, 1920, 1080, 320));
        check("100 PERCENT  NORMAL".equals(DisplayRules.label(100, 1920, 1080, 320)), "label 100");
        check("67 PERCENT  1286X724  214 DPI".equals(DisplayRules.label(67, 1920, 1080, 320)), "label 67");
        check("NOT AVAILABLE".equals(DisplayRules.label(60, 1920, 1080, 320)), "label bad scale");
        check("NOT AVAILABLE".equals(DisplayRules.label(75, 0, 0, 320)), "label bad size");
        check("NOT AVAILABLE".equals(DisplayRules.label(75, 1920, 1080, 0)), "label bad density");
        check("NOT AVAILABLE".equals(DisplayRules.label(100, 0, 0, 0)), "label 100 with bad panel values");
    }

    // ================================================================== DisplayState

    static void state() {
        section("2.8 display state");
        DisplayState a = DisplayState.of(1920, 1080, 320);
        check(a.valid && a.w == 1920 && a.h == 1080 && a.dpi == 320, "of: valid");
        check("v1;1920x1080;320".equals(a.save()), "save line");
        DisplayState b = DisplayState.parse("v1;1920x1080;320");
        check(b.valid && b.w == 1920 && b.h == 1080 && b.dpi == 320, "parse good");
        check(DisplayState.parse(a.save()).valid, "round trip");
        check(DisplayState.parse("v1;3840x2160;640").valid, "4K at 640 parses");
        check(DisplayState.parse("v1;320x320;120").valid, "smallest parses");
        check(!DisplayState.of(319, 1080, 320).valid && !DisplayState.of(1920, 319, 320).valid, "of: under 320 invalid");
        check(!DisplayState.of(8193, 1080, 320).valid && !DisplayState.of(1920, 8193, 320).valid, "of: over 8192 invalid");
        check(!DisplayState.of(1920, 1080, 119).valid && !DisplayState.of(1920, 1080, 641).valid, "of: dpi out of range invalid");
        check(DisplayState.of(8192, 8192, 640).valid && DisplayState.of(320, 320, 120).valid, "of: limits valid");
        check("".equals(DisplayState.of(0, 0, 0).save()), "invalid saves as empty");
        check(DisplayState.restoreShell(a).length == 2 && "wm size reset".equals(DisplayState.restoreShell(a)[0]) && "wm density reset".equals(DisplayState.restoreShell(a)[1]), "restoreShell lines");
        check(Arrays.equals(DisplayState.restoreShell(a), DisplayState.restoreShell(DisplayState.parse("junk"))), "restoreShell same for an invalid original");
        check(Arrays.equals(DisplayState.restoreShell(null), new String[]{"wm size reset", "wm density reset"}), "restoreShell with null");
        check(Arrays.equals(DisplayState.restoreAdb(a), new String[]{"adb shell wm size reset", "adb shell wm density reset"}), "restoreAdb lines");
        check(Arrays.equals(DisplayState.restoreAdb(null), new String[]{"adb shell wm size reset", "adb shell wm density reset"}), "restoreAdb with null");
        check("adb shell wm size reset; adb shell wm density reset".equals(DisplayState.DOC_RESTORE), "doc text");
        check(!DisplayState.needsRestore(a, 1920, 1080, 320), "same values need no restore");
        check(DisplayState.needsRestore(a, 1440, 810, 320), "size differs");
        check(DisplayState.needsRestore(a, 1920, 1080, 240), "density differs");
        check(DisplayState.needsRestore(a, 1440, 810, 240), "both differ");
        check(!DisplayState.needsRestore(DisplayState.parse("bad"), 1440, 810, 240), "invalid original needs no restore");
        check(!DisplayState.needsRestore(null, 1440, 810, 240), "null original needs no restore");
    }

    static void stateHostile() {
        section("2.8 display state: corrupt lines");
        String[] bad = {null, "", " ", "v2;1920x1080;320", "V1;1920x1080;320", "v1;1920x1080", "v1;1920x1080;320;1", "v1;1920x1080;", "v1;;320",
                "v1;1920x;320", "v1;x1080;320", "v1;1920x1080x1;320", "v1; 1920x1080;320", "v1;1920x1080 ;320", "v1;1920 x1080;320", "v1;1920x1080; 320",
                "v1;-1920x1080;320", "v1;1920x-1080;320", "v1;1920x1080;-320", "v1;+1920x1080;320", "v1;19a0x1080;320", "v1;1920x1080;3b0",
                "v1;99999x1080;320", "v1;1920x99999;320", "v1;1920x1080;99999", "v1;99999999999999999999x1080;320", "v1;1920x1080;99999999999999999999",
                "v1;1920X1080;320", "v1,1920x1080,320", "1920x1080;320", "v1;0x0;0", "v1;8193x1080;320", "v1;1920x1080;119", "v1;1920x1080;641",
                "v1;1920x1080;320\n", "\nv1;1920x1080;320", "v1;1920x1080;320\u0000", "v1;\u0661\u0669\u0662\u0660x1080;320", "v1;;;", ";;;", "v1"};
        for (String s : bad) {
            DisplayState d = null;
            boolean threw = false;
            try { d = DisplayState.parse(s); } catch (RuntimeException e) { threw = true; }
            check(!threw && d != null && !d.valid && "".equals(d.save()), "corrupt line gives invalid: " + (s == null ? "null" : s.replace("\n", "\\n").replace("\u0000", "\\0")));
        }
        StringBuilder junk = new StringBuilder("v1;");
        for (int i = 0; i < 10000; i++) junk.append((char) ('a' + i % 26));
        check(!DisplayState.parse(junk.toString()).valid, "10 KB junk line invalid");
        check(!DisplayState.parse(new String(new char[10000]).replace('\0', '9')).valid, "10000 digits invalid");
        check(!DisplayState.parse("v1;" + "1".repeat(10000) + "x1080;320").valid, "10000 digit width invalid");
        check(DisplayState.restoreShell(DisplayState.parse(junk.toString())).length == 2, "restore still works after junk");
    }

    // ================================================================== ProfileCmd

    static void cmd() {
        section("2.8 profile commands");
        check("wm size 1440x810".equals(ProfileCmd.wmSize(1440, 810)), "wmSize");
        check("wm size 640x360".equals(ProfileCmd.wmSize(640, 360)), "wmSize minimum");
        check(ProfileCmd.wmSize(639, 360) == null && ProfileCmd.wmSize(640, 359) == null, "wmSize under minimum null");
        check(ProfileCmd.wmSize(0, 0) == null && ProfileCmd.wmSize(-1440, -810) == null, "wmSize zero, negative null");
        check(ProfileCmd.wmSize(Integer.MAX_VALUE, 1080) == null && ProfileCmd.wmSize(1920, 8193) == null, "wmSize huge null");
        check("wm density 240".equals(ProfileCmd.wmDensity(240)), "wmDensity");
        check("wm density 120".equals(ProfileCmd.wmDensity(120)) && "wm density 640".equals(ProfileCmd.wmDensity(640)), "wmDensity limits");
        check(ProfileCmd.wmDensity(119) == null && ProfileCmd.wmDensity(641) == null && ProfileCmd.wmDensity(-240) == null, "wmDensity out of range null");
        check("cmd game set --mode 4 --downscale 0.67 com.game.one".equals(ProfileCmd.gameModeSet(G, 67)), "game 67");
        check("cmd game set --mode 4 --downscale 0.85 com.game.one".equals(ProfileCmd.gameModeSet(G, 85)), "game 85");
        check("cmd game set --mode 4 --downscale 0.75 com.game.one".equals(ProfileCmd.gameModeSet(G, 75)), "game 75");
        check("cmd game set --mode 4 --downscale 0.5 com.game.one".equals(ProfileCmd.gameModeSet(G, 50)), "game 50");
        check(ProfileCmd.gameModeSet(G, 100) == null, "game 100 null");
        check(ProfileCmd.gameModeSet(G, 60) == null && ProfileCmd.gameModeSet(G, 0) == null && ProfileCmd.gameModeSet(G, -75) == null, "game bad scale null");
        check("cmd game reset com.game.one".equals(ProfileCmd.gameModeReset(G)), "game reset");
        check("adb shell wm size 1440x810".equals(ProfileCmd.adb("wm size 1440x810")), "adb prefix");
        check(ProfileCmd.adb(null) == null, "adb null stays null");
        check("adb shell cmd game reset com.game.one".equals(ProfileCmd.adb(ProfileCmd.gameModeReset(G))), "adb of reset");
        check(ProfileCmd.adb(ProfileCmd.wmSize(1, 1)) == null, "adb of an invalid command is null");
    }

    static void cmdHostile() {
        section("2.8 profile commands: hostile packages");
        String[] bad = {null, "", " ", "a", "nodot", "a.b;reboot", "a.b reboot", "$(x).y", "com.$(id).x", "com.`id`.x", "com.a&&b.c", "com.a|b.c", "com.a>b.c",
                "com.a'b.c", "com.a\"b.c", "com.a\\b.c", "com.a/b.c", "com.a\nb.c", "com.a-b.c", ".com.a", "com.a.", "com..a", "1com.a", "com.1a", "com._a", "_com.a",
                "com.a b", "com.a\tb", "com.\u00e9.x", "com.a*.x", "com.a?.x", "com.a~.x", "com.a#.x", "com.a!.x", "-r.a.b", "a.b;", ";a.b", "a.b\u0000", "..", "."};
        for (String p : bad) {
            String show = p == null ? "null" : p.replace("\n", "\\n").replace("\t", "\\t").replace("\u0000", "\\0");
            check(ProfileCmd.gameModeSet(p, 75) == null, "gameModeSet rejects " + show);
            check(ProfileCmd.gameModeReset(p) == null, "gameModeReset rejects " + show);
            check(ProfileCmd.on(GameProfiles.M_GAME_MODE, p, 75, null) == null, "on rejects " + show);
            check(ProfileCmd.on(GameProfiles.M_DISPLAY_DECK, p, 75, DisplayState.of(1920, 1080, 320)) == null, "on display rejects " + show);
            check(ProfileCmd.off(GameProfiles.M_GAME_MODE, p) == null, "off game rejects " + show);
            check(!ProfileCmd.validPkg(p), "validPkg rejects " + show);
        }
        String[] good = {"com.game.one", "a.b", "com.nokoding.deck", "Com.Game_2.X9", "com.a_b.c1", "a.b.c.d.e.f"};
        for (String p : good) check(ProfileCmd.validPkg(p) && ProfileCmd.gameModeReset(p) != null, "accepts " + p);
        StringBuilder l = new StringBuilder("a");
        while (l.length() < 253) l.append("a");
        l.append(".b");
        check(l.length() == 255 && ProfileCmd.validPkg(l.toString()), "255 chars ok");
        check(!ProfileCmd.validPkg(l + "b"), "256 chars rejected");
    }

    static void cmdLists() {
        section("2.8 profile commands: on and off lists");
        DisplayState o = DisplayState.of(1920, 1080, 320);
        String[] g = ProfileCmd.on(GameProfiles.M_GAME_MODE, G, 75, o);
        check(g != null && g.length == 1 && "cmd game set --mode 4 --downscale 0.75 com.game.one".equals(g[0]), "game mode on is one line");
        check(ProfileCmd.on(GameProfiles.M_GAME_MODE, G, 75, null) != null, "game mode needs no original");
        check(ProfileCmd.on(GameProfiles.M_GAME_MODE, G, 100, o) == null, "game mode at 100 is null");
        for (int m : new int[]{GameProfiles.M_DISPLAY_DECK, GameProfiles.M_DISPLAY_HELPER, GameProfiles.M_COPY}) {
            String[] w = ProfileCmd.on(m, G, 75, o);
            check(w != null && w.length == 2 && "wm size 1440x810".equals(w[0]) && "wm density 240".equals(w[1]), "display method " + m + " on gives two lines");
            check(ProfileCmd.on(m, G, 75, null) == null, "display method " + m + " needs an original");
            check(ProfileCmd.on(m, G, 75, DisplayState.parse("junk")) == null, "display method " + m + " rejects an invalid original");
            check(ProfileCmd.on(m, G, 60, o) == null, "display method " + m + " rejects a bad scale");
            String[] full = ProfileCmd.on(m, G, 100, o);
            check(full != null && "wm size 1920x1080".equals(full[0]) && "wm density 320".equals(full[1]), "display method " + m + " at 100 is the original");
            String[] off = ProfileCmd.off(m, G);
            check(off != null && Arrays.equals(off, new String[]{"wm size reset", "wm density reset"}), "display method " + m + " off is the reset lines");
            check(Arrays.equals(ProfileCmd.off(m, null), new String[]{"wm size reset", "wm density reset"}), "display method " + m + " off needs no package");
        }
        String[] ro = ProfileCmd.off(GameProfiles.M_GAME_MODE, G);
        check(ro != null && ro.length == 1 && "cmd game reset com.game.one".equals(ro[0]), "game mode off");
        check(ProfileCmd.on(9, G, 75, o) == null && ProfileCmd.on(-1, G, 75, o) == null, "unknown method on is null");
        check(ProfileCmd.off(9, G) == null && ProfileCmd.off(-1, G) == null, "unknown method off is null");
        // a panel where the scale would fall under the minimum gives null, never a bad line
        check(ProfileCmd.on(GameProfiles.M_DISPLAY_DECK, G, 50, DisplayState.of(1000, 562, 320)) == null, "too small result is null");
        // no line ever carries shell characters
        for (int m = 0; m <= 3; m++) for (int pct : DisplayRules.SCALES) {
            String[] w = ProfileCmd.on(m, G, pct, o);
            if (w == null) continue;
            for (String line : w) check(line.matches("[a-z0-9. _-]+"), "safe characters in " + line);
        }
    }

    // ================================================================== GameProfiles

    private static GameProfiles.Profile prof(String pkg, int scale, int method, boolean auto, boolean launch) {
        GameProfiles.Profile p = new GameProfiles.Profile(pkg);
        p.scalePct = scale; p.method = method; p.autoRestore = auto; p.launchWith = launch;
        return p;
    }

    static void profilesGood() {
        section("2.8 game profiles: parse and save");
        GameProfiles g = GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.game.one\",\"scale\":67,\"method\":1,\"auto\":false,\"launch\":true},{\"pkg\":\"com.game.two\"}]}");
        check(g.count() == 2, "two profiles read");
        GameProfiles.Profile p = g.get(G);
        check(p != null && p.pkg.equals(G) && p.scalePct == 67 && p.method == 1 && !p.autoRestore && p.launchWith, "all fields read");
        GameProfiles.Profile q = g.get(H);
        check(q != null && q.scalePct == 75 && q.method == GameProfiles.M_GAME_MODE && q.autoRestore && !q.launchWith, "defaults for missing keys");
        check(g.has(G) && g.has(H) && !g.has(X) && !g.has(null), "has");
        check(g.list().get(0).pkg.equals(G) && g.list().get(1).pkg.equals(H), "insertion order");
        String s = g.save();
        check("{\"v\":1,\"profiles\":[{\"pkg\":\"com.game.one\",\"scale\":67,\"method\":1,\"auto\":false,\"launch\":true},{\"pkg\":\"com.game.two\",\"scale\":75,\"method\":0,\"auto\":true,\"launch\":false}]}".equals(s), "save text: " + s);
        GameProfiles r = GameProfiles.parse(s);
        check(r.count() == 2 && s.equals(r.save()), "round trip is stable");
        check("{\"v\":1,\"profiles\":[]}".equals(new GameProfiles().save()), "empty saves as an empty list");
        check(GameProfiles.parse(new GameProfiles().save()).count() == 0, "empty round trip");
        check(GameProfiles.parse("{\"v\":1.0,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":75.0}]}").count() == 1, "whole numbers written as 1.0 and 75.0 read");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"extra\":[1,2],\"x\":{\"y\":1}}]}").count() == 1, "unknown keys ignored");
        check("GAME MODE".equals(GameProfiles.methodName(0)) && "VISOR DISPLAY".equals(GameProfiles.methodName(1)) && "PHONE HELPER".equals(GameProfiles.methodName(2)) && "COPY COMMANDS".equals(GameProfiles.methodName(3)), "method names");
        check(GameProfiles.M_GAME_MODE == 0 && GameProfiles.M_DISPLAY_DECK == 1 && GameProfiles.M_DISPLAY_HELPER == 2 && GameProfiles.M_COPY == 3, "method numbers");
        check(GameProfiles.VERSION == 1 && GameProfiles.MAX == 40, "constants");
        // each method and each scale survives a round trip
        GameProfiles all = new GameProfiles();
        int n = 0;
        for (int m = 0; m <= 3; m++) for (int pct : DisplayRules.SCALES) all.put(prof("com.t.p" + (n++), pct, m, n % 2 == 0, n % 3 == 0));
        GameProfiles back = GameProfiles.parse(all.save());
        check(back.count() == 20 && all.save().equals(back.save()), "20 combinations round trip");
    }

    static void profilesHostile() {
        section("2.8 game profiles: hostile data");
        check(GameProfiles.parse(null).count() == 0, "null");
        check(GameProfiles.parse("").count() == 0, "empty");
        check(GameProfiles.parse("   ").count() == 0, "blank");
        check(GameProfiles.parse("not json").count() == 0, "not json");
        check(GameProfiles.parse("[]").count() == 0, "array at top");
        check(GameProfiles.parse("null").count() == 0, "null literal");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\"").count() == 0, "truncated");
        check(GameProfiles.parse("{\"profiles\":[{\"pkg\":\"com.a.b\"}]}").count() == 0, "no version");
        check(GameProfiles.parse("{\"v\":2,\"profiles\":[{\"pkg\":\"com.a.b\"}]}").count() == 0, "wrong version");
        check(GameProfiles.parse("{\"v\":\"1\",\"profiles\":[{\"pkg\":\"com.a.b\"}]}").count() == 0, "version as string");
        check(GameProfiles.parse("{\"v\":1.5,\"profiles\":[{\"pkg\":\"com.a.b\"}]}").count() == 0, "version 1.5");
        check(GameProfiles.parse("{\"v\":null,\"profiles\":[{\"pkg\":\"com.a.b\"}]}").count() == 0, "version null");
        check(GameProfiles.parse("{\"v\":1}").count() == 0, "no profiles key");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":{}}").count() == 0, "profiles is an object");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":\"x\"}").count() == 0, "profiles is a string");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":null}").count() == 0, "profiles null");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[1,\"a\",null,[],true]}").count() == 0, "entries of wrong type");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":5}]}").count() == 0, "pkg is a number");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":null}]}").count() == 0, "pkg null");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{}]}").count() == 0, "no pkg");
        String[] badPk = {"a.b;reboot", "$(x).y", "`id`.a", "nodot", "a b.c", "", ".a.b", "a.b.", "a..b", "1a.b", "a.b\\nc.d", "a.b&c.d"};
        for (String bp : badPk) check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":" + org.json.JSONObject.quote(bp) + "}]}").count() == 0, "bad package dropped: " + bp);
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":60}]}").count() == 0, "present scale 60 drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":0}]}").count() == 0, "present scale 0 drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":-75}]}").count() == 0, "negative scale drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":\"75\"}]}").count() == 0, "scale as string drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":75.5}]}").count() == 0, "fractional scale drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":null}]}").count() == 0, "scale null drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":1e30}]}").count() == 0, "huge scale drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"method\":4}]}").count() == 0, "unknown method 4 drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"method\":-1}]}").count() == 0, "negative method drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"method\":\"0\"}]}").count() == 0, "method as string drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"auto\":\"yes\"}]}").count() == 0, "auto as string drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"launch\":1}]}").count() == 0, "launch as number drops the entry");
        // one bad entry does not take the others
        GameProfiles mix = GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.one\"},{\"pkg\":\"bad\"},7,{\"pkg\":\"com.a.two\",\"scale\":60},{\"pkg\":\"com.a.three\",\"scale\":50}]}");
        check(mix.count() == 2 && mix.has("com.a.one") && mix.has("com.a.three"), "good entries around bad ones are kept");
        // duplicates: first wins
        GameProfiles dup = GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"scale\":50},{\"pkg\":\"com.a.b\",\"scale\":85}]}");
        check(dup.count() == 1 && dup.get("com.a.b").scalePct == 50, "duplicate package: first wins");
        // too many
        StringBuilder many = new StringBuilder("{\"v\":1,\"profiles\":[");
        for (int i = 0; i < 100; i++) { if (i > 0) many.append(','); many.append("{\"pkg\":\"com.m.p").append(i).append("\"}"); }
        GameProfiles m = GameProfiles.parse(many.append("]}").toString());
        check(m.count() == 40 && m.has("com.m.p0") && m.has("com.m.p39") && !m.has("com.m.p40"), "more than 40 keeps the first 40");
        check(m.list().size() == 40, "list size 40");
        // huge text
        StringBuilder huge = new StringBuilder("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"junk\":\"");
        while (huge.length() < 70000) huge.append("xxxxxxxxxx");
        check(GameProfiles.parse(huge.append("\"}]}").toString()).count() == 0, "more than 64 KB gives an empty set");
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 60000; i++) deep.append('[');
        boolean threw = false;
        GameProfiles dp = null;
        try { dp = GameProfiles.parse(deep.toString()); } catch (Throwable t) { threw = true; }
        check(!threw && dp != null && dp.count() == 0, "deeply nested junk does not throw");
        StringBuilder deep2 = new StringBuilder("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"x\":");
        for (int i = 0; i < 20000; i++) deep2.append('[');
        threw = false;
        try { dp = GameProfiles.parse(deep2.toString()); } catch (Throwable t) { threw = true; }
        check(!threw && dp.count() == 0, "deeply nested value does not throw");
        // binary junk
        StringBuilder bin = new StringBuilder();
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 5000; i++) bin.append((char) rnd.nextInt(256));
        check(GameProfiles.parse(bin.toString()).count() == 0, "random characters give an empty set");
        // fuzz: every cut of a good text parses without throwing and only gives valid profiles
        String good = "{\"v\":1,\"profiles\":[{\"pkg\":\"com.game.one\",\"scale\":67,\"method\":1,\"auto\":false,\"launch\":true},{\"pkg\":\"com.game.two\",\"scale\":50}]}";
        boolean ok = true;
        for (int i = 0; i <= good.length(); i++) {
            try {
                GameProfiles c = GameProfiles.parse(good.substring(0, i));
                for (GameProfiles.Profile pr : c.list()) if (!pr.valid()) ok = false;
            } catch (Throwable t) { ok = false; }
        }
        check(ok, "every truncation parses cleanly");
        for (GameProfiles.Profile pr : GameProfiles.parse(good).list()) check(pr.valid(), "parsed profile is valid");
    }

    static void profilesEdit() {
        section("2.8 game profiles: put, remove, replace, full");
        GameProfiles g = new GameProfiles();
        check(g.put(prof(G, 75, 0, true, false)) && g.count() == 1, "put adds");
        check(g.put(prof(H, 50, 1, true, false)) && g.count() == 2, "put adds a second");
        check(g.put(prof(G, 85, 2, false, true)) && g.count() == 2, "put replaces by package");
        check(g.get(G).scalePct == 85 && g.get(G).method == 2 && !g.get(G).autoRestore && g.get(G).launchWith, "replaced values");
        check(g.list().get(0).pkg.equals(G), "replace keeps the place");
        check(!g.put(null), "put null");
        check(!g.put(prof("bad;pkg", 75, 0, true, false)), "put rejects a bad package");
        check(!g.put(prof(G, 60, 0, true, false)) && g.get(G).scalePct == 85, "put rejects a bad scale and changes nothing");
        check(!g.put(prof(G, 75, 7, true, false)) && g.get(G).method == 2, "put rejects a bad method and changes nothing");
        check(!g.put(new GameProfiles.Profile()), "put rejects a profile without a package");
        GameProfiles.Profile cp = g.get(G);
        cp.scalePct = 50;
        check(g.get(G).scalePct == 85, "get returns a copy");
        List<GameProfiles.Profile> l = g.list();
        l.get(0).scalePct = 50;
        check(g.get(G).scalePct == 85, "list holds copies");
        try { g.list().add(new GameProfiles.Profile(X)); check(false, "list is read only"); } catch (UnsupportedOperationException e) { check(true, "list is read only"); }
        GameProfiles.Profile mine = prof(X, 75, 0, true, false);
        g.put(mine);
        mine.scalePct = 50;
        check(g.get(X).scalePct == 75, "put stores a copy");
        check(g.remove(X) && !g.has(X) && g.count() == 2, "remove");
        check(!g.remove(X) && !g.remove(null), "remove of a missing package is false");
        check(g.get(X) == null && g.get(null) == null, "get of a missing package is null");
        GameProfiles f = new GameProfiles();
        for (int i = 0; i < GameProfiles.MAX; i++) check(f.put(prof("com.f.p" + i, 75, 0, true, false)), "fill " + i);
        check(f.count() == 40, "full");
        check(!f.put(prof("com.f.extra", 75, 0, true, false)) && f.count() == 40 && !f.has("com.f.extra"), "put into a full set is refused");
        check(f.put(prof("com.f.p5", 50, 0, true, false)) && f.get("com.f.p5").scalePct == 50, "replace works when full");
        check(f.remove("com.f.p0") && f.put(prof("com.f.extra", 75, 0, true, false)), "room after a remove");
        check(f.packages().size() == 40, "packages set");
    }

    // ================================================================== ProfileMachine

    private static GameProfiles two() {
        GameProfiles g = new GameProfiles();
        g.put(prof(G, 75, GameProfiles.M_DISPLAY_DECK, true, false));
        g.put(prof(H, 50, GameProfiles.M_GAME_MODE, true, false));
        return g;
    }

    private static final int ID = ProfileMachine.IDLE, AC = ProfileMachine.ACTIVE, GR = ProfileMachine.GRACE, CO = ProfileMachine.CONFIRMING;
    private static final int NO = ProfileMachine.NONE, AP = ProfileMachine.APPLY, RE = ProfileMachine.RESTORE, RV = ProfileMachine.REVERT;

    static void machineLife() {
        section("2.8 profile machine: life cycles");
        ProfileMachine m = new ProfileMachine(two());
        check(m.state() == ID && m.active() == null && m.deadline() == 0 && m.secondsLeft(0) == 0, "starts idle");
        check(m.onForeground(D, 0) == NO && m.state() == ID, "Visor in front while idle: nothing");
        check(m.onForeground(X, 100) == NO && m.state() == ID, "app without a profile: nothing");
        check(m.onForeground(null, 200) == NO && m.onForeground("", 300) == NO && m.state() == ID, "unknown app while idle: nothing");
        // first time: confirming
        check(m.onForeground(G, 1000) == AP, "profile app: APPLY");
        check(m.state() == CO && m.active().pkg.equals(G), "first time confirming");
        check(m.deadline() == 16000, "deadline is now + 15 s");
        check(m.secondsLeft(1000) == 15 && m.secondsLeft(1001) == 15 && m.secondsLeft(1999) == 15 && m.secondsLeft(2000) == 14 && m.secondsLeft(15999) == 1 && m.secondsLeft(16000) == 0 && m.secondsLeft(99999) == 0, "seconds left rounds up and never goes negative");
        check(m.onForeground(G, 2000) == NO && m.state() == CO, "same app again while confirming: nothing");
        check(m.onForeground(D, 3000) == NO && m.state() == CO && m.deadline() == 16000, "Visor in front while confirming: the countdown keeps running");
        check(m.tick(15999) == NO && m.state() == CO, "tick before the deadline");
        check(m.confirm() == NO && m.state() == AC, "KEEP: active");
        check(m.confirmed().contains(G + "|75|1"), "key stored");
        check(m.deadline() == 0 && m.secondsLeft(5000) == 0, "no countdown when active");
        check(m.tick(999999) == NO && m.state() == AC, "tick while active does nothing");
        check(m.onForeground(G, 20000) == NO && m.state() == AC, "same app while active");
        // leave and come back within 7 s
        check(m.onForeground(D, 30000) == NO && m.state() == GR && m.deadline() == 38000, "leaving starts the grace");
        check(m.secondsLeft(30000) == 8 && m.secondsLeft(36000) == 2, "grace seconds");
        check(m.tick(37000) == NO && m.state() == GR, "7 s away: no restore");
        check(m.onForeground(G, 37000) == NO && m.state() == AC, "coming back within the grace: active again, no apply");
        check(m.tick(60000) == NO, "no restore after coming back");
        // leave for 9 s
        check(m.onForeground(X, 70000) == NO && m.state() == GR, "grace again");
        check(m.onForeground(D, 72000) == NO && m.deadline() == 78000, "going on to another app keeps the first deadline");
        check(m.tick(77999) == NO, "just before the deadline");
        check(m.tick(79000) == RE && m.state() == ID && m.active() == null, "9 s away: RESTORE and idle");
        check(m.tick(80000) == NO, "restore only once");
        // second time: no confirm
        check(m.onForeground(G, 90000) == AP && m.state() == AC, "second time: APPLY straight to active");
        check(m.deadline() == 0, "no countdown");
        m.onForeground(D, 91000);
        check(m.tick(99000) == RE && m.state() == ID, "restore after the grace");
        // a different scale or method is a new combination
        GameProfiles g = two();
        ProfileMachine m2 = new ProfileMachine(g);
        m2.onForeground(G, 0); m2.confirm(); m2.forceRestore();
        GameProfiles.Profile ch = g.get(G);
        ch.scalePct = 50;
        g.put(ch);
        check(m2.onForeground(D, 10) == NO, "idle");
        check(m2.onForeground(G, 20) == AP && m2.state() == CO, "a new scale needs confirming again");
        m2.cancel();
        ch.scalePct = 75; ch.method = GameProfiles.M_COPY;
        g.put(ch);
        m2.onForeground(D, 30);
        check(m2.onForeground(G, 40) == AP && m2.state() == CO, "a new method needs confirming again");
        // confirm timeout reverts and is not remembered
        ProfileMachine m3 = new ProfileMachine(two());
        m3.onForeground(G, 0);
        check(m3.tick(14999) == NO, "not yet");
        check(m3.tick(15000) == RV && m3.state() == ID && m3.active() == null, "timeout: REVERT and idle");
        check(m3.confirmed().isEmpty(), "a reverted key is not stored");
        check(m3.onForeground(G, 15100) == NO && m3.state() == ID, "the same app is not applied again right after a revert");
        check(m3.onForeground(D, 16000) == NO, "Visor in front");
        check(m3.onForeground(G, 17000) == AP && m3.state() == CO, "after leaving and returning it asks again");
        // cancel
        check(m3.cancel() == RV && m3.state() == ID && m3.confirmed().isEmpty(), "REVERT NOW while confirming");
        check(m3.onForeground(G, 17100) == NO, "suppressed after cancel");
        check(m3.cancel() == NO && m3.state() == ID, "cancel when idle: nothing");
        check(m3.confirm() == NO && m3.state() == ID, "confirm when idle: nothing");
        // confirm outside confirming
        ProfileMachine m4 = new ProfileMachine(two());
        m4.onForeground(G, 0); m4.confirm();
        int before = m4.confirmed().size();
        check(m4.confirm() == NO && m4.confirmed().size() == before && m4.state() == AC, "confirm while active changes nothing");
        check(m4.cancel() == RE && m4.state() == ID, "REVERT NOW while active: RESTORE");
        // saved confirmations
        ProfileMachine m5 = new ProfileMachine(two());
        m5.setConfirmed(m4.confirmed());
        check(m5.onForeground(G, 0) == AP && m5.state() == AC, "confirmed keys loaded: no confirm");
        m5.setConfirmed(null);
        check(m5.confirmed().isEmpty(), "setConfirmed(null) clears");
        Set<String> junk = new HashSet<>();
        junk.add(null); junk.add(""); junk.add("x".repeat(1000)); junk.add("ok|75|0");
        m5.setConfirmed(junk);
        check(m5.confirmed().size() == 1 && m5.confirmed().contains("ok|75|0"), "bad saved keys are dropped");
        Set<String> big = new HashSet<>();
        for (int i = 0; i < 1000; i++) big.add("k" + i);
        m5.setConfirmed(big);
        check(m5.confirmed().size() == 400, "saved keys are capped");
        try { m5.confirmed().add("x"); check(false, "confirmed() is read only"); } catch (UnsupportedOperationException e) { check(true, "confirmed() is read only"); }
        // autoRestore false never graces
        GameProfiles ga = new GameProfiles();
        ga.put(prof(G, 75, 1, false, false));
        ProfileMachine ma = new ProfileMachine(ga);
        ma.onForeground(G, 0); ma.confirm();
        check(ma.onForeground(D, 1000) == NO && ma.state() == AC, "autoRestore off: leaving keeps it active");
        check(ma.tick(1000000) == NO && ma.state() == AC, "autoRestore off: never restores by itself");
        check(ma.onForeground(null, 2000000) == NO && ma.state() == AC, "autoRestore off: unknown app keeps it active");
        check(ma.forceRestore() == RE && ma.state() == ID, "autoRestore off: forceRestore restores");
        // null and empty package
        ProfileMachine mn = new ProfileMachine(two());
        mn.onForeground(G, 0); mn.confirm();
        check(mn.onForeground(null, 100) == NO && mn.state() == GR, "unknown app while active starts the grace");
        check(mn.onForeground("", 200) == NO && mn.state() == GR && mn.deadline() == 8100, "unknown again keeps the deadline");
        check(mn.onForeground(G, 300) == NO && mn.state() == AC, "coming back");
        ProfileMachine mc = new ProfileMachine(two());
        mc.onForeground(G, 0);
        check(mc.onForeground(null, 100) == NO && mc.state() == CO, "unknown app while confirming: nothing");
        check(new ProfileMachine(null).onForeground(G, 0) == NO, "null profiles: nothing");
    }

    static void machineSwitch() {
        section("2.8 profile machine: switching and replaced");
        ProfileMachine m = new ProfileMachine(two());
        m.setConfirmed(new HashSet<>(Arrays.asList(G + "|75|1", H + "|50|0")));
        check(m.onForeground(G, 0) == AP && m.state() == AC && m.replaced() == null, "first app applied, nothing replaced");
        check(m.onForeground(H, 1000) == AP && m.state() == AC && m.active().pkg.equals(H), "direct switch: APPLY for the new profile");
        check(m.replaced() != null && m.replaced().pkg.equals(G) && m.replaced().method == GameProfiles.M_DISPLAY_DECK, "replaced says what was in place");
        check(m.onForeground(H, 1500) == NO && m.replaced() == null, "replaced is cleared by the next call");
        check(m.onForeground(D, 2000) == NO && m.state() == GR, "leave the second one");
        check(m.onForeground(G, 3000) == AP && m.state() == AC && m.replaced() != null && m.replaced().pkg.equals(H), "switch from grace");
        // switching into an unconfirmed profile starts confirming
        ProfileMachine n = new ProfileMachine(two());
        n.setConfirmed(new HashSet<>(Arrays.asList(G + "|75|1")));
        n.onForeground(G, 0);
        check(n.onForeground(H, 100) == AP && n.state() == CO && n.deadline() == 15100, "switch into an unconfirmed profile confirms");
        check(n.onForeground(G, 200) == AP && n.state() == AC && n.replaced().pkg.equals(H), "switch from confirming into a confirmed one");
        // switch while confirming
        ProfileMachine k = new ProfileMachine(two());
        k.onForeground(G, 0);
        check(k.onForeground(H, 5000) == AP && k.state() == CO && k.active().pkg.equals(H) && k.deadline() == 20000, "switch while confirming restarts the countdown");
        check(k.tick(16000) == NO, "old deadline no longer counts");
        check(k.tick(20000) == RV && k.confirmed().isEmpty(), "new deadline reverts");
    }

    static void machineManual() {
        section("2.8 profile machine: manual restore, toggle, suppression");
        // forceRestore from each state
        ProfileMachine m = new ProfileMachine(two());
        check(m.forceRestore() == RE && m.state() == ID, "forceRestore from idle: RESTORE, stays idle");
        m.onForeground(G, 0);
        check(m.forceRestore() == RE && m.state() == ID && m.active() == null, "forceRestore from confirming");
        check(m.confirmed().isEmpty(), "forceRestore from confirming stores no key");
        m.onForeground(D, 10); m.onForeground(G, 20); m.confirm();
        check(m.forceRestore() == RE && m.state() == ID, "forceRestore from active");
        check(m.onForeground(G, 30) == NO, "app in front is not applied again at once");
        m.onForeground(D, 40);
        m.onForeground(G, 50);
        check(m.state() == AC, "applied again after leaving and returning");
        m.onForeground(D, 60);
        check(m.state() == GR, "grace");
        check(m.forceRestore() == RE && m.state() == ID && m.deadline() == 0, "forceRestore from grace");
        check(m.onForeground(G, 70) == AP, "after a grace restore the app applies when it returns");
        // toggle
        ProfileMachine t = new ProfileMachine(two());
        t.setConfirmed(new HashSet<>(Arrays.asList(G + "|75|1")));
        check(t.toggle(X, 0) == NO && t.state() == ID, "toggle on an app without a profile: nothing");
        check(t.toggle(null, 0) == NO && t.toggle("", 0) == NO, "toggle with unknown app: nothing");
        check(t.toggle(G, 100) == AP && t.state() == AC, "toggle on: APPLY");
        check(t.toggle(G, 200) == RE && t.state() == ID, "toggle off: RESTORE");
        check(t.onForeground(G, 300) == NO && t.onForeground(G, 400) == NO && t.state() == ID, "stays off while the app stays in front");
        check(t.onForeground(D, 500) == NO, "Visor in front");
        check(t.onForeground(G, 600) == AP && t.state() == AC, "applies again after the app left and came back");
        check(t.toggle(G, 700) == RE, "toggle off again");
        check(t.toggle(G, 800) == AP, "toggle on lifts the suppression");
        // toggle off from Deck while grace
        t.onForeground(D, 900);
        check(t.state() == GR, "grace");
        check(t.toggle(D, 1000) == RE && t.state() == ID, "toggle from grace restores");
        check(t.onForeground(D, 1100) == NO, "Visor in front clears the suppression");
        check(t.onForeground(G, 1200) == AP, "app applies again");
        // toggle while confirming
        ProfileMachine c = new ProfileMachine(two());
        c.onForeground(G, 0);
        check(c.toggle(G, 100) == RE && c.state() == ID && c.confirmed().isEmpty(), "toggle while confirming restores and remembers nothing");
        check(c.onForeground(G, 200) == NO, "suppressed");
        // unknown app does not clear the suppression
        c.onForeground(null, 300); c.onForeground("", 400);
        check(c.onForeground(G, 500) == NO, "unknown app does not clear the suppression");
        // suppression of one app does not block another
        c.onForeground(H, 600);
        check(c.state() == CO, "another profile app still applies");
        // suppressed app in front while another profile is active counts as an app without a profile
        ProfileMachine s = new ProfileMachine(two());
        s.setConfirmed(new HashSet<>(Arrays.asList(G + "|75|1", H + "|50|0")));
        s.onForeground(G, 0);
        s.toggle(G, 10);
        s.toggle(H, 20);
        check(s.state() == AC && s.active().pkg.equals(H), "toggle another app on");
        check(s.onForeground(G, 30) == NO && s.state() == GR && s.active().pkg.equals(H), "a switched-off app in front does not take over; it counts as an app without a profile");
        check(s.onForeground(H, 40) == NO && s.state() == AC, "back to the active app");
        check(s.onForeground(G, 50) == AP && s.active().pkg.equals(G), "after another app was seen the suppression is gone");
    }

    static void machineClock() {
        section("2.8 profile machine: odd clocks");
        ProfileMachine m = new ProfileMachine(two());
        m.setConfirmed(new HashSet<>(Arrays.asList(G + "|75|1")));
        m.onForeground(G, 100000);
        m.onForeground(D, 101000);
        check(m.state() == GR, "grace");
        check(m.tick(100000) == NO && m.state() == GR, "a small step back (1 s) is tolerated");
        check(m.tick(98000 - 1) == RE && m.state() == ID, "a big step back restores");
        // far ahead
        ProfileMachine f = new ProfileMachine(two());
        f.onForeground(G, 1000);
        check(f.tick(1000L + 10_000_000_000L) == RV, "clock far ahead reverts");
        // backward in confirming
        ProfileMachine b = new ProfileMachine(two());
        b.onForeground(G, 500000);
        check(b.secondsLeft(500000) == 15, "countdown");
        check(b.secondsLeft(400000) == 0, "backward clock shows 0 seconds");
        check(b.tick(100) == RV && b.state() == ID, "clock jumps back far: revert");
        // seconds never above the length of the countdown, even for a weird clock reading
        ProfileMachine s = new ProfileMachine(two());
        s.onForeground(G, 1000);
        check(s.secondsLeft(900) == 15 && s.secondsLeft(-5) <= 15 && s.secondsLeft(Long.MIN_VALUE) >= 0 && s.secondsLeft(Long.MAX_VALUE) == 0, "secondsLeft stays between 0 and 15");
        // extreme values do not overflow
        ProfileMachine e = new ProfileMachine(two());
        check(e.onForeground(G, Long.MAX_VALUE) == AP && e.state() == CO, "now at Long.MAX_VALUE still applies");
        check(e.deadline() == Long.MAX_VALUE, "deadline saturates");
        check(e.tick(Long.MAX_VALUE) == RV, "and reverts");
        ProfileMachine n = new ProfileMachine(two());
        n.onForeground(G, Long.MIN_VALUE);
        check(n.state() == CO && n.tick(Long.MIN_VALUE + 1) == NO && n.tick(Long.MIN_VALUE + 15001) == RV, "now at Long.MIN_VALUE works");
        // same time stamps
        ProfileMachine z = new ProfileMachine(two());
        z.onForeground(G, 0);
        check(z.tick(0) == NO && z.tick(0) == NO, "repeated ticks at the same time");
        z.confirm();
        z.onForeground(D, 0);
        check(z.tick(0) == NO && z.tick(7999) == NO && z.tick(8000) == RE, "grace ends exactly at 8000 ms");
        check(ProfileMachine.key(prof(G, 75, 1, true, false)).equals(G + "|75|1"), "key text");
    }
}
