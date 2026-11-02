package com.nokoding.deck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/**
 * 2.7 guards that read the app sources, the manifest, the helper script and the docs (tools/test.sh
 * passes -Ddeck.src). They keep the pieces that cannot be unit tested in step with the logic that can.
 */
final class Guards27 {
    private Guards27() {}

    static void run() {
        scriptAndDocs();
        deckCmdSource();
        rowHelp27();
        manifestGuards();
        examplePack();
    }

    static Set<String> names(String... n) { return new HashSet<>(java.util.Arrays.asList(n)); }

    static void scriptAndDocs() {
        section("2.7 the enable script and docs say what the builder says");
        String comp = ScreensaverCmd.component();
        String[] on15 = ScreensaverCmd.on(15);
        // the script and the docs say the same as the builder
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            File tools = new File(dir, "../../../../tools");
            File script = new File(tools, "enable-screensaver.sh");
            check(script.isFile(), "tools/enable-screensaver.sh exists");
            if (script.isFile()) {
                String sc = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);
                check(sc.contains("PKG=" + ScreensaverCmd.PKG) && sc.contains("CLS=" + ScreensaverCmd.CLS) || sc.contains("com.nokoding.deck.DeckDream"), "the script names the same component");
                check(sc.contains("screensaver_components") && sc.contains("screensaver_enabled") && sc.contains("screensaver_activate_on_sleep") && sc.contains("screen_off_timeout"), "the script writes the same four settings");
                check(sc.contains("settings put secure") && sc.contains("settings delete secure screensaver_components"), "the script uses the same commands");
                check(sc.contains("MIN_MIN=" + ScreensaverCmd.MIN_MINUTES) && sc.contains("MAX_MIN=" + ScreensaverCmd.MAX_MINUTES), "the script validates minutes with the same limits (1 to 120)");
                int writes = 0;
                for (String line : sc.split("\n")) if (line.contains("settings put") || line.contains("settings delete")) writes++;
                check(writes <= 8, "the script has no other settings writes (" + writes + " lines)");
                check(sc.contains("^[A-Za-z0-9.-]+:[0-9]{1,5}$"), "the script checks TVIP:PORT like the other scripts");
            }
            File doc = new File(dir, "../../../../../docs/screensaver.md");
            if (doc.isFile()) {
                String dc = new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8);
                boolean docs = true;
                for (String c : on15) docs &= dc.contains(c);
                check(docs && dc.contains(ScreensaverCmd.off()) && dc.contains(comp), "docs/screensaver.md lists the same commands as the builder");
            } else {
                check(false, "docs/screensaver.md exists");
            }
        } catch (IOException e) {
            check(false, "reading the script or docs failed: " + e);
        }
    }

    static void deckCmdSource() {
        section("2.7 deck_cmd: the new commands are handled");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            String home = new String(Files.readAllBytes(new File(dir, "HomeActivity.java").toPath()), StandardCharsets.UTF_8);
            check(home.contains("case DeckCmd.RELOAD_WALLPAPERS:") && home.contains("case DeckCmd.PREVIEW_SAVER:"), "HomeActivity handles both new commands");
            String root = new String(Files.readAllBytes(new File(dir, "DreamRoot.java").toPath()), StandardCharsets.UTF_8);
            check(!home.contains("Runtime.getRuntime().exec") && !root.contains("exec("), "no shell is started by either command");
        } catch (IOException e) {
            check(false, "reading sources failed: " + e);
        }
    }

    static void rowHelp27() {
        section("2.7 row help for the new rows");
        String[] settings = {"CLOCK SECONDS", "SECOND CLOCK ZONE", "WALLPAPER", "WALLPAPER OPTIONS", "RELOAD WALLPAPERS", "WALLPAPER FOLDER", "WALLPAPER STATUS", "SCREENSAVER"};
        boolean ok = true;
        String miss = "";
        for (String l : settings) if (RowHelp.of("settings", l) == null) { ok = false; miss += " " + l; }
        check(ok, "every new LOOK row has a sentence:" + miss);
        String[] saver = {"SCREENSAVER STYLE", "SCREENSAVER DIM", "SCREENSAVER MOTION", "SCREENSAVER STATS", "PREVIEW SCREENSAVER", "SCREENSAVER STATUS", "OPEN ANDROID SCREENSAVER SETTINGS", "SET AS SYSTEM SCREENSAVER"};
        ok = true;
        miss = "";
        for (String l : saver) if (RowHelp.of("saver", l) == null) { ok = false; miss += " " + l; }
        check(ok, "every row of the screensaver screen has a sentence:" + miss);
        String[] cmds = {"NOW", "1. CHOOSE VISOR AS THE SCREENSAVER", "2. TURN SCREENSAVERS ON", "3. START IT WHEN THE TV IS IDLE", "OPTIONAL: START AFTER 15 MINUTES", "FROM A PHONE (TERMUX)", "UNDO", "TRY IT NOW"};
        ok = true;
        miss = "";
        for (String l : cmds) if (RowHelp.of("savercmds", l) == null) { ok = false; miss += " " + l; }
        check(ok, "every row of the screensaver commands screen has a sentence:" + miss);
        check(!RowHelp.of("savercmds", "NOW").equals(RowHelp.of("homecmds", "NOW")) && !RowHelp.of("savercmds", "UNDO").equals(RowHelp.of("settings", "UNDO STEP 3")), "the shared labels mean something else here");
        check(!RowHelp.of("savercmds", "FROM A PHONE (TERMUX)").equals(RowHelp.of("install", "FROM A PHONE (TERMUX)")), "the Termux row on this screen has its own sentence");
        // option rows: every common option, and the pack's own
        AnimSpec all = WallpaperOptions.build("T", WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"a.gif\"}", names("a.gif"), "T"));
        ok = true;
        miss = "";
        for (AnimOption o : all.options) {
            String id = WallpaperOptions.isCommon(o.key) ? "wp." + o.key : "wp.custom";
            if (RowHelp.of("wpopts", id) == null) { ok = false; miss += " " + o.key; }
        }
        check(ok && all.options.size() == 7, "every wallpaper option row (the seven of an animated pack) has a sentence:" + miss);
        check(RowHelp.of("wpopts", "wp.custom") != null && RowHelp.of("wpopts", "PACK") != null && RowHelp.of("wpopts", "RESET OPTIONS") != null, "a pack's own option, the PACK row and RESET OPTIONS too");
        check(RowHelp.covers("saver") && RowHelp.covers("savercmds") && RowHelp.covers("wpopts"), "the three new screens show help");
        check(RowHelp.tab("LOOK").contains("wallpaper") && RowHelp.tab("LOOK").contains("screensaver"), "the LOOK tab sentence mentions the new things");
        // the new Panel kinds are real, and the help for them is in the source
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            String home = new String(Files.readAllBytes(new File(dir, "HomeActivity.java").toPath()), StandardCharsets.UTF_8);
            check(home.contains("new Panel(this, \"saver\"") && home.contains("new Panel(this, \"savercmds\"") && home.contains("new Panel(this, \"wpopts\""), "HomeActivity builds the three new screens");
            for (String l : settings) check(home.contains("Row(\"" + l + "\")"), "the source has a row " + l);
        } catch (IOException e) {
            check(false, "reading sources failed: " + e);
        }
    }

    static void manifestGuards() {
        section("2.7 manifest and version guards");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            File deck = new File(dir, "../../../..");
            String mf = new String(Files.readAllBytes(new File(deck, "AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
            String build = new String(Files.readAllBytes(new File(deck, "tools/build.sh").toPath()), StandardCharsets.UTF_8);
            check(!mf.contains("android.permission.INTERNET"), "no INTERNET permission");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<uses-permission android:name=\"([^\"]+)\"").matcher(mf);
            ArrayList<String> perms = new ArrayList<>();
            while (m.find()) perms.add(m.group(1));
            check(perms.size() == 7 && perms.contains("android.permission.WRITE_SECURE_SETTINGS") && perms.contains("android.permission.KILL_BACKGROUND_PROCESSES") && perms.contains("android.permission.ACCESS_NETWORK_STATE") && perms.contains("android.permission.REQUEST_DELETE_PACKAGES") && perms.contains("android.permission.QUERY_ALL_PACKAGES")
                    && perms.contains("android.permission.VIBRATE") && perms.contains("android.permission.PACKAGE_USAGE_STATS"), "the five permissions of 2.6 plus KILL_BACKGROUND_PROCESSES (2.8) and WRITE_SECURE_SETTINGS (2.15, needed for adb pm grant), nothing else: " + perms);
            int services = mf.split("<service", -1).length - 1;
            check(services == 2 && mf.contains("android:name=\".PsService\"") && mf.contains("android:name=\".DeckDream\""), "two services: PsService and the new DeckDream");
            int at = mf.indexOf("android:name=\".DeckDream\"");
            String svc = at < 0 ? "" : mf.substring(at, mf.indexOf("</service>", at));
            check(svc.contains("android:permission=\"android.permission.BIND_DREAM_SERVICE\"") && svc.contains("android:exported=\"true\"") && svc.contains("<action android:name=\"android.service.dreams.DreamService\" />")
                    && svc.contains("android:name=\"android.service.dream\"") && svc.contains("android:resource=\"@xml/deck_dream\""), "DeckDream is guarded by BIND_DREAM_SERVICE and has the dream intent filter and metadata");
            check(new File(deck, "res/xml/deck_dream.xml").isFile(), "the dream metadata file exists");
            check(mf.split("<activity", -1).length == 2 && !mf.contains("<receiver") && !mf.contains("<provider"), "still one activity, no receiver and no provider");
            java.util.regex.Matcher vc = java.util.regex.Pattern.compile("android:versionCode=\"(\\d+)\"").matcher(mf), vn = java.util.regex.Pattern.compile("android:versionName=\"([^\"]+)\"").matcher(mf);
            check(vc.find() && vn.find(), "the manifest has a version");
            check(build.contains("--version-code " + vc.group(1) + " --version-name " + vn.group(1)), "build.sh and the manifest agree on the version " + vc.group(1) + " / " + vn.group(1));
            check(!mf.contains("<queries") || !mf.contains("DREAM_SETTINGS"), "no queries entry was added for the dream settings page");
        } catch (IOException e) {
            check(false, "reading the manifest failed: " + e);
        }
    }

    static void examplePack() {
        section("2.7 the example wallpaper pack in docs/examples/wallpaper-example");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        try {
            File ex = new File(dir, "../../../../../docs/examples/wallpaper-example");
            check(ex.isDirectory(), "the example pack folder exists");
            if (!ex.isDirectory()) return;
            WallpaperSpec s = Wallpapers.read(ex);
            check(s.error == null && s.type == WallpaperSpec.LAYERS && s.layers.size() == 6, "it reads as a layered pack of six layers: " + s.error);
            check(s.options.options.size() == 5, "with five of its own options, one of each type (and one extra INT_RANGE)");
            boolean types = true;
            int[] seen = new int[5];
            for (AnimOption o : s.options.options) seen[o.type]++;
            for (int t : seen) types &= t >= 1;
            check(types, "CHOICE, INT_RANGE, FLOAT_RANGE, BOOL and COLOR are all used");
            long total = 0;
            boolean png = true;
            for (File f : ex.listFiles()) {
                total += f.length();
                if (f.getName().endsWith(".png")) {
                    byte[] b = Files.readAllBytes(f.toPath());
                    png &= b.length > 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
                }
            }
            check(total < 300 * 1024, "the whole pack is under 300 KB (" + (total / 1024) + " KB)");
            check(png, "every .png is a PNG");
            // the layers' when and @ references all point at options that exist
            AnimSpec o = WallpaperOptions.build("Example", s);
            int shown = 0;
            o.load(null);
            for (WallpaperSpec.Layer l : s.layers) if (WallpaperSpec.layerShows(l, o)) shown++;
            check(shown == 5, "with the default mood five of the six layers show (the dusk sky is hidden)");
            o.get("mood").parse("Dusk");
            shown = 0;
            for (WallpaperSpec.Layer l : s.layers) if (WallpaperSpec.layerShows(l, o)) shown++;
            check(shown == 5, "and with mood Dusk five show again (the night sky is hidden)");
            // zip it the way the docs say and install it
            File base = LogicTests.tempDir();
            File packs = new File(base, "wallpapers");
            packs.mkdirs();
            ArrayList<Object> entries = new ArrayList<>();
            for (File f : ex.listFiles()) { entries.add(f.getName()); entries.add(Files.readAllBytes(f.toPath())); }
            File z = LogicTests.zip(packs, "Neon Example.zip", entries.toArray());
            ArrayList<PackZip.Result> rs = Wallpapers.installZips(packs);
            check(rs.size() == 1 && rs.get(0).ok && rs.get(0).name.equals("Neon Example") && z.isFile(), "zipped, it installs as Neon Example: " + rs);
            check(Wallpapers.read(new File(packs, "Neon Example")).error == null, "and the installed copy reads");
        } catch (IOException e) {
            check(false, "reading the example failed: " + e);
        }
    }
}
