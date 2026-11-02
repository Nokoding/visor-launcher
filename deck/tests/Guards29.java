package com.nokoding.deck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.9 guards that read the sources, the manifest and the docs (tools/test.sh passes -Ddeck.src). */
final class Guards29 {
    private Guards29() {}

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void run() {
        section("2.9 guards: tutorial, combos, commands, docs, version");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            File deck = new File(dir, "../../../..");
            String mf = read(new File(deck, "AndroidManifest.xml"));
            String build = read(new File(deck, "tools/build.sh"));
            check(build.contains("--version-code 24 --version-name 2.20-beta") && mf.contains("android:versionCode=\"24\"") && mf.contains("android:versionName=\"2.20-beta\""), "version 24 / 2.20-beta in the manifest and build.sh");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<uses-permission android:name=\"([^\"]+)\"").matcher(mf);
            int perms = 0;
            while (m.find()) perms++;
            check(perms == 7 && !mf.contains("android.permission.INTERNET") && mf.split("<service", -1).length - 1 == 2, "seven permissions (the 2.15 WRITE_SECURE_SETTINGS is the last one added) and two services");
            String home = read(new File(dir, "HomeActivity.java"));
            check(home.contains("case DeckCmd.OPEN_TUTORIAL:") && DeckCmd.parse("open_tutorial") == DeckCmd.OPEN_TUTORIAL && DeckCmd.parse("open_tutorial ") == DeckCmd.NONE, "open_tutorial is handled and exact");
            check(home.contains("new TutorialView(") && home.contains("new ComboLearnView(") && home.contains("maybeOfferTutorial"), "Visor opens the tutorial and the combo screen");
            // the first run offer is marked before it is shown, so it cannot come back every start
            int at = home.indexOf("private void maybeOfferTutorial()");
            String body = at < 0 ? "" : home.substring(at, home.indexOf("\n    }\n", at));
            check(body.indexOf("tp.dismiss()") > 0 && body.indexOf("tp.dismiss()") < body.indexOf("openTutorial(true)"), "the tutorial is marked as offered before it opens");
            String ps = read(new File(dir, "PsService.java"));
            check(ps.contains("ComboGate") && ps.contains("cg.onKey("), "the overlay service checks combos before single button rules");
            int cd = home.indexOf("if (comboDeck(e)) return true;");
            check(cd > 0 && cd < home.indexOf("int rr = remapDeck(e);"), "inside Visor combos are checked before single button rules");
            String rp = read(new File(dir, "RemapPrefs.java"));
            check(rp.contains("ed.remove(ComboTable.PREF)") && rp.contains("liveCombos"), "RESET ALL REMAPS clears the combos, and the master switch turns them off");
            check(home.contains("stickVol.onMotion(") && home.contains("stickVol.onKey(e)") && ps.contains("stickVol.onMotion(") && ps.contains("stickVol.held()"), "stick volume is wired into Visor and the service");
            check(ps.contains("sticksOn || stickVol.held()"), "the service asks for joystick events only while the menu is open or the hold key is down");
            check(ps.contains("InputDevice.SOURCE_TRACKBALL") && ps.contains("setSources(info, rest)"), "the service keeps a placeholder motion source so the feature set does not change when the hold key goes down, with a fallback");
            int sk = ps.indexOf("stickVol.onKey(e)");
            check(sk > 0 && sk < ps.indexOf("RemapLearnView lv = RemapLearnView.learning;"), "the service tracks the stick volume hold key before anything can return early");
            int hk = home.indexOf("stickVol.onKey(e);");
            check(hk > 0 && hk < home.indexOf("if (preview != null) {", hk - 200) + 1 && home.contains("stickVol.release();"), "Visor tracks the hold key before the preview or a test can use the key, and lets go when it pauses");
            String sv = read(new File(dir, "StickVolInput.java"));
            check(sv.contains("HoldKey") && !sv.contains("down == held"), "stick volume uses HoldKey: held while down, let go when up (not a toggle)");
            String tv = read(new File(dir, "TutorialView.java"));
            java.util.regex.Matcher bm = java.util.regex.Pattern.compile("BODY = (\\d+)f").matcher(tv);
            check(bm.find() && Integer.parseInt(bm.group(1)) >= 36 && !tv.contains("1000 - 40"), "the tutorial body text is at least 36 design pixels and the controller note is not drawn at the bottom");
            // README and the adb docs (2.11): every doc is a clickable link, every deck_cmd name is documented, the script is safe
            File docsDir = new File(deck, "../docs");
            String readme = read(new File(deck, "../README.md"));
            StringBuilder unlinked = new StringBuilder();
            String[] mds = docsDir.list();
            if (mds != null) for (String f : mds) if (f.endsWith(".md") && !readme.contains("](docs/" + f + ")")) unlinked.append(' ').append(f);
            check(unlinked.length() == 0, "README links every doc in docs/ as a clickable link:" + unlinked);
            String adb = read(new File(docsDir, "adb-commands.md"));
            StringBuilder missing = new StringBuilder();
            for (int i = 1; i < DeckCmd.NAMES.length; i++) if (!adb.contains("| " + DeckCmd.NAMES[i] + " |") || !readme.contains("| " + DeckCmd.NAMES[i] + " |")) missing.append(' ').append(DeckCmd.NAMES[i]);
            check(missing.length() == 0, "docs/adb-commands.md and the README table both list every deck_cmd name:" + missing);
            File sa = new File(deck, "tools/setup-all.sh");
            check(sa.isFile(), "tools/setup-all.sh exists");
            if (sa.isFile()) {
                String t = read(sa);
                check(t.contains("TVIP:PORT looks wrong") && t.contains("--disable-google-launcher"), "setup-all.sh validates the address and keeps the Google TV launcher step behind its own flag");
                int dis = t.indexOf("pm disable-user");
                check(dis > 0 && t.lastIndexOf("DISABLE_GTV", dis) > t.lastIndexOf("== google launcher", dis) - 400, "the Google TV launcher is only disabled inside the flag's branch");
                for (String c : new String[]{"GET_USAGE_STATS allow", "enable-ps-overlay.sh", "WRITE_SECURE_SETTINGS", "enable-screensaver.sh", "add-role-holder --user 0 android.app.role.HOME", "set-home-activity"})
                    check(t.contains(c) && adb.contains(c), "setup-all.sh runs " + c + " and the adb doc lists it");
            }
            check(readme.contains("setup-all.sh") && readme.contains("docs/adb-commands.md"), "the README points to the script and to the adb doc");
            String pr = read(new File(dir, "PerfRunner.java"));
            check(home.contains("PerfRunner.consider(") && ps.contains("PerfRunner.consider(") && pr.contains("TaskSource.kill(") && !pr.contains("forceStop"), "performance mode runs from Visor and from the service and only uses the background kill");
            check(read(new File(dir, "CcHost.java")).contains("PerfRunner.exitGame(") && CcModel.byId("q.exitgame") != null && CcModel.byId("d.perf") != null, "the control center has EXIT GAME and the performance mode toggle");
            check(DeckCmd.parse("exit_game") == DeckCmd.EXIT_GAME && home.contains("case DeckCmd.EXIT_GAME:"), "exit_game is handled");
            check(read(new File(dir, "PadTestView.java")).contains("PadOutline.") && !read(new File(dir, "PadTestView.java")).contains("ControllerModel.outline("), "the flat gamepad test draws the controller outlines");
            String gt = read(new File(docsDir, "gamepad-test.md"));
            for (String d : new String[]{"tutorial.md", "combos.md", "stick-volume.md", "adb-commands.md", "performance-mode.md"}) check(new File(deck, "../docs/" + d).isFile(), "docs/" + d + " exists");
        } catch (IOException e) {
            check(false, "reading sources failed: " + e);
        }
    }
}
