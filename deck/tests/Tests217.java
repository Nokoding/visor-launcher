package com.nokoding.deck;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.17 tests. Called from Tests29.run (by reflection). */
final class Tests217 {
    private Tests217() {}

    static void run() {
        tutorialNeverTraps();
        gameBundle();
        homeRemap();
        padTest();
        themes();
        models();
        video();
        bundledModels();
        gyroIntro();
    }

    static void tutorialNeverTraps() {
        section("2.17 tutorial: no try step can trap the player");
        final int R1 = 103, L1 = 102;
        for (int start = 0; start < TutorialModel.count(); start++) {
            int page = start, presses = 0;
            TutorialModel.TryState st = newState(page);
            while (page < TutorialModel.count() - 1 && presses < 12) {
                presses++;
                if (st != null) st.onKey(R1, true);
                boolean eaten = st != null && st.consumes(R1);
                if (st != null) st.onKey(R1, false);
                if (!eaten) { page++; st = newState(page); }
            }
            check(page == TutorialModel.count() - 1, "pressing R1 from page " + (start + 1) + " reaches the last page in " + presses + " presses");
        }
        TutorialModel.TryState s = new TutorialModel.TryState(new TutorialModel.Try(TutorialModel.COMBO, new int[]{L1, R1}, "A", "B"));
        check(s.consumes(R1) && !s.consumes(R1), "a waiting key is swallowed once, then passes");
        s.reset();
        s.skip();
        check(s.done() && !s.consumes(R1), "SKIP TRY completes the step and nothing is swallowed after");
        check(!s.consumes(97), "a key the step does not wait for is never swallowed");
    }

    private static TutorialModel.TryState newState(int page) {
        TutorialModel.Lesson l = TutorialModel.get(page);
        return l != null && l.tryIt != null && l.tryIt.kind != TutorialModel.NONE ? new TutorialModel.TryState(l.tryIt) : null;
    }

    static void gameBundle() {
        section("2.17 game bundle: performance mode and volume per game");
        check(GameBundle.perfRuns(GameBundle.PERF_ON, false) && !GameBundle.perfRuns(GameBundle.PERF_OFF, true)
                && GameBundle.perfRuns(GameBundle.PERF_FOLLOW, true) && !GameBundle.perfRuns(GameBundle.PERF_FOLLOW, false), "ON forces, OFF blocks, FOLLOW leaves it to the global mode");
        check(GameBundle.volumeIndexFor(50, 15) == 8 && GameBundle.volumeIndexFor(100, 15) == 15 && GameBundle.volumeIndexFor(0, 15) == 0
                && GameBundle.volumeIndexFor(-1, 15) == -1 && GameBundle.volumeIndexFor(50, 0) == -1 && GameBundle.volumeIndexFor(500, 15) == -1, "percent to stream index, never above the max, none gives -1");
        check(GameBundle.volumeIndex(-1) == 0 && GameBundle.VOLUMES[GameBundle.volumeIndex(60)] == 60 && GameBundle.VOLUMES[GameBundle.volumeIndex(63)] == 60 && GameBundle.VOLUMES[GameBundle.volumeIndex(99)] == 100, "row index of a volume");
        check(GameBundle.volumeAction("", "com.game", true, false) == 1, "a game with a volume comes to the front: set it");
        check(GameBundle.volumeAction("", "com.game", false, false) == 0, "a game without a volume: nothing");
        check(GameBundle.volumeAction("com.game", "com.other", false, false) == -1, "another app takes over: put the old volume back");
        check(GameBundle.volumeAction("com.game", "com.game", true, false) == 0, "the same game again: nothing more");
        check(GameBundle.volumeAction("com.game", "com.nokoding.deck", false, true) == 0 && GameBundle.volumeAction("com.game", "com.android.systemui", false, true) == 0, "Visor's menu or the system bar over the game does not count as leaving");
        check(GameBundle.volumeAction("com.game", null, false, false) == 0 && GameBundle.volumeAction(null, "", true, false) == 0, "no front app: nothing");
        GameProfiles gp = new GameProfiles();
        GameProfiles.Profile p = new GameProfiles.Profile("com.x.game");
        p.perf = GameBundle.PERF_ON;
        p.volume = 40;
        check(gp.put(p), "a profile with a bundle is valid");
        GameProfiles back = GameProfiles.parse(gp.save());
        GameProfiles.Profile q = back.get("com.x.game");
        check(q != null && q.perf == GameBundle.PERF_ON && q.volume == 40, "the bundle survives save and parse");
        GameProfiles.Profile plain = new GameProfiles.Profile("com.x.plain");
        gp.put(plain);
        check(!gp.save().contains("\"perf\":0") && GameProfiles.parse(gp.save()).get("com.x.plain").volume == GameBundle.VOL_NONE, "defaults are not written and read back as defaults");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"perf\":7}]}").count() == 0, "a bad perf drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"vol\":101}]}").count() == 0, "a volume over 100 drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\",\"vol\":\"loud\"}]}").count() == 0, "a text volume drops the entry");
        check(GameProfiles.parse("{\"v\":1,\"profiles\":[{\"pkg\":\"com.a.b\"}]}").get("com.a.b").perf == GameBundle.PERF_FOLLOW, "older saved profiles read as FOLLOW GLOBAL, no volume");
        GameProfiles.Profile bad = new GameProfiles.Profile("com.x.bad");
        bad.volume = 300;
        check(!new GameProfiles().put(bad), "an invalid volume is not accepted");
    }

    static void homeRemap() {
        section("2.17 Home in advanced remap: guards");
        long home = RemapKey.pack(3, 0, 0, 0);
        check(RemapKey.cls(home) == RemapKey.ADVANCED && RemapKey.remappable(home), "Home is an advanced button now");
        LogicTests.MapSink sink = new LogicTests.MapSink();
        check(RemapTable.save(sink, home, new RemapTable.Rule(RemapAction.SCREENSHOT, "", RemapAction.DEFAULT, "", "")), "a Home rule can be saved");
        RemapTable t = sink.table();
        int adv = RemapGate.F_ADVANCED, hm = RemapGate.F_HOME, sv = RemapGate.F_SERVICE;
        RemapGate.Decision d = new RemapGate.Decision();
        String self = "com.nokoding.deck", app = "com.netflix.ninja";
        RemapGate.decide(d, home, RemapGate.P_DOWN, app, self, t, adv | hm | sv, null, RemapGate.ESC_IDLE);
        check(d.consume, "with ADVANCED, the Home warning and the service, Home is taken");
        RemapGate.decide(d, home, RemapGate.P_DOWN, app, self, t, adv | sv, null, RemapGate.ESC_IDLE);
        check(!d.consume, "without the Home warning Home is never taken");
        RemapGate.decide(d, home, RemapGate.P_DOWN, app, self, t, adv | hm, null, RemapGate.ESC_IDLE);
        check(!d.consume, "without the service (Visor's own window) Home is never taken");
        RemapGate.decide(d, home, RemapGate.P_DOWN, app, self, t, hm | sv, null, RemapGate.ESC_IDLE);
        check(!d.consume, "without ADVANCED REMAP Home is never taken");
        RemapGate g = new RemapGate(self);
        g.table = t;
        g.flags = adv | hm | sv;
        long now = 1000;
        check(g.onKey(home, true, 0, now, app) == RemapGate.ACT && g.act == RemapAction.SCREENSHOT, "a Home press runs its action");
        g.onKey(home, false, 0, now + 30, app);
        int passes = 0;
        for (int i = 1; i <= 4; i++) {
            now += 300;
            int r = g.onKey(home, true, 0, now, app);
            if (r == RemapGate.PASS) passes++;
            g.onKey(home, false, 0, now + 30, app);
        }
        check(passes >= 1 && g.takeHomeEscape() && !g.takeHomeEscape(), "five quick Home presses give Home back and report it once");
        check(g.onKey(home, true, 0, now + 5000, app) == RemapGate.PASS, "after the Home escape Home passes");
        g.reset();
        check(g.onKey(home, true, 0, now + 9000, app) != RemapGate.PASS, "reset brings the rule back");
        RemapGate slow = new RemapGate(self);
        slow.table = t;
        slow.flags = adv | hm | sv;
        long n2 = 1000;
        boolean anyPass = false;
        for (int i = 0; i < 8; i++) {
            n2 += 2500;   // slower than the escape window
            anyPass |= slow.onKey(home, true, 0, n2, app) == RemapGate.PASS;
            slow.onKey(home, false, 0, n2 + 30, app);
        }
        check(!anyPass && !slow.takeHomeEscape(), "slow presses never trigger the Home escape");
        // clearing
        LogicTests.MapSink s2 = new LogicTests.MapSink();
        RemapTable.save(s2, home, new RemapTable.Rule(RemapAction.NOTHING, "", RemapAction.DEFAULT, "", ""));
        s2.putBool(RemapTable.KEY_HOME, true);
        RemapTable.clearNav(s2, s2.m);
        check(s2.table().get(home) == null && !s2.flag(RemapTable.KEY_HOME), "the Back escape (clearNav) also restores Home");
        RemapTable.save(s2, home, new RemapTable.Rule(RemapAction.NOTHING, "", RemapAction.DEFAULT, "", ""));
        s2.putBool(RemapTable.KEY_HOME, true);
        RemapTable.resetAll(s2, s2.m);
        check(s2.table().get(home) == null && !s2.flag(RemapTable.KEY_HOME), "RESET ALL REMAPS restores Home and turns the Home switch off");
        RemapTable.save(s2, home, new RemapTable.Rule(RemapAction.NOTHING, "", RemapAction.DEFAULT, "", ""));
        s2.putBool(RemapTable.KEY_HOME, true);
        RemapTable.clearAdvanced(s2, s2.m);
        check(s2.table().get(home) == null && !s2.flag(RemapTable.KEY_HOME), "TURN OFF ADVANCED REMAP restores Home too");
    }

    static void padTest() {
        section("2.17 gamepad test, Steam style: hold B, skip, report");
        PadTest.ExitHold h = new PadTest.ExitHold();
        h.setEast(true, 100);
        check(!h.done(1599) && h.done(1600) && Math.abs(h.progress(850) - 0.5f) < 1e-6f, "B: the ring fills over 1.5 s");
        h.setEast(false, 900);
        check(!h.holding() && !h.done(5000), "a tap of B does not exit");
        h.setEast(true, 2000);
        h.setEast(true, 2100);
        check(h.done(3500), "key repeats do not restart the B hold");
        PadTest.State st = new PadTest.State();
        st.setType(PadTest.PLAYSTATION);
        check(st.skipNext() >= 0 && st.skippedCount() == 1 && st.count() == 1, "SKIP NEXT counts one button as done and marks it skipped");
        int guard = 0;
        while (st.skipNext() >= 0 && guard++ < 40) { }
        check(st.complete() && st.count() == st.total() && st.skippedCount() == st.total() && st.nextWanted() == -1, "skipping everything finishes the run");
        st.reset();
        check(st.skippedCount() == 0 && st.count() == 0 && st.nextWanted() >= 0, "RESET clears the skips");
        st.setFull(false);
        check(st.skipNext() == -1, "QUICK TEST has nothing to skip");
        st.setFull(true);
        int first = st.nextWanted();
        st.set(first, true, PadTest.State.SRC_KEY);
        check(st.nextWanted() != first && !st.skipped(first), "a pressed button is not skipped");
    }

    static void themes() {
        section("2.17 themes: file format, contrast, folder");
        ThemeSpec t = ThemeSpec.of("Neon Tea", 0xFFFF2E97, 0xFFA855F7, 0xFF07040D, 0xFF120A21, 0xFFF4EEFF);
        check(t.name.equals("NEON TEA") && t.bg1 != 0 && t.dim != 0, "a theme works out its other two colours and cleans its name");
        ThemeSpec back = ThemeSpec.parse(t.toJson());
        check(back != null && back.sameColors(t) && back.name.equals("NEON TEA"), "save and parse round trip");
        check(ThemeSpec.parse("{\"format\":1,\"colors\":{\"accent\":\"ff2e97\",\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\",\"text\":\"#F4EEFF\"}}") != null, "the minimum file reads, a hex without # too");
        String[] bad = {null, "", "nope", "[]", "{}", "{\"format\":2,\"colors\":{}}", "{\"format\":\"1\",\"colors\":{}}", "{\"format\":1.5,\"colors\":{}}",
                "{\"format\":1}", "{\"format\":1,\"colors\":5}",
                "{\"format\":1,\"colors\":{\"accent\":\"#GGGGGG\",\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\",\"text\":\"#F4EEFF\"}}",
                "{\"format\":1,\"colors\":{\"accent\":16711680,\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\",\"text\":\"#F4EEFF\"}}",
                "{\"format\":1,\"colors\":{\"accent\":\"#FF2E97\",\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\"}}",
                "{\"format\":1,\"colors\":{\"accent\":\"#FF2E97\",\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\",\"text\":\"#F4EEFF\",\"dim\":\"red\"}}",
                "{\"format\":1,\"colors\":{\"accent\":\"#FFFFFFF\",\"accent2\":\"#A855F7\",\"background\":\"#07040D\",\"panel\":\"#120A21\",\"text\":\"#F4EEFF\"}}"};
        boolean allNull = true;
        for (String b : bad) allNull &= ThemeSpec.parse(b) == null;
        check(allNull, "bad, wrong type, missing and hostile files give null, never an exception");
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 4000; i++) deep.append('[');
        check(ThemeSpec.parse(deep.toString()) == null, "deeply nested text gives null");
        StringBuilder big = new StringBuilder("{\"format\":1,\"name\":\"");
        for (int i = 0; i < ThemeSpec.MAX_FILE; i++) big.append('x');
        check(ThemeSpec.parse(big.append("\"}").toString()) == null, "a file over 8 KB is refused");
        check(ThemeSpec.safeName("../../etc/passwd").equals("ETCPASSWD") || !ThemeSpec.safeName("../../etc/passwd").contains("/"), "a path in a name loses its slashes");
        check(ThemeSpec.safeName(null).equals("CUSTOM") && ThemeSpec.safeName("***").equals("CUSTOM") && ThemeSpec.safeName("a very long theme name that goes on and on").length() <= ThemeSpec.MAX_NAME, "empty and long names are fixed");
        check(ThemeSpec.cleanName("THEME 1") && !ThemeSpec.cleanName("..") && !ThemeSpec.cleanName("a/b") && !ThemeSpec.cleanName(" X") && !ThemeSpec.cleanName(""), "file base names are checked");
        check(ThemeSpec.parseHex("#FF2E97") == 0xFFFF2E97 && ThemeSpec.parseHex("ff2e97") == 0xFFFF2E97 && ThemeSpec.parseHex("#FF2E9") == 0 && ThemeSpec.parseHex(null) == 0 && ThemeSpec.hex(0xFFFF2E97).equals("#FF2E97"), "hex in and out");
        check(Math.abs(ThemeSpec.contrast(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01 && Math.abs(ThemeSpec.contrast(0xFF123456, 0xFF123456) - 1.0) < 1e-9, "contrast: black on white 21, same colour 1");
        check(ThemeSpec.of("x", 0xFFFF2E97, 0xFFA855F7, 0xFF07040D, 0xFF120A21, 0xFFF4EEFF).warning() == null, "the SYNTHWAVE colours are readable");
        ThemeSpec hard = ThemeSpec.of("x", 0xFFFF2E97, 0xFFA855F7, 0xFF202020, 0xFF120A21, 0xFF303030);
        check(hard.warning() != null && hard.warning().contains("TEXT"), "dark grey text on a dark background warns");
        ThemeSpec faint = ThemeSpec.of("x", 0xFF101018, 0xFFA855F7, 0xFF07040D, 0xFF120A21, 0xFFF4EEFF);
        check(faint.warning() != null && faint.warning().contains("ACCENT"), "an accent that melts into the background warns");
        check(ThemeSpec.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080 && ThemeSpec.mix(0xFF102030, 0xFFFFFFFF, 0f) == 0xFF102030, "mix");
        // migration of a seven colour theme keeps every colour
        ThemeSpec old = ThemeSpec.full("CUSTOM", 0xFFFF2E97, 0xFFA855F7, 0xFF07040D, 0xFF170A2C, 0xFF120A21, 0xFFF4EEFF, 0xFFA99DC6);
        ThemeSpec mig = ThemeSpec.parse(old.toJson());
        check(mig != null && mig.sameColors(old), "an existing seven colour theme is carried over exactly");
        // the folder
        java.io.File dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("decktheme").toFile();
            check(ThemeStore.list(dir).isEmpty() && ThemeStore.list(null).isEmpty() && ThemeStore.list(new java.io.File(dir, "nope")).isEmpty(), "an empty or missing folder lists nothing");
            check(ThemeStore.export(dir, t) == null && ThemeStore.list(dir).size() == 1 && ThemeStore.list(dir).get(0).sameColors(t), "export writes a file that lists and reads back");
            check(ThemeStore.export(dir, t) == null && ThemeStore.list(dir).size() == 1, "exporting the same name again replaces it");
            java.nio.file.Files.write(new java.io.File(dir, "BROKEN.json").toPath(), "{nope".getBytes("UTF-8"));
            java.nio.file.Files.write(new java.io.File(dir, "notes.txt").toPath(), "x".getBytes("UTF-8"));
            java.nio.file.Files.write(new java.io.File(dir, "HUGE.json").toPath(), new byte[ThemeSpec.MAX_FILE + 10]);
            check(ThemeStore.list(dir).size() == 1, "broken, oversize and other files are skipped");
            check(ThemeStore.freeName(dir).equals("THEME 1"), "free name");
            ThemeStore.export(dir, ThemeSpec.of("THEME 1", 0xFFFFFFFF, 0xFF00FF00, 0xFF000000, 0xFF111111, 0xFFFFFFFF));
            check(ThemeStore.freeName(dir).equals("THEME 2"), "free name skips a used one");
            for (java.io.File f : dir.listFiles()) f.delete();
            dir.delete();
        } catch (java.io.IOException e) {
            check(false, "temp folder: " + e);
        }
    }

    static void models() {
        section("2.17 3D models: built-in parts and the OBJ files");
        float[] n = new float[3];
        for (int t : new int[]{PadTest.PLAYSTATION, PadTest.XBOX}) {
            ControllerModel m = new ControllerModel(t);
            // every hardware part faces outward from its own centre
            java.util.HashMap<Integer, float[]> sum = new java.util.HashMap<>();
            for (int f = 0; f < m.faces.length; f++) {
                if (m.group[f] != 2) continue;
                float[] s = sum.get(m.part[f]);
                if (s == null) { s = new float[4]; sum.put(m.part[f], s); }
                for (int k : m.faces[f]) { s[0] += m.verts[k * 3]; s[1] += m.verts[k * 3 + 1]; s[2] += m.verts[k * 3 + 2]; s[3]++; }
            }
            boolean out = true;
            for (int f = 0; f < m.faces.length; f++) {
                if (m.group[f] != 2) continue;
                float[] s = sum.get(m.part[f]);
                float cx = s[0] / s[3], cy = s[1] / s[3], cz = s[2] / s[3];
                float fx = 0, fy = 0, fz = 0;
                for (int k : m.faces[f]) { fx += m.verts[k * 3]; fy += m.verts[k * 3 + 1]; fz += m.verts[k * 3 + 2]; }
                int c = m.faces[f].length;
                ControllerModel.normal(m.verts, m.faces[f], n);
                out &= n[0] * (fx / c - cx) + n[1] * (fy / c - cy) + n[2] * (fz / c - cz) > 0;
            }
            check(out && sum.size() == 6, PadTest.TYPE_NAMES[t] + ": two grips, two bumpers and two triggers, every face points out of its part (" + sum.size() + " parts)");
            boolean b1 = false, b2 = false, home = false;
            for (int b : m.button) { b1 |= b == PadTest.L1; b2 |= b == PadTest.R2; home |= b == PadTest.HOME; }
            check(b1 && b2 && home, PadTest.TYPE_NAMES[t] + ": bumpers, triggers and the home mark carry their buttons");
            float minY = 0, maxX = 0, minZ = 0, maxZ = 0;
            for (int i = 0; i < m.vertexCount; i++) {
                minY = Math.min(minY, m.verts[i * 3 + 1]);
                maxX = Math.max(maxX, Math.abs(m.verts[i * 3]));
                minZ = Math.min(minZ, m.verts[i * 3 + 2]);
                maxZ = Math.max(maxZ, m.verts[i * 3 + 2]);
            }
            check(maxX * 2 > 14.5f && maxX * 2 < 17f && (maxZ - minZ) > 10f && (maxZ - minZ) < 14f && -minY > 2.5f, PadTest.TYPE_NAMES[t] + ": the size is near the real pad (" + maxX * 2 + " wide, " + (maxZ - minZ) + " deep, grips down to " + minY + ")");
        }
        String cube = "v -1 -1 -1\nv 1 -1 -1\nv 1 1 -1\nv -1 1 -1\nv -1 -1 1\nv 1 -1 1\nv 1 1 1\nv -1 1 1\n"
                + "f 1 2 3 4\nf 5 8 7 6\nf 1 5 6 2\nf 3 7 8 4\nf 1 4 8 5\nf 2 6 7 3\n";
        ControllerModel m = ModelObj.parse(PadTest.PLAYSTATION, "# a cube\n" + cube);
        check(m != null && m.fromFile && m.faces.length == 12 && m.vertexCount == 8 && m.topFace >= 0, "a good OBJ reads");
        ControllerModel mm = ModelObj.parse(PadTest.XBOX, cube.replace("v -1 -1 -1", "v -100 -100 -100").replace("v 1 1 1", "v 100 100 100"));
        check(mm != null && Math.abs(mm.verts[0] + 10f) < 1e-3f && Math.abs(mm.verts[18] - 10f) < 1e-3f, "a model bigger than 40 is taken as millimetres");
        check(ModelObj.parse(PadTest.XBOX, cube.replace("f 1 2 3 4", "f 1/1/1 2/2/2 3/3/3 4/4/4")) != null, "slash indices use the first number");
        String[] bad = {null, "", "nothing", "v 1 2 3\n", "v 1 2 3\nv 1 2 3\nv 1 2 3\nv 1 2 3\n", cube + "f 1 2 99\n", cube + "f 1 2 -1\n", cube + "f 1 2\n", cube.replace("v 1 -1 -1", "v NaN 0 0"),
                cube.replace("v 1 -1 -1", "v 1e99 0 0"), cube + "f 1 2 3 4 5 6 7 8 1\n", cube + "f a b c\n", cube.replace("v 1 1 1", "v 1 1"), cube.replace("v 1 -1 -1", "v 9999 0 0")};
        boolean allBad = true;
        for (String b : bad) allBad &= ModelObj.parse(PadTest.XBOX, b) == null;
        check(allBad, "bad numbers, bad or missing indices, no faces, too big: all give null");
        StringBuilder many = new StringBuilder(cube);
        for (int i = 0; i < ModelObj.MAX_TRIS; i++) many.append("f 1 2 3\n");
        check(ModelObj.parse(PadTest.XBOX, many.toString()) == null, "over the triangle cap is refused");
        StringBuilder verts = new StringBuilder();
        for (int i = 0; i < ModelObj.MAX_VERTS + 1; i++) verts.append("v 1 1 1\n");
        check(ModelObj.parse(PadTest.XBOX, verts + "f 1 2 3\n") == null, "over the vertex cap is refused");
        check(ModelObj.fileFor(PadTest.PLAYSTATION).equals("dualsense.obj") && ModelObj.fileFor(PadTest.XBOX).equals("xbox-one.obj") && ModelObj.fileFor(PadTest.GENERIC) == null, "file names per controller");
        try {
            java.io.File dir = java.nio.file.Files.createTempDirectory("deckmodel").toFile();
            check(ModelObj.load(dir, PadTest.PLAYSTATION) == null && ModelObj.load(null, PadTest.PLAYSTATION) == null && ModelObj.forType(dir, PadTest.PLAYSTATION).faces.length > 20, "no file gives the built-in model");
            java.nio.file.Files.write(new java.io.File(dir, "dualsense.obj").toPath(), cube.getBytes("UTF-8"));
            check(ModelObj.load(dir, PadTest.PLAYSTATION) != null && ModelObj.forType(dir, PadTest.PLAYSTATION).fromFile && !ModelObj.forType(dir, PadTest.XBOX).fromFile, "the file replaces only its own controller (no bundled opener set)");
            java.nio.file.Files.write(new java.io.File(dir, "xbox-one.obj").toPath(), "garbage".getBytes("UTF-8"));
            check(!ModelObj.forType(dir, PadTest.XBOX).fromFile, "a bad file falls back to the built-in model");
            java.nio.file.Files.write(new java.io.File(dir, "xbox-one.obj").toPath(), new byte[ModelObj.MAX_BYTES + 5]);
            check(ModelObj.load(dir, PadTest.XBOX) == null, "a file over 1.5 MB is not read");
            for (java.io.File f : dir.listFiles()) f.delete();
            dir.delete();
        } catch (java.io.IOException e) {
            check(false, "temp folder: " + e);
        }
    }

    static void video() {
        section("2.17 video wallpapers: limits, math, wallpaper.json, pack sizes");
        long MB = 1024 * 1024;
        check(VideoLimits.check(1280, 720, 0, 20000, 5_000_000, 30, 10 * MB) == null, "1280x720, 30 fps, 5 Mbit/s, 10 MB is allowed");
        check(VideoLimits.check(1920, 1080, 0, 20000, 5_000_000, 30, 10 * MB) != null, "1080p is refused");
        check(VideoLimits.check(720, 1280, 0, 20000, 5_000_000, 30, 10 * MB) != null && VideoLimits.check(720, 1280, 90, 20000, 5_000_000, 30, 10 * MB) == null, "a portrait file turned by its rotation is judged after the turn");
        check(VideoLimits.check(1280, 720, 0, 20000, 5_000_000, 60, 10 * MB) != null, "60 fps is refused");
        check(VideoLimits.check(1280, 720, 0, 20000, 5_000_000, 30.2, 10 * MB) == null && VideoLimits.check(1280, 720, 0, 20000, 6_200_000, 0, 10 * MB) == null, "encoder rounding (30.2 fps, 6.2 Mbit/s) is allowed");
        check(VideoLimits.check(1280, 720, 0, 20000, 9_000_000, 30, 10 * MB) != null, "9 Mbit/s is refused");
        check(VideoLimits.check(1280, 720, 0, 20000, 5_000_000, 30, 41 * MB) != null && VideoLimits.check(1280, 720, 0, 20000, 5_000_000, 30, 0) != null, "over 40 MB and empty files are refused");
        check(VideoLimits.check(1280, 720, 0, 100, 5_000_000, 30, MB) != null && VideoLimits.check(1280, 720, 0, 400_000, 5_000_000, 30, MB) != null, "too short and over 5 minutes are refused");
        check(VideoLimits.check(0, 0, 0, 20000, 0, 0, MB) != null && VideoLimits.check(-5, 720, 0, 20000, 0, 0, MB) != null, "an unreadable size is refused");
        check(VideoLimits.check(640, 360, 0, 20000, 0, 0, MB) == null, "unknown bitrate and frame rate are not held against a small video");
        check(VideoLimits.parseLong(null, 7) == 7 && VideoLimits.parseLong("12", 7) == 12 && VideoLimits.parseLong("x", 7) == 7 && VideoLimits.parseDouble("NaN", 1) == 1 && VideoLimits.parseDouble("29.97", 1) == 29.97, "header numbers parse safely");
        float[] o = new float[4];
        check(VideoMath.transform(o, 1920, 1080, 1280, 720, 1.5, -100, -50) && Math.abs(o[0] - 1.0f) < 1e-5f && Math.abs(o[1] - 1.0f) < 1e-5f && o[2] == -100f && o[3] == -50f, "a 720p video at 1.5x fills 1080p with no stretch");
        check(!VideoMath.transform(o, 0, 1080, 1280, 720, 1, 0, 0) && !VideoMath.transform(o, 1920, 1080, 1280, 720, Double.NaN, 0, 0) && !VideoMath.transform(null, 1, 1, 1, 1, 1, 0, 0), "bad sizes give no transform");
        int[] sz = new int[2];
        VideoMath.rotated(1280, 720, 90, sz);
        check(sz[0] == 720 && sz[1] == 1280, "rotation swaps the sides");
        java.util.HashSet<String> files = new java.util.HashSet<>(java.util.Arrays.asList("loop.mp4", "a.png"));
        WallpaperSpec v = WallpaperSpec.parse("{\"format\":1,\"type\":\"video\",\"file\":\"loop.mp4\"}", files, "p");
        check(v != null && v.type == WallpaperSpec.VIDEO && v.file.equals("loop.mp4") && v.error == null, "type video with an mp4 reads");
        WallpaperSpec bad1 = WallpaperSpec.parse("{\"format\":1,\"type\":\"image\",\"file\":\"loop.mp4\"}", files, "p");
        WallpaperSpec bad2 = WallpaperSpec.parse("{\"format\":1,\"type\":\"video\",\"file\":\"a.png\"}", files, "p");
        WallpaperSpec bad3 = WallpaperSpec.parse("{\"format\":1,\"type\":\"video\",\"file\":\"../loop.mp4\"}", files, "p");
        check(bad1.error != null && bad2.error != null && bad3.error != null, "a video in an image pack, a picture as a video and a path in the name are refused");
        check(PackZip.WALLPAPERS.capFor("LOOP.MP4") == 40 * MB && PackZip.WALLPAPERS.capFor("a.png") == 8 * MB && PackZip.WALLPAPERS.totalFor(true) == 48 * MB && PackZip.WALLPAPERS.totalFor(false) == 24 * MB, "caps: 40 MB for an mp4, 8 MB for an image, 48 MB with a video, 24 MB without");
        check(PackZip.isMedia("loop.mp4", PackZip.WALLPAPERS) && !PackZip.isMedia("loop.mp4", PackZip.ICONS), "mp4 is a wallpaper file only");
    }

    static void bundledModels() {
        section("2.18 the real controller models that ship in the app (assets/models, VMD)");
        String dir = System.getProperty("deck.src");
        if (dir == null) return;
        int[] pads = {PadTest.PLAYSTATION, PadTest.XBOX};
        String[] names = {"dualsense", "xbox-one"};
        int[] minTris = {100000, 40000};
        final java.io.File assets = new java.io.File(dir, "../../../../assets/models");
        for (int i = 0; i < 2; i++) {
            for (int light = 0; light < 2; light++) {
                String file = names[i] + (light == 1 ? "-light" : "") + ".vmd";
                try {
                    byte[] data = java.nio.file.Files.readAllBytes(new java.io.File(assets, file).toPath());
                    ControllerModel m = ModelObj.parseVmd(pads[i], data);
                    check(m != null && m.fromFile && m.faceN != null, file + " reads");
                    if (m == null) continue;
                    if (light == 0) check(m.faces.length >= minTris[i], file + " keeps every triangle of the source: " + m.faces.length);
                    else check(m.faces.length >= 11000 && m.faces.length <= 12100, file + " is the light version: " + m.faces.length + " triangles");
                    float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
                    for (int v = 0; v < m.vertexCount; v++) {
                        minX = Math.min(minX, m.verts[v * 3]); maxX = Math.max(maxX, m.verts[v * 3]);
                        minY = Math.min(minY, m.verts[v * 3 + 1]); maxY = Math.max(maxY, m.verts[v * 3 + 1]);
                        minZ = Math.min(minZ, m.verts[v * 3 + 2]); maxZ = Math.max(maxZ, m.verts[v * 3 + 2]);
                    }
                    float w = maxX - minX, th = maxY - minY, dp = maxZ - minZ;
                    check(w > 15f && w < 16.6f && th > 6f && th < 9.5f && dp > 8.5f && dp < 11.5f, file + " has the real size in cm: " + w + " wide, " + th + " thick, " + dp + " deep");
                    check(Math.abs((minX + maxX) / 2) < 0.2f && Math.abs((minY + maxY) / 2) < 0.2f && Math.abs((minZ + maxZ) / 2) < 0.2f, file + " is centred");
                    java.util.HashSet<Integer> ids = new java.util.HashSet<>();
                    for (int b : m.button) if (b >= 0) ids.add(b);
                    check(ids.size() >= 10, file + " tags the faces of its buttons (" + ids.size() + " different buttons)");
                    int up = 0, upN = 0, down = 0, downN = 0;
                    for (int f = 0; f < m.faces.length; f++) {
                        float cy = m.faceC[f * 3 + 1];
                        if (cy > maxY - 0.4f) { upN++; if (m.faceN[f * 3 + 1] > 0) up++; }
                        if (cy < minY + 0.4f) { downN++; if (m.faceN[f * 3 + 1] < 0) down++; }
                    }
                    check(upN > 20 && up > upN * 0.5 && downN > 20 && down > downN * 0.5, file + " faces point outward (" + up + "/" + upN + " on top, " + down + "/" + downN + " underneath)");
                    float len = 0;
                    for (int f = 0; f < Math.min(2000, m.faces.length); f++) len += Math.abs(m.faceN[f * 3] * m.faceN[f * 3] + m.faceN[f * 3 + 1] * m.faceN[f * 3 + 1] + m.faceN[f * 3 + 2] * m.faceN[f * 3 + 2] - 1f);
                    check(len < 40f, file + ": the stored face normals have length 1");
                    check(data.length < (light == 0 ? 1700 : 300) * 1024, file + " stays small: " + data.length / 1024 + " KB");
                } catch (java.io.IOException e) {
                    check(false, "reading " + file + " failed: " + e);
                }
            }
            final java.io.File af = assets;
            ModelObj.bundled = name -> new java.io.FileInputStream(new java.io.File(af, name));
            ModelObj.lightDetail = false;
            ControllerModel full = ModelObj.loadBundled(pads[i]);
            ModelObj.lightDetail = true;
            ControllerModel lite = ModelObj.loadBundled(pads[i]);
            ModelObj.lightDetail = false;
            check(full != null && lite != null && full.faces.length > lite.faces.length * 3, names[i] + ": the detail switch picks the full or the light model");
            check(ModelObj.forType(null, pads[i]).fromFile && ModelObj.describe(null, pads[i]).startsWith("BUNDLED MODEL"), names[i] + ": forType prefers the shipped model to the hand made one, and the status says so");
            ModelObj.bundled = null;
            check(!ModelObj.forType(null, pads[i]).fromFile, names[i] + ": without the opener the hand made model is the last resort");
        }
        check(ModelObj.parseVmd(PadTest.XBOX, null) == null && ModelObj.parseVmd(PadTest.XBOX, new byte[10]) == null && ModelObj.parseVmd(PadTest.XBOX, "VMD1xxxxxxxxxxxxxxxx".getBytes()) == null, "bad VMD bytes give null");
        byte[] ok = new byte[16 + 4 * 6 + 1 * 6 + 1];
        ok[0] = 'V'; ok[1] = 'M'; ok[2] = 'D'; ok[3] = '1';
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(ok).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.position(4); bb.putInt(4); bb.putInt(1); bb.putFloat(0.0005f);
        bb.position(16 + 24); bb.putShort((short) 0); bb.putShort((short) 1); bb.putShort((short) 2);
        check(ModelObj.parseVmd(PadTest.XBOX, ok) != null, "a tiny good VMD reads");
        bb.position(16 + 24); bb.putShort((short) 0); bb.putShort((short) 1); bb.putShort((short) 9);
        check(ModelObj.parseVmd(PadTest.XBOX, ok) == null, "a triangle that uses a missing vertex is refused");
        check(ModelObj.parseVmd(PadTest.XBOX, java.util.Arrays.copyOf(ok, ok.length - 1)) == null, "a cut VMD is refused");
    }

    static void gyroIntro() {
        section("2.18 the one-time gyro introduction");
        check(GyroIntro.shouldOffer(false, false, true, true), "first time, home showing, a gyro controller: offered");
        check(!GyroIntro.shouldOffer(true, false, true, true), "never twice");
        check(!GyroIntro.shouldOffer(false, true, true, true), "it waits for the first run tutorial");
        check(!GyroIntro.shouldOffer(false, false, false, true), "not while something else is open or Visor is not in front");
        check(!GyroIntro.shouldOffer(false, false, true, false), "not without a controller that has a gyro");
        check(GyroIntro.LINES.length == 2 && GyroIntro.LINES[0].length() < 100 && GyroIntro.LINES[1].length() < 110, "the welcome lines fit on one row each");
    }
}
