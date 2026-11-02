package com.nokoding.deck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/**
 * 2.8 guards that read the app sources, the manifest, the helper scripts and the docs (tools/test.sh passes
 * -Ddeck.src). They keep what cannot be unit tested in step with the pure logic that can.
 */
final class Guards28 {
    private Guards28() {}

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void run() {
        section("2.8 guards: manifest, service config, hidden API, commands, scripts, docs");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            File deck = new File(dir, "../../../..");
            String mf = read(new File(deck, "AndroidManifest.xml"));
            check(mf.contains("<uses-permission android:name=\"android.permission.KILL_BACKGROUND_PROCESSES\"") && !mf.contains("<uses-permission android:name=\"android.permission.FORCE_STOP_PACKAGES\"")
                    && mf.contains("<uses-permission android:name=\"android.permission.WRITE_SECURE_SETTINGS\"") && !mf.contains("<uses-permission android:name=\"android.permission.INTERNET\""),
                    "KILL_BACKGROUND_PROCESSES and WRITE_SECURE_SETTINGS (so adb pm grant works) are asked for, FORCE_STOP_PACKAGES and INTERNET are not");
            String svc = read(new File(deck, "res/xml/ps_service.xml"));
            check(svc.contains("android:canRetrieveWindowContent=\"true\"") && svc.contains("typeWindowStateChanged") && !svc.contains("typeAllMask") && !svc.contains("typeWindowContentChanged"),
                    "the accessibility config reads window content (for FORCE STOP) but still listens for window state changes only");
            // hidden API reflection lives in exactly one class
            String[] files = new File(dir).list();
            boolean only = true;
            String where = "";
            if (files != null) for (String f : files) {
                if (!f.endsWith(".java") || f.equals("DisplayControl.java")) continue;
                String t = read(new File(dir, f));
                if (t.contains("android.os.ServiceManager") || t.contains("IWindowManager")) { only = false; where += " " + f; }
            }
            check(only, "hidden API reflection (ServiceManager, IWindowManager) is only in DisplayControl:" + where);
            String dc = read(new File(dir, "DisplayControl.java"));
            check(dc.contains("catch (Throwable") && dc.contains("static String apply(") && dc.contains("static String restore("), "DisplayControl catches everything and reports a reason");
            // restore does not read saved data
            int at = dc.indexOf("static String restore(");
            String restore = dc.substring(at, dc.indexOf("\n    }\n", at));
            check(!restore.contains("getString") && !restore.contains("DisplayState"), "restore never reads the saved values");
            // deck_cmd restore_display
            String home = read(new File(dir, "HomeActivity.java"));
            check(home.contains("DeckCmd.RESTORE_DISPLAY") && home.contains("restoreNow()") && !home.contains("Runtime.getRuntime().exec"), "HomeActivity handles restore_display early and starts no shell");
            check(home.indexOf("DeckCmd.RESTORE_DISPLAY") < home.indexOf("DeckCmd.RESET_REMAP"), "restore_display is read before the other commands, so it works in safe mode");
            // Deck starting restores a left over change
            check(home.contains("startupRestore()") && read(new File(dir, "PsService.java")).contains("startupRestore()"), "Visor and the service both undo a left over display change when they start");
            // no package name reaches a shell string without validation
            String ts = read(new File(dir, "TaskSource.java"));
            check(ts.contains("KillCmd.validPackage(pkg)"), "kill and app info check the package name first");
            String ps = read(new File(dir, "PsService.java"));
            check(ps.contains("KillCmd.validPackage(pkg)") && ps.contains("ForceStopMatcher.state("), "force stop checks the package name and uses the matcher");
            // control center host: every tile id handled or listed as live only
            String ch = read(new File(dir, "CcHost.java"));
            StringBuilder miss = new StringBuilder();
            for (int s = 0; s < CcModel.COUNT; s++) for (CcModel.Tile t : CcModel.defaults(s)) {
                if (t.kind == CcModel.HEADER || t.kind == CcModel.SLIDER) continue;
                String q = "\"" + t.id + "\"";
                String qm = read(new File(dir, "QuickMenu.java"));
                String hm = home;
                if (!ch.contains(q) && !qm.contains(q) && !hm.contains(q)) miss.append(' ').append(t.id);
            }
            check(miss.length() == 0, "every button and toggle tile has a handler:" + miss);
            // scripts and docs
            for (String sct : new String[]{"force-stop.sh", "game-profile.sh"}) {
                File f = new File(deck, "tools/" + sct);
                check(f.isFile(), sct + " exists");
                if (!f.isFile()) continue;
                String t = read(f);
                check(t.contains("set -euo pipefail") && t.contains("TVIP:PORT") && t.contains("[[ \"$PKG\" =~"), sct + " is strict, takes TVIP:PORT and validates the package");
            }
            String gp = read(new File(deck, "tools/game-profile.sh"));
            check(gp.contains("wm size reset") && gp.contains("wm density reset") && gp.contains("cmd game reset") && gp.contains("-ge 640") && gp.contains("-ge 360"), "game-profile.sh restores, and checks the minimum size");
            File docs = new File(deck, "../docs");
            for (String d : new String[]{"control-center.md", "task-manager.md", "game-profiles.md", "touchpad-volume.md"}) {
                check(new File(docs, d).isFile(), "docs/" + d + " exists");
            }
            File gpd = new File(docs, "game-profiles.md");
            if (gpd.isFile()) {
                String t = read(gpd);
                check(t.contains(DisplayState.DOC_RESTORE) && t.contains("Roblox") && t.contains("RAM") && t.contains("wm size reset"), "game-profiles.md has the restore line, the Roblox note and the RAM point");
            }
            File tvd = new File(docs, "touchpad-volume.md");
            if (tvd.isFile()) {
                String t = read(tvd);
                check(t.contains("fallback") || t.contains("FALLBACK") || t.contains("Fallback"), "touchpad-volume.md describes the fallback");
            }
        } catch (IOException e) {
            check(false, "reading sources failed: " + e);
        }
    }
}
