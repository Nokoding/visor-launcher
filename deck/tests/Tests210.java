package com.nokoding.deck;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.10 tests: stick volume. Called from Tests29.run (by reflection). */
final class Tests210 {
    private Tests210() {}

    static void run() {
        section("2.10 stick volume: spinning the stick");
        // a full clockwise turn in 5 degree moves, starting at angle 0, radius 0.9
        for (int sp = 0; sp < StickVol.SPEED_DEG.length; sp++) {
            StickVol v = new StickVol();
            v.config(sp, false);
            int total = 0;
            long t = 1000;
            for (int a = 0; a <= 360; a += 5) total += v.feed((float) (0.9 * Math.cos(Math.toRadians(a))), (float) (0.9 * Math.sin(Math.toRadians(a))), t += 20);
            int want = 360 / StickVol.SPEED_DEG[sp];   // float rounding may leave the last step just short
            check(total >= want - 1 && total <= want, "a full clockwise turn at " + StickVol.speedName(sp) + " gives about " + want + " steps (" + total + ")");
        }
        StickVol v = new StickVol();
        int cw = 0, ccw = 0;
        long t = 5000;
        for (int a = 0; a <= 180; a += 5) cw += v.feed((float) (0.9 * Math.cos(Math.toRadians(a))), (float) (0.9 * Math.sin(Math.toRadians(a))), t += 20);
        v.reset();
        for (int a = 0; a >= -180; a -= 5) ccw += v.feed((float) (0.9 * Math.cos(Math.toRadians(a))), (float) (0.9 * Math.sin(Math.toRadians(a))), t += 20);
        check(cw > 0 && ccw < 0 && cw == -ccw, "clockwise is louder, counter clockwise is quieter, the same amount (" + cw + ", " + ccw + ")");
        StickVol inv = new StickVol();
        inv.config(StickVol.SPEED_DEFAULT, true);
        int iv = 0;
        for (int a = 0; a <= 180; a += 5) iv += inv.feed((float) (0.9 * Math.cos(Math.toRadians(a))), (float) (0.9 * Math.sin(Math.toRadians(a))), t += 20);
        check(iv == -cw, "invert flips the direction (" + iv + ")");
        // a flick does nothing
        StickVol fl = new StickVol();
        int f = fl.feed(0f, 0f, 100) + fl.feed(0.9f, 0f, 120) + fl.feed(0.95f, 0.05f, 140) + fl.feed(0f, 0f, 200);
        check(f == 0, "a quick push and release moves nothing");
        // the stick resting near the centre does nothing
        int rest = 0;
        for (int i = 0; i < 50; i++) rest += fl.feed(0.1f * (i % 2), 0.1f, 1000 + i * 10);
        check(rest == 0, "a stick that rests near the middle moves nothing");
        // bad input
        check(v.feed(Float.NaN, 0.5f, 10) == 0 && v.feed(0.5f, Float.POSITIVE_INFINITY, 10) == 0, "NaN and infinite input are ignored");
        // jump cap: a stick that teleports through large angles never gives more than 4
        StickVol j = new StickVol();
        j.config(3, false);
        j.feed(0.9f, 0f, 100);
        int big = j.feed(0f, 0.9f, 110);   // 90 degrees at 30 per step would be 3, still inside the cap
        check(Math.abs(big) <= StickVol.MAX_JUMP, "one call never gives more than " + StickVol.MAX_JUMP + " steps");
        // bad config falls back
        StickVol b = new StickVol();
        b.config(99, false);
        b.config(-3, true);
        check(StickVol.speedName(99).equals("NORMAL") && StickVol.speedName(0).equals("SLOW") && StickVol.SPEED_NAMES.length == StickVol.SPEED_DEG.length, "speed names and the fallback");
        // reset in the middle of a turn
        StickVol r = new StickVol();
        int a1 = 0;
        for (int a = 0; a <= 50; a += 5) a1 += r.feed((float) (0.9 * Math.cos(Math.toRadians(a))), (float) (0.9 * Math.sin(Math.toRadians(a))), 100 + a * 5);
        r.reset();
        int a2 = r.feed(0.9f, 0f, 900);
        check(a2 == 0, "after a reset a stick that is already out starts a new turn and gives nothing at once");
        holdKey();
        padOutline();
        perfMode();
    }

    static void holdKey() {
        section("2.11 hold key: held while down, let go when it comes up");
        HoldKey h = new HoldKey();
        check(!h.held(0), "not held at the start");
        check(h.press(100) && h.held(150), "pressing holds it");
        check(!h.press(200) && h.held(250), "a second down while held changes nothing");
        check(h.release() && !h.held(300), "letting go lets go at once, it is not a toggle");
        check(!h.release(), "a release when not held changes nothing");
        // press, release, press, release: always exactly the press and release states
        boolean ok = true;
        long t = 1000;
        for (int i = 0; i < 6; i++) {
            h.press(t += 10);
            ok &= h.held(t + 1);
            h.release();
            ok &= !h.held(t + 2);
        }
        check(ok, "six press and release rounds each hold and then let go");
        // a lost up: the key goes stale
        h.press(5000);
        check(h.held(5000 + HoldKey.STALE_MS) && !h.held(5000 + HoldKey.STALE_MS + 1), "a key whose up never came is let go after " + HoldKey.STALE_MS + " ms");
        check(!h.held(5000 + 10), "and stays let go");
        // activity keeps it alive
        h.press(10000);
        for (int i = 1; i <= 5; i++) h.touch(10000 + i * 10000L);
        check(h.held(10000 + 5 * 10000L + 1000), "stick activity while held keeps it from going stale");
        h.release();
        h.touch(99999);
        check(!h.held(100000), "touch does not hold a key that is up");
        // clock going backwards
        h.press(50000);
        check(h.held(100) && !h.held(100 + HoldKey.STALE_MS + 1), "a clock that jumps back does not keep it for ever");
        h.press(1);
        h.reset();
        check(!h.held(2), "reset lets go");
    }

    static void padOutline() {
        section("2.12 controller outlines for the flat gamepad test");
        int[] layouts = {PadTest.PLAYSTATION, PadTest.XBOX};
        for (int l : layouts) {
            String n = l == PadTest.XBOX ? "Xbox" : "DualSense";
            float[] g = PadOutline.segments(l), st = PadOutline.start(l);
            check(g.length % 6 == 0 && g.length >= 36 && st.length == 2, n + " outline is a start point and whole curve segments");
            // the body ends on the centre line, so the mirror closes cleanly
            check(g[g.length - 2] == 0f && st[0] == 0f, n + " outline starts and ends on the centre line");
            float[] pts = PadOutline.sample(l, 12);
            float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
            for (int i = 0; i < pts.length; i += 2) { minX = Math.min(minX, pts[i]); maxX = Math.max(maxX, pts[i]); minY = Math.min(minY, pts[i + 1]); maxY = Math.max(maxY, pts[i + 1]); }
            check(minX > -400 && maxX < 400 && minY > -300 && maxY < 300, n + " body fits the test area (" + minX + " to " + maxX + ", " + minY + " to " + maxY + ")");
            check(Math.abs(minX + maxX) < 1e-3, n + " body is mirror symmetric");
        }
        // every button the test places on the body is inside it (shoulders sit above it, so they are not checked here)
        float[][] ps = {{-243, -106}, {244, -106}, {-243, -154}, {-243, -58}, {-291, -106}, {-195, -106}, {187, -106}, {301, -106}, {244, -163}, {244, -49},
                {-125, 1}, {125, 1}, {-181, -190}, {181, -190}, {0, -12}, {0, -155}};
        boolean ok = true;
        for (float[] p : ps) ok &= PadOutline.inside(PadTest.PLAYSTATION, p[0], p[1]);
        check(ok, "every DualSense button position is inside the outline");
        float[][] xb = {{-99, 37}, {-99, -9}, {-99, 83}, {-145, 37}, {-53, 37}, {209, -92}, {209, -147}, {209, -37}, {154, -92}, {264, -92}, {-197, -90}, {99, 27}, {-57, -92}, {55, -92}, {0, -175}};
        ok = true;
        for (float[] p : xb) ok &= PadOutline.inside(PadTest.XBOX, p[0], p[1]);
        check(ok, "every Xbox button position is inside the outline");
        check(!PadOutline.inside(PadTest.PLAYSTATION, 0, 200) && !PadOutline.inside(PadTest.XBOX, 0, 200) && !PadOutline.inside(PadTest.PLAYSTATION, 0, -400), "the gap between the grips and the space above the body are outside");
        check(PadOutline.inside(PadTest.PLAYSTATION, -300, 200) && PadOutline.inside(PadTest.XBOX, 280, 200), "the grips are inside");
        check(PadOutline.rings(PadTest.PLAYSTATION).length == 12 && PadOutline.rings(PadTest.XBOX).length == 18 && PadOutline.shoulders().length == 20
                && PadOutline.rects(PadTest.PLAYSTATION).length == 15 && PadOutline.rects(PadTest.XBOX).length == 0 && PadOutline.lines(PadTest.PLAYSTATION).length == 8 && PadOutline.lines(PadTest.XBOX).length == 0,
                "the detail lists have the expected sizes (a touchpad, create and options slits and light bar for the DualSense, the stick wells and the logo for the Xbox)");
        check(PadOutline.xbox(PadTest.XBOX) && !PadOutline.xbox(PadTest.PLAYSTATION) && !PadOutline.xbox(PadTest.SWITCH), "only the Xbox layout gets the Xbox outline");
    }

    static void perfMode() {
        section("2.12 performance mode: when it runs, what it closes, what EXIT GAME closes");
        String self = "com.nokoding.deck", g = "com.roblox.client";
        check(PerfMode.shouldRun(PerfMode.GAMES, true, false, g, self, 1000, null, 0), "GAMES runs for a game");
        check(!PerfMode.shouldRun(PerfMode.GAMES, false, true, "com.netflix.ninja", self, 1000, null, 0), "GAMES does not run for an app that is not a game, even if chosen");
        check(PerfMode.shouldRun(PerfMode.CHOSEN, false, true, "com.netflix.ninja", self, 1000, null, 0) && !PerfMode.shouldRun(PerfMode.CHOSEN, true, false, g, self, 1000, null, 0), "CHOSEN APPS runs only for chosen apps");
        check(!PerfMode.shouldRun(PerfMode.OFF, true, true, g, self, 1000, null, 0) && !PerfMode.shouldRun(99, true, true, g, self, 1000, null, 0) && !PerfMode.shouldRun(-1, true, true, g, self, 1000, null, 0), "OFF and a bad mode never run");
        check(!PerfMode.shouldRun(PerfMode.GAMES, true, false, self, self, 1000, null, 0) && !PerfMode.shouldRun(PerfMode.GAMES, true, false, null, self, 1000, null, 0)
                && !PerfMode.shouldRun(PerfMode.GAMES, true, false, "a b;c", self, 1000, null, 0), "never for Visor, nothing, or a bad package name");
        check(!PerfMode.shouldRun(PerfMode.GAMES, true, false, g, self, 5000, g, 1000), "the same game again within the cool down does not close everything twice");
        check(PerfMode.shouldRun(PerfMode.GAMES, true, false, g, self, 1000 + PerfMode.COOLDOWN_MS, g, 1000), "after the cool down it runs again");
        check(PerfMode.shouldRun(PerfMode.GAMES, true, false, g, self, 5000, "com.other.game", 4000), "another game right after runs");
        check(PerfMode.shouldRun(PerfMode.GAMES, true, false, g, self, 100, g, 5000), "a clock that went backwards does not block it");
        java.util.ArrayList<String> cand = new java.util.ArrayList<>(java.util.Arrays.asList("com.netflix.ninja", g, self, "com.spotify.tv.android", "com.netflix.ninja", null, "bad name", "com.keep.me", "com.x.y"));
        java.util.HashSet<String> prot = new java.util.HashSet<>(java.util.Arrays.asList("com.keep.me"));
        java.util.List<String> t = PerfMode.targets(cand, g, self, prot);
        check(t.equals(java.util.Arrays.asList("com.netflix.ninja", "com.spotify.tv.android", "com.x.y")), "targets drop the game, Visor, protected apps, duplicates, null and bad names: " + t);
        check(PerfMode.targets(null, g, self, null).isEmpty() && PerfMode.targets(new java.util.ArrayList<String>(), g, self, prot).isEmpty(), "no candidates, nothing to close");
        java.util.ArrayList<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) many.add("com.app.n" + i);
        check(PerfMode.targets(many, g, self, null).size() == PerfMode.MAX_TARGETS, "the list is cut to " + PerfMode.MAX_TARGETS);
        check(g.equals(PerfMode.exitTarget(g, null, self)) && g.equals(PerfMode.exitTarget(null, g, self)) && g.equals(PerfMode.exitTarget(self, g, self)), "EXIT GAME closes the app in front, or the remembered game when Visor is in front");
        check(PerfMode.exitTarget(self, null, self) == null && PerfMode.exitTarget(null, null, self) == null && PerfMode.exitTarget(null, "bad name", self) == null && PerfMode.exitTarget("", self, self) == null, "no game known means nothing to exit, Visor is never the target");
        check(PerfMode.exitTarget("com.other.app", g, self).equals("com.other.app"), "the app in front wins over the remembered game");
        check(PerfMode.summary(0).equals("NOTHING TO CLOSE") && PerfMode.summary(1).equals("CLOSED 1 APP") && PerfMode.summary(7).equals("CLOSED 7 APPS") && PerfMode.summary(-3).equals("NOTHING TO CLOSE"), "the result text");
        check(PerfMode.clampMode(2) == 2 && PerfMode.clampMode(3) == 0 && PerfMode.NAMES.length == 3, "mode values");
    }
}
