package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain JVM tests for Deck's pure logic. Run: tools/test.sh */
public final class LogicTests {
    static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++; else { fail++; System.out.println("  FAIL: " + what); }
    }

    static void section(String s) { System.out.println("[" + s + "]"); }

    public static void main(String[] a) {
        v1Regression();
        pan();
        wheel();
        stick();
        dock();
        sounds();
        synth();
        gyro();
        input();
        fmt();
        haptics();
        hum();
        pointer();
        gyroDirection();
        packZip();
        recents();
        volume();
        deckCmd();
        iconPick();
        playerLeds();
        padTestDetect();
        padTestState();
        exitHold();
        confetti();
        mat3d();
        attitude();
        corners();
        controllerModel();
        fmtFixed();
        iconPackZip();
        iconPrecedence();
        stickRoute();
        sensorClock();
        stillness();
        gyroBias();
        axisMap();
        accelGate();
        gyroDrift();
        gyroCalStore();
        calCheck();
        typeProgress();
        tickCap();
        revealInterrupt();
        animSpec();
        animConfig();
        flyoutTimeline();
        typeSounds();
        psDecide();
        psGate();
        psKill();
        psRules();
        psSetup();
        psStick();
        staleLength();
        frameGate();
        chipFocus();
        revealWakes();
        revealPlan();
        rowFit();
        marquee();
        rowHelp();
        remapKeys();
        remapActions();
        remapStorage();
        remapDecide();
        remapGate();
        remapEscape();
        remapLearn();
        remapHold();
        remapCmd();
        remapHelp();
        Tests27.run();
        Guards27.run();
        Tests28.run();
        Tests29.run();
        System.out.println();
        System.out.println("RESULT: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** Reproduces bugs A and B with the v1 rules, to show the root cause. */
    static void v1Regression() {
        section("v1 regression model (bugs A and B)");
        // v1: near layer spring k=95 zeta=0.9, target = -nx*W*0.036 where nx = focus x from center.
        // On the wheel the focused tile is always at the center, so nx = 0 and the target is 0.
        Spring par = new Spring(null, 95f, 0.9f, 0.2f);
        FrameStats v1Slow = new FrameStats(0.027f, 90); // v1 thresholds
        float dt = 1f / 30f; // a weak GPU drawing at about 30 fps
        int liteAtTick = -1;
        float[] settled = new float[6];
        float firstPeak = 0;
        for (int tick = 1; tick <= 6; tick++) {
            par.target = 0f;                      // nx = 0 on the wheel
            par.kick(-1 * 140f * 3);              // pressing RIGHT kicked the layer LEFT (bug B)
            float peak = 0;
            for (int f = 0; f < 22; f++) {        // about 22 frames per tick at 30 fps
                par.step(dt);
                if (Math.abs(par.value) > Math.abs(peak)) peak = par.value;
                if (v1Slow.sample(dt) && liteAtTick < 0) liteAtTick = tick;
            }
            if (tick == 1) firstPeak = peak;
            settled[tick - 1] = par.value;
        }
        System.out.println("  v1 peak offset per tick " + firstPeak + " px (negative = left), back near " + settled[0]);
        System.out.println("  v1 slow detector switched to lite during tick " + liteAtTick + " (lite zeroed parallax and tilt)");
        check(firstPeak < 0, "v1 moved the background left on a right press");
        check(Math.abs(settled[0]) < 15 && Math.abs(settled[3]) < 15, "v1 always sprang back toward 0: same fixed animation each tick");
        check(liteAtTick >= 3 && liteAtTick <= 5, "v1 lite mode kicked in after about 4 ticks at 30 fps");
        FrameStats v2Slow = new FrameStats(0.040f, 150);
        boolean v2Trip = false;
        for (int i = 0; i < 600; i++) v2Trip |= v2Slow.sample(dt);
        check(!v2Trip, "v2 detector does not trip at a steady 30 fps (and in v2 it never touches pan or tilt)");
    }

    static void settle(PanModel p, double seconds) {
        for (double t = 0; t < seconds; t += 1 / 60.0) p.update(1 / 60.0);
    }

    static void pan() {
        section("pan: cumulative target, lagging spring, wrap");
        PanModel p = new PanModel();
        p.step(1, 0);
        for (int i = 0; i < 30; i++) p.update(1 / 60.0); // 0.5 s
        double frac = p.x / PanModel.STEP_X;
        System.out.println("  one step: camera at " + Math.round(frac * 100) + "% of the way after 0.5 s");
        check(frac > 0.2 && frac < 0.95, "camera lags behind the target (slow catch up)");
        settle(p, 2.0);
        check(Math.abs(p.x - PanModel.STEP_X) < 0.5 && !p.moving, "camera arrives within about 2 s");

        int[] marks = {10, 50, 500};
        PanModel q = new PanModel();
        int done = 0;
        double prevX = 0;
        boolean everStuck = false;
        for (int m : marks) {
            while (done < m) {
                double before = q.x;
                q.step(1, 0);
                settle(q, 2.5);
                if (!(q.x > before + PanModel.STEP_X * 0.98)) everStuck = true;
                done++;
            }
            System.out.println("  after " + m + " steps: target " + q.tx + "  camera " + Math.round(q.x * 100) / 100.0);
            check(Math.abs(q.x - m * PanModel.STEP_X) < 0.5, "camera follows all " + m + " steps (no clamp)");
            check(q.x > prevX, "still moving forward at step " + m);
            prevX = q.x;
        }
        check(!everStuck, "every single step moved the camera a full step (no stall like v1)");
        double period = 2400;
        double w = PanModel.wrap(q.x * 0.3, period);
        check(w >= 0 && w < period, "layer offset wraps into [0, period): " + w);
        check(PanModel.wrap(-1, period) > 2398, "negative pan wraps to the far end");
        check(PanModel.wrap(period * 7, period) == 0, "exact multiples wrap to 0");

        PanModel f = new PanModel();
        for (int i = 0; i < 40; i++) {               // holding right: 20 steps per second for 2 s
            f.step(1, 0);
            for (int k = 0; k < 3; k++) f.update(1 / 60.0);
        }
        double lag = f.tx - f.x;
        System.out.println("  fast spin: target " + f.tx + ", camera trails by " + Math.round(lag) + " px");
        check(lag > PanModel.STEP_X * 5, "background trails behind during a fast spin");
        double t = 0;
        while (f.moving && t < 5) { f.update(1 / 60.0); t += 1 / 60.0; }
        System.out.println("  caught up " + Math.round(t * 100) / 100.0 + " s after release");
        check(t > 0.8 && t < 3.0, "slow catch up after release (about 1 to 2 s)");

        PanModel o = new PanModel();
        o.dir = -1;
        o.step(1, 0);
        settle(o, 3);
        check(o.x < 0, "Opposite setting flips the sign");
        PanModel v = new PanModel();
        for (int i = 0; i < 200; i++) v.step(0, 1);
        check(v.ty < PanModel.Y_LIMIT && v.ty > PanModel.Y_LIMIT * 0.9, "vertical pan soft limited: " + v.ty);
        for (int i = 0; i < 200; i++) v.step(0, -1);
        check(v.ty < 0, "vertical pan comes back");
    }

    static void wheel() {
        section("infinite wheel index math");
        int slots = 9;
        boolean distinctOk = true, countOk = true, centerOk = true;
        float[] positions = {0f, 0.3f, 0.5f, 0.99f, -0.5f, -3.7f, 41.2f, 99999.5f, -123456.25f};
        for (int n = 1; n <= 14; n++) {
            for (float pos : positions) {
                int first = WheelMath.first(pos, n, slots), w = WheelMath.window(n, slots);
                HashSet<Integer> apps = new HashSet<>();
                boolean hasCenter = false;
                for (int v = first; v < first + w; v++) {
                    apps.add(WheelMath.wrap(v, n));
                    if (v == Math.round(pos)) hasCenter = true;
                }
                if (apps.size() != w) distinctOk = false;
                if (w != Math.min(n, slots)) countOk = false;
                if (!hasCenter && Math.abs(pos - Math.round(pos)) < 0.49f) centerOk = false;
            }
        }
        check(distinctOk, "no duplicate apps visible for 1 to 14 apps (no launchable ghosts)");
        check(countOk, "window shows min(apps, slots) tiles");
        check(centerOk, "the focused virtual index is always in the window");
        check(WheelMath.wrap(7 + 1, 8) == 0, "next after the last app is app 1");
        check(WheelMath.wrap(0 - 1, 8) == 7, "previous before app 1 is the last app");
        check(WheelMath.wrap(-17, 5) == 3, "floorMod for negatives");
        int t = 0, n = 6;
        for (int i = 0; i < 25; i++) t++;
        check(WheelMath.wrap(t, n) == 1, "25 nexts on 6 apps lands on index 1");
        int big = 100003, sh = WheelMath.rebase(big, 7);
        check(sh % 7 == 0 && WheelMath.wrap(big - sh, 7) == WheelMath.wrap(big, 7) && Math.abs(big - sh) < 7, "rebase keeps the same app and a small target");
        check(WheelMath.rebase(50, 7) == 0, "no rebase for small targets");
        check(WheelMath.edgeAlpha(0f, 3, slots) == 1f && WheelMath.edgeAlpha(1.5f, 3, slots) == 0f, "edge tiles fade when apps < slots");
    }

    static void stick() {
        section("stick dial with detents (at 200 percent, the 2.1 detent)");
        StickDial d = new StickDial();
        d.detent = StickRoute.detent(200);
        check(Math.abs(d.detent - StickDial.DETENT_2_1) < 1e-9, "200 percent is the 2.1 angle per item");
        int ticks = turn(d, 0, 2 * Math.PI, 64, 0);
        check(ticks == 12, "one clockwise turn = 12 ticks (got " + ticks + ")");
        ticks = turn(d, 0, -2 * Math.PI, 64, 1000);
        check(ticks == -12, "one counter clockwise turn = -12 ticks (got " + ticks + ")");
        ticks = turn(d, Math.toRadians(170), Math.toRadians(260), 30, 2000);
        check(ticks == 3, "crossing the pi seam counts correctly (got " + ticks + ")");
        // direction change: 45 deg forward then 45 back
        d.update(0, 0, 3000);
        int sum = 0;
        for (int i = 0; i <= 15; i++) { double ang = Math.toRadians(i * 3); d.update((float) Math.cos(ang), (float) Math.sin(ang), 3100 + i * 10); sum += d.ticks; }
        int fwd = sum;
        for (int i = 15; i >= 0; i--) { double ang = Math.toRadians(i * 3); d.update((float) Math.cos(ang), (float) Math.sin(ang), 3300 + (15 - i) * 10); sum += d.ticks; }
        check(fwd == 1 && sum == 0, "reverse direction ticks back (fwd " + fwd + ", net " + sum + ")");
        d.update(0, 0, 4000);
        d.update(0.2f, 0.1f, 4010);
        check(!d.active, "inside the 0.35 deadzone nothing happens");
        // flick right
        d.update(0.95f, 0.05f, 5000);
        d.update(0.97f, 0.02f, 5060);
        d.update(0.0f, 0.0f, 5120);
        check(d.flickX == 1 && d.flickY == 0, "quick flick right steps one item");
        // slow hold is not a flick
        d.update(0.0f, 0.9f, 6000);
        d.update(0.0f, 0.9f, 6500);
        d.update(0.0f, 0.0f, 6700);
        check(d.flickX == 0 && d.flickY == 0, "a long hold is not a flick");
        d.update(0, 0, 7000);
    }

    static int turn(StickDial d, double from, double to, int steps, long t0) {
        int ticks = 0;
        for (int i = 0; i <= steps; i++) {
            double ang = from + (to - from) * i / steps;
            d.update((float) Math.cos(ang), (float) Math.sin(ang), t0 + i * 8L);
            ticks += d.ticks;
        }
        d.update(0, 0, t0 + steps * 8L + 300);
        return ticks;
    }

    static void dock() {
        section("dock order");
        List<String> pins = new ArrayList<>();
        List<String> live = Arrays.asList("a/A", "b/B", "c/C", "d/D", "e/E");
        check(OrderList.pin(pins, "a/A", 4, live) == 1, "pin a");
        check(OrderList.pin(pins, "b/B", 4, live) == 1 && OrderList.pin(pins, "c/C", 4, live) == 1 && OrderList.pin(pins, "d/D", 4, live) == 1, "pin b c d");
        check(OrderList.pin(pins, "e/E", 4, live) == -1, "dock full at 4");
        check(OrderList.pin(pins, "a/A", 4, live) == 0, "pinning twice is a no-op");
        check(OrderList.move(pins, "c/C", -1) && pins.toString().equals("[a/A, c/C, b/B, d/D]"), "move left");
        check(!OrderList.move(pins, "a/A", -1), "cannot move past the left edge");
        check(!OrderList.move(pins, "d/D", 1), "cannot move past the right edge");
        String saved = OrderList.join(pins);
        List<String> back = new ArrayList<>();
        OrderList.split(saved, back);
        check(back.equals(pins), "order survives save and load");
        check(OrderList.pin(pins, "e/E", 4, Arrays.asList("a/A", "c/C", "e/E")) == 1, "uninstalled pins do not count toward the limit");
        check(OrderList.unpin(pins, "c/C") && !pins.contains("c/C"), "unpin");
    }

    static void sounds() {
        section("sound pack mapping");
        String[] files = {"move.wav", "Select.OGG", "blip.wav", "boom.mp3", "notes.txt", "launch.mp3"};
        SoundMap m = SoundMap.resolve("mypack", files, null);
        check("move.wav".equals(m.file[SoundMap.MOVE]), "move.wav maps to move");
        check("Select.OGG".equals(m.file[SoundMap.SELECT]), "case insensitive names (Select.OGG)");
        check("launch.mp3".equals(m.file[SoundMap.LAUNCH]), "mp3 works");
        check(m.file[SoundMap.BACK] == null, "missing events fall back to built in");
        check(m.found() == 3 && "mypack".equals(m.name) && m.volume[0] == 1f, "found 3, default name and volume");

        String json = "{\"name\":\"Arcade Pack\",\"map\":{\"move\":\"blip.wav\",\"error\":\"boom.mp3\",\"back\":\"../evil.wav\",\"toggle\":\"nothere.wav\"},"
                + "\"volume\":{\"move\":0.6,\"error\":7,\"select\":-1}}";
        SoundMap j = SoundMap.resolve("folder", files, json);
        check("Arcade Pack".equals(j.name), "pack.json name");
        check("blip.wav".equals(j.file[SoundMap.MOVE]), "pack.json renames move to blip.wav");
        check("boom.mp3".equals(j.file[SoundMap.ERROR]), "pack.json maps error");
        check(j.file[SoundMap.BACK] == null, "path traversal in pack.json is rejected");
        check(j.file[SoundMap.TOGGLE] == null, "mapping to a missing file falls back");
        check(Math.abs(j.volume[SoundMap.MOVE] - 0.6f) < 1e-6 && j.volume[SoundMap.ERROR] == 1f && j.volume[SoundMap.SELECT] == 0f, "volumes parsed and clamped to 0..1");
        SoundMap bad = SoundMap.resolve("p", files, "{not json");
        check("move.wav".equals(bad.file[SoundMap.MOVE]) && "p".equals(bad.name), "broken pack.json is ignored, names still work");
        check(SoundMap.EVENTS.length == 17 && SoundMap.index("pack_loaded") == 12 && SoundMap.index("test_complete") == SoundMap.TEST_COMPLETE
                && SoundMap.index("type_tick") == SoundMap.TYPE_TICK && SoundMap.index("flyout_close") == 16, "17 events (2.2 adds test_complete, 2.3 type_tick, flyout_open, flyout_close)");
        check(SoundMap.BUILTIN_VOL.length == SoundMap.EVENTS.length, "a built in volume for every event");
    }

    static void synth() {
        section("built in synth");
        boolean ok = true;
        for (int e = 0; e < SoundMap.EVENTS.length; e++) {
            short[] p = Synth.pcm(e);
            byte[] w = Synth.wav(p);
            int peak = 0;
            for (short s : p) peak = Math.max(peak, Math.abs(s));
            boolean good = w.length == 44 + p.length * 2 && w[0] == 'R' && w[8] == 'W' && w[36] == 'd'
                    && peak > 2000 && p.length < Synth.RATE / 2;
            if (!good) { ok = false; System.out.println("  bad sound " + SoundMap.EVENTS[e]); }
        }
        check(ok, SoundMap.EVENTS.length + " valid, audible, short WAVs");
    }

    static void gyro() {
        section("gyro sway filter");
        GyroFilter g = new GyroFilter();
        for (int i = 0; i < 120; i++) g.update(5f, -5f, 1 / 60f);
        check(Math.abs(g.rx) <= GyroFilter.MAX && Math.abs(g.ry) <= GyroFilter.MAX && g.rx > 1f, "bounded sway: " + g.rx);
        for (int i = 0; i < 180; i++) g.update(0f, 0f, 1 / 60f);
        check(g.resting(), "decays back to rest after the controller stops: " + g.rx);
        g.update(Float.NaN, 0, 1 / 60f);
        check(!Float.isNaN(g.rx), "NaN input is ignored");
    }

    static void input() {
        section("input mode");
        int keyboard = 0x101, dpad = 0x201;
        check(InputMode.classify(keyboard | dpad, keyboard | dpad, false, false) == InputMode.REMOTE, "remote dpad is REMOTE");
        check(InputMode.classify(keyboard, keyboard, true, false) == InputMode.GAMEPAD, "BUTTON_A is GAMEPAD");
        check(InputMode.classify(InputMode.SRC_JOYSTICK, InputMode.SRC_JOYSTICK | InputMode.SRC_GAMEPAD, false, false) == InputMode.GAMEPAD, "stick motion is GAMEPAD");
        check(InputMode.classify(dpad, dpad | InputMode.SRC_GAMEPAD, false, false) == InputMode.GAMEPAD, "dpad keys from a gamepad device");
        check(InputMode.classify(keyboard, keyboard | InputMode.SRC_GAMEPAD, false, true) == InputMode.REMOTE, "virtual (HDMI CEC or adb) input is REMOTE");
        check(InputMode.style(0x054c) == InputMode.STYLE_SONY && InputMode.style(0x045e) == InputMode.STYLE_XBOX && InputMode.style(0x2dc8) == InputMode.STYLE_GENERIC, "glyph style by vendor id");
    }

    static void haptics() {
        section("haptic pattern table");
        HapticPatterns.Wave r = HapticPatterns.build(HapticPatterns.TICK, false, HapticPatterns.CRISP, 100, true);
        HapticPatterns.Wave l = HapticPatterns.build(HapticPatterns.TICK, true, HapticPatterns.CRISP, 100, true);
        check(r != null && l == null && r.a[0] == 115 && r.t[0] == 8, "crisp wheel tick: right motor only, short and stiff");
        HapticPatterns.Wave soft = HapticPatterns.build(HapticPatterns.TICK, false, HapticPatterns.SOFT, 100, true);
        check(soft.t[0] > r.t[0] && soft.a[0] < r.a[0], "soft is longer and gentler than crisp");
        HapticPatterns.Wave heavyL = HapticPatterns.build(HapticPatterns.TICK, true, HapticPatterns.HEAVY, 100, true);
        check(heavyL != null && heavyL.a[0] == 60, "heavy adds the left motor to right only patterns");
        HapticPatterns.Wave half = HapticPatterns.build(HapticPatterns.SELECT, true, HapticPatterns.CRISP, 50, true);
        check(half.a[0] == 75, "50 percent strength halves amplitude (got " + half.a[0] + ")");
        check(HapticPatterns.build(HapticPatterns.SELECT, true, HapticPatterns.CRISP, 0, true) == null, "strength off is silent");
        HapticPatterns.Wave sel = HapticPatterns.build(HapticPatterns.SELECT, false, HapticPatterns.CRISP, 100, true);
        HapticPatterns.Wave bk = HapticPatterns.build(HapticPatterns.BACK, true, HapticPatterns.CRISP, 100, true);
        check(sel.a[0] > bk.a[0], "press (select) is stronger than back");
        HapticPatterns.Wave err = HapticPatterns.build(HapticPatterns.ERROR, true, HapticPatterns.CRISP, 100, true);
        check(err.t.length == 3 && err.a[1] == 0 && err.a[0] == 200, "error is two strong pulses with a pause");
        HapticPatterns.Wave mo = HapticPatterns.build(HapticPatterns.MENU_OPEN, true, HapticPatterns.CRISP, 100, true);
        boolean rising = true;
        for (int i = 1; i < mo.a.length; i++) if (mo.a[i] < mo.a[i - 1]) rising = false;
        check(rising && mo.total == 56, "menu_open ramps up (crisp: 70 ms scaled to " + mo.total + ")");
        HapticPatterns.Wave dc = HapticPatterns.build(HapticPatterns.SELECT, true, HapticPatterns.CRISP, 100, false);
        boolean binary = true;
        for (int v : dc.a) if (v != 0 && v != 255) binary = false;
        check(binary && dc.total == half.total, "no amplitude control: on/off duty cycle, same total length");
        check(HapticPatterns.L.length == HapticPatterns.NAMES.length && HapticPatterns.R.length == HapticPatterns.NAMES.length
                && HapticPatterns.FOR_SOUND.length == SoundMap.EVENTS.length, "tables line up");
        boolean ranges = true;
        for (int[][] tab : new int[][][]{HapticPatterns.L, HapticPatterns.R})
            for (int[] row : tab) for (int i = 1; i < row.length; i += 2) if (row[i] < 0 || row[i] > 255) ranges = false;
        check(ranges, "all amplitudes in 0..255");
    }

    static void hum() {
        section("wheel hum");
        HumModel m = new HumModel();
        long now = 0;
        int issues = 0, maxAmp = 0, minAmp = 999;
        long lastIssue = -1000, minGap = 999;
        float at250 = -1;
        long startT = -1, fullT = -1;
        for (int f = 0; f < 60; f++) {           // 1 s of steady spinning, a detent every 60 ms
            now = f * 16L + 16;
            if (now % 60 < 16) m.onTick(now);
            m.update(now, 0.016f);
            if (m.level > 0 && startT < 0) startT = now;
            if (m.level >= 0.95f && fullT < 0) fullT = now;
            if (now >= 250 && at250 < 0) at250 = m.level;
            int a = m.poll(now);
            if (a > 0) {
                issues++;
                maxAmp = Math.max(maxAmp, a);
                minAmp = Math.min(minAmp, a);
                if (lastIssue >= 0) minGap = Math.min(minGap, now - lastIssue);
                lastIssue = now;
            }
        }
        System.out.println("  hum starts at the 2nd detent (" + startT + " ms), full after " + (fullT - startT) + " ms, "
                + issues + " re-issues in 1 s, amp " + minAmp + ".." + maxAmp);
        check(fullT > 0 && fullT - startT <= 260, "ramps up over about 250 ms");
        check(maxAmp <= HapticPatterns.HUM_MAX && minAmp >= HapticPatterns.HUM_MIN, "amplitude stays inside 18..110");
        check(minGap >= HapticPatterns.HUM_REISSUE_MS && issues <= 13, "re-issued at most every 80 ms (min gap " + minGap + ")");
        long stop = now;
        boolean cancelled = false;
        long cancelAt = -1;
        for (int f = 1; f < 60; f++) {
            now = stop + f * 16L;
            m.update(now, 0.016f);
            int a = m.poll(now);
            if (a == 0) { cancelled = true; cancelAt = now - stop; break; }
        }
        System.out.println("  cancelled " + cancelAt + " ms after the last detent");
        check(cancelled && cancelAt <= 450, "decays and cancels after the stick stops");
        check(m.poll(now + 16) == -1 && !m.active(), "cancel is sent once, then idle");
        HumModel slow = new HumModel();
        long t = 0;
        float peak = 0;
        for (int f = 0; f < 120; f++) {
            t = f * 16L + 16;
            if (t % 300 < 16) slow.onTick(t);
            slow.update(t, 0.016f);
            peak = Math.max(peak, slow.level);
        }
        check(peak < 0.6f, "slow turning hums softly (peak level " + peak + ")");
    }

    static void pointer() {
        section("pointer push (grid hover)");
        float[] o = new float[2];
        float R = 432f, M = 22f;
        PointerMath.push(100f, 0f, R, M, o);
        check(o[0] > 0f && Math.abs(o[1]) < 1e-4f, "a tile right of the cursor is pushed right (away)");
        PointerMath.push(-60f, -80f, R, M, o);
        check(o[0] < 0f && o[1] < 0f, "a tile up-left of the cursor is pushed up-left");
        float near, far;
        PointerMath.push(50f, 0f, R, M, o); near = o[0];
        PointerMath.push(300f, 0f, R, M, o); far = o[0];
        check(near > far && far > 0f && near <= M, "push falls off with distance and never exceeds the max");
        PointerMath.push(R + 1f, 0f, R, M, o);
        check(o[0] == 0f && o[1] == 0f, "no push outside the radius");
        PointerMath.push(0f, 0f, R, M, o);
        check(o[0] == 0f && o[1] == 0f && !Float.isNaN(o[0]), "tile exactly under the cursor: no push, no NaN");
        float v = 0f;
        for (int i = 0; i < 30; i++) v = PointerMath.ease(v, 20f, 0.22f, 1 / 60f);
        check(v > 19.5f && v <= 20f, "displacement eases in within half a second");
        check(Math.abs(PointerMath.angle(0, 0, 10, 0)) < 1e-3f && Math.abs(PointerMath.angle(0, 0, 0, 10) - 90f) < 1e-3f, "edge arc angle: 0 right, 90 down");
    }

    static void fmt() {
        section("stats number formatting");
        char[] b = new char[16];
        check(new String(b, 0, Fmt.size(b, 0, 512)).equals("512B"), "512B");
        check(new String(b, 0, Fmt.size(b, 0, 1536)).equals("1.5K"), "1.5K");
        check(new String(b, 0, Fmt.size(b, 0, 12L * 1024 * 1024)).equals("12M"), "12M");
        check(new String(b, 0, Fmt.size(b, 0, 1503238553L)).equals("1.4G"), "1.4G");
        check(new String(b, 0, Fmt.num(b, 0, 0)).equals("0") && new String(b, 0, Fmt.num(b, 0, 4096)).equals("4096"), "integers");
    }

    // ---------------------------------------------------------------- 2.1

    /**
     * Physical tilt as raw controller gyro rates, from the field reports (see GyroFilter.axes):
     * tilting up gives v0 > 0, tilting left gives v1 + v2 > 0 (here split between yaw and roll).
     */
    static float[] tilt(int right, int up) {
        return new float[]{0.8f * up, -0.5f * right, -0.3f * right};
    }

    static String dirName(int right, int up) {
        String v = up > 0 ? "up" : (up < 0 ? "down" : ""), h = right > 0 ? "right" : (right < 0 ? "left" : "");
        return v.isEmpty() ? h : (h.isEmpty() ? v : v + " " + h);
    }

    static void gyroDirection() {
        section("2.2 gyro direction: up is up, left is left (Follow), Opposite flips both");
        float[] o = new float[2], f = new float[2];
        // sway convention: out[0] < 0 points up, out[1] < 0 points left (and shifts left)
        boolean allFollow = true, allOpposite = true;
        int cases = 0;
        for (int right = -1; right <= 1; right++) {
            for (int up = -1; up <= 1; up++) {
                if (right == 0 && up == 0) continue;
                float[] t = tilt(right, up);
                GyroFilter.axes(t[0], t[1], t[2], true, f);
                GyroFilter.axes(t[0], t[1], t[2], false, o);
                boolean vOk = up == 0 ? f[0] == 0f : Math.signum(-f[0]) == up;
                boolean hOk = right == 0 ? Math.abs(f[1]) < 1e-6f : Math.signum(f[1]) == right;
                check(vOk && hOk, "Follow: tilt " + dirName(right, up) + " sways " + dirName(right, up));
                allFollow &= vOk && hOk;
                allOpposite &= o[0] == -f[0] && o[1] == -f[1];
                cases++;
            }
        }
        check(cases == 8 && allFollow, "all four corners and all four edges follow the tilt");
        check(allOpposite, "Opposite flips both axes together for all eight directions");
        // only the vertical sign changed from 2.1: 2.1 Follow was (v0, -(v1 + v2))
        float[] t = tilt(-1, 1);
        GyroFilter.axes(t[0], t[1], t[2], true, f);
        check(f[0] == -t[0] && f[1] == -(t[1] + t[2]), "2.2 keeps the 2.1 horizontal sign and flips only the vertical");
        GyroFilter g = new GyroFilter();
        for (int i = 0; i < 20; i++) { GyroFilter.axes(t[0], t[1], t[2], true, f); g.update(f[0], f[1], 1 / 60f); }
        check(g.rx < 0 && g.ry < 0 && g.shiftX() < 0, "filtered: a top left tilt tips the top left edge away and shifts left");
        GyroFilter.axes(Float.NaN, 1f, Float.POSITIVE_INFINITY, true, f);
        check(f[0] == 0f && f[1] == 0f, "NaN and infinite sensor values map to zero");
        // 2.2 gyro strength
        GyroFilter a = new GyroFilter(), b = new GyroFilter();
        b.scale = 2f;
        for (int i = 0; i < 10; i++) { a.update(-1f, 1f, 1 / 60f); b.update(-1f, 1f, 1 / 60f); }
        check(Math.abs(b.rx - 2 * a.rx) < 1e-3f && b.ry > a.ry, "strength 200% doubles the sway");
        for (int i = 0; i < 300; i++) b.update(50f, 50f, 1 / 60f);
        check(b.rx <= GyroFilter.MAX * 2f + 1e-4f && b.rx > GyroFilter.MAX, "the limit scales with strength");
        check(GyroFilter.clampScale(100) == 1f && GyroFilter.clampScale(5) == 0.25f && GyroFilter.clampScale(900) == 2f, "strength setting clamps to 25..200 percent");
        GyroFilter c = new GyroFilter();
        c.scale = Float.NaN;
        c.update(1f, 1f, 1 / 60f);
        check(!Float.isNaN(c.rx) && c.rx > 0, "a broken strength value falls back to 100 percent");
    }

    static File tempDir() throws IOException { return Files.createTempDirectory("decktest").toFile(); }

    static File zip(File dir, String name, Object... entries) throws IOException {
        File z = new File(dir, name);
        try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(z))) {
            for (int i = 0; i < entries.length; i += 2) {
                zo.putNextEntry(new ZipEntry((String) entries[i]));
                Object d = entries[i + 1];
                if (d instanceof byte[]) zo.write((byte[]) d);
                else if (d instanceof Integer) zo.write(new byte[(Integer) d]);
                zo.closeEntry();
            }
        }
        return z;
    }

    static void packZip() {
        section("2.1 sound pack zip install and safety");
        check(PackZip.classify("../evil.wav", false) == PackZip.UNSAFE, "../ is rejected");
        check(PackZip.classify("pack/../../evil.wav", false) == PackZip.UNSAFE, "a .. in the middle is rejected");
        check(PackZip.classify("/sdcard/evil.wav", false) == PackZip.UNSAFE, "an absolute path is rejected");
        check(PackZip.classify("..\\evil.wav", false) == PackZip.UNSAFE, "a Windows style ..\\ is rejected");
        check(PackZip.classify("C:\\evil.wav", false) == PackZip.UNSAFE, "a drive letter is rejected");
        check(PackZip.classify("a\u0000.wav", false) == PackZip.UNSAFE, "a NUL in the name is rejected");
        check(PackZip.classify("move.WAV", false) == PackZip.KEEP && PackZip.classify("Arcade/pack.json", false) == PackZip.KEEP, "audio and pack.json are kept, any case");
        check(PackZip.classify("run.sh", false) == PackZip.SKIP && PackZip.classify("classes.dex", false) == PackZip.SKIP
                && PackZip.classify("a/.DS_Store", false) == PackZip.SKIP && PackZip.classify("__MACOSX/a/._move.wav", false) == PackZip.SKIP
                && PackZip.classify("Arcade/", true) == PackZip.SKIP && PackZip.classify(".wav", false) == PackZip.SKIP, "other files, dot files, macOS junk and folders are skipped");
        check(PackZip.cleanName("..My Pack!").equals("My Pack_") && PackZip.cleanName("a/b").equals("a_b"), "pack names are cleaned: " + PackZip.cleanName("..My Pack!"));
        try {
            File base = tempDir();
            File packs = new File(base, "soundpacks");
            packs.mkdirs();
            // 1. files at the zip root
            File z1 = zip(packs, "Arcade.zip", "move.wav", new byte[100], "select.ogg", new byte[50],
                    "pack.json", "{\"name\":\"Arcade\"}".getBytes("UTF-8"), "readme.txt", new byte[10]);
            PackZip.Result r = PackZip.extract(z1, packs);
            check(r.ok && "Arcade".equals(r.name) && r.files == 3 && new File(packs, "Arcade/move.wav").length() == 100
                    && !new File(packs, "Arcade/readme.txt").exists(), "root layout installs as the zip name: " + r);
            check(PackZip.installed(z1, packs) && PackZip.scan(packs).isEmpty(), "an installed zip is not extracted again");
            check(!new File(packs, ".unpack-Arcade").exists(), "no temp folder left behind");
            // 2. one top folder, with macOS junk
            File z2 = zip(packs, "download (1).zip", "Neon/", null, "Neon/move.wav", new byte[10], "Neon/back.mp3", new byte[10],
                    "__MACOSX/Neon/._move.wav", new byte[4], "Neon/.DS_Store", new byte[4]);
            r = PackZip.extract(z2, packs);
            check(r.ok && "Neon".equals(r.name) && r.files == 2, "nested layout installs as the folder name: " + r);
            // 3. zip slip
            File z3 = zip(packs, "evil.zip", "ok.wav", new byte[10], "../evil.wav", new byte[10]);
            r = PackZip.extract(z3, packs);
            check(!r.ok && r.error.contains("UNSAFE") && !new File(base, "evil.wav").exists() && !new File(packs, "evil").exists(), "zip slip rejects the whole zip: " + r);
            File z4 = zip(packs, "abs.zip", "/tmp/x.wav", new byte[10]);
            r = PackZip.extract(z4, packs);
            check(!r.ok && r.error.contains("UNSAFE"), "absolute entry rejects the zip");
            File z5 = zip(packs, "win.zip", "..\\evil2.wav", new byte[10]);
            r = PackZip.extract(z5, packs);
            check(!r.ok && !new File(base, "evil2.wav").exists(), "backslash zip slip rejects the zip");
            // 4. only bad file types
            File z6 = zip(packs, "junk.zip", "run.sh", new byte[10], "app.apk", new byte[10]);
            r = PackZip.extract(z6, packs);
            check(!r.ok && r.error.contains("NO SOUND"), "a zip without sound files is refused: " + r);
            File z6b = zip(packs, "jsononly.zip", "pack.json", "{}".getBytes("UTF-8"));
            r = PackZip.extract(z6b, packs);
            check(!r.ok && !new File(packs, "jsononly").exists(), "pack.json alone is not a pack");
            // 5. too many files
            Object[] many = new Object[(PackZip.MAX_FILES + 1) * 2];
            for (int i = 0; i <= PackZip.MAX_FILES; i++) { many[i * 2] = "f" + i + ".wav"; many[i * 2 + 1] = new byte[1]; }
            r = PackZip.extract(zip(packs, "many.zip", many), packs);
            check(!r.ok && r.error.contains("FILES"), "file count is capped at " + PackZip.MAX_FILES);
            // 6. one file over 1 MB
            r = PackZip.extract(zip(packs, "big.zip", "move.wav", (int) PackZip.MAX_FILE + 1), packs);
            check(!r.ok && r.error.contains("1 MB") && !new File(packs, "big").exists(), "a file over 1 MB is refused: " + r);
            // 7. total size cap (also a zip bomb: zeros compress to almost nothing)
            Object[] bomb = new Object[34];
            for (int i = 0; i < 17; i++) { bomb[i * 2] = "b" + i + ".wav"; bomb[i * 2 + 1] = 1000000; }
            File zb = zip(packs, "bomb.zip", bomb);
            r = PackZip.extract(zb, packs);
            check(!r.ok && r.error.contains("MB UNPACKED") && zb.length() < 200000, "total unpacked size is capped (zip was " + zb.length() + " bytes)");
            // 8. layouts that are not allowed
            r = PackZip.extract(zip(packs, "deep.zip", "a/b/move.wav", new byte[1]), packs);
            check(!r.ok && r.error.contains("ONE FOLDER"), "deeper nesting is refused");
            r = PackZip.extract(zip(packs, "two.zip", "a/move.wav", new byte[1], "b/move.wav", new byte[1]), packs);
            check(!r.ok, "two top folders are refused");
            // 9. a changed zip is installed again and replaces the folder
            new File(packs, "Arcade/stale.wav").createNewFile();
            z1.setLastModified(z1.lastModified() - 60000);
            check(!PackZip.installed(z1, packs), "a zip with a new timestamp counts as new");
            java.util.ArrayList<PackZip.Result> rs = PackZip.scan(packs);
            boolean again = false;
            for (PackZip.Result x : rs) if ("Arcade.zip".equals(x.zip) && x.ok) again = true;
            check(again && !new File(packs, "Arcade/stale.wav").exists() && "Arcade".equals(PackZip.installedAs(z1, packs)), "reinstall replaces the old folder");
            PackZip.deleteTree(packs, base);
            PackZip.deleteTree(base, base.getParentFile());
        } catch (IOException e) {
            check(false, "zip test io error " + e);
        }
    }

    static void recents() {
        section("2.1 recent apps");
        Recents r = new Recents();
        r.record("a/.A", 100);
        r.record("b/.B", 200);
        r.record("c/.C", 300);
        r.record("a/.A", 400);
        check(r.list.size() == 3 && r.list.get(0).key.equals("a/.A"), "a repeat launch moves the app to the front, once");
        Recents back = Recents.load(r.save() + "\ngarbage\n12x\tz\n\t\n");
        check(back.list.size() == 3 && back.list.get(0).key.equals("a/.A") && back.list.get(2).key.equals("b/.B"), "save and load round trip, broken lines skipped");
        for (int i = 0; i < 30; i++) r.record("k" + i, 1000 + i);
        check(r.list.size() == Recents.MAX_KEEP, "history is capped at " + Recents.MAX_KEEP);
        List<Recents.Entry> sys = new ArrayList<>();
        sys.add(new Recents.Entry("yt", 50));
        sys.add(new Recents.Entry("nf", 90));
        sys.add(new Recents.Entry("yt", 120));
        sys.add(new Recents.Entry("old", 10));
        sys.add(new Recents.Entry("gone", 500));
        sys.add(new Recents.Entry("pinned", 400));
        List<String> live = Arrays.asList("yt", "nf", "old", "pinned");
        ArrayList<String> got = Recents.pick(sys, 4, new HashSet<>(Arrays.asList("pinned")), live);
        check(got.equals(Arrays.asList("yt", "nf", "old")), "newest first, deduped, pinned and uninstalled left out: " + got);
        check(Recents.pick(sys, 0, null, null).isEmpty() && Recents.pick(sys, 2, null, live).size() == 2, "count 0 shows none, the count caps the list");
        check(Recents.clampCount(-3) == 0 && Recents.clampCount(99) == 8 && Recents.clampCount(4) == 4, "count setting clamps to 0..8");
        check(r.remove("k29") && !r.remove("k29"), "remove from recents");
    }

    static void volume() {
        section("2.1 quick menu volume");
        check(VolumeMath.step(7, 15, 1) == 8 && VolumeMath.step(15, 15, 1) == 15 && VolumeMath.step(0, 15, -1) == 0, "steps clamp to 0..max");
        check(VolumeMath.lit(0, 15, 20) == 0 && VolumeMath.lit(1, 15, 20) >= 1 && VolumeMath.lit(15, 15, 20) == 20 && VolumeMath.lit(8, 15, 20) == 11, "bar segments: off, at least one, full, half " + VolumeMath.lit(8, 15, 20));
        check(VolumeMath.lit(5, 0, 20) == 0 && VolumeMath.step(3, 0, 1) == 0, "a stream with max 0 is safe");
        check(VolumeMath.label(15, 15, false, false).startsWith("100%") && VolumeMath.label(3, 15, true, false).equals("MUTED")
                && VolumeMath.label(3, 15, false, true).equals("TV CONTROLS VOLUME"), "readout text");
    }

    static void deckCmd() {
        section("2.1 deck_cmd intent extra");
        check(DeckCmd.parse("reload_packs") == DeckCmd.RELOAD_PACKS && DeckCmd.parse("reload_icons") == DeckCmd.RELOAD_ICONS, "known commands");
        check(DeckCmd.parse("open_setup") == DeckCmd.OPEN_SETUP && DeckCmd.parse("open_setup ") == DeckCmd.NONE, "2.4 open_setup (the PS overlay's OPEN SETUP row)");
        check(DeckCmd.parse("RELOAD_PACKS") == DeckCmd.NONE && DeckCmd.parse(" reload_packs") == DeckCmd.NONE
                && DeckCmd.parse("rm -rf /") == DeckCmd.NONE && DeckCmd.parse("") == DeckCmd.NONE && DeckCmd.parse(null) == DeckCmd.NONE
                && DeckCmd.parse(Integer.valueOf(1)) == DeckCmd.NONE, "unknown, padded, empty and non string values are ignored");
        StringBuilder longer = new StringBuilder();
        for (int i = 0; i < 40; i++) longer.append('r');
        check(DeckCmd.parse(longer.toString()) == DeckCmd.NONE, "over long values are ignored");
    }

    static void iconPick() {
        section("2.1 sharper icons fallback chain");
        int[] c = IconPick.densityChain(320);
        check(c[0] == IconPick.D_XXXHIGH && c[c.length - 1] == 0 && c.length == 4, "xhdpi box tries xxxhdpi, xxhdpi, xhdpi, then the plain call");
        check(IconPick.densityChain(640).length == 2, "an xxxhdpi screen skips the lower steps");
        check(IconPick.iconRaster(139.2f) == 256 && IconPick.iconRaster(40f) == 96 && IconPick.iconRaster(100f) == 200, "icons raster at 2x the drawn size, 96..256");
        check(IconPick.useBanner(320, 96, 256) && !IconPick.useBanner(160, 192, 256) && IconPick.useBanner(IconPick.SCALABLE, 192, 256)
                && !IconPick.useBanner(240, IconPick.SCALABLE, 256) && IconPick.useBanner(320, IconPick.SCALABLE, 256)
                && IconPick.useBanner(100, 0, 256) && !IconPick.useBanner(0, 96, 256), "banner wins only when it is at least as sharp as the icon");
        check(IconPick.bannerRasterW(320, 352, 560) == 352 && IconPick.bannerRasterW(640, 352, 560) == 560
                && IconPick.bannerRasterW(480, 352, 560) == 480 && IconPick.bannerRasterW(IconPick.SCALABLE, 352, 560) == 560, "banner raster follows the source, 352..560");
        check(IconPick.wide(512, 288) && !IconPick.wide(512, 512), "a wide custom image is used like a banner");
        check(IconPick.sampleSize(2048, 1024, 512) == 4 && IconPick.sampleSize(600, 600, 512) == 1, "big custom images are decoded down");
    }

    static void playerLeds() {
        section("2.1 player LEDs (replacement for the missing light bar)");
        check(PlayerLeds.index("input8:white:player-3") == 3 && PlayerLeds.index("input8:white:player-1") == 1
                && PlayerLeds.index("player5") == 5 && PlayerLeds.index("input8:rgb:indicator") == -1
                && PlayerLeds.index("player-") == -1 && PlayerLeds.index(null) == -1, "player number from the dumpsys light names");
        int n = 20;
        boolean spread = PlayerLeds.wheelBucket(0, n, 5) == 0 && PlayerLeds.wheelBucket(19, n, 5) == 4 && PlayerLeds.wheelBucket(10, n, 5) == 2;
        check(spread, "wheel position: first item lights LED 1, last lights LED 5, middle lights LED 3");
        boolean one = true, mono = true;
        int last = -1;
        for (int i = 0; i < n; i++) {
            int m = PlayerLeds.wheelMask(i, n, 5);
            one &= Integer.bitCount(m) == 1;
            int b = PlayerLeds.wheelBucket(i, n, 5);
            mono &= b >= last;
            last = b;
        }
        check(one && mono, "exactly one LED, moving left to right as the wheel advances");
        check(PlayerLeds.wheelBucket(-1, n, 5) == 4 && PlayerLeds.wheelBucket(21, n, 5) == 0 && PlayerLeds.wheelMask(0, 0, 5) == 0, "wraps like the wheel, empty list lights nothing");
        check(PlayerLeds.wheelBucket(1, 2, 5) == 2 && PlayerLeds.wheelBucket(0, 1, 5) == 0, "few apps still pick a sensible LED");
        check(PlayerLeds.batteryCount(1f, 5) == 5 && PlayerLeds.batteryCount(0.81f, 5) == 5 && PlayerLeds.batteryCount(0.8f, 5) == 4
                && PlayerLeds.batteryCount(0.5f, 5) == 3 && PlayerLeds.batteryCount(0.05f, 5) == 1, "battery 100/81/80/50/5 percent lights 5/5/4/3/1 LEDs");
        check(PlayerLeds.batteryCount(0f, 5) == 0 && PlayerLeds.batteryCount(Float.NaN, 5) == 0, "unknown or empty battery lights none");
        check(PlayerLeds.batteryMask(0.5f, 5) == 0x07 && PlayerLeds.batteryMask(1f, 5) == PlayerLeds.ALL, "battery fills from the left");
        check(PlayerLeds.color(0x04, 2) == PlayerLeds.ON && PlayerLeds.color(0x04, 1) == 0 && (PlayerLeds.ON >>> 24) == 0xFF, "on is full alpha (MonoLight brightness), off is 0");
    }

    // ---------------------------------------------------------------- 2.2

    static void padTestDetect() {
        section("2.2 gamepad test: controller type and layouts");
        check(PadTest.detect(0x054c, 0x0ce6, "DualSense Wireless Controller") == PadTest.PLAYSTATION
                && PadTest.detect(0x054c, 0x05c4, null) == PadTest.PLAYSTATION && PadTest.detect(0x054c, 0x09cc, "") == PadTest.PLAYSTATION, "Sony ids are PlayStation (DualSense, DualShock 4 both versions)");
        check(PadTest.detect(0x045e, 0x0b13, "Xbox Wireless Controller") == PadTest.XBOX && PadTest.detect(0x045e, 0, null) == PadTest.XBOX, "Microsoft ids are Xbox");
        check(PadTest.detect(0x057e, 0x2009, "Pro Controller") == PadTest.SWITCH, "Nintendo ids are Switch");
        check(PadTest.detect(0, 0, "Wireless Controller") == PadTest.PLAYSTATION && PadTest.detect(0, 0, "DUALSENSE edge") == PadTest.PLAYSTATION
                && PadTest.detect(0, 0, "Xbox 360 pad") == PadTest.XBOX && PadTest.detect(0, 0, "Nintendo Switch Pro Controller") == PadTest.SWITCH, "names are a fallback when the ids are unknown");
        check(PadTest.detect(0x2dc8, 0x3106, "8BitDo SN30 Pro") == PadTest.GENERIC && PadTest.detect(0, 0, null) == PadTest.GENERIC, "anything else is generic");
        check(PadTest.detect(0x054c, 0, "Xbox") == PadTest.PLAYSTATION, "the vendor id wins over the name");
        check(PadTest.model(0x054c, 0x0ce6).equals("DUALSENSE") && PadTest.model(0x054c, 0x05c4).equals("DUALSHOCK 4")
                && PadTest.model(0x057e, 0x2009).equals("PRO CONTROLLER") && PadTest.model(0x1234, 1).isEmpty(), "known models by product id");
        check(PadTest.layout(PadTest.SWITCH) == PadTest.GENERIC && PadTest.layout(PadTest.XBOX) == PadTest.XBOX
                && PadTest.TYPE_NAMES[PadTest.SWITCH].equals("NINTENDO SWITCH (LAYOUT NOT FULLY SUPPORTED)"), "Switch uses the generic layout and says so");
        check(Integer.bitCount(PadTest.required(PadTest.PLAYSTATION)) == 16 && Integer.bitCount(PadTest.required(PadTest.XBOX)) == 16
                && Integer.bitCount(PadTest.required(PadTest.GENERIC)) == 16, "16 required buttons on every type");
        check(PadTest.optional(PadTest.PLAYSTATION) == ((1 << PadTest.HOME) | (1 << PadTest.TOUCHPAD))
                && PadTest.optional(PadTest.XBOX) == (1 << PadTest.HOME) && (PadTest.required(PadTest.XBOX) & PadTest.optional(PadTest.XBOX)) == 0,
                "optional: PS button and touchpad on PlayStation, Guide on Xbox, never counted");
        check(PadTest.has(PadTest.PLAYSTATION, PadTest.TOUCHPAD) && !PadTest.has(PadTest.XBOX, PadTest.TOUCHPAD), "only PlayStation has a touchpad button");
        String[] ps = new String[PadTest.COUNT];
        for (int i = 0; i < PadTest.COUNT; i++) ps[i] = PadTest.label(PadTest.PLAYSTATION, 0x0ce6, i);
        check(ps[PadTest.SOUTH].equals("CROSS") && ps[PadTest.EAST].equals("CIRCLE") && ps[PadTest.WEST].equals("SQUARE") && ps[PadTest.NORTH].equals("TRIANGLE")
                && ps[PadTest.START].equals("OPTIONS") && ps[PadTest.SELECT].equals("CREATE") && ps[PadTest.HOME].equals("PS") && ps[PadTest.TOUCHPAD].equals("TOUCHPAD")
                && ps[PadTest.L2].equals("L2") && ps[PadTest.R3].equals("R3"), "PlayStation labels");
        check(PadTest.label(PadTest.PLAYSTATION, 0x05c4, PadTest.SELECT).equals("SHARE"), "DualShock 4 says SHARE");
        check(PadTest.label(PadTest.XBOX, 0, PadTest.SOUTH).equals("A") && PadTest.label(PadTest.XBOX, 0, PadTest.L1).equals("LB")
                && PadTest.label(PadTest.XBOX, 0, PadTest.R2).equals("RT") && PadTest.label(PadTest.XBOX, 0, PadTest.START).equals("MENU")
                && PadTest.label(PadTest.XBOX, 0, PadTest.SELECT).equals("VIEW") && PadTest.label(PadTest.XBOX, 0, PadTest.HOME).equals("GUIDE")
                && PadTest.label(PadTest.XBOX, 0, PadTest.L3).equals("LEFT STICK"), "Xbox labels");
        // key codes from the AOSP DualSense key layout (Vendor_054c_Product_0ce6.kl)
        check(PadTest.buttonFor(96) == PadTest.SOUTH && PadTest.buttonFor(97) == PadTest.EAST && PadTest.buttonFor(99) == PadTest.WEST
                && PadTest.buttonFor(100) == PadTest.NORTH && PadTest.buttonFor(108) == PadTest.START && PadTest.buttonFor(109) == PadTest.SELECT
                && PadTest.buttonFor(110) == PadTest.HOME && PadTest.buttonFor(106) == PadTest.L3 && PadTest.buttonFor(105) == PadTest.R2
                && PadTest.buttonFor(19) == PadTest.UP && PadTest.buttonFor(22) == PadTest.RIGHT, "key codes map to buttons");
        check(PadTest.buttonFor(98) == -1 && PadTest.buttonFor(4) == -1 && PadTest.buttonFor(188) == -1, "BUTTON_C, BACK and BUTTON_1 are not in the layout (shown as raw keys)");
        check(PadTest.analogHeld(0.6f, false) && !PadTest.analogHeld(0.4f, false) && PadTest.analogHeld(0.4f, true) && !PadTest.analogHeld(0.2f, true)
                && PadTest.analogHeld(-0.9f, false) && !PadTest.analogHeld(Float.NaN, true), "analog press with hysteresis (0.5 in, 0.3 out)");
        check(PadTest.hex4(0x54c).equals("054C") && PadTest.hex4(0x2009).equals("2009"), "ids shown as 4 digit hex");
    }

    static void padTestState() {
        section("2.2 gamepad test: FULL and QUICK");
        PadTest.State st = new PadTest.State();
        st.setType(PadTest.PLAYSTATION);
        check(st.full && st.count() == 0 && st.total() == 16 && !st.complete(), "a new FULL run starts at 0/16");
        st.set(PadTest.SOUTH, true, PadTest.State.SRC_KEY);
        st.set(PadTest.SOUTH, false, PadTest.State.SRC_KEY);
        check(st.lit(PadTest.SOUTH) && !st.held(PadTest.SOUTH) && st.count() == 1, "FULL: a button stays lit after release");
        st.set(PadTest.SOUTH, true, PadTest.State.SRC_KEY);
        check(st.count() == 1, "pressing again does not count twice");
        st.set(PadTest.HOME, true, PadTest.State.SRC_KEY);
        st.set(PadTest.TOUCHPAD, true, PadTest.State.SRC_POINTER);
        check(st.count() == 1 && st.lit(PadTest.HOME), "optional buttons light up but do not count");
        boolean early = false;
        for (int b = 0; b < 16; b++) {
            if (b == PadTest.R3) continue;
            st.set(b, true, PadTest.State.SRC_KEY);
            st.set(b, false, PadTest.State.SRC_KEY);
            early |= st.complete();
        }
        check(!early && st.count() == 15 && !st.takeCompletion(), "15/16 is not complete");
        st.set(PadTest.R3, true, PadTest.State.SRC_KEY);
        check(st.complete() && st.count() == 16 && st.takeCompletion() && !st.takeCompletion(), "the last required button completes the run, announced once");
        PadTest.State noOpt = new PadTest.State();
        noOpt.setType(PadTest.XBOX);
        for (int b = 0; b < 16; b++) noOpt.set(b, true, PadTest.State.SRC_KEY);
        check(noOpt.complete() && !noOpt.seen(PadTest.HOME), "the test finishes without ever seeing the Guide button");
        st.reset();
        check(st.count() == 0 && !st.complete() && !st.lit(PadTest.SOUTH) && st.held() == 0, "reset clears everything");
        for (int b = 0; b < 16; b++) st.set(b, true, PadTest.State.SRC_KEY);
        check(st.takeCompletion(), "after reset the run can complete and celebrate again");
        // key and axis both hold L2: releasing one keeps it held
        PadTest.State q = new PadTest.State();
        q.setType(PadTest.PLAYSTATION);
        q.setFull(false);
        q.set(PadTest.L2, true, PadTest.State.SRC_KEY);
        q.set(PadTest.L2, true, PadTest.State.SRC_AXIS);
        q.set(PadTest.L2, false, PadTest.State.SRC_KEY);
        check(q.lit(PadTest.L2), "QUICK: L2 stays lit while the analog trigger still holds it");
        q.set(PadTest.L2, false, PadTest.State.SRC_AXIS);
        check(!q.lit(PadTest.L2), "QUICK: dark on release");
        for (int b = 0; b < 16; b++) { q.set(b, true, PadTest.State.SRC_KEY); q.set(b, false, PadTest.State.SRC_KEY); }
        check(!q.complete() && !q.takeCompletion() && q.count() == 16, "QUICK never celebrates (no confetti)");
        q.setFull(true);
        check(q.full && q.count() == 0, "switching mode starts a fresh run");
        q.set(-1, true, 0);
        q.set(99, true, 0);
        q.set(PadTest.SOUTH, true, 7);
        check(q.count() == 0, "bad button or source values are ignored");
    }

    static void exitHold() {
        section("2.2 test screens: hold Start and Select to exit");
        PadTest.ExitHold h = new PadTest.ExitHold();
        h.set(true, false, 0);
        check(!h.holding() && h.progress(5000) == 0f && !h.done(5000), "Start alone does nothing");
        h.set(true, true, 1000);
        check(h.holding() && Math.abs(h.progress(2000) - 0.5f) < 1e-6f && !h.done(2999), "both held: the ring fills over 2 s");
        check(h.done(3000) && h.progress(9000) == 1f, "exits after 2 s");
        h.set(true, false, 2500);
        h.set(true, true, 2600);
        check(!h.done(4000) && h.done(4600), "letting go of one button restarts the timer");
        h.set(true, true, 4700);
        check(h.done(4700), "holding on does not restart it");
        h.clear();
        check(!h.holding() && !h.done(100000), "clear stops it");
        h.setEast(true, 5000);
        check(h.holding() && !h.done(6499) && h.done(6500) && h.eastLeads(5500), "2.17: holding B alone exits after 1.5 s and the ring sits on B");
        h.setEast(false, 6000);
        check(!h.holding(), "letting go of B stops it");
        h.set(true, true, 7000);
        h.setEast(true, 7500);
        h.setEast(false, 7600);
        check(h.holding() && h.done(9000) && !h.eastLeads(7650), "letting go of B does not stop a Start + Select hold");
        h.clear();
    }

    static void confetti() {
        section("2.2 confetti burst (pooled)");
        Confetti c = new Confetti();
        float[] xs = c.x;
        c.burst(960, 900, 1f, 4, 42);
        check(c.alive() == Confetti.N && Confetti.N == 120, "120 particles");
        boolean left = false, right = false, up = false;
        for (int i = 0; i < Confetti.N; i++) { left |= c.vx[i] < 0; right |= c.vx[i] > 0; up |= c.vy[i] < 0; }
        check(left && right && up, "it fans out both ways and upward");
        for (int i = 0; i < 30; i++) c.update(1 / 60f);
        boolean finite = true;
        for (int i = 0; i < Confetti.N; i++) finite &= !Float.isNaN(c.x[i]) && !Float.isNaN(c.y[i]);
        check(finite && c.active(), "still flying after 0.5 s, all values finite");
        for (int i = 0; i < 180; i++) c.update(1 / 60f);
        check(!c.active() && c.alive() == 0 && c.alpha(0) == 0f, "all gone after about 2 s");
        c.burst(100, 100, 0.5f, 3, 7);
        check(c.x == xs && c.active() && c.color[5] == 5 % 3, "a second burst reuses the same arrays");
        c.clear();
        check(!c.active(), "clear stops it");
    }

    static boolean near(float a, float b) { return Math.abs(a - b) < 1e-4f; }

    static void mat3d() {
        section("2.2 gyro test: rotation matrices and projection");
        float[] m = new float[9], n = new float[9], o = new float[9], v = new float[3], t1 = new float[9], t2 = new float[9];
        float q = (float) (Math.PI / 2);
        Mat3D.rotX(q, m);
        Mat3D.apply(m, 0, 1, 0, v, 0);
        check(near(v[0], 0) && near(v[1], 0) && near(v[2], 1), "rotX(90): Y turns to Z (far edge up)");
        Mat3D.rotY(q, m);
        Mat3D.apply(m, 0, 0, 1, v, 0);
        check(near(v[0], 1) && near(v[2], 0), "rotY(90): Z turns to X");
        Mat3D.apply(m, 1, 0, 0, v, 0);
        check(near(v[2], -1), "rotY(90): the right side goes forward (turning left)");
        Mat3D.rotZ(q, m);
        Mat3D.apply(m, 1, 0, 0, v, 0);
        check(near(v[1], 1), "rotZ(90): X turns to Y (right side up)");
        Mat3D.euler(0.3f, -0.7f, 1.1f, m, t1, t2);
        check(near(Mat3D.det(m), 1f), "pose matrix has determinant 1");
        boolean ortho = true;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float d = m[i] * m[j] + m[3 + i] * m[3 + j] + m[6 + i] * m[6 + j];
            ortho &= near(d, i == j ? 1f : 0f);
        }
        check(ortho, "pose matrix is orthonormal");
        Mat3D.rotY(0.3f, n);
        Mat3D.rotX(-0.7f, o);
        Mat3D.mul(n, o, t1);
        Mat3D.rotZ(1.1f, o);
        Mat3D.mul(t1, o, t2);
        boolean same = true;
        for (int i = 0; i < 9; i++) same &= near(t2[i], m[i]);
        check(same, "euler(yaw, pitch, roll) = Ry * Rx * Rz");
        Mat3D.identity(n);
        Mat3D.mul(m, n, o);
        same = true;
        for (int i = 0; i < 9; i++) same &= near(o[i], m[i]);
        check(same, "identity changes nothing");
        float[] s = new float[4];
        check(Mat3D.project(0, 0, 0, 40, 800, 960, 540, s, 0) && near(s[0], 960) && near(s[1], 540), "the origin projects to the screen center");
        Mat3D.project(4, 2, 0, 40, 800, 960, 540, s, 0);
        Mat3D.project(4, 2, 20, 40, 800, 960, 540, s, 2);
        check(near(s[0], 1040) && near(s[1], 500) && s[2] > s[0] && s[3] < s[1], "x right is right, y up is up, nearer is bigger");
        check(!Mat3D.project(0, 0, 41, 40, 800, 960, 540, s, 0), "points behind the camera are rejected");
        float[] depth = {3f, 9f, 1f, 5f, 9f};
        int[] order = {0, 1, 2, 3, 4};
        Mat3D.sortFarFirst(depth, order, 5);
        check(depth[order[0]] == 9f && depth[order[1]] == 9f && order[2] == 3 && order[3] == 0 && order[4] == 2, "painter's sort: farthest first");
    }

    static void attitude() {
        section("2.2 gyro test: complementary filter");
        Attitude a = new Attitude();
        float g = Attitude.G;
        a.accel(0, g, 0, 0.01f);
        check(a.seeded && near(a.pitch, 0) && near(a.roll, 0), "flat and still: level");
        // tilt up 30 degrees: gravity in the controller frame is (0, cos, -sin)
        float th = (float) Math.toRadians(30);
        check(near(Attitude.accelPitch(0, (float) Math.cos(th) * g, -(float) Math.sin(th) * g), th), "gravity gives pitch (far edge up is positive)");
        check(near(Attitude.accelRoll((float) Math.sin(th) * g, (float) Math.cos(th) * g), th), "gravity gives roll (right side up is positive)");
        // integrate a turn: 1 rad/s about X for 0.5 s, accel agreeing
        Attitude b = new Attitude();
        b.accel(0, g, 0, 0.01f);
        for (int i = 0; i < 50; i++) {
            b.gyro(1f, 0, 0, 0.01f);
            float p = b.pitch;
            b.accel(0, (float) Math.cos(p) * g, -(float) Math.sin(p) * g, 0.01f);
        }
        check(Math.abs(b.pitch - 0.5f) < 0.02f, "gyro integrates pitch: " + b.pitch);
        // drift: a constant 0.02 rad/s gyro bias while the controller lies still and level
        Attitude d = new Attitude();
        d.accel(0, g, 0, 0.01f);
        float pure = 0f;
        for (int i = 0; i < 6000; i++) {          // 60 s at 100 Hz
            d.gyro(0.02f, 0.02f, 0.02f, 0.01f);
            d.accel(0, g, 0, 0.01f);
            pure += 0.02f * 0.01f;
        }
        check(Math.abs(d.pitch) < 0.03f && Math.abs(d.roll) < 0.03f && pure > 1.1f, "accel holds pitch and roll against drift (" + d.pitch + " vs " + pure + " without)");
        check(d.yaw > 1.1f, "yaw has no gravity reference, so it drifts until Recenter");
        d.recenter();
        check(near(d.relPitch(), 0) && near(d.relYaw(), 0) && near(d.relRoll(), 0), "recenter makes the current pose neutral");
        d.gyro(0, 1f, 0, 0.1f);
        check(Math.abs(d.relYaw() - 0.1f) < 1e-3f, "after recenter, turning reads from zero");
        // shaking: a reading far from 1 g is ignored
        Attitude s = new Attitude();
        s.accel(0, g, 0, 0.01f);
        check(!s.accel(3 * g, 0, 0, 0.01f) && near(s.roll, 0), "a 3 g shake does not move the level");
        check(!s.accel(Float.NaN, g, 0, 0.01f), "NaN readings are ignored");
        s.gyro(Float.NaN, 0, 0, 0.01f);
        check(!Float.isNaN(s.pitch), "NaN gyro is ignored");
        // seeding from the first reading, then converging back after a big jump
        Attitude c = new Attitude();
        c.accel(0, (float) Math.cos(th) * g, -(float) Math.sin(th) * g, 0.01f);
        check(near(c.pitch, th), "the first reading seeds the angles");
        c.pitch = 0f;
        for (int i = 0; i < 300; i++) c.accel(0, (float) Math.cos(th) * g, -(float) Math.sin(th) * g, 0.01f);
        check(Math.abs(c.pitch - th) < 0.01f, "a wrong pitch converges back to gravity in 3 s");
        check(near(Attitude.wrap((float) (3 * Math.PI)), (float) Math.PI) || near(Attitude.wrap((float) (3 * Math.PI)), (float) -Math.PI), "angles wrap to +-180");
        Attitude r = new Attitude();
        r.roll = -0.2f;
        r.pitch = 0.1f;
        check(r.tiltRight() > 0 && r.tiltUp() > 0, "rolling the right side down reads as a tilt right");
    }

    static void corners() {
        section("2.2 gyro test: corner markers");
        check(Mat3D.corner(-10, 10, 8) == Mat3D.TL && Mat3D.corner(10, 10, 8) == Mat3D.TR
                && Mat3D.corner(-10, -10, 8) == Mat3D.BL && Mat3D.corner(10, -10, 8) == Mat3D.BR, "each diagonal lights its corner");
        check(Mat3D.corner(3, 3, 8) == Mat3D.NO_CORNER, "a small tilt lights nothing");
        check(Mat3D.corner(20, 1, 8) == Mat3D.NO_CORNER && Mat3D.corner(0, -20, 8) == Mat3D.NO_CORNER, "straight left, right, up or down is not a corner");
        check(Mat3D.corner(20, 9, 8) == Mat3D.TR && Mat3D.corner(20, 6, 8) == Mat3D.NO_CORNER, "it has to lean clearly diagonal");
        check(Mat3D.corner(Float.NaN, 10, 8) == Mat3D.NO_CORNER, "NaN lights nothing");
    }

    static void controllerModel() {
        section("2.2 gyro test: low poly controller model");
        int[] types = {PadTest.PLAYSTATION, PadTest.XBOX, PadTest.GENERIC, PadTest.SWITCH};
        float[] nrm = new float[3];
        for (int t : types) {
            ControllerModel m = new ControllerModel(t);
            boolean idx = true, outward = true, top = true;
            for (int f = 0; f < m.faces.length; f++) {
                int[] fc = m.faces[f];
                idx &= fc.length >= 3;
                float cx = 0, cy = 0, cz = 0;
                for (int v : fc) {
                    idx &= v >= 0 && v < m.vertexCount;
                    if (v >= 0 && v < m.vertexCount) { cx += m.verts[v * 3]; cy += m.verts[v * 3 + 1]; cz += m.verts[v * 3 + 2]; }
                }
                cx /= fc.length; cy /= fc.length; cz /= fc.length;
                ControllerModel.normal(m.verts, fc, nrm);
                if (m.group[f] == 0 && f > 1) {
                    // body side: a step along the normal from the face center leaves the outline
                    float len = (float) Math.sqrt(nrm[0] * nrm[0] + nrm[2] * nrm[2]);
                    float[] ol = ControllerModel.outline(m.type);
                    outward &= len > 0 && !insideXZ(ol, cx + nrm[0] / len * 0.05f, cz + nrm[2] / len * 0.05f)
                            && insideXZ(ol, cx - nrm[0] / len * 0.05f, cz - nrm[2] / len * 0.05f);
                } else if (m.group[f] == 1 && Math.abs(nrm[1]) > 1e-3f) {
                    top &= nrm[1] > 0;   // flat decorations and stick caps face up
                }
            }
            ControllerModel.normal(m.verts, m.faces[m.topFace], nrm);
            boolean capUp = nrm[1] > 0;
            ControllerModel.normal(m.verts, m.faces[1], nrm);
            boolean capDown = nrm[1] < 0;
            check(idx, PadTest.TYPE_NAMES[t] + ": every face uses valid vertices");
            check(capUp && capDown && outward && top, PadTest.TYPE_NAMES[t] + ": every face points outward (needed for culling and shading)");
        }
        ControllerModel ps = new ControllerModel(PadTest.PLAYSTATION), xb = new ControllerModel(PadTest.XBOX);
        boolean psPad = false, xbPad = false;
        for (int b : ps.button) psPad |= b == PadTest.TOUCHPAD;
        for (int b : xb.button) xbPad |= b == PadTest.TOUCHPAD;
        check(psPad && !xbPad, "PlayStation has a touchpad, Xbox does not");
        float[] o = ControllerModel.outline(PadTest.PLAYSTATION);
        boolean sym = true;
        int n = o.length / 2;
        for (int i = 1; i < n / 2; i++) sym &= near(o[i * 2], -o[(n - i) * 2]) && near(o[i * 2 + 1], o[(n - i) * 2 + 1]);
        check(sym && n == 28, "the outline is mirror symmetric (" + n + " points)");
        check(ps.faces.length < 170 && ps.vertexCount < 400, "low poly: " + ps.faces.length + " faces, " + ps.vertexCount + " vertices");
    }

    /** Even odd point in polygon test on x, z pairs. */
    static boolean insideXZ(float[] o, float x, float z) {
        boolean in = false;
        int n = o.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = o[i * 2], zi = o[i * 2 + 1], xj = o[j * 2], zj = o[j * 2 + 1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    static void fmtFixed() {
        section("2.2 readout number formatting");
        char[] b = new char[16];
        check(new String(b, 0, Fmt.fixed(b, 0, 0.4249f, 2)).equals("+0.42") && new String(b, 0, Fmt.fixed(b, 0, -12.5f, 1)).equals("-12.5"), "+0.42 and -12.5");
        check(new String(b, 0, Fmt.fixed(b, 0, 0f, 2)).equals(" 0.00") && new String(b, 0, Fmt.fixed(b, 0, -0.001f, 2)).equals(" 0.00"), "zero has no sign");
        check(new String(b, 0, Fmt.fixed(b, 0, 7.05f, 0)).equals("+7") && new String(b, 0, Fmt.fixed(b, 0, 1.005f, 3)).equals("+1.005"), "0 and 3 decimals");
        check(new String(b, 0, Fmt.fixed(b, 0, Float.NaN, 2)).equals("--"), "NaN prints --");
        char[] tiny = new char[3];
        check(Fmt.fixed(tiny, 0, -123.456f, 2) == 3, "never writes past the buffer");
    }

    static void iconPackZip() {
        section("2.2 icon pack zips (same safety rules as sound packs)");
        PackZip.Rules r = PackZip.ICONS;
        check(PackZip.classify("com.netflix.ninja.png", false, r) == PackZip.KEEP && PackZip.classify("Neon/com.google.android.youtube.tv.WEBP", false, r) == PackZip.KEEP, "png and webp are kept, any case");
        check(PackZip.classify("move.wav", false, r) == PackZip.SKIP && PackZip.classify("pack.json", false, r) == PackZip.SKIP
                && PackZip.classify("a.jpg", false, r) == PackZip.SKIP && PackZip.classify("x.svg", false, r) == PackZip.SKIP && PackZip.classify("evil.apk", false, r) == PackZip.SKIP,
                "sounds, pack.json, jpg, svg and apk are skipped");
        check(PackZip.classify("../evil.png", false, r) == PackZip.UNSAFE && PackZip.classify("/sdcard/x.png", false, r) == PackZip.UNSAFE
                && PackZip.classify("..\\x.png", false, r) == PackZip.UNSAFE && PackZip.classify("C:\\x.png", false, r) == PackZip.UNSAFE, "zip slip names are unsafe");
        check(PackZip.classify("move.wav", false) == PackZip.KEEP && PackZip.classify("a.png", false) == PackZip.SKIP, "sound pack rules are unchanged");
        try {
            File base = tempDir();
            File icons = new File(base, "icons");
            icons.mkdirs();
            new File(icons, "com.single.app.png").createNewFile();
            File z1 = zip(icons, "Neon.zip", "com.netflix.ninja.png", new byte[300], "com.google.android.youtube.tv.webp", new byte[200],
                    "readme.txt", new byte[5], "move.wav", new byte[5]);
            PackZip.Result res = PackZip.extract(z1, icons, r);
            check(res.ok && "Neon".equals(res.name) && res.files == 2 && new File(icons, "Neon/com.netflix.ninja.png").length() == 300
                    && !new File(icons, "Neon/readme.txt").exists() && !new File(icons, "Neon/move.wav").exists(), "root layout installs as the zip name, only images: " + res);
            check(!new File(icons, ".unpack-Neon").exists() && PackZip.installed(z1, icons), "temp folder renamed into place, marked installed");
            File z2 = zip(icons, "download.zip", "Arcade/", null, "Arcade/com.a.png", new byte[10], "__MACOSX/Arcade/._com.a.png", new byte[3]);
            res = PackZip.extract(z2, icons, r);
            check(res.ok && "Arcade".equals(res.name) && res.files == 1, "one top folder is allowed: " + res);
            res = PackZip.extract(zip(icons, "evil.zip", "ok.png", new byte[1], "../evil.png", new byte[1]), icons, r);
            check(!res.ok && res.error.contains("UNSAFE") && !new File(base, "evil.png").exists(), "zip slip rejects the whole icon zip");
            res = PackZip.extract(zip(icons, "sounds.zip", "move.wav", new byte[10]), icons, r);
            check(!res.ok && res.error.contains("NO ICON FILES"), "a zip without images is refused: " + res);
            res = PackZip.extract(zip(icons, "deep.zip", "a/b/com.a.png", new byte[1]), icons, r);
            check(!res.ok && res.error.contains("ONE FOLDER"), "deeper folders are refused");
            res = PackZip.extract(zip(icons, "big.zip", "com.a.png", (int) r.maxFile + 1), icons, r);
            check(!res.ok && res.error.contains("4 MB") && !new File(icons, "big").exists(), "an image over 4 MB is refused: " + res);
            Object[] many = new Object[(r.maxFiles + 1) * 2];
            for (int i = 0; i <= r.maxFiles; i++) { many[i * 2] = "com.app" + i + ".png"; many[i * 2 + 1] = new byte[1]; }
            res = PackZip.extract(zip(icons, "many.zip", many), icons, r);
            check(!res.ok && res.error.contains("400"), "image count is capped at 400");
            Object[] bomb = new Object[34];
            for (int i = 0; i < 17; i++) { bomb[i * 2] = "com.b" + i + ".png"; bomb[i * 2 + 1] = 4000000; }
            res = PackZip.extract(zip(icons, "bomb.zip", bomb), icons, r);
            check(!res.ok && res.error.contains("MB UNPACKED"), "total unpacked size is capped at 64 MB");
            String[] packs = IconPacks.list(icons);
            check(Arrays.asList(packs).equals(Arrays.asList("Arcade", "Neon")), "pack list: both packs, sorted, no temp or single files: " + Arrays.toString(packs));
            check(IconPacks.count(new File(icons, "Neon")) == 2, "Setup can count the images in a pack");
            check(IconPacks.installZips(icons).size() == 6 && IconPacks.installZips(icons).size() == 6, "installed zips are skipped, failed ones are reported again on the next scan");
            PackZip.deleteTree(icons, base);
            PackZip.deleteTree(base, base.getParentFile());
        } catch (IOException e) {
            check(false, "icon zip io error " + e);
        }
    }

    static void iconPrecedence() {
        section("2.2 icon precedence: single file, then the chosen pack, then the app");
        try {
            File base = tempDir();
            File icons = new File(base, "icons");
            new File(icons, "Neon").mkdirs();
            new File(icons, "Arcade").mkdirs();
            new File(icons, "com.both.png").createNewFile();
            new File(icons, "Neon/com.both.png").createNewFile();
            new File(icons, "Neon/com.packonly.webp").createNewFile();
            new File(icons, "Arcade/com.packonly.png").createNewFile();
            check(IconPacks.source(icons, "Neon", "com.both") == IconPacks.SINGLE
                    && IconPacks.pick(icons, "Neon", "com.both").getParentFile().equals(icons), "a single file in the icons folder wins over the pack");
            check(IconPacks.source(icons, "Neon", "com.packonly") == IconPacks.PACK
                    && IconPacks.pick(icons, "Neon", "com.packonly").getName().equals("com.packonly.webp"), "the chosen pack is used when there is no single file");
            check(IconPacks.pick(icons, "Arcade", "com.packonly").getName().equals("com.packonly.png"), "another pack gives its own image");
            check(IconPacks.pick(icons, "", "com.packonly") == null && IconPacks.source(icons, "", "com.packonly") == IconPacks.NONE, "no pack chosen: the app's own icon");
            check(IconPacks.pick(icons, "Gone", "com.packonly") == null && IconPacks.pick(icons, "", "com.both") != null, "a missing pack falls back, single files still work");
            check(IconPacks.pick(icons, "Neon", "com.nothing") == null, "an app the pack lacks keeps its own icon");
            check(IconPacks.pick(icons, "../icons", "com.both") != null && IconPacks.packDir(icons, "../x") == null && IconPacks.packDir(icons, ".unpack-Neon") == null,
                    "pack names cannot leave the icons folder or pick a temp folder");
            check(IconPacks.image(icons, "../com.both") == null && IconPacks.image(icons, "a/b") == null && !IconPacks.validPackage("") && IconPacks.validPackage("com.a_b.c9"),
                    "package names must be plain");
            check(IconPacks.image(icons, "com.both") != null && IconPacks.image(icons, "com.packonly") == null, "the 2.1 single file lookup (IconCache.overrideFile) is unchanged");
            PackZip.deleteTree(icons, base);
            PackZip.deleteTree(base, base.getParentFile());
        } catch (IOException e) {
            check(false, "icon precedence io error " + e);
        }
    }

    static void stickRoute() {
        section("2.2 stick routing: spinner scrolls, navigator navigates");
        // roles
        check(StickRoute.role(true, true) == StickRoute.SPINNER && StickRoute.role(false, true) == StickRoute.NAVIGATOR, "default: right stick spins, left stick navigates");
        check(StickRoute.role(true, false) == StickRoute.NAVIGATOR && StickRoute.role(false, false) == StickRoute.SPINNER, "WHEEL STICK left swaps the roles");
        int[] ctxs = {StickRoute.CTX_WHEEL, StickRoute.CTX_GRID, StickRoute.CTX_LIST, StickRoute.CTX_OTHER_ZONE};
        boolean rightNever = true, leftAlways = true, swapped = true;
        for (int c : ctxs) {
            rightNever &= !StickRoute.mayChangeZone(StickRoute.action(StickRoute.role(true, true), c));
            leftAlways &= StickRoute.mayChangeZone(StickRoute.action(StickRoute.role(false, true), c));
            swapped &= !StickRoute.mayChangeZone(StickRoute.action(StickRoute.role(false, false), c))
                    && StickRoute.mayChangeZone(StickRoute.action(StickRoute.role(true, false), c));
        }
        check(rightNever, "the right stick (spinner) can never produce a zone change, in any context");
        check(leftAlways, "the left stick (navigator) always can, like the D-pad");
        check(swapped, "flipping the setting swaps that too");
        check(StickRoute.action(StickRoute.SPINNER, StickRoute.CTX_WHEEL) == StickRoute.A_STEP_ITEMS
                && StickRoute.action(StickRoute.SPINNER, StickRoute.CTX_LIST) == StickRoute.A_STEP_LIST
                && StickRoute.action(StickRoute.SPINNER, StickRoute.CTX_OTHER_ZONE) == StickRoute.A_NONE, "spinner: items on the wheel and grid, rows in lists, nothing on the top bar or dock");
        // speed
        check(Math.abs(StickRoute.detent(100) - 2 * StickDial.DETENT_2_1) < 1e-9 && StickDial.DETENT == StickRoute.detent(100), "100 percent needs twice the 2.1 angle per item (half as fast)");
        check(Math.abs(StickRoute.detent(50) - 2 * StickRoute.detent(100)) < 1e-9 && Math.abs(StickRoute.detent(150) * 1.5 - StickRoute.detent(100)) < 1e-9, "speed percent scales the angle per item");
        check(StickRoute.rate(StickRoute.CTX_WHEEL, 100) == 5f && StickRoute.rate(StickRoute.CTX_WHEEL, 200) == 10f
                && StickRoute.rate(StickRoute.CTX_WHEEL, 25) == 1.25f && StickRoute.rate(StickRoute.CTX_GRID, 100) == 6f, "speed percent scales the per second cap (wheel 5/s, grid 6/s at 100)");
        check(StickRoute.clampSpeed(90) == 100 && StickRoute.clampSpeed(-5) == 25 && StickRoute.clampSpeed(999) == 500
                && StickRoute.speedIndex(150) == 4 && StickRoute.SPEEDS.length == 9 && StickRoute.SPEED_NAMES.length == 9, "saved speed snaps to 25, 50, 75, 100, 150, 200, 300, 400 or 500 (2.11)");
        check(StickRoute.clampSpeed(250) == 200 || StickRoute.clampSpeed(250) == 300, "a value between two steps goes to a neighbour")
;
        check(Math.abs(StickRoute.detent(500) * 5 - StickRoute.detent(100)) < 1e-9 && Math.abs(Math.toDegrees(StickRoute.detent(500)) - 12.0) < 1e-6, "500 percent is 12 degrees of stick per item, five times the default speed");
        check(StickRoute.rate(StickRoute.CTX_WHEEL, 500) == 25f && StickRoute.rate(StickRoute.CTX_GRID, 500) == 30f && StickRoute.rate(StickRoute.CTX_LIST, 300) == 15f, "the per second cap scales to 500 percent (wheel 25/s, grid 30/s)");
        boolean up = true;
        for (int i = 1; i < StickRoute.SPEEDS.length; i++) up &= StickRoute.SPEEDS[i] > StickRoute.SPEEDS[i - 1] && StickRoute.speedIndex(StickRoute.SPEEDS[i]) == i;
        check(up, "the speed steps rise and each maps back to its own index");
        StickDial d = new StickDial();
        int t = turn(d, 0, 2 * Math.PI, 64, 0);
        check(t == 6, "default: one full turn is 6 items (2.1 was 12), got " + t);
        d.update(0, 0, 900);
        d.detent = StickRoute.detent(25);
        t = turn(d, 0, 2 * Math.PI, 64, 1000);
        check(t == 1, "25 percent: one turn is 1.5 items, got " + t);
        d.update(0, 0, 1900);
        d.update(0.2f, 0.2f, 1950);
        check(!d.active, "the deadzone is unchanged");
        // the per second cap
        StickRoute.Limiter lim = new StickRoute.Limiter();
        int moved = 0;
        for (long ms = 0; ms < 2000; ms += 16) moved += lim.take(3, ms, StickRoute.rate(StickRoute.CTX_WHEEL, 100));
        check(moved >= 8 && moved <= 11, "a wild spin asking 3 items a frame moves about 10 items in 2 s at 5/s, got " + moved);
        lim.reset();
        moved = 0;
        for (long ms = 0; ms < 2000; ms += 16) moved += lim.take(-3, ms, StickRoute.rate(StickRoute.CTX_WHEEL, 200));
        check(moved <= -16 && moved >= -21, "200 percent doubles the cap, backwards works, got " + moved);
        lim.reset();
        check(lim.take(1, 0, 5f) == 1 && lim.take(1, 100, 5f) == 0 && lim.take(1, 200, 5f) == 1, "one item, then wait 200 ms for the next at 5/s");
        check(lim.take(0, 5000, 5f) == 0 && lim.take(4, 5000, 5f) == 1, "no burst after a pause: still one item at a time");
        check(lim.take(1, 9000, 0f) == 0 && lim.take(1, 9000, Float.NaN) == 0, "a zero or broken rate moves nothing");
        // hum follows the real items per second
        HumModel hm = new HumModel();
        hm.scaleTo(5f);
        long now = 0;
        for (int i = 0; i < 10; i++) { hm.onTick(now); for (int f = 0; f < 12; f++) { hm.update(now, 1 / 60f); now += 1000 / 60; } now += 0; }
        check(hm.level > 0.9f, "at the 5/s cap the hum reaches full (" + hm.level + "), it does not stop between items");
        HumModel slow = new HumModel();
        slow.scaleTo(5f);
        now = 0;
        for (int i = 0; i < 6; i++) { slow.onTick(now); for (int f = 0; f < 24; f++) { slow.update(now, 1 / 60f); now += 1000 / 60; } }
        check(slow.level > 0.3f && slow.level < 0.7f, "2.5 items per second gives about half hum (" + slow.level + ")");
        // grid edges
        check(StickRoute.gridStep(0, 12, -1) == 0 && StickRoute.gridStep(11, 12, 1) == 11 && StickRoute.gridStep(4, 12, 1) == 5, "grid: next and previous stop at the first and last item (no wrap)");
        check(StickRoute.gridMove(0, 12, 5, 0, -1) == 0 && StickRoute.gridMove(2, 12, 5, 0, -1) == 2, "grid: up from the top row stays in the grid (never the top bar)");
        check(StickRoute.gridMove(10, 12, 5, 0, 1) == 10 && StickRoute.gridMove(7, 12, 5, 0, 1) == 11, "grid: down from the last row stays (never the dock), a short last row clamps");
        check(StickRoute.gridMove(5, 12, 5, -1, 0) == 5 && StickRoute.gridMove(4, 12, 5, 1, 0) == 4 && StickRoute.gridMove(11, 12, 5, 1, 0) == 11, "grid: left and right stop at the row edges");
        check(StickRoute.gridMove(6, 12, 5, 1, 0) == 7 && StickRoute.gridMove(6, 12, 5, 0, -1) == 1, "grid: inside moves work");
        // lists (Setup, quick menu)
        check(StickRoute.listStep(0, 8, -1) == 0 && StickRoute.listStep(7, 8, 1) == 7 && StickRoute.listStep(3, 8, 1) == 4, "lists: up and down only inside the rows");
        check(StickRoute.listStep(-1, 8, -1) == -1 && StickRoute.listStep(-1, 8, 1) == 0 && StickRoute.listStep(0, 8, -1) != -1, "lists: the spinner never moves onto the tab strip");
        check(StickRoute.listStep(0, 0, 1) == 0, "an empty list is safe");
    }

    // ---------------------------------------------------------------- 2.3 gyro calibration

    static final java.util.Random RNG = new java.util.Random(42);

    static float noise(float sd) { return (float) (RNG.nextGaussian() * sd); }

    static void sensorClock() {
        section("2.3 gyro: dt from sensor timestamps");
        SensorClock k = new SensorClock();
        long t = 5_000_000_000L;
        boolean skipped = true;
        for (int i = 0; i < SensorClock.SKIP; i++) { if (k.dt(t) != 0f) skipped = false; t += 10_000_000L; }
        check(skipped, "the first " + SensorClock.SKIP + " events are dropped");
        check(near(k.dt(t), 0.01f), "a 10 ms step reads 0.01 s");
        t += 5_000_000L;
        float a = k.dt(t);
        t += 15_000_000L;
        float b = k.dt(t);
        check(near(a, 0.005f) && near(b, 0.015f), "uneven steps each keep their own length (" + a + ", " + b + ")");
        check(k.dt(t - 1_000_000L) == 0f && k.dt(t) == 0f, "a step backwards or a duplicate is skipped");
        t += 120_000_000L;
        check(near(k.dt(t), SensorClock.MAX_DT_S), "a late sample is clamped to " + SensorClock.MAX_DT_S + " s");
        t += 600_000_000L;
        check(k.dt(t) == 0f, "a pause longer than " + SensorClock.GAP_S + " s is not integrated");
        t += 4_000_000L;
        check(near(k.dt(t), 0.004f), "and the next sample continues normally");
        SensorClock r = new SensorClock();
        long u = 0;
        for (int i = 0; i < 400; i++) { r.dt(u); u += 4_000_000L; }
        check(Math.abs(r.hz - 250f) < 5f, "sample rate estimate (" + r.hz + " Hz at 4 ms)");
    }

    /** Feeds still samples: bias plus noise on the gyro, 1 g plus noise on the accelerometer. */
    static long feedStill(Stillness s, long t, float secs, float bias, float gyroSd, float accelG) {
        int n = Math.round(secs * 200);
        for (int i = 0; i < n; i++) {
            t += 5_000_000L;
            s.feedAccel(noise(0.02f), accelG * Attitude.G + noise(0.02f), noise(0.02f), 0.005f, t);
            s.feedGyro(bias + noise(gyroSd), -bias + noise(gyroSd), noise(gyroSd), 0.005f, t);
        }
        return t;
    }

    static void stillness() {
        section("2.3 gyro: stillness detector");
        Stillness s = new Stillness();
        long t = feedStill(s, 0, 1f, 0.02f, 0.003f, 1f);
        check(s.still(), "a resting controller with a gyro bias and noise is still (the bias is not motion)");
        check(s.stillFor(t) > 0.5f && s.stillFor(t) < 0.8f, "still time counts up (" + s.stillFor(t) + " s)");
        t += 5_000_000L;
        s.feedGyro(0.6f, 0.1f, 0f, 0.005f, t);
        check(!s.still() && s.stillFor(t) == 0f, "a bump ends it at once");
        t = feedStill(s, t, 0.1f, 0.02f, 0.003f, 1f);
        check(!s.still(), "and it does not come back right away");
        t = feedStill(s, t, 1.5f, 0.02f, 0.003f, 1f);
        check(s.still(), "it comes back once the controller settles");
        // hand held: shaky gyro
        Stillness h = new Stillness();
        feedStill(h, 0, 2f, 0f, 0.05f, 1f);
        check(!h.still(), "a hand held controller (0.05 rad/s jitter) is not still");
        // slow steady turn: low variance but a real rate
        Stillness turn = new Stillness();
        long tt = 0;
        for (int i = 0; i < 400; i++) { tt += 5_000_000L; turn.feedAccel(0, Attitude.G, 0, 0.005f, tt); turn.feedGyro(0, 0.3f, 0, 0.005f, tt); }
        check(!turn.still(), "a slow steady turn (0.3 rad/s) is not mistaken for still");
        // accelerometer far from 1 g, or not steady
        Stillness acc = new Stillness();
        feedStill(acc, 0, 2f, 0f, 0.003f, 1.06f);
        check(!acc.still(), "an accelerometer 6 percent off 1 g is not still");
        Stillness ok2 = new Stillness();
        feedStill(ok2, 0, 2f, 0f, 0.003f, 1.02f);
        check(ok2.still(), "2 percent off (sensor scale error) still counts");
        // gyro only (sway on the home screen registers no accelerometer)
        Stillness g = new Stillness();
        g.hasAccel = false;
        long tg = 0;
        for (int i = 0; i < 300; i++) { tg += 5_000_000L; g.feedGyro(0.01f + noise(0.003f), noise(0.003f), noise(0.003f), 0.005f, tg); }
        check(g.still(), "gyro only mode works without an accelerometer");
    }

    static void gyroBias() {
        section("2.3 gyro: bias estimate and online zero rate");
        float bx = 0.021f, by = -0.013f, bz = 0.008f, sd = 0.003f;
        Stillness s = new Stillness();
        GyroBias b = new GyroBias();
        long t = 0;
        boolean done = false;
        int samples = 0;
        while (!done && samples < 4000) {
            t += 5_000_000L;
            float x = bx + noise(sd), y = by + noise(sd), z = bz + noise(sd);
            s.feedAccel(noise(0.02f), Attitude.G, noise(0.02f), 0.005f, t);
            s.feedGyro(x, y, z, 0.005f, t);
            b.feedAccel(0, Attitude.G, 0);
            done = b.feed(x, y, z, 0.005f, s.still());
            samples++;
            // a bump in the middle of the collection
            if (samples == 300) {
                for (int i = 0; i < 20; i++) { t += 5_000_000L; s.feedGyro(0.9f, 0.4f, -0.2f, 0.005f, t); b.feed(0.9f, 0.4f, -0.2f, 0.005f, s.still()); }
                check(b.progress() == 0f && b.restarts == 1, "motion restarts the collection");
            }
        }
        check(done && samples * 0.005f < 6.5f, "done 3 s after it settles again (" + samples * 0.005f + " s in all, with the bump at 1.5 s)");
        float err = Math.max(Math.abs(b.bx() - bx), Math.max(Math.abs(b.by() - by), Math.abs(b.bz() - bz)));
        check(err < 0.0008f, "bias estimate within 0.0008 rad/s of the truth (" + err + "), the bump is not in it");
        check(b.hasGravity() && near(b.gy(), Attitude.G), "gravity is averaged with it");
        // online tracking
        GyroCal c = new GyroCal();
        Stillness s2 = new Stillness();
        long t2 = 0;
        int nudged = 0;
        for (int i = 0; i < 200 * 60; i++) {
            t2 += 5_000_000L;
            float x = 0.01f + noise(sd), y = -0.005f + noise(sd), z = noise(sd);
            s2.feedAccel(0, Attitude.G, 0, 0.005f, t2);
            s2.feedGyro(x, y, z, 0.005f, t2);
            float sf = s2.stillFor(t2);
            boolean n = GyroBias.online(c, x, y, z, 0.005f, sf);
            if (n) nudged++;
            if (i == 150) check(nudged == 0, "no online update before 1 s of stillness");
        }
        check(nudged > 0 && Math.abs(c.bx - 0.01f) < 0.002f && Math.abs(c.by + 0.005f) < 0.002f, "60 s still: online bias converges (" + c.bx + ", " + c.by + ")");
        float before = c.bx;
        check(!GyroBias.online(c, 0.5f, 0, 0, 0.005f, 0f) && c.bx == before, "never updates while moving");
        check(!GyroBias.online(c, before + 0.1f, 0, 0, 0.005f, 5f) && c.bx == before, "a slow steady turn far from the bias is rejected even when 'still'");
        GyroCal big = new GyroCal();
        big.setBias(0.5f, Float.NaN, -0.4f);
        check(big.bx == GyroCal.MAX_BIAS && big.by == 0f && big.bz == -GyroCal.MAX_BIAS, "bias is clamped and NaN safe");
    }

    /** Plays one model rotation through a true raw mapping (raw = M^T * model) into an AxisMap gesture. */
    static int gesture(AxisMap am, float[] mTrue, int modelAxis, float rate, float secs, float crossTalk) {
        int st = AxisMap.RUNNING;
        am.begin();
        for (int i = 0; i < Math.round(secs * 200) && st == AxisMap.RUNNING; i++) {
            float[] mod = new float[3];
            mod[modelAxis] = rate;
            mod[(modelAxis + 1) % 3] = crossTalk * rate + noise(0.01f);
            float[] raw = new float[3];
            for (int r = 0; r < 3; r++) raw[r] = mTrue[0 * 3 + r] * mod[0] + mTrue[1 * 3 + r] * mod[1] + mTrue[2 * 3 + r] * mod[2];
            st = am.feed(raw[0], raw[1], raw[2], 0.005f);
        }
        return st;
    }

    static boolean solve(float[] mTrue, float[] out, boolean wrongYaw) {
        AxisMap am = new AxisMap();
        int[] ax = new int[3], sg = new int[3];
        for (int g = 0; g < 3; g++) {
            float rate = AxisMap.MODEL_SIGN[g] * 1.5f * (wrongYaw && g == 2 ? -1 : 1);
            if (gesture(am, mTrue, AxisMap.MODEL_AXIS[g], rate, 1f, 0.2f) != AxisMap.DONE) return false;
            ax[g] = am.axis;
            sg[g] = am.sign;
        }
        return AxisMap.build(ax, sg, out);
    }

    static void axisMap() {
        section("2.3 gyro: axis mapping from guided rotations");
        float[] id = {1, 0, 0, 0, 1, 0, 0, 0, 1}, m = new float[9];
        check(solve(id, m, false) && Arrays.equals(m, id), "the 2.2 frame (raw = model) comes out as the identity");
        float[] swapped = {0, 0, -1, 1, 0, 0, 0, -1, 0};   // model X = -raw2, Y = raw0, Z = -raw1
        check(Math.abs(Mat3D.det(swapped) - 1f) < 1e-6f, "(test matrix is a proper rotation)");
        check(solve(swapped, m, false) && Arrays.equals(m, swapped), "swapped axes with sign flips are found: " + Arrays.toString(m));
        float[] flip = {-1, 0, 0, 0, 1, 0, 0, 0, -1};
        check(solve(flip, m, false) && Arrays.equals(m, flip), "two flipped signs are found");
        check(!solve(id, m, true), "turning right instead of left gives a mirrored frame and is refused");
        AxisMap am = new AxisMap();
        am.begin();
        int st = AxisMap.RUNNING;
        for (int i = 0; i < 400 && st == AxisMap.RUNNING; i++) st = am.feed(1f, 1f, 0f, 0.005f);
        check(st == AxisMap.MIXED, "a turn split evenly over two axes asks to redo");
        am.begin();
        for (int i = 0; i < 40; i++) am.feed(0.5f, 0, 0, 0.005f);
        check(am.axis < 0 && am.progress() > 0.1f && am.progress() < 0.5f, "a small turn is only progress (" + am.progress() + ")");
        check(!AxisMap.build(new int[]{0, 0, 1}, new int[]{1, 1, 1}, m), "the same raw axis twice is refused");
        check(!AxisMap.validSignedPerm(new float[]{1, 0, 0, 0, 1, 0, 0, 0, 0}) && !AxisMap.validSignedPerm(new float[]{1, 1, 0, 0, 1, 0, 0, 0, 1})
                && !AxisMap.validSignedPerm(new float[]{0.5f, 0, 0, 0, 1, 0, 0, 0, 1}) && AxisMap.validSignedPerm(id), "signed permutation check");
    }

    static void accelGate() {
        section("2.3 gyro: accelerometer gating");
        float g = Attitude.G;
        Attitude a = new Attitude();
        a.accel(0, g, 0, 0.01f);
        check(!a.accel(0, 1.15f * g, 0, 0.01f), "1.15 g (accepted in 2.2) is now ignored");
        check(a.accel(0, 1.05f * g, 0, 0.01f), "1.05 g is used");
        check(!a.accel(0, 0.85f * g, 0, 0.01f), "0.85 g is ignored");
        a.gyro(0.8f, 0, 0, 0.01f);
        check(!a.accel(0, g, 0, 0.01f), "no correction while turning fast (0.8 rad/s)");
        a.gyro(0.2f, 0, 0, 0.01f);
        check(a.accel(0, g, 0, 0.01f), "correction comes back when the turn is slow");
        Attitude y = new Attitude();
        y.yaw = 0.5f;
        for (int i = 0; i < 800; i++) y.settleYaw(0.01f);   // 8 s still
        check(y.relYaw() > 0.15f && y.relYaw() < 0.22f, "still: yaw settles back with an 8 s time constant (" + y.relYaw() + ")");
        Attitude y2 = new Attitude();
        y2.yaw = 0.5f;
        check(near(y2.relYaw(), 0.5f), "without settleYaw (moving) yaw stays");
    }

    static void gyroDrift() {
        section("2.3 gyro: 60 s drift, uncalibrated vs calibrated");
        float bx = 0.02f, by = 0.015f, bz = -0.01f, sd = 0.002f, dt = 0.005f;
        // uncalibrated: the 2.2 path
        Attitude u = new Attitude();
        GyroCal none = new GyroCal();
        float[] o = new float[3];
        for (int i = 0; i < 200 * 60; i++) {
            none.gyro(bx + noise(sd), by + noise(sd), bz + noise(sd), o);
            u.gyro(o[0], o[1], o[2], dt);
        }
        float uDeg = Math.abs(Attitude.deg(u.yaw));
        check(uDeg > 30f, "uncalibrated: yaw drifts " + uDeg + " degrees in a minute");
        // calibrated: 3 s bias step, then a minute of integration (no yaw settle, no accel help)
        Stillness s = new Stillness();
        GyroBias b = new GyroBias();
        long t = 0;
        while (!b.done) {
            t += 5_000_000L;
            float x = bx + noise(sd), y = by + noise(sd), z = bz + noise(sd);
            s.feedAccel(0, Attitude.G, 0, dt, t);
            s.feedGyro(x, y, z, dt, t);
            b.feed(x, y, z, dt, s.still());
        }
        GyroCal c = new GyroCal();
        c.setBias(b.bx(), b.by(), b.bz());
        Attitude k = new Attitude();
        for (int i = 0; i < 200 * 60; i++) {
            c.gyro(bx + noise(sd), by + noise(sd), bz + noise(sd), o);
            k.gyro(o[0], o[1], o[2], dt);
        }
        float kDeg = Math.max(Math.abs(Attitude.deg(k.yaw)), Math.max(Math.abs(Attitude.deg(k.pitch)), Math.abs(Attitude.deg(k.roll))));
        check(kDeg < 1f, "calibrated: under 1 degree per minute on every axis (" + kDeg + ")");
        // the same with online tracking from a rough start (bias half wrong)
        GyroCal on = new GyroCal();
        on.setBias(bx * 0.5f, by * 0.5f, bz * 0.5f);
        Stillness s2 = new Stillness();
        Attitude w = new Attitude();
        long t2 = 0;
        for (int i = 0; i < 200 * 120; i++) {
            t2 += 5_000_000L;
            float x = bx + noise(sd), y = by + noise(sd), z = bz + noise(sd);
            s2.feedAccel(0, Attitude.G, 0, dt, t2);
            s2.feedGyro(x, y, z, dt, t2);
            GyroBias.online(on, x, y, z, dt, s2.stillFor(t2));
            if (i >= 200 * 60) { on.gyro(x, y, z, o); w.gyro(o[0], o[1], o[2], dt); }
        }
        float wDeg = Math.abs(Attitude.deg(w.yaw));
        check(wDeg < 1f, "online tracking alone fixes a half wrong bias: second minute drifts " + wDeg + " degrees");
    }

    static void gyroCalStore() {
        section("2.3 gyro: saved calibration");
        GyroCal c = new GyroCal();
        float[] o = new float[3];
        c.gyro(0.1f, 0.2f, 0.3f, o);
        check(near(o[0], 0.1f) && near(o[1], 0.2f) && near(o[2], 0.3f) && !c.calibrated() && "NOT CALIBRATED".equals(c.status()),
                "not calibrated = zero bias and the identity, the 2.2 behavior");
        c.setBias(0.01f, -0.02f, 0.003f);
        float[] sw = {0, 0, -1, 1, 0, 0, 0, -1, 0};
        System.arraycopy(sw, 0, c.m, 0, 9);
        c.gyro(0.01f + 0.5f, -0.02f, 0.003f, o);
        check(near(o[1], 0.5f) && near(o[0], 0) && near(o[2], 0), "bias is removed before the axis map");
        c.savedAt = 1793491200000L;   // 2026-11-01
        c.checkErr = 1.5f;
        GyroCal d = GyroCal.decode(c.encode());
        check(d != null && near(d.bx, 0.01f) && near(d.by, -0.02f) && Arrays.equals(d.m, sw) && d.savedAt == c.savedAt && near(d.checkErr, 1.5f), "encode and decode round trip");
        check(d != null && d.status().startsWith("CALIBRATED 2026-1") && d.status().contains("BIAS 0.0226 RAD/S"), "status line: " + (d == null ? "" : d.status()));
        check(GyroCal.decode(null) == null && GyroCal.decode("v1;1;2") == null && GyroCal.decode("garbage") == null, "broken values are ignored");
        check(GyroCal.decode(c.encode().replace(";0;0;-1;1;0;0;0;-1;0;", ";1;1;0;0;1;0;0;0;1;")) == null, "a stored matrix that is not a signed permutation is refused");
        check("gyrocal.054c.0ce6.abc123".equals(GyroCal.key(0x054c, 0x0ce6, "abc-123")), "key from vendor, product and descriptor: " + GyroCal.key(0x054c, 0x0ce6, "abc-123"));
        check(GyroCal.key(1, 2, null).endsWith(".any"), "no descriptor still gives a key");
        GyroCal g = new GyroCal();
        check(g.gravityCheck(0, Attitude.G, 0) == 1 && g.accOk && g.accSign == 1, "flat: gravity on +Y, accelerometer trusted");
        check(g.gravityCheck(0, -Attitude.G, 0) == -1 && g.accOk && g.accSign == -1, "gravity on -Y: accelerometer sign flipped");
        g.accel(0, -Attitude.G, 0, o);
        check(near(o[1], Attitude.G), "and the flip is applied to readings");
        check(g.gravityCheck(Attitude.G, 0, 0) == 0 && !g.accOk, "gravity on another axis: tilt correction off");
    }

    static void calCheck() {
        section("2.3 gyro: CHECK step");
        CalCheck k = new CalCheck();
        check(near(k.angleDeg((float) (Math.PI / 2), 0, 0), 90f) && near(k.angleDeg(0, 0, 0), 0f), "rotation angle of a pose");
        check(Math.abs(k.angleDeg(0.3f, 0.4f, 0) - k.angleDeg(0, 0.3f, 0.4f)) < 1.5f, "angle is about the same whatever the axis");
        check(!k.feed(0, true, 0.2f) && k.state == CalCheck.HOLD, "waits for stillness");
        check(k.feed(0, true, 0.6f) && k.state == CalCheck.ROTATE, "then takes the start pose (caller recenters)");
        for (int d = 0; d <= 90; d += 10) k.feed(d, false, 0);
        check(k.state == CalCheck.RETURN, "a 90 degree turn is seen");
        k.feed(88, true, 1.2f);
        check(k.state == CalCheck.RETURN, "holding still while still turned does not finish");
        k.feed(2.5f, true, 0.9f);
        check(k.state == CalCheck.DONE && near(k.errDeg, 2.5f), "back and still: leftover error " + k.errDeg + " degrees");
        k.reset();
        check(k.state == CalCheck.HOLD && k.errDeg < 0, "REDO starts over");
    }

    // ---------------------------------------------------------------- 2.3 options flyout animation

    static final class FakeClock implements TextReveal.Clock {
        long t;
        @Override public long now() { return t; }
    }

    static final class Count implements TextReveal.Callback {
        int ticks, frames, done;
        float minRate = 9, maxRate = 0;
        @Override public void onRevealTick(float rate) { ticks++; minRate = Math.min(minRate, rate); maxRate = Math.max(maxRate, rate); }
        @Override public void onRevealFrame() { frames++; }
        @Override public void onRevealDone() { done++; }
    }

    static TextReveal reveal(FakeClock ck, Count cb, int mode, boolean rtl) {
        TextReveal r = new TextReveal(ck, cb);
        r.p.mode = mode;
        r.p.rtl = rtl;
        return r;
    }

    static void typeProgress() {
        section("2.3 text reveal: typing progress, stagger, direction");
        FakeClock ck = new FakeClock();
        Count cb = new Count();
        TextReveal r = reveal(ck, cb, TextReveal.TYPE, false);
        r.start(Arrays.asList("MOVE LEFT", "REMOVE FROM DOCK X", "HIDE"), 0);
        check(r.visible(0) == 0 && r.visible(1) == 0 && r.running(), "nothing shows at the start");
        check(r.rowMs(0) == 100 && r.rowMs(1) == 200 && r.rowMs(2) == 44, "90 characters a second: 9 chars take 100 ms, 18 take 200 ms");
        ck.t = 50; r.step();
        check(r.visible(0) == 4 && r.visible(1) == 0 && r.cursor(0) && !r.cursor(1), "row 0 half typed with the cursor on it, row 1 not started (70 ms stagger)");
        ck.t = 120; r.step();
        check(r.visible(0) == 9 && r.visible(1) == 4 && !r.cursor(0) && r.cursor(1) && r.visible(2) == 0, "rows overlap: row 0 done (cursor gone), row 1 typing, row 2 waiting");
        ck.t = 155; r.step();
        check(r.visible(1) > 0 && r.visible(2) > 0 && r.visible(1) < 18, "two rows type at the same time");
        check(r.totalMs() == 270, "total time is the last row's end (" + r.totalMs() + " ms)");
        ck.t = 270; r.step();
        check(!r.running() && r.visible(1) == 18 && r.visible(2) == 4 && cb.done == 1, "done once, everything shows");
        check(r.visible(7) == Integer.MAX_VALUE && r.alpha(7) == 1f, "lines it does not know show in full");
        // direction: RIGHT TO LEFT types the same count from the other end (the view draws the tail), FADE slides the other way
        FakeClock c2 = new FakeClock();
        TextReveal l = reveal(c2, new Count(), TextReveal.FADE, false), rt = reveal(c2, new Count(), TextReveal.FADE, true);
        l.start(Arrays.asList("ABCDEFGH"), 0);
        rt.start(Arrays.asList("ABCDEFGH"), 0);
        c2.t = 30; l.step(); rt.step();
        check(l.slide(0) < 0 && rt.slide(0) > 0 && Math.abs(l.slide(0) + rt.slide(0)) < 1e-5f, "FADE slides along the typing direction: from the left for LTR, from the right for RTL");
        check(l.alpha(0) > 0f && l.alpha(0) < 1f && l.visible(0) == 8, "FADE: a whole line, fading in");
        c2.t = 180; l.step();
        check(near(l.alpha(0), 1f) && near(l.slide(0), 0f), "FADE ends at full alpha, no offset");
        FakeClock c3 = new FakeClock();
        TextReveal tr = reveal(c3, new Count(), TextReveal.TYPE, true);
        tr.start(Arrays.asList("ABCDEFGHIJ"), 20);
        c3.t = 20 + 55; tr.step();
        check(tr.visible(0) == 4 && tr.cursor(0), "RIGHT TO LEFT with a 20 ms delay: same progress, counted from the delay");
        // stagger and row caps for long lists and long lines
        FakeClock c4 = new FakeClock();
        TextReveal big = reveal(c4, new Count(), TextReveal.TYPE, false);
        String[] many = new String[12];
        Arrays.fill(many, "OPTION");
        big.start(Arrays.asList(many), 0);
        check(big.stagger() == 280 / 11, "12 rows: the stagger shrinks so the last row starts within 280 ms (" + big.stagger() + " ms)");
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 100; i++) longLine.append('X');
        big.start(Arrays.asList(longLine.toString()), 0);
        check(big.rowMs(0) == 320, "a 100 character line types in 320 ms at most, not 1.1 s");
        TextReveal none = reveal(c4, new Count(), TextReveal.NONE, false);
        Count nc = new Count();
        none = new TextReveal(c4, nc);
        none.p.mode = TextReveal.NONE;
        none.start(Arrays.asList("A", "B"), 0);
        check(!none.running() && none.visible(1) == 1 && nc.done == 1, "NONE is instant");
        TextReveal empty = reveal(c4, new Count(), TextReveal.TYPE, false);
        empty.start(new ArrayList<String>(), 0);
        check(!empty.running(), "no lines: nothing to run");
    }

    static void tickCap() {
        section("2.3 type_tick scheduler and its cap");
        FakeClock ck = new FakeClock();
        Count cb = new Count();
        TextReveal r = reveal(ck, cb, TextReveal.TYPE, false);
        r.p.cps = 200;
        r.p.maxRowMs = 10000;
        String[] rows = new String[8];
        Arrays.fill(rows, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123");   // 30 chars, 150 ms each at 200 cps
        r.start(Arrays.asList(rows), 0);
        int[] at = new int[400];
        int n = 0;
        for (ck.t = 0; ck.t <= 1000; ck.t += 1) {          // a very fast display: 1 ms frames
            int before = cb.ticks;
            r.step();
            if (cb.ticks > before && n < at.length) at[n++] = (int) ck.t;
        }
        int worst = 0;
        for (int i = 0; i < n; i++) { int k = 0; for (int j = i; j < n && at[j] - at[i] < 1000; j++) k++; worst = Math.max(worst, k); }
        check(n > 10 && worst <= TickScheduler.MAX_PER_SEC, "8 rows at 200 cps: " + n + " ticks, at most " + worst + " in any second (cap 40)");
        boolean gaps = true;
        for (int i = 1; i < n; i++) if (at[i] - at[i - 1] < TickScheduler.MIN_GAP_MS) gaps = false;
        check(gaps, "ticks are at least 25 ms apart");
        check(cb.minRate >= 1f - TickScheduler.JITTER - 1e-4f && cb.maxRate <= 1f + TickScheduler.JITTER + 1e-4f && cb.maxRate - cb.minRate > 0.05f,
                "pitch jitter stays in 0.9 to 1.1 and actually varies (" + cb.minRate + " to " + cb.maxRate + ")");
        check(3200 * (1f - TickScheduler.JITTER) >= 2500 && 3200 * (1f + TickScheduler.JITTER) <= 4000, "the 3.2 kHz tick stays in 2.5 to 4 kHz with jitter");
        // one row: one tick per 2 characters when there is room under the cap
        FakeClock c2 = new FakeClock();
        Count one = new Count();
        TextReveal s = reveal(c2, one, TextReveal.TYPE, false);
        s.p.cps = 20;                                         // 10 chars over 500 ms: 5 ticks owed, 100 ms apart
        s.start(Arrays.asList("ABCDEFGHIJ"), 0);
        for (c2.t = 0; c2.t <= 600; c2.t += 16) s.step();
        check(one.ticks == 5, "one row of 10 characters typed slowly: 5 ticks (one per 2 characters), got " + one.ticks);
        Count quiet = new Count();
        TextReveal q = reveal(c2, quiet, TextReveal.TYPE, false);
        q.p.ticks = false;
        c2.t = 0;
        q.start(Arrays.asList("ABCDEFGHIJ"), 0);
        for (; c2.t <= 600; c2.t += 16) q.step();
        Count fade = new Count();
        TextReveal f = reveal(c2, fade, TextReveal.FADE, false);
        c2.t = 0;
        f.start(Arrays.asList("ABCDEFGHIJ"), 0);
        for (; c2.t <= 600; c2.t += 16) f.step();
        check(quiet.ticks == 0 && fade.ticks == 0, "SOUND off, and FADE, make no ticks");
        // the cap is shared by every reveal on screen
        TickScheduler shared = new TickScheduler();
        FakeClock c3 = new FakeClock();
        Count a = new Count(), b = new Count();
        TextReveal ra = reveal(c3, a, TextReveal.TYPE, false), rb = reveal(c3, b, TextReveal.TYPE, false);
        ra.share(shared); rb.share(shared);
        ra.p.cps = rb.p.cps = 200;
        ra.start(Arrays.asList(rows), 0);
        rb.start(Arrays.asList(rows), 0);
        for (c3.t = 0; c3.t < 1000; c3.t += 1) { ra.step(); rb.step(); }
        check(a.ticks + b.ticks <= TickScheduler.MAX_PER_SEC + 1, "two menus at once share the 40 per second cap (" + (a.ticks + b.ticks) + ")");
        TickScheduler t = new TickScheduler();
        check(!t.offer(0, 0) && t.offer(3, 0) && !t.offer(3, 10) && t.offer(1, 25), "offer: nothing owed plays nothing, then one tick per 25 ms");
    }

    static void revealInterrupt() {
        section("2.3 interrupts and reduce effects");
        FakeClock ck = new FakeClock();
        Count cb = new Count();
        TextReveal r = reveal(ck, cb, TextReveal.TYPE, false);
        r.start(Arrays.asList("MOVE LEFT", "PIN TO DOCK", "APP INFO"), 0);
        ck.t = 30; r.step();
        int ticks = cb.ticks;
        check(r.finish() && !r.running() && r.visible(0) == 9 && r.visible(2) == 8 && !r.cursor(0) && cb.done == 1,
                "a key press mid typing finishes it at once: all text, no cursor, done fires once");
        ck.t = 60;
        check(!r.step() && cb.ticks == ticks && !r.finish() && cb.done == 1, "after that: no more steps, no more ticks, a second finish does nothing");
        r.clear(80);
        ck.t = 100; r.step();
        check(r.running() && r.clearing() && r.visible(1) > 0 && r.visible(1) < 11, "close: the text untypes (" + r.visible(1) + " of 11 left at half time)");
        check(r.finish() && r.visible(1) == 0 && r.visible(9) == 0, "a key during the close clears it at once");
        r.start(Arrays.asList("AB"), 0);
        ck.t = 140; r.step();
        r.clear(80);
        ck.t = 220; r.step();
        check(!r.running() && r.visible(0) == 0, "the clear ends empty after its time");
        // reduce effects: whatever is chosen, a short fade at most, no stagger, no cursor, no ticks
        AnimSpec spec = AnimConfig.flyout();
        TextReveal.Params p = new TextReveal.Params();
        AnimConfig.reveal(spec, AnimConfig.SETUP, true, p);
        FakeClock c2 = new FakeClock();
        Count rc = new Count();
        TextReveal red = new TextReveal(c2, rc);
        red.p.set(p);
        String[] rows = new String[8];
        Arrays.fill(rows, "A LONG SETUP ROW LABEL");
        red.start(Arrays.asList(rows), 0);
        check(red.totalMs() <= AnimConfig.REDUCE_FADE_MS && red.stagger() == 0 && !red.cursor(0), "reduce effects: 8 rows appear together within 120 ms (" + red.totalMs() + " ms)");
        for (c2.t = 0; c2.t <= 200; c2.t += 16) red.step();
        check(rc.ticks == 0 && red.slide(0) == 0f, "reduce effects: no ticks, no slide");
        FlyoutAnim.Params fp = new FlyoutAnim.Params();
        AnimConfig.flyout(spec, AnimConfig.APP_MENU, true, fp);
        FlyoutAnim fa = new FlyoutAnim();
        fa.p.set(fp);
        fa.open(0, false);
        check(!fa.running && fa.glide == 1f && fa.grow == 1f && fa.textDelay() == 0, "reduce effects: no icon glide, the panel is there at once");
        fa.close(10);
        check(!fa.running && fa.gone(), "reduce effects: closes at once");
    }

    static final class MapStore implements AnimSpec.Store {
        final java.util.HashMap<String, String> m = new java.util.HashMap<>();
        @Override public String get(String k) { return m.get(k); }
        @Override public void put(String k, String v) { m.put(k, v); }
        @Override public void remove(String k) { m.remove(k); }
    }

    static void animSpec() {
        section("2.3 animation options schema");
        AnimSpec s = AnimConfig.flyout();
        check(s.options.size() == 10 + AnimConfig.SCREENS.length && s.skipped == 0, "OPTIONS FLYOUT: 10 options plus " + AnimConfig.SCREENS.length + " screen switches, no bad lines");
        AnimOption text = s.get("text"), speed = s.get("speed"), stg = s.get("stagger"), scale = s.get("scale"), dir = s.get("dir");
        check("TYPE".equals(text.choice()) && speed.i() == 90 && stg.i() == 70 && near(scale.f(), 1f) && "LEFT TO RIGHT".equals(dir.choice())
                && s.get("cursor").on() && s.get("glide").on() && "LEFT".equals(s.get("side").choice()) && "SOLID".equals(s.get("style").choice()), "defaults");
        check(speed.min == 40 && speed.max == 200 && stg.min == 0 && stg.max == 200 && scale.min == 0.5 && scale.max == 2, "ranges from the schema");
        check("90 CPS".equals(speed.display()) && "1.00X".equals(scale.display()) && "ON".equals(s.get("sound").display()), "row text: " + speed.display() + ", " + scale.display());
        check(speed.parse("300") && speed.i() == 200 && speed.parse("45") && speed.i() == 50 && !speed.parse("fast") && speed.i() == 50, "INT: clamped, snapped to the step, bad text refused");
        check(text.parse("fade") && text.i() == 1 && dir.parse("right_to_left") && dir.i() == 1 && !text.parse("SPIN") && text.i() == 1, "CHOICE: by name, any case, underscores for spaces");
        check(scale.parse("1.3") && near(scale.f(), 1.25f) && scale.parse("0.1") && near(scale.f(), 0.5f) && scale.parse("2x") && near(scale.f(), 2f), "FLOAT: snapped, clamped, 2x works");
        AnimOption cur = s.get("cursor");
        check(cur.parse("no") && !cur.on() && cur.parse("TRUE") && cur.on() && !cur.parse("maybe"), "BOOL: on off yes no true false 1 0");
        check(scale.steps() == 7 && "0.75X".equals(scale.stepText(1)) && near(scale.f(), 2f), "FLOAT as a values row: 7 steps, stepText leaves the value alone");
        AnimSpec bad = AnimSpec.parse("x", "X", "a | A | CHOICE | ONE | ONE\nb | B | INT | 9..1 | 5\nc | C | NOPE | | 1\nd | D | CHOICE | P, Q | R\ne | E | BOOL | | on\ne | E2 | BOOL | | off\nonly | three | fields\nf | F | COLOR | | #00FF00\nBad Key | G | BOOL | | on\n");
        check(bad.options.size() == 2 && bad.skipped == 7 && bad.get("f").color() == 0xFF00FF00, "bad schema lines are skipped, good ones kept (" + bad.options.size() + " kept, " + bad.skipped + " skipped)");
        AnimOption col = bad.get("f");
        check(col.parse("#80112233") && col.color() == 0x80112233 && !col.parse("112233") && "#80112233".equals(col.text()), "COLOR: #RRGGBB or #AARRGGBB");
        // store round trip under anim.<id>.<key>
        MapStore st = new MapStore();
        AnimSpec a = AnimConfig.flyout();
        a.get("speed").parse("150");
        a.save(st, a.get("speed"));
        a.get("text").parse("FADE");
        a.save(st, a.get("text"));
        check("150".equals(st.get("anim.flyout.speed")) && "FADE".equals(st.get("anim.flyout.text")), "stored as text under anim.flyout.KEY");
        st.put("anim.flyout.stagger", "garbage");
        AnimSpec b2 = AnimConfig.flyout();
        b2.load(st);
        check(b2.get("speed").i() == 150 && b2.get("text").i() == 1 && b2.get("stagger").i() == 70, "load reads saved values, a broken one keeps the default");
        int n = b2.apply("# a theme file\ntext = none\nspeed=120\nbogus = 1\nscale = nope\nscreen.toast = off\n");
        check(n == 3 && b2.get("text").i() == 2 && b2.get("speed").i() == 120 && !b2.get("screen.toast").on(), "a theme can set values by key (" + n + " set, unknown and bad ignored)");
        check(b2.dump().contains("speed = 120\n") && b2.dump().contains("dir = LEFT TO RIGHT\n"), "dump writes the same format back");
        b2.resetAll(st);
        check(b2.get("speed").i() == 90 && st.get("anim.flyout.speed") == null, "reset to defaults clears the store");
    }

    static void animConfig() {
        section("2.3 animation options to parameters");
        AnimSpec s = AnimConfig.flyout();
        TextReveal.Params p = new TextReveal.Params();
        AnimConfig.reveal(s, AnimConfig.SETUP, false, p);
        check(p.mode == TextReveal.TYPE && near(p.cps, 90f) && p.staggerMs == 70 && p.cursor && p.ticks && !p.rtl, "defaults: TYPE, 90 cps, 70 ms stagger, cursor and ticks");
        s.get("scale").parse("2");
        s.get("dir").parse("RIGHT TO LEFT");
        AnimConfig.reveal(s, AnimConfig.SETUP, false, p);
        check(near(p.cps, 45f) && p.staggerMs == 140 && p.fadeMs == 360 && p.maxSpanMs == 560 && p.rtl, "DURATION SCALE 2 doubles every time");
        s.get("sound").parse("off");
        AnimConfig.reveal(s, AnimConfig.SETUP, false, p);
        check(!p.ticks && !AnimConfig.sound(s), "SOUND off: no ticks");
        s.get("text").parse("NONE");
        AnimConfig.reveal(s, AnimConfig.SETUP, true, p);
        check(p.mode == TextReveal.NONE, "reduce effects keeps NONE as NONE");
        s.get("text").parse("TYPE");
        s.get("screen.quick").parse("off");
        AnimConfig.reveal(s, AnimConfig.QUICK, false, p);
        check(p.mode == TextReveal.NONE && !p.ticks, "a screen opted out by name gets no animation");
        AnimConfig.reveal(s, AnimConfig.SETUP, false, p);
        check(p.mode == TextReveal.TYPE, "the others keep theirs");
        FlyoutAnim.Params f = new FlyoutAnim.Params();
        s.get("glide").parse("off");
        AnimConfig.flyout(s, AnimConfig.APP_MENU, false, f);
        check(!f.glide && !f.instant && near(f.scale, 2f), "ICON GLIDE off: the panel still grows, the icon does not travel");
        s.get("screen.appmenu").parse("off");
        AnimConfig.flyout(s, AnimConfig.APP_MENU, false, f);
        check(f.instant && AnimConfig.clearMs(s, AnimConfig.APP_MENU, false) == 0, "app menus opted out: instant, no clear");
        check(AnimConfig.screenFor("tile").equals(AnimConfig.APP_MENU) && AnimConfig.screenFor("dock").equals(AnimConfig.APP_MENU) && AnimConfig.screenFor("icon").equals(AnimConfig.APP_MENU)
                && AnimConfig.screenFor("settings").equals(AnimConfig.SETUP) && AnimConfig.screenFor("custom").equals(AnimConfig.SETUP)
                && AnimConfig.screenFor("install").equals(AnimConfig.INSTALL) && AnimConfig.screenFor("report").equals(AnimConfig.REPORT)
                && AnimConfig.screenFor("homecmds").equals(AnimConfig.HOME_CMDS) && AnimConfig.screenFor(null).equals(AnimConfig.SETUP), "panel kinds map onto screen names");
        check(AnimConfig.isFlyout("tile") && AnimConfig.isFlyout("preview") && !AnimConfig.isFlyout("settings") && !AnimConfig.isFlyout("install"), "the icon flyout is only for menus of one item");
        AnimSpec d = AnimConfig.flyout();
        d.get("side").parse("RIGHT");
        d.get("style").parse("GLASS");
        check(AnimConfig.rightSide(d) && AnimConfig.glass(d) && !AnimConfig.rightSide(AnimConfig.flyout()), "FLYOUT SIDE and PANEL STYLE");
    }

    static void flyoutTimeline() {
        section("2.3 options flyout timeline");
        FlyoutAnim f = new FlyoutAnim();
        f.open(0, false);
        check(f.running && f.glide == 0f && f.grow == 0f && f.textDelay() == FlyoutAnim.TEXT_AT, "opens from the tile");
        f.step(90);
        check(f.glide > 0.5f && f.glide < 1f && f.grow == 0f, "the icon glides first (" + f.glide + "), the panel waits");
        f.step(180);
        check(f.glide == 1f && f.grow > 0f && f.grow < 1f && !f.ready(), "icon docked at 180 ms, the panel is growing out of it");
        f.step(270);
        check(!f.running && f.ready() && f.grow == 1f, "open motion done at 270 ms");
        // the whole open with 3 rows of text at the defaults is about 450 ms
        FakeClock ck = new FakeClock();
        TextReveal r = new TextReveal(ck, new Count());
        r.start(Arrays.asList("ALPHA APP", "MOVE LEFT", "PIN TO DOCK"), f.textDelay());
        long total = f.textDelay() + r.totalMs();
        check(total >= 380 && total <= 480, "open with three short rows takes about 450 ms (" + total + " ms)");
        f.close(1000);
        f.step(1000 + 125);
        check(f.running && f.grow < 0.5f && f.glide > 0.3f, "close: the panel retracts, then the icon flies back");
        f.step(1000 + FlyoutAnim.CLOSE_MS);
        check(f.gone() && f.glide == 0f && f.grow == 0f && FlyoutAnim.CLOSE_MS == 250, "close is 250 ms, faster than open");
        FlyoutAnim h = new FlyoutAnim();
        h.p.scale = 0.5f;
        h.open(0, false);
        h.step(136);
        check(!h.running, "DURATION SCALE 0.5 halves it");
        FlyoutAnim g = new FlyoutAnim();
        g.p.glide = false;
        g.open(0, false);
        g.step(85);
        check(g.glide == 1f && g.iconAlpha > 0f && g.iconAlpha < 1f && g.textDelay() == FlyoutAnim.TEXT_AT_NO_GLIDE, "ICON GLIDE off: the icon fades in at the dock with the panel");
        FlyoutAnim d = new FlyoutAnim();
        d.open(0, true);
        check(!d.running && d.ready() && d.textDelay() == 0, "a flyout replacing a flyout (Change icon) keeps the icon docked");
        FlyoutAnim k = new FlyoutAnim();
        k.open(0, false);
        k.step(50);
        check(k.finish() && k.ready() && k.glide == 1f && !k.finish(), "a key during the open jumps to the end");
        k.close(500);
        k.step(520);
        check(k.finish() && k.gone(), "and during the close it is gone at once");
    }

    static void typeSounds() {
        section("2.3 type_tick and flyout sounds");
        short[] t = Synth.pcm(SoundMap.TYPE_TICK);
        float ms = t.length * 1000f / Synth.RATE;
        int cross = 0;
        for (int i = 1; i < t.length; i++) if ((t[i - 1] < 0) != (t[i] < 0)) cross++;
        float hz = cross / 2f / (t.length / (float) Synth.RATE);
        check(ms >= 6f && ms <= 10f, "type_tick is 6 to 10 ms (" + ms + ")");
        check(hz >= 2500f && hz <= 4000f, "type_tick sits in 2.5 to 4 kHz (" + hz + " Hz)");
        check(Synth.pcm(SoundMap.FLYOUT_OPEN).length < Synth.RATE / 8 && Synth.pcm(SoundMap.FLYOUT_CLOSE).length < Synth.RATE / 8, "flyout sweeps are short");
        check(HapticPatterns.FOR_SOUND[SoundMap.TYPE_TICK] == -1 && HapticPatterns.FOR_SOUND[SoundMap.FLYOUT_OPEN] == HapticPatterns.MENU_OPEN, "no rumble per tick, the flyout rumbles like a menu");
        String[] files = {"type_tick.wav", "flyout_open.ogg"};
        SoundMap m = SoundMap.resolve("p", files, null);
        check("type_tick.wav".equals(m.file[SoundMap.TYPE_TICK]) && "flyout_open.ogg".equals(m.file[SoundMap.FLYOUT_OPEN]) && m.file[SoundMap.FLYOUT_CLOSE] == null, "packs can replace the three new sounds by file name");
    }

    // ------------------------------------------------------------------ 2.4 PS overlay

    static final String DECK = "com.nokoding.deck", GAME = "com.example.game", TV = "com.example.tv";

    static void psDecide() {
        section("2.4 PS overlay: consume or pass");
        HashSet<String> pass = new HashSet<>(Arrays.asList(GAME));
        int M = PsGate.BUTTON_MODE;
        check(PsGate.decide(M, true, true, TV, DECK, pass, false, null, true) == PsGate.OPEN, "PS from a gamepad over a normal app opens the menu");
        check(PsGate.decide(M, true, true, GAME, DECK, pass, false, null, true) == PsGate.PASS, "PS over a pass-through app goes to the app");
        check(PsGate.decide(M, true, false, TV, DECK, pass, false, null, true) == PsGate.PASS, "PS from a non-gamepad device is never taken while the menu is closed");
        check(PsGate.decide(M, true, true, DECK, DECK, pass, false, null, true) == PsGate.PASS, "Visor in front: the key goes to Visor's own screen, which opens its own menu");
        check(PsGate.decide(M, true, true, null, DECK, pass, false, null, true) == PsGate.PASS, "unknown app in front: pass (never steal from a game Visor has not seen yet)");
        check(PsGate.decide(M, true, true, TV, DECK, pass, false, TV, true) == PsGate.PASS, "kill switch bypass: the app keeps it");
        check(PsGate.decide(M, true, true, TV, DECK, pass, false, null, false) == PsGate.PASS, "OVERLAY CAPTURE off: nothing is taken");
        check(PsGate.decide(M, false, true, TV, DECK, pass, false, null, true) == PsGate.PASS, "a repeat alone never opens it");
        int[] others = {PsGate.DPAD_UP, PsGate.DPAD_CENTER, PsGate.BUTTON_A, PsGate.BUTTON_B, PsGate.BACK, PsGate.HOME, PsGate.MENU, PsGate.BUTTON_START, PsGate.VOLUME_UP, 999};
        boolean allPass = true;
        for (int c : others) for (int g = 0; g < 2; g++) allPass &= PsGate.decide(c, true, g == 1, TV, DECK, pass, false, null, true) == PsGate.PASS;
        check(allPass, "menu closed: every key but PS passes, from any device");
        // menu open
        check(PsGate.decide(M, true, true, TV, DECK, pass, true, null, true) == PsGate.CLOSE, "PS again closes the menu");
        check(PsGate.decide(M, true, true, GAME, DECK, pass, true, null, true) == PsGate.CLOSE, "an open menu closes on PS even over a pass-through app");
        check(PsGate.decide(PsGate.HOME, true, false, TV, DECK, pass, true, null, true) == PsGate.CLOSE_PASS, "Home closes the menu and still reaches the system");
        check(PsGate.decide(PsGate.BACK, true, false, TV, DECK, pass, true, null, true) == PsGate.CLOSE
                && PsGate.decide(PsGate.BUTTON_B, true, true, TV, DECK, pass, true, null, true) == PsGate.CLOSE, "Back from the remote and B close it");
        check(PsGate.decide(PsGate.DPAD_UP, true, false, TV, DECK, pass, true, null, true) == PsGate.MOVE_UP
                && PsGate.decide(PsGate.DPAD_DOWN, false, false, TV, DECK, pass, true, null, true) == PsGate.MOVE_DOWN
                && PsGate.decide(PsGate.BUTTON_L1, true, true, TV, DECK, pass, true, null, true) == PsGate.TAB_PREV
                && PsGate.decide(PsGate.BUTTON_R1, true, true, TV, DECK, pass, true, null, true) == PsGate.TAB_NEXT, "D-pad (repeats too) moves, L1 and R1 change the control center section (2.8)");
        check(PsGate.decide(PsGate.DPAD_LEFT, true, false, TV, DECK, pass, true, null, true) == PsGate.LEFT
                && PsGate.decide(PsGate.DPAD_RIGHT, true, false, TV, DECK, pass, true, null, true) == PsGate.RIGHT, "left and right change the volume row");
        check(PsGate.decide(PsGate.BUTTON_A, true, true, TV, DECK, pass, true, null, true) == PsGate.OK
                && PsGate.decide(PsGate.DPAD_CENTER, true, false, TV, DECK, pass, true, null, true) == PsGate.OK
                && PsGate.decide(PsGate.BUTTON_A, false, true, TV, DECK, pass, true, null, true) == PsGate.SWALLOW, "A and OK select once, holding does not repeat");
        check(PsGate.decide(PsGate.BUTTON_START, true, true, TV, DECK, pass, true, null, true) == PsGate.SETUP, "Start opens Visor's Setup");
        check(PsGate.decide(PsGate.BUTTON_X, true, true, TV, DECK, pass, true, null, true) == PsGate.SWALLOW
                && PsGate.decide(PsGate.BUTTON_R2, true, true, TV, DECK, pass, true, null, true) == PsGate.SWALLOW, "other gamepad buttons are eaten while the menu is open");
        check(PsGate.decide(PsGate.VOLUME_UP, true, false, TV, DECK, pass, true, null, true) == PsGate.PASS
                && PsGate.decide(PsGate.POWER, true, false, TV, DECK, pass, true, null, true) == PsGate.PASS
                && PsGate.decide(999, true, false, TV, DECK, pass, true, null, true) == PsGate.PASS, "volume, power and unknown keys pass even with the menu open");
        check(PsGate.decide(PsGate.DPAD_UP, true, false, DECK, DECK, pass, true, null, true) == PsGate.MOVE_UP, "the test menu over Visor is driven by the service too");
        check(!PsGate.consumes(PsGate.PASS) && !PsGate.consumes(PsGate.CLOSE_PASS) && PsGate.consumes(PsGate.OPEN) && PsGate.consumes(PsGate.SWALLOW), "consumes()");
    }

    static void psGate() {
        section("2.4 PS overlay: key pairs and state");
        PsGate g = new PsGate(DECK);
        g.pass = new HashSet<>(Arrays.asList(GAME));
        int M = PsGate.BUTTON_MODE;
        g.setForeground(TV);
        check(g.onKey(M, true, 0, true, 0) == PsGate.OPEN && g.menuOpen, "PS down opens");
        check(g.onKey(M, true, 1, true, 50) == PsGate.SWALLOW, "the PS repeat is eaten");
        check(g.onKey(M, false, 0, true, 100) == PsGate.SWALLOW && !g.eaten(M), "its up is eaten too, then forgotten");
        check(g.onKey(PsGate.BUTTON_A, true, 0, true, 200) == PsGate.OK, "A selects");
        g.closed();
        check(g.onKey(PsGate.BUTTON_A, false, 0, true, 260) == PsGate.SWALLOW, "the A up after the menu closed does not reach the game");
        check(g.onKey(PsGate.BUTTON_A, true, 0, true, 300) == PsGate.PASS && g.onKey(PsGate.BUTTON_A, false, 0, true, 320) == PsGate.PASS, "next A press is the app's again, down and up");
        // a press that started in the app stays the app's
        check(g.onKey(PsGate.DPAD_UP, true, 0, false, 400) == PsGate.PASS, "D-pad down passes while closed");
        g.onKey(M, true, 0, true, 410);
        check(g.menuOpen && g.onKey(PsGate.DPAD_UP, true, 3, false, 420) == PsGate.PASS, "a repeat of a key the app got keeps going to the app");
        check(g.onKey(PsGate.DPAD_UP, false, 0, false, 430) == PsGate.PASS, "and its up reaches the app, so the app sees the full press");
        g.onKey(M, false, 0, true, 440);
        check(g.onKey(PsGate.HOME, true, 0, false, 500) == PsGate.CLOSE_PASS && !g.menuOpen, "Home closes and passes");
        check(g.onKey(PsGate.HOME, false, 0, false, 520) == PsGate.PASS, "the Home up passes too");
        // pass-through app
        g.setForeground(GAME);
        check(g.onKey(M, true, 0, true, 600) == PsGate.PASS && g.onKey(M, false, 0, true, 650) == PsGate.PASS && !g.menuOpen, "a game on the list gets the PS button, down and up");
        // remote that sends BUTTON_MODE
        g.setForeground(TV);
        check(g.onKey(M, true, 0, false, 700) == PsGate.PASS, "BUTTON_MODE from a remote is not taken");
        g.onKey(M, false, 0, false, 710);
        // kill switch
        check(g.onKey(M, true, 0, true, 1000) == PsGate.OPEN, "hold PS: opens first");
        check(!g.kill.due(4999) && g.kill.due(5000), "the kill switch fires at 4 s");
        g.killSwitch();
        check(!g.menuOpen && TV.equals(g.bypass), "kill switch closes the menu and lets go of this app");
        check(g.onKey(M, false, 0, true, 5100) == PsGate.SWALLOW, "the up of the held press is still eaten (no stray up)");
        check(g.onKey(M, true, 0, true, 6000) == PsGate.PASS && g.onKey(M, false, 0, true, 6050) == PsGate.PASS, "after the kill switch the app gets the PS button");
        check(!g.setForeground(TV) && TV.equals(g.bypass), "same app again: still bypassed");
        g.setForeground("com.other.app");
        check(g.bypass == null && g.onKey(M, true, 0, true, 7000) == PsGate.OPEN, "leaving the app ends the bypass");
        g.reset();
        check(!g.menuOpen && !g.eaten(M) && !g.kill.held(), "reset forgets every half press");
        g.setForeground("");
        check("com.other.app".equals(g.fg), "an empty package never replaces the app in front");
        PsGate h = new PsGate(DECK);
        h.setForeground(TV);
        h.capture = false;
        check(h.onKey(M, true, 0, true, 0) == PsGate.PASS, "capture off passes the PS button");
        h.opened();
        check(h.onKey(PsGate.BACK, true, 0, false, 10) == PsGate.CLOSE, "a menu opened by Setup's test closes on Back even with capture off");
        check(h.onKey(-5, true, 0, false, 20) == PsGate.PASS && h.onKey(100000, false, 0, false, 30) == PsGate.PASS, "odd key codes are safe");
    }

    static void psKill() {
        section("2.4 PS kill switch timer");
        PsKill k = new PsKill();
        check(!k.due(10000) && k.remaining(0) == -1, "not armed until a press");
        k.press(1000);
        check(k.remaining(1000) == 4000 && k.remaining(4000) == 1000, "remaining time");
        check(!k.due(4999), "not before 4 s");
        check(k.due(5000), "fires at 4 s");
        check(!k.due(9000) && k.remaining(9000) == -1, "fires once per press");
        k.press(10000);
        k.release();
        check(!k.due(20000), "releasing early cancels it");
        k.press(30000);
        k.press(33000);
        check(!k.due(36000) && k.due(37000), "a new press restarts the hold");
    }

    static void psRules() {
        section("2.4 PS pass-through list and the game default");
        check(PsRules.isGame(PsRules.CATEGORY_GAME, 0), "CATEGORY_GAME is a game");
        check(PsRules.isGame(-1, PsRules.FLAG_IS_GAME), "FLAG_IS_GAME (old apps) is a game");
        check(!PsRules.isGame(-1, 0) && !PsRules.isGame(1, 0), "undefined and audio are not games");
        PsRules r = PsRules.load("", "", true);
        check(r.keeps(GAME, true) && !r.keeps(TV, false), "default: games keep the PS button, other apps give it to Visor");
        check(!r.changed(GAME, true) && !r.changed(TV, false), "nothing changed by default");
        check(r.set(TV, false, true) && r.pass.contains(TV) && r.keeps(TV, false) && r.changed(TV, false), "mark a normal app THE APP");
        check(r.set(GAME, true, false) && r.deck.contains(GAME) && !r.keeps(GAME, true), "mark a game VISOR");
        check(!r.set(GAME, true, false), "setting the same value again stores nothing new");
        check(r.set(GAME, true, true) && !r.deck.contains(GAME) && !r.pass.contains(GAME) && r.keeps(GAME, true), "back to its default: nothing stored");
        check(r.toggle(TV, false) == false && !r.pass.contains(TV), "toggle flips");
        r.set(TV, false, true);
        r.set(GAME, true, false);
        PsRules back = PsRules.load(r.passText(), r.deckText(), true);
        check(back.pass.equals(r.pass) && back.deck.equals(r.deck), "storage round trip");
        check(PsRules.write(Arrays.asList("b.b", "a.a")).equals("a.a\nb.b"), "stored sorted, one package per line");
        PsRules bad = PsRules.load("good.app\n\n../evil\nrm -rf\n1bad\n  spaced.app  ", "both.app", true);
        check(bad.pass.size() == 2 && bad.pass.contains("good.app") && bad.pass.contains("spaced.app"), "bad lines are dropped, spaces trimmed");
        PsRules both = PsRules.load("both.app", "both.app", true);
        check(both.keeps("both.app", true) && both.keeps("both.app", false) && both.deck.isEmpty(), "on both lists by damage: the app keeps it");
        check(!r.set("not a package", false, true) && !r.set(null, false, true), "only plain package names are stored");
        // game default off
        PsRules off = PsRules.load("", "", false);
        check(!off.keeps(GAME, true), "games default off: Visor takes it in games too");
        check(off.set(GAME, true, true) && off.pass.contains(GAME), "and the user can still mark a game THE APP");
        // effective set
        PsRules e = PsRules.load(TV, GAME, true);
        HashSet<String> eff = e.effective(Arrays.asList(GAME, "com.example.game2"));
        check(eff.contains(TV) && eff.contains("com.example.game2") && !eff.contains(GAME), "effective set: games minus VISOR marks plus THE APP marks");
        e.gameDefault = false;
        eff = e.effective(Arrays.asList(GAME, "com.example.game2"));
        check(eff.size() == 1 && eff.contains(TV), "effective set with the game default off");
        check(e.effective(null).contains(TV), "no game list yet: the marks still count");
    }

    static void psSetup() {
        section("2.4 PS overlay enable commands keep other services");
        String S = PsSetup.SERVICE;
        check(S.equals("com.nokoding.deck/com.nokoding.deck.PsService"), "service name");
        check(PsSetup.withDeck(null).equals(S) && PsSetup.withDeck("").equals(S) && PsSetup.withDeck("null").equals(S), "nothing enabled yet: just Visor");
        String talk = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService";
        String remote = "com.example.remote/.Svc$Inner";
        check(PsSetup.withDeck(talk).equals(talk + ":" + S), "TalkBack stays on, Visor is added after it");
        check(PsSetup.withDeck(talk + ":" + remote).equals(talk + ":" + remote + ":" + S), "two services stay, in order");
        check(PsSetup.withDeck(talk + ":" + S).equals(talk + ":" + S), "already listed: unchanged");
        check(PsSetup.withDeck(PsSetup.SHORT + ":" + talk).equals(S + ":" + talk), "the short form counts as Visor's entry");
        check(PsSetup.withDeck(talk + "::" + talk + ": ").equals(talk + ":" + S), "blanks and duplicates are cleaned");
        check(PsSetup.withoutDeck(talk + ":" + S + ":" + remote).equals(talk + ":" + remote), "disable removes only Visor");
        check(PsSetup.withoutDeck(S).isEmpty() && PsSetup.withoutDeck(PsSetup.SHORT).isEmpty(), "Visor alone: empty");
        check(PsSetup.listed(talk + ":" + S) && !PsSetup.listed(talk) && !PsSetup.listed(null), "listed()");
        String[] on = PsSetup.enableCommands(talk);
        check(on.length == 2 && on[0].equals("adb shell settings put secure enabled_accessibility_services '" + talk + ":" + S + "'")
                && on[1].equals("adb shell settings put secure accessibility_enabled 1"), "enable command carries the merged list, quoted");
        check(PsSetup.enableCommands(remote)[0].contains("'" + remote + ":" + S + "'"), "a $ in a class name stays inside single quotes");
        check(PsSetup.enableCommands("evil;rm -rf /")[0].endsWith("'" + S + "'"), "an odd current value is never echoed into a command");
        String[] off = PsSetup.disableCommands(S);
        check(off.length == 2 && off[0].equals("adb shell settings delete secure enabled_accessibility_services")
                && off[1].endsWith("accessibility_enabled 0"), "disable with Visor alone deletes the setting");
        off = PsSetup.disableCommands(talk + ":" + S);
        check(off.length == 1 && off[0].equals("adb shell settings put secure enabled_accessibility_services '" + talk + "'"), "disable with others keeps them and accessibility on");
        check(PsSetup.safeValue(talk + ":" + remote) && !PsSetup.safeValue("a b") && !PsSetup.safeValue("a'b") && !PsSetup.safeValue("a;b"), "safeValue");
        check(PsSetup.status(false, false, false) == PsSetup.NOT_ENABLED && PsSetup.status(true, true, false) == PsSetup.OFF
                && PsSetup.status(true, true, true) == PsSetup.ON, "status NOT ENABLED, OFF, ON");
        check(PsSetup.statusLine(PsSetup.OFF, true, false).contains("ACCESSIBILITY IS OFF"), "OFF explains accessibility_enabled 0");
        check(PsSetup.verdict(PsSetup.ON, true, true).startsWith("WORKS"), "verdict: works when the service saw PS");
        check(PsSetup.verdict(PsSetup.ON, false, false).contains("CANNOT WORK"), "verdict: says plainly when the key never arrives");
        check(PsSetup.verdict(PsSetup.NOT_ENABLED, true, true).startsWith("TURN THE SERVICE ON"), "verdict: never claims it works while the service is off");
    }

    static void psStick() {
        section("2.4 PS overlay stick and hat steps");
        PsStick s = new PsStick();
        check(s.feed(0.2f, 0) == 0, "inside the dead zone: nothing");
        check(s.feed(0.8f, 10) == 1, "push: one step at once");
        check(s.feed(0.9f, 100) == 0 && s.poll(389) == 0 && s.poll(390) == 1, "first repeat after 380 ms");
        check(s.poll(450) == 0 && s.poll(500) == 1 && s.poll(610) == 1, "then every 110 ms");
        check(s.feed(0.4f, 615) == 0 && s.dir() == 1, "between the thresholds it stays held (no chatter)");
        check(s.feed(0.1f, 620) == 0 && s.dir() == 0 && s.poll(5000) == 0, "release stops it");
        check(s.feed(-1f, 700) == -1, "a hat at -1 steps up");
        check(s.feed(1f, 710) == 1, "flipping straight to the other side steps at once");
        s.reset();
        check(s.poll(10000) == 0 && s.waitMs(0) == -1, "reset");
    }

    // ---------------------------------------------------------------- 2.4.1 text that stays complete

    /** What every view draws for line i whose text is `actual` characters long right now (the same call the views make). */
    static int shown(TextReveal r, int i, int actual) {
        return Math.min(actual, r.visible(i, actual));
    }

    static void staleLength() {
        section("2.4.1 text stays complete when a row changes after the reveal started");
        // the Setup bug: the reveal got the length of an unfocused row ("WHEEL" = 5 characters).
        // Focus then adds the arrows ("<  WHEEL  >" = 11), the line grew, and the reveal still capped it at 5 + the label.
        final int label = 6, plain = label + 5, focused = label + 11;
        FakeClock ck = new FakeClock();
        TextReveal r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{10, plain, plain, plain}, 4, 0);
        ck.t = 5000; r.step();
        check(!r.running(), "the reveal is long finished");
        check(shown(r, 1, focused) == focused, "a focused row with arrows shows all " + focused + " characters (showed " + shown(r, 1, focused) + ")");
        check(shown(r, 2, plain + 8) == plain + 8, "a value that grew after the reveal shows in full");
        check(shown(r, 2, 3) == 3, "a value that shrank shows in full");
        check(shown(r, 3, 31) == 31, "PRESS OK AGAIN text longer than the label shows in full");
        check(r.visible(1) == plain, "the old one argument call still reports the start length (kept for tests)");
        check(shown(r, -1, 40) == 40 && shown(r, 9, 40) == 40, "lines the reveal does not know show in full");

        // typing: progress is a share of the real length, so a grown row ends complete
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{plain}, 1, 0);
        int total = r.rowMs(0);
        ck.t = total / 2; r.step();
        int half = shown(r, 0, focused);
        check(half > 0 && half < focused && Math.abs(half - focused / 2) <= 1, "half way through typing a grown row shows about half of it (" + half + " of " + focused + ")");
        ck.t = total; r.step();
        check(shown(r, 0, focused) == focused, "at the end of the typing the grown row is complete");

        // interrupted by a key (finish): everything shows in full whatever the lengths are now
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{8, plain, plain}, 3, 0);
        ck.t = 30; r.step();
        check(r.finish() && shown(r, 1, focused) == focused && shown(r, 2, plain) == plain, "finish() shows the full text of every row, grown or not");

        // many quick changes: the row grows and shrinks on every frame, the end is always complete
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{8, plain, plain}, 3, 0);
        boolean bad = false;
        for (int t = 0; t < 600; t += 7) {
            ck.t = t; r.step();
            int act = plain + (t / 7 % 2 == 0 ? 6 : 0);
            int k = shown(r, 1, act);
            if (k < 0 || k > act) bad = true;
        }
        check(!bad, "a row that flips between two lengths never shows a bad count");
        check(!r.running() && shown(r, 1, plain + 6) == plain + 6 && shown(r, 1, plain) == plain, "after the quick changes both lengths show in full");

        // restarted while typing (focus moved, tab switched), with the clock far ahead of the first start
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{8, plain}, 2, 0);
        ck.t = 40; r.step();
        ck.t = 100000;
        r.startLengths(new int[]{8, focused}, 2, 0);
        check(shown(r, 1, focused) == 0 && r.running(), "a restart after a long gap starts from nothing, not from the stale clock");
        ck.t = 100000 + r.totalMs() + 1; r.step();
        check(!r.running() && shown(r, 1, focused) == focused, "and it still ends complete");
        // restart then interrupt, over and over
        for (int i = 0; i < 20; i++) {
            r.startLengths(new int[]{8, plain}, 2, 0);
            ck.t += 13; r.step();
            if (i % 2 == 0) r.finish();
        }
        r.finish();
        check(shown(r, 1, focused) == focused && shown(r, 0, 8) == 8, "20 restarts and interrupts: the full text shows");

        // right to left, FADE and NONE
        for (int mode = 0; mode < 3; mode++) {
            ck = new FakeClock();
            r = reveal(ck, new Count(), mode, true);
            r.startLengths(new int[]{8, plain}, 2, 0);
            ck.t = 100000; r.step();
            check(shown(r, 1, focused) == focused, "right to left, mode " + mode + ": a grown row is complete when done");
        }
        // the stagger shrink on a long list does not leave rows short
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        int[] big = new int[30];
        java.util.Arrays.fill(big, 12);
        r.startLengths(big, 30, 0);
        ck.t = 100000; r.step();
        boolean all = true;
        for (int i = 0; i < 30; i++) if (shown(r, i, 12 + 6) != 18) all = false;
        check(all, "a 30 row list ends with every grown row complete");
        // reduce effects: a 120 ms fade, then complete
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.FADE, false);
        r.p.fadeMs = 120;
        r.startLengths(new int[]{8, plain}, 2, 0);
        ck.t = 400; r.step();
        check(shown(r, 1, focused) == focused && r.alpha(1) == 1f, "a faded in row is complete and fully opaque");
        // close: CLEARING untypes the real length, CLEARED is empty, and a new start brings everything back
        ck = new FakeClock();
        r = reveal(ck, new Count(), TextReveal.TYPE, false);
        r.startLengths(new int[]{8, plain}, 2, 0);
        ck.t = 5000; r.step();
        r.clear(200);
        check(shown(r, 1, focused) == focused, "the close starts from the full text");
        ck.t = 5100; r.step();
        int mid = shown(r, 1, focused);
        check(mid > 0 && mid < focused, "the close untypes the real length (" + mid + ")");
        ck.t = 5300; r.step();
        check(shown(r, 1, focused) == 0, "closed: nothing shows");
        r.startLengths(new int[]{8, plain}, 2, 0);
        ck.t = 20000; r.step();
        check(shown(r, 1, focused) == focused, "reopened: complete again");
    }

    // ---------------------------------------------------------------- 2.4.1 reticle owner and chip focus

    static void frameGate() {
        section("2.4.1 who may own the lock-on reticle");
        check(FrameGate.owner(false, false, false, true) == FrameGate.HOME, "plain home screen: the wheel owns it");
        check(FrameGate.owner(true, false, false, true) == FrameGate.HIDDEN, "a full screen test hides it (the Gyro test bug)");
        check(FrameGate.owner(true, false, true, true) == FrameGate.HIDDEN, "a test wins over a leftover panel");
        check(FrameGate.owner(true, true, false, true) == FrameGate.HIDDEN, "a test wins over the quick menu");
        check(FrameGate.owner(false, true, false, true) == FrameGate.HIDDEN, "the quick menu draws its own brackets");
        check(FrameGate.owner(false, false, true, true) == FrameGate.PANEL, "a ready panel gets it on its row");
        check(FrameGate.owner(false, false, true, false) == FrameGate.HIDDEN, "a growing flyout waits");
        check(FrameGate.owner(false, false, false, false) == FrameGate.HOME, "a closing panel hands it back to the wheel");
        // closing a test brings it back
        check(FrameGate.owner(false, false, false, true) == FrameGate.HOME, "after the test closes the wheel owns it again");
    }

    static void chipFocus() {
        section("2.4.1 test screen chip focus");
        ChipFocus f = new ChipFocus(4, false);   // the Gamepad test: pad D-pad is a test input
        check(!f.active() && f.activate() == ChipFocus.NONE, "starts with no focus: A keeps counting as a button");
        check(!f.key(0, -1, true, 0) && !f.active(), "the pad's D-pad up never starts chip focus where D-pad is tested");
        check(!f.stick(0f, -0.4f, 10) && !f.active(), "a small up push does not start it");
        check(f.stick(0f, -0.9f, 20) && f.focus() == 0, "left stick up starts it on the first chip");
        check(!f.stick(0f, -0.9f, 30) && f.focus() == 0, "holding up does not do it again");
        check(f.stick(0.9f, -0.2f, 100) && f.focus() == 1, "stick right moves one chip");
        check(!f.stick(0.9f, -0.2f, 200) && f.focus() == 1, "held: no step before the first repeat");
        check(f.poll(100 + 380) && f.focus() == 2, "repeat after 380 ms");
        check(f.waitMs(500) >= 0, "a repeat is pending while held");
        check(f.poll(100 + 380 + 110) && f.focus() == 3, "then every 110 ms");
        check(!f.poll(100 + 380 + 220) && f.focus() == 3, "the last chip stops (no wrap, no trap)");
        f.stick(0f, 0f, 800);
        check(f.waitMs(800) == -1 && !f.poll(2000), "released stick: no more repeats");
        check(f.stick(-0.9f, 0f, 900) && f.focus() == 2, "stick left moves back");
        f.stick(0f, 0f, 950);
        check(f.stick(0f, 0.9f, 1000) && !f.active(), "stick down leaves the chips");
        f.stick(0f, 0f, 1050);
        check(f.stick(0f, -0.9f, 1100) && f.active() && f.focus() == 2, "pushing up again starts it on the chip it was left on");
        f.stick(0f, 0f, 1400);
        // a diagonal push is not a sideways step
        check(!f.stick(0.7f, -0.9f, 1500) && f.focus() == 2, "mostly up with a little right does not step");
        f.stick(0f, 0f, 1600);
        // the D-pad moves and leaves once focus exists, and counts nothing here
        check(f.key(1, 0, true, 1700) && f.focus() == 3, "pad D-pad right steps once a chip has focus");
        check(f.key(-1, 0, true, 1710) && f.focus() == 2, "pad D-pad left steps back");
        check(f.key(0, 1, true, 1720) && !f.active(), "pad D-pad down leaves");
        check(!f.key(1, 0, true, 1730) && !f.active(), "pad D-pad left or right with no focus does nothing here (stays a test input)");
        // the remote always starts it
        check(f.key(0, -1, false, 1800) && f.active(), "remote D-pad up starts it");
        check(f.key(1, 0, false, 1810), "remote right is used by the chips while focused");
        check(!f.key(0, -1, false, 1820), "remote up again: nothing, the old shortcut can run");
        check(f.key(0, 1, false, 1830) && !f.active(), "remote down leaves");
        check(!f.key(1, 0, false, 1840), "remote left or right with no focus is free for the old shortcuts");
        // idle release
        f.stick(0f, 0f, 1900);
        f.stick(0f, -0.9f, 2000);
        check(f.active() && f.expireIn(2000) == ChipFocus.IDLE_MS, "a focus knows when it will let go");
        check(!f.expire(2000 + ChipFocus.IDLE_MS - 1) && f.active(), "not before the idle time");
        check(f.expire(2000 + ChipFocus.IDLE_MS) && !f.active() && f.expireIn(9000) == -1, "released after the idle time");
        // a stuck stick pushed up never keeps chips forever
        f.stick(0f, 0f, 9000);
        f.stick(0f, -1f, 9100);
        for (long t = 9200; t < 9100 + ChipFocus.IDLE_MS + 100; t += 50) f.stick(0f, -1f, t);
        check(f.expire(9100 + ChipFocus.IDLE_MS + 100) && !f.active(), "a drifting stick cannot hold the focus");
        check(!f.stick(0f, -1f, 20000) && !f.active(), "and it does not restart while it stays pushed");
        // bad numbers
        f.stick(0f, 0f, 21000);
        check(!f.stick(Float.NaN, Float.NaN, 21100) && !f.active(), "NaN axes are ignored");
        // the Gyro test: the pad D-pad may start it
        ChipFocus g = new ChipFocus(4, true);
        check(g.key(0, -1, true, 0) && g.focus() == 0, "Gyro test: pad D-pad up starts it");
        check(g.key(1, 0, true, 10) && g.focus() == 1 && g.key(0, 1, true, 20) && !g.active(), "and moves and leaves");
        // chip count changes and clear
        g.key(0, -1, true, 30);
        g.key(1, 0, true, 40);
        g.setCount(2);
        check(g.focus() <= 1, "fewer chips: focus stays on a real chip");
        g.clear();
        check(!g.active() && g.activate() == ChipFocus.NONE, "clear lets go");
        ChipFocus z = new ChipFocus(0, true);
        check(!z.stick(0f, -1f, 0) && !z.key(0, -1, false, 0) && !z.active(), "no chips: nothing to focus");
    }

    // ---------------------------------------------------------------- 2.5 text fixes and row help

    /** A clock that counts wake() calls and stands in for the Engine: a wake posts one frame. */
    static final class LoopClock implements TextReveal.Clock {
        long t;
        int wakes;
        boolean posted;
        @Override public long now() { return t; }
        @Override public void wake() { wakes++; posted = true; }
    }

    /** Runs the posted frames the way Engine does: each frame steps the reveals, and asks for another while one runs. */
    static int runLoop(LoopClock ck, TextReveal... rs) {
        int frames = 0;
        while (ck.posted && frames < 5000) {
            ck.posted = false;
            ck.t += 16;
            frames++;
            boolean more = false;
            for (TextReveal r : rs) { r.step(); more |= r.running(); }
            if (more) ck.posted = true;
        }
        return frames;
    }

    static void revealWakes() {
        section("2.5 a text reveal wakes the frame loop itself (bug 3)");
        LoopClock ck = new LoopClock();
        TextReveal r = reveal2(ck, TextReveal.TYPE);
        check(!ck.posted && ck.wakes == 0, "an idle loop has no frame posted");
        r.startLengths(new int[]{12, 12, 12}, 3, 0);
        check(ck.wakes == 1 && ck.posted, "start() asks the clock for a frame by itself");
        check(r.needsFrames(), "and the reveal says it needs frames");
        int frames = runLoop(ck, r);
        check(!r.running() && frames > 3, "the posted frames run it to the end (" + frames + " frames)");
        check(shown(r, 0, 12) == 12 && shown(r, 1, 12) == 12 && shown(r, 2, 12) == 12, "every row ends fully visible");
        check(!ck.posted, "the loop goes back to sleep when nothing runs");

        // a start with a delay still wakes, and nothing is visible before the delay
        ck = new LoopClock();
        r = reveal2(ck, TextReveal.FADE);
        r.startLengths(new int[]{5, 5}, 2, 200);
        check(ck.wakes == 1, "a delayed start wakes too (the loop has to run through the delay)");
        runLoop(ck, r);
        check(shown(r, 0, 5) == 5 && shown(r, 1, 5) == 5, "FADE with a delay ends complete");

        // reveals that need no frames do not wake the loop
        ck = new LoopClock();
        r = reveal2(ck, TextReveal.NONE);
        r.startLengths(new int[]{5, 5}, 2, 0);
        check(ck.wakes == 0 && !r.needsFrames(), "NONE shows at once and asks for no frame");
        check(shown(r, 0, 5) == 5, "NONE text is complete at once");
        r = reveal2(ck, TextReveal.TYPE);
        r.startLengths(new int[0], 0, 0);
        check(ck.wakes == 0, "an empty list asks for no frame");

        // restarts and clears
        ck = new LoopClock();
        r = reveal2(ck, TextReveal.TYPE);
        r.startLengths(new int[]{30}, 1, 0);
        runLoop(ck, r);
        int w = ck.wakes;
        r.startLengths(new int[]{30}, 1, 0);
        check(ck.wakes == w + 1 && ck.posted, "the next reveal wakes a loop that went back to sleep");
        ck.t += 40;
        r.step();
        r.startLengths(new int[]{30}, 1, 0);
        check(ck.wakes == w + 2, "a restart in the middle of typing wakes again (harmless: kick() is idempotent)");
        runLoop(ck, r);
        check(shown(r, 0, 30) == 30, "20 characters later it still ends complete");
        w = ck.wakes;
        r.clear(80);
        check(ck.wakes == w + 1 && r.needsFrames(), "clear() wakes the loop");
        runLoop(ck, r);
        check(r.visible(0, 30) == 0, "and the close ends empty");
        ck = new LoopClock();
        r = reveal2(ck, TextReveal.NONE);
        r.startLengths(new int[]{3}, 1, 0);
        r.clear(80);
        check(ck.wakes == 0, "a NONE close is instant and wakes nothing");

        // the three reveals of one panel (rows, confirm, help) share one loop
        ck = new LoopClock();
        TextReveal rows = reveal2(ck, TextReveal.TYPE), conf = reveal2(ck, TextReveal.TYPE), help = reveal2(ck, TextReveal.FADE);
        rows.startLengths(new int[]{10, 10}, 2, 0);
        runLoop(ck, rows, conf, help);
        check(!ck.posted, "the loop slept after the rows finished");
        help.startLengths(new int[]{40}, 1, 0);
        check(ck.posted, "a help line fade that starts while the loop sleeps wakes it");
        runLoop(ck, rows, conf, help);
        check(help.alpha(0) == 1f && shown(help, 0, 40) == 40, "the help line ends fully shown");
        conf.startLengths(new int[]{26}, 1, 0);
        check(ck.posted, "so does a confirmation prompt");
        runLoop(ck, rows, conf, help);
        check(shown(conf, 0, 26) == 26, "and it ends complete");

        // the control: a clock that ignores wake() leaves the loop asleep, which is how it froze before
        FakeClock plain = new FakeClock();
        TextReveal frozen = reveal(plain, new Count(), TextReveal.TYPE, false);
        frozen.startLengths(new int[]{12}, 1, 0);
        check(frozen.running(), "control: without a wake the reveal is running but nobody steps it");
    }

    static TextReveal reveal2(TextReveal.Clock ck, int mode) {
        TextReveal r = new TextReveal(ck, new Count());
        r.p.mode = mode;
        return r;
    }

    static void revealPlan() {
        section("2.5 RevealPlan: a refresh quiets one reveal, never the panel (bug 1)");
        TextReveal.Params user = new TextReveal.Params();
        user.mode = TextReveal.TYPE;
        RevealPlan plan = new RevealPlan(user);
        TextReveal.Params out = new TextReveal.Params();
        plan.next(out);
        check(out.mode == TextReveal.TYPE && out.cursor && out.ticks, "a normal start uses the user's parameters");
        check(!plan.quietPending(), "nothing is pending");
        plan.quietNext();
        check(plan.quietPending(), "quietNext() arms one quiet start");
        plan.next(out);
        check(out.mode == TextReveal.NONE && !out.cursor && !out.ticks, "the refresh start is NONE, silent, no cursor");
        check(user.mode == TextReveal.TYPE && user.cursor && user.ticks, "the user's parameters were not touched (2.4.1 changed them)");
        check(!plan.quietPending(), "the quiet start used itself up");
        for (int i = 0; i < 5; i++) {
            plan.next(out);
            check(out.mode == TextReveal.TYPE && out.cursor && out.ticks, "tab switch " + (i + 1) + " after the refresh types again");
        }
        plan.quietNext();
        plan.quietNext();
        plan.next(out);
        plan.next(out);
        check(out.mode == TextReveal.TYPE, "arming twice still quiets one start only");
        user.mode = TextReveal.FADE;
        user.fadeMs = 150;
        plan.next(out);
        check(out.mode == TextReveal.FADE && out.fadeMs == 150, "a changed user option (FADE) is picked up at the next start");
        user.rtl = true;
        plan.next(out);
        check(out.rtl, "so is TYPING DIRECTION");

        // the real flow, through TextReveal: refresh, then tab switches, every row ends fully visible
        for (int mode = 0; mode < 3; mode++) {
            user = new TextReveal.Params();
            user.mode = mode;
            plan = new RevealPlan(user);
            LoopClock ck = new LoopClock();
            TextReveal r = new TextReveal(ck, new Count());
            int[] lens = {9, 17, 17, 22};
            for (int step = 0; step < 4; step++) {
                if (step == 0) plan.quietNext();
                plan.next(r.p);
                r.startLengths(lens, 4, 0);
                boolean instant = step == 0 || mode == TextReveal.NONE;
                check(r.running() == !instant, "mode " + mode + " step " + step + ": " + (instant ? "instant" : "animated") + " as it should");
                runLoop(ck, r);
                boolean all = true;
                for (int i = 0; i < 4; i++) all &= shown(r, i, lens[i]) == lens[i];
                check(all && !r.running(), "mode " + mode + " step " + step + ": every row ends fully visible");
            }
        }

        // the help line
        TextReveal.Params h = new TextReveal.Params();
        user = new TextReveal.Params();
        user.mode = TextReveal.TYPE;
        user.fadeMs = 400;
        user.staggerMs = 90;
        RevealPlan.helpParams(user, h);
        check(h.mode == TextReveal.FADE && h.slide == 0f && !h.cursor && !h.ticks && h.staggerMs == 0, "the help line is a plain crossfade, no ticks, no slide");
        check(h.fadeMs == 220, "and a short one");
        check(user.mode == TextReveal.TYPE && user.fadeMs == 400, "the user's parameters stay as they were");
        user.mode = TextReveal.NONE;
        RevealPlan.helpParams(user, h);
        check(h.mode == TextReveal.NONE, "TEXT ANIMATION NONE keeps the help line instant");
        TextReveal.Params red = new TextReveal.Params();
        AnimConfig.reveal(AnimConfig.flyout(), AnimConfig.SETUP, true, red);
        RevealPlan.helpParams(red, h);
        check(h.mode == TextReveal.FADE && h.fadeMs == AnimConfig.REDUCE_FADE_MS, "Reduce effects arrives as the short fade it already is");
    }

    /** 10 px per character, so the numbers in the tests can be checked by hand. */
    static final RowFit.Measure MONO = new RowFit.Measure() { @Override public float width(String s) { return s.length() * 10f; } };

    static void rowFit() {
        section("2.5 RowFit: one ellipsis function shares a row's width (bug 2)");
        check(RowFit.ellipsize("HELLO", 50, MONO).equals("HELLO"), "text that fits is returned as it is");
        check(RowFit.ellipsize("HELLO WORLD", 80, MONO).equals("HELLO..."), "cut text ends in three plain dots and fits: " + RowFit.ellipsize("HELLO WORLD", 80, MONO));
        check(RowFit.ellipsize("HELLO WORLD", 100, MONO).equals("HELLO W..."), "one more character room, one more character");
        check(RowFit.ellipsize("HELLO  WORLD", 90, MONO).equals("HELLO..."), "a space before the dots is dropped");
        check(RowFit.ellipsize("HELLO", 25, MONO).isEmpty(), "less room than the dots: nothing, never half dots");
        check(RowFit.ellipsize("HELLO", 30, MONO).equals("..."), "exactly the dots");
        check(RowFit.ellipsize("", 100, MONO).isEmpty() && RowFit.ellipsize(null, 100, MONO).isEmpty(), "empty and null are empty");
        check(RowFit.ellipsize("HELLO", 0, MONO).isEmpty() && RowFit.ellipsize("HELLO", -5, MONO).isEmpty(), "zero and negative room give nothing");
        check(RowFit.ellipsize("AB😀CD", 50, MONO).equals("AB..."), "an emoji is never cut in half: " + RowFit.ellipsize("AB😀CD", 50, MONO));
        // a proportional measure: wide and narrow letters
        RowFit.Measure prop = new RowFit.Measure() {
            @Override public float width(String s) { float w = 0; for (int i = 0; i < s.length(); i++) w += s.charAt(i) == 'W' ? 18f : (s.charAt(i) == 'I' ? 5f : 10f); return w; }
        };
        boolean fits = true, dots = true;
        String t = "WWIWIWWIIWWWIWIWIWWIWI";
        for (float max = 0; max < 300; max += 3.5f) {
            String c = RowFit.ellipsize(t, max, prop);
            fits &= prop.width(c) <= max + 0.001f;
            dots &= c.isEmpty() || c.equals(t) || c.endsWith("...");
        }
        check(fits, "with uneven letter widths the result always fits its room");
        check(dots, "and it is either whole, empty or ends in dots");

        RowFit.Split sp = new RowFit.Split();
        float gap = 24, minLabel = 120;
        // short label, short value: nothing is cut
        RowFit.split(1000, 0, 100, 150, 0, 60, gap, minLabel, sp);
        check(sp.labelMax == 100 && sp.valueMax == 150, "short: label " + sp.labelMax + " and value " + sp.valueMax + " keep their full width");
        check(sp.used == 100 + gap + 150, "short: used width is label, gap and value");
        // long label, short value: the label gives way, the value stays whole
        RowFit.split(1000, 0, 900, 150, 0, 60, gap, minLabel, sp);
        check(sp.valueMax == 150 && Math.abs(sp.labelMax - (1000 - gap - 150)) < 0.01f, "long label: value whole (150), label gets the rest (" + sp.labelMax + ")");
        // long value, short label: the label stays whole, the value is cut to the rest
        RowFit.split(1000, 0, 100, 1400, 0, 60, gap, minLabel, sp);
        check(sp.labelMax == 100 && Math.abs(sp.valueMax - (1000 - gap - 100)) < 0.01f, "long value: label whole (100), value gets the rest (" + sp.valueMax + ")");
        // both long: 40 percent label, 60 percent value
        RowFit.split(1000, 0, 900, 1400, 0, 60, gap, minLabel, sp);
        float rest = 1000 - gap;
        check(Math.abs(sp.labelMax - 0.4f * rest) < 0.01f && Math.abs(sp.valueMax - 0.6f * rest) < 0.01f, "both long: 40/60 split (" + sp.labelMax + " / " + sp.valueMax + ")");
        check(sp.labelMax + gap + sp.valueMax <= 1000.01f, "both long: never wider than the row");
        // the OK mark and a slider bar are fixed, the text shares what is left
        RowFit.split(1000, 90, 900, 1400, 0, 60, gap, minLabel, sp);
        check(sp.used <= 1000 - 90 + 0.01f, "the reserved mark width is never used");
        RowFit.split(660, 0, 400, 40, 268, 40, gap, minLabel, sp);
        check(sp.used <= 660 + 0.01f && sp.valueMax == 40, "a slider on a narrow flyout row: bar 268 + value 40, label gets " + sp.labelMax);
        check(sp.labelMax >= minLabel, "and the label keeps at least " + minLabel);
        // the value block never goes under its arrows, even if the label wants more
        RowFit.split(400, 0, 600, 600, 0, 150, gap, minLabel, sp);
        check(sp.valueMax >= 150 - 0.01f && sp.labelMax + gap + sp.valueMax <= 400.01f, "the value keeps its arrows (min 150): label " + sp.labelMax + ", value " + sp.valueMax);
        // no value at all: the label takes the row
        RowFit.split(500, 40, 900, 0, 0, 0, gap, minLabel, sp);
        check(sp.labelMax == 460 && sp.valueMax == 0, "no value: the label gets everything but the mark");
        // tiny widths
        RowFit.split(30, 0, 100, 100, 0, 30, gap, minLabel, sp);
        check(sp.labelMax >= 0 && sp.valueMax >= 0 && sp.labelMax + sp.valueMax <= 30, "30 px wide: nothing negative, nothing over the width");
        RowFit.split(0, 0, 100, 100, 0, 30, gap, minLabel, sp);
        check(sp.labelMax == 0 && sp.valueMax == 0, "0 px wide: no room, nothing drawn");
        RowFit.split(-50, 20, 100, 100, 0, 30, gap, minLabel, sp);
        check(sp.labelMax == 0 && sp.valueMax == 0, "a negative width is treated as none");
        RowFit.split(20, 0, 100, 0, 268, 0, gap, minLabel, sp);
        check(sp.labelMax == 0, "a fixed bar wider than the row leaves the label nothing (and the bar is not squeezed)");
        // NaN and infinity do not crash or poison the rest
        RowFit.split(Float.NaN, 0, 100, 100, 0, 30, gap, minLabel, sp);
        check(!Float.isNaN(sp.labelMax) && !Float.isNaN(sp.valueMax), "a NaN width gives numbers, not NaN");

        // properties over a sweep
        boolean inv = true, whole = true, floors = true;
        for (float usable = 0; usable <= 1200; usable += 37) {
            for (float lw = 0; lw <= 1300; lw += 83) {
                for (float vw = 0; vw <= 1300; vw += 97) {
                    for (float extra = 0; extra <= 300; extra += 150) {
                        float mv = Math.min(vw, 60), reserve = usable > 200 ? 40 : 0;
                        RowFit.split(usable, reserve, lw, vw, extra, mv, gap, minLabel, sp);
                        float avail = Math.max(0, usable - reserve);
                        boolean room = avail >= extra + (lw > 0 && (vw > 0 || extra > 0) ? gap : 0);
                        inv &= sp.labelMax >= -0.001f && sp.valueMax >= -0.001f && sp.labelMax <= lw + 0.001f && sp.valueMax <= vw + 0.001f;
                        if (room) inv &= sp.used <= avail + 0.01f;
                        boolean hasV = vw > 0 || extra > 0;
                        float g = lw > 0 && hasV ? gap : 0;
                        if (room && lw + vw + extra + g <= avail) whole &= Math.abs(sp.labelMax - lw) < 0.01f && Math.abs(sp.valueMax - vw) < 0.01f;
                        if (room && vw > 0 && avail - extra - g >= mv) floors &= sp.valueMax >= mv - 0.01f;
                    }
                }
            }
        }
        check(inv, "sweep: never negative, never more than asked, never wider than the row");
        check(whole, "sweep: when both fit, neither is cut");
        check(floors, "sweep: the value never goes under its minimum while the row has room for it");
        RowFit.Split a = new RowFit.Split(), b = new RowFit.Split();
        RowFit.split(777, 12, 345, 222, 18, 44, gap, minLabel, a);
        RowFit.split(777, 12, 345, 222, 18, 44, gap, minLabel, b);
        check(a.labelMax == b.labelMax && a.valueMax == b.valueMax && a.used == b.used, "same input, same split (focused or not gives the same label)");
        // RIGHT TO LEFT
        check(RowFit.mirror(100, 50, 0, 1000, false) == 100, "left to right keeps the edge");
        check(RowFit.mirror(100, 50, 0, 1000, true) == 850, "right to left mirrors it inside the row");
        check(RowFit.mirror(RowFit.mirror(100, 50, 20, 980, true), 50, 20, 980, true) == 100, "and mirroring twice is the identity");
    }

    static void marquee() {
        section("2.5 Marquee: endless scroll math");
        Marquee m = new Marquee();
        m.set(300, 500, 90, 60f, 1400);
        check(!m.scrolls() && m.offset(123456) == 0f && m.periodMs() == 0 && !m.moving(5) && !m.showsSecond(5) && m.pauseLeft(5) == 0, "text that fits never moves");
        m.set(500, 500, 90, 60f, 1400);
        check(!m.scrolls(), "text exactly as wide as the view does not move");
        m.set(1500, 1000, 100, 100f, 1000);
        m.restart(5000);
        check(m.scrolls() && m.cycle() == 1600f, "wide text scrolls; a cycle is text plus gap (1600)");
        check(m.periodMs() == 1000 + 16000, "one period is the rest plus 16 s of slide at 100 px/s");
        check(m.offset(5000) == 0f && m.offset(5999) == 0f, "it rests at the start for the pause");
        check(m.pauseLeft(5000) == 1000 && m.pauseLeft(5400) == 600 && m.pauseLeft(6000) == 0, "pauseLeft counts the rest down");
        check(!m.moving(5500) && m.moving(6100), "moving() is false in the rest and true in the slide");
        check(Math.abs(m.offset(7000) - 100f) < 0.01f, "after 1 s of slide it moved 100 px");
        check(Math.abs(m.offset(6000 + 8000) - 800f) < 0.01f, "after 8 s, 800 px");
        float endOff = m.offset(5000 + 17000 - 1);
        check(endOff > 1598f && endOff <= 1600f, "just before the wrap it is at the end of the cycle (" + endOff + ")");
        check(m.offset(5000 + 17000) == 0f, "at the period it is back at the start: the loop closes");
        // seamless: the second copy sits where the first began
        float second = m.cycle() - endOff;
        check(second >= 0f && second < 2f, "the second copy ends within 2 px of where the first one started");
        check(!m.showsSecond(7000) && m.showsSecond(5000 + 17000 - 1), "the second copy shows only once it enters the view");
        long near = 6000 + 6000;   // offset 600: second copy at 1000, view is 1000 wide, so just out
        check(!m.showsSecond(near) && m.showsSecond(near + 20), "the second copy appears the moment its edge reaches the view");
        // many cycles: no drift
        boolean same = true, bounded = true;
        for (long t = 5000; t < 5000 + 17000; t += 333) {
            same &= Math.abs(m.offset(t) - m.offset(t + 17000L * 1000)) < 0.001f;
            same &= Math.abs(m.offset(t) - m.offset(t + 17000L * 3)) < 0.001f;
            float o = m.offset(t);
            bounded &= o >= 0f && o <= m.cycle();
        }
        check(same, "a thousand cycles later it is in the same place");
        check(bounded, "the offset stays between 0 and one cycle");
        // never goes backwards inside a cycle
        boolean mono = true;
        float prev = 0f;
        for (long t = 5000; t < 5000 + 17000; t += 7) { float o = m.offset(t); mono &= o >= prev - 0.0001f; prev = o; }
        check(mono, "inside a cycle the text only moves left");
        // restart rests again
        m.restart(100000);
        check(m.offset(100000) == 0f && m.pauseLeft(100500) == 500, "restart() starts a new rest");
        check(m.offset(90000) >= 0f && m.offset(90000) <= m.cycle(), "a time before the restart still gives an offset inside one cycle");
        // bad numbers
        m.set(2000, 500, 0, 0f, -5);
        m.restart(0);
        check(m.cycle() >= 2001f && m.periodMs() > 0, "no gap and no speed fall back to a gap of 1 and the default speed");
        check(m.offset(0) == 0f && m.offset(10) > 0f, "a negative pause is no pause");
        m.set(2000, -10, 50, 40f, 500);
        check(m.scrolls(), "a negative view width counts as none");
        m.set(Float.POSITIVE_INFINITY, 500, 50, 40f, 500);
        m.restart(0);
        check(m.offset(1000) >= 0f && !Float.isNaN(m.offset(1000)) && m.periodMs() > 0, "an infinite text width gives numbers, not a crash");
        m.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN, 500);
        check(!m.scrolls() && m.offset(1000) == 0f, "NaN everywhere: it simply does not move");
        // the real numbers: 1032 px footer, about 10.2 px per character
        float charW = 10.2f, footer = 1032f;
        m.set(130 * charW, footer, 90, 56f, Marquee.DEFAULT_PAUSE_MS);
        m.restart(0);
        check(m.scrolls() && m.periodMs() > Marquee.DEFAULT_PAUSE_MS, "a 130 character sentence scrolls in the Setup footer");
        m.set(100 * charW, footer, 90, 56f, Marquee.DEFAULT_PAUSE_MS);
        check(!m.scrolls(), "a 100 character sentence fits it");
    }

    /** Every string literal in the app sources that is passed to a Panel.Row, without running Android. */
    static java.util.List<String> rowLabelsInSource(String dir) throws IOException {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        String src = new String(Files.readAllBytes(new File(dir, "HomeActivity.java").toPath()), "UTF-8");
        // the hold OK app menus (tile, dock) and the plain input list do not show help
        src = cutMethod(src, "private void openTileMenu(");
        src = cutMethod(src, "private void openDockMenu(");
        src = cutMethod(src, "private void openInputs(");
        src = cutMethod(src, "private void addPsRow(");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Row\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(src);
        while (m.find()) out.add(m.group(1));
        m = java.util.regex.Pattern.compile("Row\\([^;\"\\n]*\\?\\s*\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").matcher(src);
        while (m.find()) { out.add(m.group(1)); out.add(m.group(2)); }
        m = java.util.regex.Pattern.compile("String\\[\\] (?:labels|names) = \\{([^}]*)\\}").matcher(src);
        while (m.find()) {
            java.util.regex.Matcher q = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
            while (q.find()) out.add(q.group(1));
        }
        return out;
    }

    static String cutMethod(String src, String head) {
        int a = src.indexOf(head);
        if (a < 0) return src;
        int b = src.indexOf("\n    }\n", a);
        return src.substring(0, a) + (b < 0 ? "" : src.substring(b));
    }

    static void rowHelp() {
        section("2.5 RowHelp: a sentence for every row");
        String dir = System.getProperty("deck.src");
        check(dir != null && new File(dir, "HomeActivity.java").isFile(), "the test knows the source folder (run tools/test.sh)");
        // text quality
        int n = 0, longest = 0;
        boolean ok = true;
        String bad = "";
        for (String k : RowHelp.keys()) {
            String s = RowHelp.raw(k);
            n++;
            longest = Math.max(longest, s == null ? 0 : s.length());
            boolean good = s != null && s.length() >= 12 && s.length() <= 150 && s.endsWith(".")
                    && Character.isUpperCase(s.charAt(0)) && s.indexOf(". ") < 0 && s.indexOf('.') == s.length() - 1
                    && s.indexOf(';') < 0 && s.indexOf('—') < 0 && s.indexOf('–') < 0 && s.indexOf("  ") < 0 && s.indexOf(" - ") < 0;
            if (!good) { ok = false; bad += " [" + k + "]"; }
        }
        check(ok, "every sentence is one sentence of 12 to 150 characters, ends in a period, no semicolon or dash:" + bad);
        System.out.println("  " + n + " help entries, longest " + longest + " characters");
        check(n >= 150, "the table is not empty (" + n + " entries)");
        // lookups
        check(RowHelp.of("settings", "LAYOUT") != null && RowHelp.of("settings", "layout") != null, "lookup ignores case");
        check(RowHelp.of("settings", "NO SUCH ROW") == null, "an unknown row has no help");
        check(RowHelp.of("settings", null) == null, "null is safe");
        check(RowHelp.of("install", "ZIP  mypack.zip").equals(RowHelp.raw("install:ZIP  *")), "a ZIP row finds the zip sentence");
        check(RowHelp.of("install", "PACK  Retro").equals(RowHelp.raw("install:PACK  *")), "a PACK row finds the pack sentence");
        check(RowHelp.of("settings", "PS OVERLAY: ON").equals(RowHelp.raw("PS OVERLAY: *")), "the PS OVERLAY status row finds its sentence for every state");
        check(RowHelp.of("psapps", "SOME APP  // GAME  // CHANGED").equals(RowHelp.raw("psapps:*")), "a PS BUTTON APPS app row finds the app sentence");
        check(RowHelp.of("psapps", "DEFAULT").equals(RowHelp.raw("psapps:DEFAULT")), "kind first: DEFAULT in PS BUTTON APPS");
        check(RowHelp.of("pscmds", "NOW") != null && !RowHelp.of("pscmds", "NOW").equals(RowHelp.of("homecmds", "NOW")), "NOW means different things in the two command screens");
        check(RowHelp.of("custom", "PRESET") != null && !RowHelp.of("custom", "PRESET").equals(RowHelp.of("light", "PRESET")), "PRESET differs between custom colors and the light bar");
        check(RowHelp.of("addapp", "NETFLIX") != null && RowHelp.of("report", "SOME FINDING") != null, "data rows (apps, report lines) get their screen's sentence");
        check(RowHelp.of("test", "wheel_hum (1.5 s demo)") != null, "the demo row, whose label is mixed case in the source, is found");
        check(RowHelp.of("settings", "ANIM.SCREEN.TOAST") != null, "per screen switches share a sentence");
        // tabs
        String[] tabs = {"LOOK", "MOTION", "ANIMATIONS", "SOUND", "PAD", "APPS", "SYSTEM", "REMAP", "SOUNDS", "HAPTICS"};
        boolean tabsOk = true;
        for (String t : tabs) tabsOk &= RowHelp.tab(t) != null;
        check(tabsOk && RowHelp.tab("NOPE") == null, "every tab on a tab strip has a sentence");
        // data driven rows
        boolean schema = true;
        String miss = "";
        for (AnimOption o : AnimConfig.flyout().options) {
            if (RowHelp.of("anim", "anim." + o.key) == null) { schema = false; miss += " " + o.key; }
        }
        check(schema, "every animation option has a sentence:" + miss);
        boolean events = true;
        for (String e : SoundMap.EVENTS) events &= RowHelp.of("test", e.toUpperCase(java.util.Locale.ROOT)) != null;
        for (String e : HapticPatterns.NAMES) events &= RowHelp.of("test", e.toUpperCase(java.util.Locale.ROOT)) != null;
        check(events, "every sound event and haptic pattern row has a sentence");
        // the kinds
        check(RowHelp.covers("settings") && RowHelp.covers("psapps") && !RowHelp.covers("tile") && !RowHelp.covers("dock") && !RowHelp.covers(null), "covers() knows which screens show help");
        if (dir == null) return;
        try {
            // the guard: every Panel kind in the app either shows help or is on the short exempt list
            String home = new String(Files.readAllBytes(new File(dir, "HomeActivity.java").toPath()), "UTF-8");
            java.util.regex.Matcher km = java.util.regex.Pattern.compile("new Panel\\(this, \"([a-z]+)\"").matcher(home);
            java.util.HashSet<String> kinds = new java.util.HashSet<>();
            while (km.find()) kinds.add(km.group(1));
            java.util.List<String> exempt = Arrays.asList("tile", "dock", "inputs");
            String unknown = "";
            for (String k : kinds) if (!RowHelp.covers(k) && !exempt.contains(k)) unknown += " " + k;
            check(!kinds.isEmpty() && unknown.isEmpty(), "every Panel kind is covered or exempt (" + kinds.size() + " kinds):" + unknown);
            String dead = "";
            for (String k : RowHelp.KINDS) if (!kinds.contains(k)) dead += " " + k;
            check(dead.isEmpty(), "every covered kind is a real Panel kind:" + dead);

            // the guard: no row label in the source without a sentence
            java.util.List<String> labels = rowLabelsInSource(dir);
            String missing = "";
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String l : labels) {
                if (!seen.add(l)) continue;
                if (!RowHelp.known(l)) missing += " [" + l + "]";
            }
            check(labels.size() > 150, "the scan found the row labels (" + labels.size() + ")");
            check(missing.isEmpty(), "every row id has help text. Missing:" + missing);

            // and no sentence for a row that no longer exists
            java.util.HashSet<String> upper = new java.util.HashSet<>();
            java.util.regex.Matcher lm = java.util.regex.Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").matcher(home);
            while (lm.find()) upper.add(lm.group(1).toUpperCase(java.util.Locale.ROOT));
            String orphan = "";
            for (String k : RowHelp.keys()) {
                if (k.startsWith("tab:") || k.startsWith("ANIM.") || k.startsWith("REMAP.") || k.startsWith("WP.") || k.endsWith("*")) continue;   // REMAP.* ids are generated from RemapKey and RemapAction (remapHelp checks them)
                int c = k.indexOf(':');
                String id = c > 0 && RowHelp.covers(k.substring(0, c)) ? k.substring(c + 1) : k;
                if (!upper.contains(id)) orphan += " [" + k + "]";
            }
            check(orphan.isEmpty(), "every help entry belongs to a row in the source:" + orphan);
            // the self test of the scanner: it does see a known label, and it would see a missing one
            check(seen.contains("LAYOUT") && seen.contains("PS BUTTON APPS") && seen.contains("1. MAKE VISOR THE HOME ROLE"), "the scan sees known labels");
            check(!RowHelp.known("A ROW NOBODY WROTE HELP FOR"), "known() fails for a label with no sentence");
        } catch (IOException e) {
            check(false, "reading the sources failed: " + e);
        }
    }

    // ================================================================== 2.6 button remap

    /** A Sink that writes into a plain map, like SharedPreferences without Android. */
    static final class MapSink implements RemapTable.Sink {
        final java.util.TreeMap<String, Object> m = new java.util.TreeMap<>();
        @Override public void put(String k, String v) { m.put(k, v); }
        @Override public void putBool(String k, boolean v) { m.put(k, v); }
        @Override public void remove(String k) { m.remove(k); }
        RemapTable table() { return RemapTable.load(m); }
        boolean flag(String k) { return Boolean.TRUE.equals(m.get(k)); }
    }

    static final String RSELF = "com.nokoding.deck", RAPP = "com.netflix.ninja", RGAME = "com.game.fun";
    static final long KCHUP = RemapKey.pack(166, 0, 0, 0), KTV = RemapKey.pack(170, 0, 0, 0), KINPUT = RemapKey.pack(178, 0, 0, 0),
            KPROFILE = RemapKey.pack(288, 0, 0, 0), KOK = RemapKey.pack(23, 0, 0, 0), KBACK = RemapKey.pack(4, 0, 0, 0),
            KLEFT = RemapKey.pack(21, 0, 0, 0), KRIGHT = RemapKey.pack(22, 0, 0, 0), KVOLUP = RemapKey.pack(24, 0, 0, 0),
            KMENU = RemapKey.pack(82, 0, 0, 0);

    static RemapTable.Rule rule(int sh, String sa, int lo, String la) { return new RemapTable.Rule(sh, sa, lo, la, ""); }

    static RemapTable tableOf(Object... kv) {
        MapSink s = new MapSink();
        for (int i = 0; i < kv.length; i += 2) RemapTable.save(s, (Long) kv[i], (RemapTable.Rule) kv[i + 1]);
        return s.table();
    }

    static Set<String> gamesSet() { return new HashSet<>(Arrays.asList(RGAME)); }

    static void remapKeys() {
        section("2.6 remap: key identity, names and classes");
        check(RemapKey.pack(166, 99, 5, 6) == RemapKey.pack(166, 0, 0, 0), "a known key code is the identity, scan code and device are ignored");
        long sc = RemapKey.pack(0, 233, 4012, 9001);
        check(RemapKey.isScan(sc) && RemapKey.scan(sc) == 233 && RemapKey.vendor(sc) == 4012 && RemapKey.product(sc) == 9001 && RemapKey.code(sc) == 0, "key code 0 is named by scan code, vendor and product");
        check(RemapKey.pack(0, 233, 4012, 9001) != RemapKey.pack(0, 233, 4012, 9002) && RemapKey.pack(0, 233, 4012, 9001) != RemapKey.pack(0, 234, 4012, 9001)
                && RemapKey.pack(0, 233, 1, 9001) != RemapKey.pack(0, 233, 4012, 9001), "the same scan code on another device is another button");
        check(RemapKey.id(KCHUP).equals("kc:166") && RemapKey.id(sc).equals("sc:233:4012:9001"), "ids read kc:CODE and sc:SCAN:VENDOR:PRODUCT");
        check(RemapKey.parse("kc:166") == KCHUP && RemapKey.parse("sc:233:4012:9001") == sc && RemapKey.parse(RemapKey.id(sc)) == sc, "ids round trip");
        check(RemapKey.parse("sc:0:0:0") == RemapKey.INVALID && RemapKey.parse("sc:1:0:0") == RemapKey.pack(0, 1, 0, 0), "scan code 0 names nothing, scan code 1 on an unnamed device does");
        String[] bad = {null, "", "kc", "kc:", "kc:0", "kc:-1", "kc:+5", "kc: 5", "kc:5 ", "kc:1a", "kc:65536", "kc:9999999", "KC:166", "sc:", "sc:1", "sc:1:2", "sc:1:2:", "sc:1:2:3:4",
                "sc:70000:1:1", "sc:1:70000:1", "sc:1:1:70000", "sc:a:b:c", "sc:-1:1:1", "xx:5", "166", "kc:166\n", "kc:0166x", "kc:1234567",
                "sc:1:2:3 ", "sc::2:3", "kc:166:1", "kc:١٢٣"};
        boolean allBad = true;
        String which = "";
        for (String b : bad) if (RemapKey.parse(b) != RemapKey.INVALID) { allBad = false; which += " [" + b + "]"; }
        check(allBad, "bad ids all read as INVALID:" + which);
        check(RemapKey.pack(-5, 0, 0, 0) == RemapKey.INVALID && RemapKey.pack(0, 0, 4012, 9001) == RemapKey.INVALID && RemapKey.pack(0, -1, 0, 0) == RemapKey.INVALID
                && RemapKey.pack(0, 70000, 0, 0) == RemapKey.INVALID && RemapKey.pack(70000, 5, 0, 0) == RemapKey.INVALID, "impossible codes, and code 0 without a scan code, never make an identity");
        check(!RemapKey.valid(RemapKey.INVALID) && RemapKey.id(RemapKey.INVALID).isEmpty() && RemapKey.name(RemapKey.INVALID).isEmpty(), "INVALID has no id and no name");
        // the table
        int[] spec = {166, 167, 170, 178, 288, 172, 165, 183, 184, 185, 186, 229, 173, 174, 175, 176, 230, 234, 284, 187, 219, 231, 82};
        boolean all = true;
        String miss = "";
        for (int c : spec) if (RemapKey.def(c) == null) { all = false; miss += " " + c; }
        check(all, "every key code the spec names is in the table:" + miss);
        check(RemapKey.def(166).name.equals("CHANNEL UP") && RemapKey.def(167).name.equals("CHANNEL DOWN") && RemapKey.def(170).name.equals("TV BUTTON")
                && RemapKey.def(178).name.equals("INPUT BUTTON") && RemapKey.def(288).name.equals("ACCOUNT SWITCHER"), "the five standard buttons carry the names the user knows");
        check(RemapKey.STANDARD_FIRST.length == 5 && RemapKey.STANDARD_FIRST[0] == 166 && RemapKey.STANDARD_FIRST[1] == 167 && RemapKey.STANDARD_FIRST[2] == 170
                && RemapKey.STANDARD_FIRST[3] == 178 && RemapKey.STANDARD_FIRST[4] == 288, "Setup lists channel up and down, TV, input and the account switcher first");
        boolean uniq = true, std = true;
        HashSet<Integer> codes = new HashSet<>();
        HashSet<String> names = new HashSet<>(), konst = new HashSet<>();
        for (RemapKey.Def d : RemapKey.all()) {
            uniq &= codes.add(d.code) && names.add(d.name) && konst.add(d.konst);
            uniq &= d.name.equals(d.name.toUpperCase(java.util.Locale.ROOT)) && d.name.length() <= 24;
        }
        for (int c : RemapKey.STANDARD_FIRST) std &= RemapKey.clsOfCode(c) == RemapKey.STANDARD;
        check(uniq, "codes, names and constants are unique, names are capitals");
        check(std, "the five are standard buttons: no warning");
        check(RemapKey.clsOfCode(183) == RemapKey.STANDARD && RemapKey.clsOfCode(172) == RemapKey.STANDARD && RemapKey.clsOfCode(165) == RemapKey.STANDARD, "color buttons, guide and info are standard");
        check(RemapKey.clsOfCode(19) == RemapKey.NAV && RemapKey.clsOfCode(20) == RemapKey.NAV && RemapKey.clsOfCode(21) == RemapKey.NAV && RemapKey.clsOfCode(22) == RemapKey.NAV
                && RemapKey.clsOfCode(23) == RemapKey.NAV && RemapKey.clsOfCode(4) == RemapKey.NAV && RemapKey.clsOfCode(66) == RemapKey.NAV && RemapKey.clsOfCode(160) == RemapKey.NAV
                && RemapKey.clsOfCode(111) == RemapKey.NAV, "D-pad, OK, Back, Enter and Escape are navigation");
        check(RemapKey.clsOfCode(82) == RemapKey.ADVANCED && RemapKey.clsOfCode(85) == RemapKey.ADVANCED && RemapKey.clsOfCode(24) == RemapKey.ADVANCED && RemapKey.clsOfCode(25) == RemapKey.ADVANCED
                && RemapKey.clsOfCode(164) == RemapKey.ADVANCED && RemapKey.clsOfCode(219) == RemapKey.ADVANCED && RemapKey.clsOfCode(231) == RemapKey.ADVANCED, "menu, media, volume and the assistant keys are advanced");
        check(RemapKey.clsOfCode(3) == RemapKey.ADVANCED && RemapKey.clsOfCode(26) == RemapKey.PROTECTED && RemapKey.clsOfCode(26) == RemapKey.PROTECTED && RemapKey.clsOfCode(110) == RemapKey.PROTECTED
                && RemapKey.clsOfCode(96) == RemapKey.PROTECTED && RemapKey.clsOfCode(100) == RemapKey.PROTECTED && RemapKey.clsOfCode(30) == RemapKey.PROTECTED
                && RemapKey.clsOfCode(8) == RemapKey.PROTECTED && RemapKey.clsOfCode(67) == RemapKey.PROTECTED && RemapKey.clsOfCode(223) == RemapKey.PROTECTED
                && RemapKey.clsOfCode(197) == RemapKey.PROTECTED, "Home, Power, sleep, the PS button, game buttons and typing keys can never be remapped");
        check(RemapKey.clsOfCode(345) == RemapKey.STANDARD && RemapKey.clsOfCode(600) == RemapKey.STANDARD && RemapKey.cls(sc) == RemapKey.STANDARD, "a key code the table does not know is a standard button (so unknown remote buttons work)");
        check(!RemapKey.known(RemapKey.pack(345, 0, 0, 0)) && RemapKey.name(RemapKey.pack(345, 0, 0, 0)).equals("KEY 345") && RemapKey.name(sc).equals("SCAN 233 (4012:9001)"), "unknown buttons get a readable name");
        check(RemapKey.remappable(KCHUP) && RemapKey.remappable(sc) && RemapKey.remappable(KOK) && !RemapKey.remappable(RemapKey.pack(26, 0, 0, 0)) && !RemapKey.remappable(RemapKey.INVALID), "remappable() rules out the protected keys and nonsense");
        boolean adv = true;
        for (int c : RemapKey.ADVANCED_LIST) adv &= RemapKey.def(c) != null && RemapKey.clsOfCode(c) != RemapKey.STANDARD && RemapKey.clsOfCode(c) != RemapKey.PROTECTED;
        check(adv, "every entry of the advanced screen is a known advanced or navigation key");
        check(RemapKey.protectedReason(RemapKey.pack(110, 0, 0, 0)).contains("PS") && RemapKey.protectedReason(RemapKey.pack(26, 0, 0, 0)).contains("POWER")
                && !RemapKey.protectedReason(RemapKey.pack(30, 0, 0, 0)).isEmpty(), "each protected key says why");
        boolean helpOk = true;
        for (RemapKey.Def d : RemapKey.all()) helpOk &= d.help != null && d.help.length() >= 12 && d.help.endsWith(".");
        check(helpOk && RemapKey.help(RemapKey.pack(345, 0, 0, 0)).endsWith("."), "every key has a help sentence, unknown ones a generic one");
        check(RemapKey.helpId(KCHUP).equals("remap.key.166") && RemapKey.helpId(sc).equals("remap.key.other") && RemapKey.helpId(RemapKey.pack(345, 0, 0, 0)).equals("remap.key.other"), "help ids");
        check(RemapKey.hasName("CHANNEL UP") && !RemapKey.hasName("NOPE"), "hasName");
    }

    static void remapActions() {
        section("2.6 remap: actions");
        check(RemapAction.COUNT == 31, "31 actions: 25 real ones (2.16 added EXIT GAME and TOGGLE PERFORMANCE MODE) and 6 inside Visor only");
        HashSet<String> ids = new HashSet<>(), names = new HashSet<>();
        boolean ok = true, helpOk = true;
        for (int a = 0; a < RemapAction.COUNT; a++) {
            ok &= ids.add(RemapAction.id(a)) && names.add(RemapAction.name(a)) && RemapAction.byId(RemapAction.id(a)) == a;
            String h = RemapAction.help(a);
            helpOk &= h.length() >= 12 && h.length() <= 150 && h.endsWith(".");
        }
        check(ok, "ids and names are unique and every id round trips");
        check(helpOk, "every action has one sentence of help");
        check(RemapAction.byId("nope") == -1 && RemapAction.byId(null) == -1 && RemapAction.byId("") == -1 && RemapAction.byId("DEFAULT") == -1, "unknown ids are -1 (case matters)");
        check(RemapAction.id(-3).equals("default") && RemapAction.id(999).equals("default") && RemapAction.name(999).equals("DEFAULT"), "out of range reads as DEFAULT");
        String[] need = {"DEFAULT", "DO NOTHING", "LAUNCH APP", "OPEN VISOR HOME", "OPEN VISOR PS MENU", "OPEN VISOR SETUP", "HOME", "BACK", "RECENTS", "QUICK SETTINGS", "POWER MENU",
                "SCREENSHOT", "PREVIOUS APP", "MEDIA PLAY PAUSE", "MEDIA NEXT", "MEDIA PREVIOUS", "MEDIA STOP", "VOLUME UP", "VOLUME DOWN", "MUTE", "ANDROID SETTINGS", "OPEN INPUT PICKER"};
        boolean spec = true;
        for (String n : need) spec &= names.contains(n);
        check(spec, "every action the spec lists is there by that name");
        int[] std = RemapAction.picker(false, true);
        check(std[0] == RemapAction.DEFAULT && std.length == 25, "a standard button gets DEFAULT first and the 25 real actions");
        boolean noKeys = true;
        for (int a : std) noKeys &= !RemapAction.deckKey(a);
        check(noKeys, "the inside Visor actions are not offered for a standard button");
        check(RemapAction.picker(true, true).length == 31, "a navigation button also gets the six inside Visor actions");
        int[] noInputs = RemapAction.picker(false, false);
        boolean hidden = true;
        for (int a : noInputs) hidden &= a != RemapAction.INPUT_PICKER;
        check(hidden && noInputs.length == 24, "OPEN INPUT PICKER is hidden when the box lists no inputs");
        check(RemapAction.deckKeyCode(RemapAction.KEY_UP) == 19 && RemapAction.deckKeyCode(RemapAction.KEY_DOWN) == 20 && RemapAction.deckKeyCode(RemapAction.KEY_LEFT) == 21
                && RemapAction.deckKeyCode(RemapAction.KEY_RIGHT) == 22 && RemapAction.deckKeyCode(RemapAction.KEY_OK) == 23 && RemapAction.deckKeyCode(RemapAction.KEY_BACK) == 4
                && RemapAction.deckKeyCode(RemapAction.HOME) == 0, "inside Visor actions map to the key codes Visor reads");
        check(RemapAction.needsService(RemapAction.RECENTS) && RemapAction.needsService(RemapAction.SCREENSHOT) && RemapAction.needsService(RemapAction.QUICK)
                && RemapAction.needsService(RemapAction.POWER_MENU) && !RemapAction.needsService(RemapAction.VOL_UP) && !RemapAction.needsService(RemapAction.APP), "which actions need the service");
        check(RemapAction.allowedFor(RemapAction.KEY_OK, KOK) && !RemapAction.allowedFor(RemapAction.KEY_OK, KCHUP) && RemapAction.allowedFor(RemapAction.HOME, KCHUP) && !RemapAction.allowedFor(-1, KCHUP) && !RemapAction.allowedFor(99, KCHUP),
                "inside Visor actions only for navigation buttons");
        check(RemapAction.needsArg(RemapAction.APP) && !RemapAction.needsArg(RemapAction.HOME), "only LAUNCH APP takes an argument");
        java.util.List<String> recents = Arrays.asList("com.a/.Main", "com.b/.Main", "com.nokoding.deck/.HomeActivity", "com.c/.Main");
        check("com.b/.Main".equals(RemapAction.previousApp(recents, "com.a", RSELF)), "PREVIOUS APP skips the app in front");
        check("com.a/.Main".equals(RemapAction.previousApp(recents, RSELF, RSELF)), "in Visor it is the newest app");
        check("com.c/.Main".equals(RemapAction.previousApp(Arrays.asList("com.nokoding.deck/.HomeActivity", "com.c/.Main"), "com.a", RSELF)), "PREVIOUS APP never picks Visor");
        check(RemapAction.previousApp(Arrays.asList("com.a/.Main"), "com.a", RSELF) == null && RemapAction.previousApp(null, "x", RSELF) == null && RemapAction.previousApp(Arrays.asList((String) null, ""), "x", RSELF) == null,
                "no previous app gives null");
    }

    static void remapStorage() {
        section("2.6 remap: saved rules, versioning and bad data");
        RemapTable.Rule r = rule(RemapAction.VOL_UP, "", RemapAction.APP, "com.netflix.ninja");
        r.label = "CHANNEL UP";
        String text = RemapTable.write(r);
        check(text.equals("v1|vol_up||app|com.netflix.ninja|CHANNEL UP"), "the saved form is one versioned line: " + text);
        RemapTable.Rule back = RemapTable.parse(text);
        check(back != null && back.shortA == RemapAction.VOL_UP && back.longA == RemapAction.APP && back.longArg.equals("com.netflix.ninja") && back.label.equals("CHANNEL UP"), "a rule round trips");
        check(RemapTable.parse("v2|vol_up||default||X") == null && RemapTable.parse("v0|a|b|c|d|e") == null && RemapTable.parse("vol_up") == null, "another version is not read");
        check(RemapTable.parse("") == null && RemapTable.parse(null) == null && RemapTable.parse("v1|a|b|c|d") == null && RemapTable.parse("v1|a|b|c|d|e|f") == null && RemapTable.parse("v1||||||") == null, "wrong field counts are not read");
        StringBuilder huge = new StringBuilder("v1|home||default||");
        for (int i = 0; i < 500; i++) huge.append('x');
        check(RemapTable.parse(huge.toString()) == null, "an over long line is not read");
        RemapTable.Rule unk = RemapTable.parse("v1|teleport||home||");
        check(unk != null && unk.shortA == RemapAction.DEFAULT && unk.longA == RemapAction.HOME, "an unknown action id reads as DEFAULT, the rest survives");
        check(RemapTable.cleanLabel("A|B\nC;D").equals("A?B?C?D") && RemapTable.cleanLabel(null).isEmpty() && RemapTable.cleanLabel("0123456789012345678901234567890123456789ABCDEF").length() == 40
                && RemapTable.cleanLabel("  x ").equals("x"), "labels are plain and cut to 40");
        // fix()
        RemapTable.Rule f = rule(RemapAction.APP, "not a package", RemapAction.APP, "ok.pkg").fix(KCHUP);
        check(f.shortA == RemapAction.DEFAULT && f.shortArg.isEmpty() && f.longA == RemapAction.APP && f.longArg.equals("ok.pkg"), "LAUNCH APP without a real package falls back to DEFAULT");
        RemapTable.Rule f2 = rule(RemapAction.KEY_OK, "", RemapAction.HOME, "").fix(KCHUP);
        check(f2.shortA == RemapAction.DEFAULT && f2.longA == RemapAction.HOME, "an inside Visor action on a standard button is dropped");
        RemapTable.Rule f3 = rule(RemapAction.KEY_OK, "", RemapAction.HOME, "").fix(KBACK);
        check(f3.shortA == RemapAction.KEY_OK && f3.longA == RemapAction.DEFAULT, "a swap on Back keeps no long press");
        RemapTable.Rule f4 = rule(RemapAction.HOME, "junk", RemapAction.KEY_UP, "").fix(KOK);
        check(f4.shortArg.isEmpty() && f4.longA == RemapAction.DEFAULT, "arguments of actions that take none are dropped, a swap never sits on the long press");
        check(rule(0, "", 0, "").summary().equals("DEFAULT") && rule(RemapAction.HOME, "", 0, "").summary().equals("SHORT: HOME")
                && rule(RemapAction.HOME, "", RemapAction.SCREENSHOT, "").summary().equals("SHORT: HOME  LONG: SCREENSHOT")
                && rule(0, "", RemapAction.APP, "com.netflix.ninja").summary().equals("LONG: APP COM.NETFLIX.NINJA")
                && rule(0, "", RemapAction.APP, "com.netflix.ninja").summary(pkg -> "Netflix").equals("LONG: APP NETFLIX")
                && rule(0, "", RemapAction.APP, "com.x").summary(pkg -> null).equals("LONG: APP COM.X"), "the Setup summary, with the app's name when it is known");
        // load with a hostile map
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("rm:kc:166", "v1|vol_up||default||");
        m.put("rm:kc:26", "v1|nothing||default||");                  // Power: protected
        m.put("rm:kc:110", "v1|nothing||default||");                // PS button: protected
        m.put("rm:kc:30", "v1|nothing||default||");                 // a letter key
        m.put("rm:kc:abc", "v1|nothing||default||");
        m.put("rm:kc:170", Integer.valueOf(5));                     // not a string
        m.put("rm:kc:178", "v9|nothing||default||");
        m.put("rm:kc:288", "garbage");
        m.put("rm:sc:233:4012:9001", "v1|home||default||SCAN KEY");
        m.put("rm:", "v1|home||default||");
        m.put("other:kc:172", "v1|home||default||");
        m.put("rmAdv", Boolean.TRUE);
        m.put(null, "v1|home||default||");
        RemapTable t = RemapTable.load(m);
        check(t.size() == 2 && t.get(KCHUP) != null && t.get(RemapKey.parse("sc:233:4012:9001")) != null, "load keeps the two good entries and drops protected, broken, other version and foreign ones");
        check(t.get(KTV) == null && t.get(KINPUT) == null && t.get(KPROFILE) == null && t.get(RemapKey.pack(26, 0, 0, 0)) == null && t.get(RemapKey.pack(30, 0, 0, 0)) == null, "nothing is invented for a bad entry");
        check(RemapTable.load(null) == RemapTable.EMPTY && RemapTable.load(new java.util.HashMap<String, Object>()) == RemapTable.EMPTY && RemapTable.EMPTY.get(KCHUP) == null && !RemapTable.EMPTY.any(), "no data is an empty table");
        java.util.HashMap<String, Object> many = new java.util.HashMap<>();
        for (int i = 1; i <= 400; i++) many.put("rm:kc:" + (1000 + i), "v1|home||default||");
        RemapTable cap = RemapTable.load(many);
        check(cap.size() == RemapTable.MAX_RULES, "at most " + RemapTable.MAX_RULES + " rules are kept");
        boolean sorted = true;
        for (int i = 1; i < cap.size(); i++) sorted &= cap.keyAt(i - 1) < cap.keyAt(i);
        check(sorted && cap.get(RemapKey.pack(1001, 0, 0, 0)) != null && cap.get(RemapKey.pack(1400, 0, 0, 0)) == null, "the table is sorted and the cut is the same every time (entry name order)");
        // save / clear / reset
        MapSink s = new MapSink();
        check(RemapTable.save(s, KCHUP, rule(RemapAction.HOME, "", 0, "")) && s.m.containsKey("rm:kc:166"), "save writes rm:kc:166");
        check(!RemapTable.save(s, RemapKey.pack(26, 0, 0, 0), rule(RemapAction.HOME, "", 0, "")) && !RemapTable.save(s, RemapKey.INVALID, rule(1, "", 0, "")) && !RemapTable.save(s, KCHUP, null), "save refuses Home, nonsense and null");
        RemapTable.save(s, RemapKey.pack(0, 233, 4012, 9001), rule(RemapAction.NOTHING, "", 0, ""));
        check(s.m.containsKey("rm:sc:233:4012:9001") && s.table().size() == 2, "a scan code button is saved by scan, vendor and product");
        RemapTable.clear(s, KCHUP);
        check(!s.m.containsKey("rm:kc:166") && s.table().size() == 1, "clear removes one button");
        s.putBool(RemapTable.KEY_ADV, true);
        s.putBool(RemapTable.KEY_NAV, true);
        s.putBool(RemapTable.KEY_GAMES, true);
        s.put("foo", "bar");
        RemapTable.save(s, KTV, rule(RemapAction.MUTE, "", 0, ""));
        int n = RemapTable.resetAll(s, new java.util.HashMap<String, Object>(s.m));
        check(n == 2 && s.table().size() == 0 && !s.flag(RemapTable.KEY_ADV) && !s.flag(RemapTable.KEY_NAV) && !s.flag(RemapTable.KEY_GAMES) && "bar".equals(s.m.get("foo")),
                "RESET ALL removes every rule, turns every switch off and leaves other settings alone");
        check(RemapTable.resetAll(s, null) == 0, "reset with nothing saved is fine");
        // clearNav / clearAdvanced
        MapSink c = new MapSink();
        RemapTable.save(c, KOK, rule(RemapAction.KEY_BACK, "", 0, ""));
        RemapTable.save(c, KBACK, rule(RemapAction.NOTHING, "", 0, ""));
        RemapTable.save(c, KLEFT, rule(RemapAction.HOME, "", 0, ""));
        RemapTable.save(c, KVOLUP, rule(RemapAction.MUTE, "", 0, ""));
        RemapTable.save(c, KCHUP, rule(RemapAction.HOME, "", 0, ""));
        c.m.put("rm:kc:bogus", "v1|home||default||");
        c.putBool(RemapTable.KEY_NAV, true);
        c.putBool(RemapTable.KEY_ADV, true);
        int removed = RemapTable.clearNav(c, new java.util.HashMap<String, Object>(c.m));
        RemapTable after = c.table();
        check(removed == 4 && after.size() == 2 && after.get(KVOLUP) != null && after.get(KCHUP) != null && !c.flag(RemapTable.KEY_NAV) && c.flag(RemapTable.KEY_ADV) && !c.m.containsKey("rm:kc:bogus"),
                "clearNav removes OK, Back and the D-pad (and junk), keeps volume and channel, turns the system wide switch off");
        RemapTable.clearAdvanced(c, new java.util.HashMap<String, Object>(c.m));
        check(c.table().size() == 1 && c.table().get(KCHUP) != null && !c.flag(RemapTable.KEY_ADV), "turning advanced off removes the system button rules and keeps the standard ones");
        // the learned list needs a label that survives
        MapSink l = new MapSink();
        RemapTable.Rule lr = rule(0, "", 0, "");
        lr.label = "SCAN 7 (1:2)";
        RemapTable.save(l, RemapKey.pack(0, 7, 1, 2), lr);
        RemapTable.Rule got = l.table().get(RemapKey.pack(0, 7, 1, 2));
        check(got != null && !got.active() && got.label.equals("SCAN 7 (1:2)"), "a learned button without an action is kept with its name");
        check(RemapTable.keyOfPref("rm:kc:166") == KCHUP && RemapTable.keyOfPref("kc:166") == RemapKey.INVALID && RemapTable.keyOfPref(null) == RemapKey.INVALID, "entry names read back");
    }

    static RemapGate.Decision dec(long key, int press, String fg, RemapTable t, int flags, Set<String> pass, int esc) {
        RemapGate.Decision d = new RemapGate.Decision();
        RemapGate.decide(d, key, press, fg, RSELF, t, flags, pass, esc);
        return d;
    }

    static void remapDecide() {
        section("2.6 remap: the decision function");
        RemapTable std = tableOf(KCHUP, rule(RemapAction.VOL_UP, "", 0, ""), KTV, rule(RemapAction.SCREENSHOT, "", RemapAction.HOME, ""), KINPUT, rule(0, "", RemapAction.MUTE, ""),
                KPROFILE, rule(RemapAction.APP, "com.netflix.ninja", 0, ""));
        int ADV = RemapGate.F_ADVANCED, NAVF = RemapGate.F_NAV, GAMES = RemapGate.F_GAMES;
        RemapGate.Decision d;
        // standard key, no rule, unknown, protected
        d = dec(RemapKey.pack(172, 0, 0, 0), RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(!d.consume, "a button with no rule is never taken");
        d = dec(RemapKey.pack(0, 233, 4012, 9001), RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(!d.consume, "an unknown scan code button with no rule is left alone");
        MapSink ps = new MapSink();
        RemapTable.save(ps, RemapKey.pack(0, 233, 4012, 9001), rule(RemapAction.RECENTS, "", 0, ""));
        d = dec(RemapKey.pack(0, 233, 4012, 9001), RemapGate.P_DOWN, RAPP, ps.table(), 0, null, 0);
        check(d.consume && d.immediate && d.action == RemapAction.RECENTS, "a learned scan code button works with no advanced switch");
        d = dec(RemapKey.pack(0, 233, 4012, 9002), RemapGate.P_DOWN, RAPP, ps.table(), 0, null, 0);
        check(!d.consume, "the same scan code on another device is not taken");
        d = dec(KCHUP, RemapGate.P_DOWN, RAPP, null, 0, null, 0);
        check(!d.consume, "no table at all passes");
        d = dec(RemapKey.INVALID, RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(!d.consume, "an invalid key passes");
        // protected: even with a forged table
        java.util.HashMap<String, Object> forged = new java.util.HashMap<>();
        forged.put("rm:kc:3", "v1|nothing||default||");
        d = dec(RemapKey.pack(26, 0, 0, 0), RemapGate.P_DOWN, RAPP, RemapTable.load(forged), ADV | NAVF, null, 0);
        check(!d.consume, "Home is never taken, whatever is saved");
        // immediate vs long
        d = dec(KCHUP, RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(d.consume && d.immediate && !d.longPending && d.action == RemapAction.VOL_UP, "a short press only rule acts at once on the first press");
        d = dec(KTV, RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(d.consume && !d.immediate && d.longPending && d.action == RemapAction.DEFAULT, "with a long press set the first press waits");
        d = dec(KTV, RemapGate.P_SHORT, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.SCREENSHOT, "released early: the short press action");
        d = dec(KTV, RemapGate.P_LONG, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.HOME, "held: the long press action");
        d = dec(KINPUT, RemapGate.P_SHORT, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.NOTHING, "short press DEFAULT next to a long press does nothing (Android cannot hand the press back)");
        d = dec(KINPUT, RemapGate.P_LONG, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.MUTE, "and the long press still works");
        d = dec(KPROFILE, RemapGate.P_DOWN, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.APP && d.arg.equals("com.netflix.ninja"), "LAUNCH APP carries its package");
        d = dec(KCHUP, RemapGate.P_LONG, RAPP, std, 0, null, 0);
        check(d.consume && d.action == RemapAction.VOL_UP, "a long press with no long action repeats the short action");
        // foreground
        d = dec(KCHUP, RemapGate.P_DOWN, null, std, 0, null, 0);
        check(!d.consume, "the app in front is not known yet: nothing is taken");
        d = dec(KCHUP, RemapGate.P_DOWN, "", std, 0, null, 0);
        check(!d.consume, "an empty package counts as not known");
        d = dec(KCHUP, RemapGate.P_DOWN, RGAME, std, 0, gamesSet(), 0);
        check(!d.consume, "in a game (the PS pass-through list) nothing is taken");
        d = dec(KCHUP, RemapGate.P_DOWN, RGAME, std, GAMES, gamesSet(), 0);
        check(d.consume, "REMAP IN GAMES turns it on");
        d = dec(KCHUP, RemapGate.P_DOWN, RAPP, std, 0, gamesSet(), 0);
        check(d.consume, "another app is not a game");
        d = dec(KCHUP, RemapGate.P_DOWN, RSELF, std, 0, gamesSet(), 0);
        check(d.consume, "inside Visor the rule applies");
        // advanced
        RemapTable adv = tableOf(KVOLUP, rule(RemapAction.MUTE, "", 0, ""), KMENU, rule(RemapAction.DECK_SETUP, "", 0, ""));
        d = dec(KVOLUP, RemapGate.P_DOWN, RAPP, adv, 0, null, 0);
        check(!d.consume, "an advanced button is not taken before the advanced unlock");
        d = dec(KVOLUP, RemapGate.P_DOWN, RAPP, adv, ADV, null, 0);
        check(d.consume && d.action == RemapAction.MUTE, "after the unlock it is, in every app, with no second switch");
        d = dec(KMENU, RemapGate.P_DOWN, RSELF, adv, 0, null, 0);
        check(!d.consume, "also inside Visor: no unlock, no remap of the menu button");
        // navigation
        RemapTable nav = tableOf(KOK, rule(RemapAction.HOME, "", 0, ""), KBACK, rule(RemapAction.NOTHING, "", 0, ""), KLEFT, rule(RemapAction.KEY_RIGHT, "", 0, ""), KRIGHT, rule(RemapAction.KEY_LEFT, "", 0, ""));
        d = dec(KOK, RemapGate.P_DOWN, RSELF, nav, 0, null, 0);
        check(!d.consume, "OK inside Visor is not remapped before the advanced unlock");
        d = dec(KOK, RemapGate.P_DOWN, RSELF, nav, ADV, null, 0);
        check(d.consume && d.action == RemapAction.HOME, "OK inside Visor acts after the unlock");
        d = dec(KOK, RemapGate.P_DOWN, RAPP, nav, ADV, null, 0);
        check(!d.consume, "OK in another app stays normal without SYSTEM WIDE NAVIGATION REMAP");
        d = dec(KOK, RemapGate.P_DOWN, RAPP, nav, NAVF, null, 0);
        check(!d.consume, "the system wide switch alone is not enough, advanced must be unlocked too");
        d = dec(KOK, RemapGate.P_DOWN, RAPP, nav, ADV | NAVF, null, 0);
        check(d.consume && d.action == RemapAction.HOME, "OK in another app with both switches runs the action");
        d = dec(KBACK, RemapGate.P_DOWN, RAPP, nav, ADV | NAVF, null, 0);
        check(d.consume && d.immediate && d.action == RemapAction.NOTHING, "Back can be swallowed system wide with both switches");
        d = dec(KLEFT, RemapGate.P_DOWN, RSELF, nav, ADV, null, 0);
        check(!d.consume && d.swapCode == 22 && d.action == RemapAction.KEY_RIGHT, "left read as right inside Visor: a swap, not a consume");
        d = dec(KRIGHT, RemapGate.P_DOWN, RSELF, nav, ADV, null, 0);
        check(d.swapCode == 21, "right read as left");
        d = dec(KLEFT, RemapGate.P_DOWN, RSELF, nav, 0, null, 0);
        check(d.swapCode == 0 && !d.consume, "no swap before the advanced unlock");
        d = dec(KLEFT, RemapGate.P_DOWN, RAPP, nav, ADV | NAVF, null, 0);
        check(d.swapCode == 0 && !d.consume, "a swap exists inside Visor only, other apps get the normal key");
        // escape is the first step
        d = dec(KBACK, RemapGate.P_ESCAPE, RAPP, nav, ADV | NAVF, null, RemapGate.ESC_HELD);
        check(d.clearNav && !d.consume, "the escape chord asks to clear the navigation remaps and lets Back through");
        d = dec(KBACK, RemapGate.P_ESCAPE, null, null, 0, null, 0);
        check(d.clearNav, "the escape works with no table and no known app");
        d = dec(KOK, RemapGate.P_DOWN, RSELF, nav, ADV | NAVF, null, RemapGate.ESC_FIRED);
        check(!d.consume, "once the escape fired no navigation button is taken, even though the saved rules are still there");
        d = dec(KBACK, RemapGate.P_DOWN, RAPP, nav, ADV | NAVF, null, RemapGate.ESC_FIRED);
        check(!d.consume, "including Back itself");
        d = dec(KLEFT, RemapGate.P_DOWN, RSELF, nav, ADV, null, RemapGate.ESC_FIRED);
        check(d.swapCode == 0, "and no swap");
        d = dec(KCHUP, RemapGate.P_DOWN, RAPP, std, 0, null, RemapGate.ESC_FIRED);
        check(d.consume, "the escape does not touch the standard buttons");
        d = dec(KBACK, RemapGate.P_DOWN, RAPP, nav, ADV | NAVF, null, RemapGate.ESC_HELD);
        check(d.consume, "while Back is only held (not yet 6 seconds) its rule still applies");
        // conflict: both a short and long action on a nav key
        RemapTable both = tableOf(KOK, rule(RemapAction.HOME, "", RemapAction.RECENTS, ""));
        d = dec(KOK, RemapGate.P_DOWN, RSELF, both, ADV, null, 0);
        check(d.consume && d.longPending, "OK with a short and a long action waits for the release");
        d = dec(KOK, RemapGate.P_LONG, RSELF, both, ADV, null, 0);
        check(d.action == RemapAction.RECENTS, "and the long action runs when held");
        // both default: nothing
        RemapTable none = tableOf(KCHUP, rule(0, "", 0, ""));
        d = dec(KCHUP, RemapGate.P_DOWN, RAPP, none, 0, null, 0);
        check(!d.consume, "a learned button with both presses DEFAULT is left alone");
        // reuse of the Decision object
        RemapGate.Decision reuse = new RemapGate.Decision();
        RemapGate.decide(reuse, KCHUP, RemapGate.P_DOWN, RAPP, RSELF, std, 0, null, 0);
        RemapGate.decide(reuse, RemapKey.pack(172, 0, 0, 0), RemapGate.P_DOWN, RAPP, RSELF, std, 0, null, 0);
        check(!reuse.consume && reuse.action == RemapAction.DEFAULT && reuse.arg.isEmpty(), "a Decision that is reused starts clean");
    }

    static void remapGate() {
        section("2.6 remap: press tracking (pairs stay whole)");
        RemapTable t = tableOf(KCHUP, rule(RemapAction.VOL_UP, "", 0, ""), KTV, rule(RemapAction.SCREENSHOT, "", RemapAction.HOME, ""), KINPUT, rule(RemapAction.NOTHING, "", 0, ""),
                KPROFILE, rule(RemapAction.APP, "com.netflix.ninja", 0, ""));
        RemapGate g = new RemapGate(RSELF);
        g.table = t;
        g.pass = gamesSet();
        long now = 10000;
        // immediate
        check(g.onKey(KCHUP, true, 0, now, RAPP) == RemapGate.ACT && g.act == RemapAction.VOL_UP, "channel up runs VOLUME UP on the first press");
        check(g.onKey(KCHUP, true, 1, now + 400, RAPP) == RemapGate.SWALLOW, "its repeats are swallowed");
        check(g.onKey(KCHUP, false, 0, now + 900, RAPP) == RemapGate.SWALLOW, "its release is swallowed too");
        check(g.onKey(KCHUP, false, 0, now + 950, RAPP) == RemapGate.PASS, "a release with no press behind it passes");
        // rate guard
        now += 5000;
        check(g.onKey(KCHUP, true, 0, now, RAPP) == RemapGate.ACT, "pressed again later: it runs again");
        g.onKey(KCHUP, false, 0, now + 40, RAPP);
        check(g.onKey(KCHUP, true, 0, now + 60, RAPP) == RemapGate.SWALLOW, "a second press within 120 ms is swallowed without running again (bounce guard)");
        g.onKey(KCHUP, false, 0, now + 80, RAPP);
        // do nothing
        now += 5000;
        check(g.onKey(KINPUT, true, 0, now, RAPP) == RemapGate.SWALLOW && g.act == RemapAction.NOTHING, "DO NOTHING swallows and runs nothing");
        check(g.onKey(KINPUT, false, 0, now + 50, RAPP) == RemapGate.SWALLOW, "and swallows the release");
        // short with a long action
        now += 5000;
        check(g.onKey(KTV, true, 0, now, RAPP) == RemapGate.SWALLOW, "TV with a long press waits: the first press is swallowed");
        check(g.busy() && g.nextDelay(now) == RemapGate.LONG_MS && g.nextDelay(now + 100) == RemapGate.LONG_MS - 100, "the timer is asked to come back at 500 ms");
        check(g.onKey(KTV, false, 0, now + 200, RAPP) == RemapGate.ACT && g.act == RemapAction.SCREENSHOT && !g.busy(), "released after 200 ms: the short press action");
        // long via the timer
        now += 5000;
        g.onKey(KTV, true, 0, now, RAPP);
        check(g.poll(now + 499, RAPP) == RemapGate.NONE, "not yet at 499 ms");
        check(g.poll(now + 500, RAPP) == RemapGate.LONG_ACT && g.act == RemapAction.HOME, "at 500 ms the long press action runs");
        check(g.poll(now + 600, RAPP) == RemapGate.NONE, "and only once");
        check(g.onKey(KTV, true, 5, now + 800, RAPP) == RemapGate.SWALLOW && g.onKey(KTV, false, 0, now + 1200, RAPP) == RemapGate.SWALLOW, "the repeats and the release after a long press are swallowed, no short press follows");
        // long when the timer was late
        now += 5000;
        g.onKey(KTV, true, 0, now, RAPP);
        check(g.onKey(KTV, false, 0, now + 900, RAPP) == RemapGate.ACT && g.act == RemapAction.HOME, "a late timer: the release after 900 ms still counts as the long press");
        // launch app
        now += 5000;
        check(g.onKey(KPROFILE, true, 0, now, RAPP) == RemapGate.ACT && g.act == RemapAction.APP && g.arg.equals("com.netflix.ninja"), "LAUNCH APP gives the package");
        g.onKey(KPROFILE, false, 0, now + 30, RAPP);
        // pass through
        now += 5000;
        long guide = RemapKey.pack(172, 0, 0, 0);
        check(g.onKey(guide, true, 0, now, RAPP) == RemapGate.PASS && g.onKey(guide, true, 2, now + 300, RAPP) == RemapGate.PASS && g.onKey(guide, false, 0, now + 600, RAPP) == RemapGate.PASS, "a button with no rule: every event passes");
        // a game
        now += 5000;
        check(g.onKey(KCHUP, true, 0, now, RGAME) == RemapGate.PASS && g.onKey(KCHUP, false, 0, now + 100, RGAME) == RemapGate.PASS, "in a game the button goes through, down and up");
        // down in a normal app, app switches to a game before the up
        now += 5000;
        g.onKey(KTV, true, 0, now, RAPP);
        check(g.onKey(KTV, false, 0, now + 100, RGAME) == RemapGate.SWALLOW, "if the app changes to a game while a taken button is held, the release ends quietly");
        // a button passed down in a game, then the release in another app still passes
        now += 5000;
        g.onKey(KCHUP, true, 0, now, RGAME);
        check(g.onKey(KCHUP, false, 0, now + 100, RAPP) == RemapGate.PASS, "an up whose down went to the app goes to the app");
        // new first press frees an old unfinished press
        now += 5000;
        g.onKey(KTV, true, 0, now, RAPP);
        check(g.onKey(KTV, true, 0, now + 2000, RAPP) == RemapGate.SWALLOW, "a first press while the old one never ended starts over");
        g.onKey(KTV, false, 0, now + 2100, RAPP);
        // many keys at once: six taken presses fill the slots
        g.reset();
        now += 5000;
        Object[] kv = new Object[14];
        for (int i = 0; i < 7; i++) { kv[i * 2] = Long.valueOf(RemapKey.pack(1000 + i, 0, 0, 0)); kv[i * 2 + 1] = rule(RemapAction.HOME, "", RemapAction.BACK, ""); }
        g.table = tableOf(kv);
        boolean sixTaken = true;
        for (int i = 0; i < 6; i++) sixTaken &= g.onKey(RemapKey.pack(1000 + i, 0, 0, 0), true, 0, now, RAPP) == RemapGate.SWALLOW;
        check(sixTaken, "six buttons held at once are all followed");
        check(g.onKey(RemapKey.pack(1006, 0, 0, 0), true, 0, now, RAPP) == RemapGate.PASS && !g.holds(RemapKey.pack(1006, 0, 0, 0)), "with every slot full a seventh button is left alone, never half followed");
        check(g.onKey(RemapKey.pack(1006, 0, 0, 0), false, 0, now + 10, RAPP) == RemapGate.PASS, "and its release passes too");
        g.table = t;
        g.reset();
        // a passed press leaves no slot, so keys with no rule can never use up the room
        for (int i = 0; i < 20; i++) g.onKey(RemapKey.pack(2000 + i, 0, 0, 0), true, 0, now, RAPP);
        check(g.onKey(KTV, true, 0, now + 1, RAPP) == RemapGate.SWALLOW && g.holds(KTV), "twenty held keys with no rule leave room for a taken one");
        g.reset();
        // stale
        now += 5000;
        g.onKey(KTV, true, 0, now, RAPP);
        check(g.busy(), "waiting for a long press is busy");
        g.poll(now + 500, RAPP);
        g.onKey(guide, true, 0, now + RemapGate.STALE_MS + 1, RAPP);
        check(!g.holds(KTV), "a press that never ended is forgotten after 30 seconds");
        // table change mid press
        g.reset();
        now += 50000;
        g.onKey(KTV, true, 0, now, RAPP);
        g.table = RemapTable.EMPTY;
        check(g.onKey(KTV, false, 0, now + 100, RAPP) == RemapGate.SWALLOW, "rules removed while a press is held: the release ends quietly, nothing runs");
        g.table = t;
        // a null table falls back to PASS
        g.reset();
        g.table = null;
        check(g.onKey(KCHUP, true, 0, now + 70000, RAPP) == RemapGate.PASS && g.onKey(KCHUP, false, 0, now + 70100, RAPP) == RemapGate.PASS, "no table: everything passes");
        g.table = t;
        // fg unknown
        g.reset();
        check(g.onKey(KCHUP, true, 0, now + 80000, null) == RemapGate.PASS, "app in front not known: pass");
        // invalid key
        check(g.onKey(RemapKey.INVALID, true, 0, now, RAPP) == RemapGate.PASS && g.onKey(0L, false, 0, now, RAPP) == RemapGate.PASS, "an invalid key passes");
        // swap sequence inside Deck, stateless
        RemapTable sw = tableOf(KLEFT, rule(RemapAction.KEY_RIGHT, "", 0, ""), KRIGHT, rule(RemapAction.KEY_LEFT, "", 0, ""), KOK, rule(RemapAction.KEY_BACK, "", 0, ""), KBACK, rule(RemapAction.KEY_OK, "", 0, ""));
        RemapGate dg = new RemapGate(RSELF);
        dg.table = sw;
        dg.flags = RemapGate.F_ADVANCED;
        check(dg.onKey(KLEFT, true, 0, 1000, RSELF) == RemapGate.SWAP && dg.swapCode == 22 && dg.onKey(KLEFT, true, 3, 1300, RSELF) == RemapGate.SWAP && dg.swapCode == 22
                && dg.onKey(KLEFT, false, 0, 1500, RSELF) == RemapGate.SWAP && dg.swapCode == 22, "a swapped button is read as the other key on down, repeat and up");
        check(dg.onKey(KOK, true, 0, 2000, RSELF) == RemapGate.SWAP && dg.swapCode == 4 && dg.onKey(KBACK, true, 0, 2100, RSELF) == RemapGate.SWAP && dg.swapCode == 23, "OK and Back swap");
        // loops: swaps do not chain, the result key is not looked up again
        long a = dg.swapCode;
        check(a == 23, "a swap is one step: Back read as OK is not then read as Back again (the caller uses the key once)");
        dg.flags = 0;
        check(dg.onKey(KLEFT, true, 0, 3000, RSELF) == RemapGate.PASS, "swaps off before the unlock");
        dg.flags = RemapGate.F_ADVANCED | RemapGate.F_NAV;
        check(dg.onKey(KLEFT, true, 0, 4000, RAPP) == RemapGate.PASS, "swaps never leave Visor");
        // DECK KEY actions are not run as actions
        RemapGate home = new RemapGate(RSELF);
        home.table = tableOf(KOK, rule(RemapAction.HOME, "", 0, ""));
        home.flags = RemapGate.F_ADVANCED;
        check(home.onKey(KOK, true, 0, 5000, RSELF) == RemapGate.ACT && home.act == RemapAction.HOME, "inside Visor a navigation button can run a real action after the unlock");
        // Deck mid press from the service: fg is Deck but the press began elsewhere
        RemapGate svc = new RemapGate(RSELF);
        svc.table = t;
        svc.onKey(KTV, true, 0, 6000, RAPP);
        check(svc.holds(KTV) && !svc.holds(KCHUP), "holds() says which presses the gate took");
        check(svc.onKey(KTV, false, 0, 6100, RSELF) == RemapGate.ACT, "the release of a press that began in another app still completes");
        // a second gate (Deck's) never sees the keys the first took
        check(RemapGate.LONG_MS == 500 && RemapGate.ESCAPE_MS == 6000 && RemapGate.MIN_GAP_MS == 120, "the timings the spec names: 500 ms long press, 6 s escape");
    }

    static void remapEscape() {
        section("2.6 remap: recovery (hold Back, reset row, deck_cmd)");
        RemapTable nav = tableOf(KOK, rule(RemapAction.HOME, "", 0, ""), KBACK, rule(RemapAction.NOTHING, "", 0, ""), KCHUP, rule(RemapAction.VOL_UP, "", 0, ""));
        RemapGate g = new RemapGate(RSELF);
        g.table = nav;
        g.flags = RemapGate.F_ADVANCED | RemapGate.F_NAV;
        long t0 = 100000;
        check(g.onKey(KBACK, true, 0, t0, RAPP) == RemapGate.SWALLOW, "Back is down and its rule (DO NOTHING) takes it");
        check(g.esc == RemapGate.ESC_HELD && g.busy(), "holding Back arms the escape");
        check(g.nextDelay(t0) == RemapGate.ESCAPE_MS && g.nextDelay(t0 + 4000) == 2000, "the timer is asked to come back when the 6 seconds are up");
        check(g.poll(t0 + 5999, RAPP) == RemapGate.NONE && g.esc == RemapGate.ESC_HELD, "5.999 seconds: nothing yet");
        check(g.poll(t0 + 6000, RAPP) == RemapGate.ESCAPE && g.esc == RemapGate.ESC_FIRED, "6 seconds: the escape fires, even though Back is remapped to DO NOTHING");
        check(g.poll(t0 + 7000, RAPP) == RemapGate.NONE, "once per hold");
        check(g.onKey(KOK, true, 0, t0 + 7100, RAPP) == RemapGate.PASS, "until Back is let go no navigation button is taken, before the saved rules are even cleared");
        g.onKey(KOK, false, 0, t0 + 7200, RAPP);
        check(g.onKey(KCHUP, true, 0, t0 + 7300, RAPP) == RemapGate.ACT, "channel up keeps working during the escape");
        g.onKey(KCHUP, false, 0, t0 + 7400, RAPP);
        g.onKey(KBACK, false, 0, t0 + 7500, RAPP);
        check(g.esc == RemapGate.ESC_IDLE && !g.busy(), "letting go of Back ends the escape");
        // a short Back press never arms the escape for long
        g.onKey(KBACK, true, 0, t0 + 20000, RAPP);
        g.onKey(KBACK, false, 0, t0 + 20300, RAPP);
        check(g.poll(t0 + 30000, RAPP) == RemapGate.NONE, "a short Back press does not escape");
        // Back with no rule at all still escapes
        RemapGate g2 = new RemapGate(RSELF);
        g2.table = tableOf(KCHUP, rule(RemapAction.VOL_UP, "", 0, ""));
        check(g2.onKey(KBACK, true, 0, 1000, RAPP) == RemapGate.PASS && g2.busy(), "Back without a rule passes, and still arms the escape");
        check(g2.poll(7000, RAPP) == RemapGate.ESCAPE, "and escapes after 6 seconds");
        // repeats of Back (Deck's window sends them) do not restart the clock
        RemapGate g3 = new RemapGate(RSELF);
        g3.table = nav;
        g3.flags = RemapGate.F_ADVANCED;
        g3.onKey(KBACK, true, 0, 0, RSELF);
        for (int i = 1; i < 60; i++) g3.onKey(KBACK, true, i, i * 100L, RSELF);
        check(g3.poll(6000, RSELF) == RemapGate.ESCAPE, "key repeats do not restart the 6 seconds");
        // the whole recovery through the saved data
        MapSink s = new MapSink();
        RemapTable.save(s, KOK, rule(RemapAction.HOME, "", 0, ""));
        RemapTable.save(s, KBACK, rule(RemapAction.NOTHING, "", 0, ""));
        RemapTable.save(s, KCHUP, rule(RemapAction.VOL_UP, "", 0, ""));
        s.putBool(RemapTable.KEY_ADV, true);
        s.putBool(RemapTable.KEY_NAV, true);
        RemapGate live = new RemapGate(RSELF);
        live.table = s.table();
        live.flags = RemapGate.F_ADVANCED | RemapGate.F_NAV;
        live.onKey(KBACK, true, 0, 0, RAPP);
        check(live.poll(6000, RAPP) == RemapGate.ESCAPE, "service: Back held 6 s");
        RemapTable.clearNav(s, new java.util.HashMap<String, Object>(s.m));
        live.table = s.table();
        live.flags = (s.flag(RemapTable.KEY_ADV) ? RemapGate.F_ADVANCED : 0) | (s.flag(RemapTable.KEY_NAV) ? RemapGate.F_NAV : 0);
        live.onKey(KBACK, false, 0, 6100, RAPP);
        check(live.onKey(KOK, true, 0, 7000, RAPP) == RemapGate.PASS && live.onKey(KBACK, true, 0, 7100, RAPP) == RemapGate.PASS, "after the clear OK and Back are plain keys again");
        live.onKey(KBACK, false, 0, 7200, RAPP);
        check(live.onKey(KCHUP, true, 0, 8000, RAPP) == RemapGate.ACT, "and the channel up rule is still there");
        // RESET ALL
        RemapTable.resetAll(s, new java.util.HashMap<String, Object>(s.m));
        live.table = s.table();
        live.flags = 0;
        live.reset();
        check(live.onKey(KCHUP, true, 0, 9000, RAPP) == RemapGate.PASS && s.table().size() == 0, "RESET ALL REMAPS: nothing is taken any more");
        // reset() clears a stuck state
        RemapGate stuck = new RemapGate(RSELF);
        stuck.table = tableOf(KTV, rule(RemapAction.SCREENSHOT, "", RemapAction.HOME, ""));
        stuck.onKey(KTV, true, 0, 0, RAPP);
        stuck.onKey(KBACK, true, 0, 0, RAPP);
        stuck.reset();
        check(!stuck.busy() && !stuck.holds(KTV) && stuck.esc == RemapGate.ESC_IDLE && stuck.onKey(KTV, false, 0, 10, RAPP) == RemapGate.PASS, "reset() forgets every half press, so an error can never leave a button swallowed");
    }

    static void remapLearn() {
        section("2.6 remap: LEARN A BUTTON merges what Visor and the service saw");
        RemapLearn l = new RemapLearn();
        check(!l.any() && l.verdict(true)[0].startsWith("PRESS A BUTTON"), "empty at first");
        check(l.feed(true, 170, 0, 0, 0, "ONN REMOTE", 0x101, 1000) && l.svc && !l.deck && l.key == KTV, "the service sees it first");
        check(l.feed(false, 170, 0, 0, 0, "ONN REMOTE", 0x501, 1002) && l.svc && l.deck && l.key == KTV && l.device.equals("ONN REMOTE") && l.source == 0x501, "then Visor: both flags on the same press");
        check(l.verdict(true)[0].startsWith("BOTH SAW IT"), "both: it can be caught in every app");
        check(l.feed(false, 166, 0, 0, 0, "ONN REMOTE", 0x501, 1500) && !l.svc && l.deck && l.key == KCHUP, "another button starts a new capture, the old flags are gone");
        check(l.feed(true, 166, 0, 0, 0, "", 0, 4300) && l.key == KCHUP && l.svc && !l.deck, "the same button after 2.5 s is a new press");
        check(l.verdict(true)[0].startsWith("ONLY THE SERVICE SAW IT") && l.verdict(true)[0].contains("TRY IT"), "service only: says the system took it before Visor, and not to trust it blindly");
        l.clear();
        l.feed(false, 172, 0, 0, 0, "R", 0x101, 10);
        check(l.verdict(true)[0].startsWith("ONLY VISOR SAW IT") && l.verdict(true)[0].contains("INSIDE VISOR ONLY"), "Visor only with the service on: the key filter did not get it");
        check(l.verdict(false)[0].contains("THE SERVICE IS OFF"), "Visor only with the service off says so");
        check(l.verdict(true).length == 1, "a standard button has one line");
        l.clear();
        l.feed(false, 82, 0, 0, 0, "R", 0x101, 10);
        check(l.verdict(true).length == 2 && l.verdict(true)[1].contains("ADVANCED REMAP"), "a system button points to ADVANCED REMAP");
        check(!l.canAssign(false) && l.canAssign(true), "assign needs the unlock for system buttons");
        l.clear();
        l.feed(false, 26, 0, 0, 0, "R", 0x101, 10);
        check(l.verdict(true)[0].startsWith("THIS BUTTON CANNOT BE REMAPPED") && !l.canAssign(true), "a protected button says why");
        l.clear();
        check(l.feed(false, 0, 233, 4012, 9001, "REMOTE", 0x101, 10) && l.key == RemapKey.parse("sc:233:4012:9001") && l.code == 0 && l.scan == 233, "key code 0: named by scan code and device");
        check(l.canAssign(false), "an unknown scan code button can be assigned with no unlock");
        check(!l.feed(false, 0, -4, 0, 0, "", 0, 20), "nonsense is ignored");
        l.clear();
        check(!l.any() && l.device.isEmpty() && !l.deck && !l.svc, "clear() empties it");
        check(RemapLearn.sourceText(0x301).equals("0x301 KEYBOARD DPAD") && RemapLearn.sourceText(0x1000511).contains("JOYSTICK") && RemapLearn.sourceText(0).equals("0x0")
                && RemapLearn.sourceText(0x02000001).contains("HDMI") && !RemapLearn.sourceText(0x02000001).contains("KEYBOARD"), "source names");
        check(RemapLearn.filterSource(0x501) && RemapLearn.filterSource(0x101) && !RemapLearn.filterSource(0x02000001) && !RemapLearn.filterSource(0x1002), "the key filter only takes sources that include KEYBOARD");
    }

    static void remapHold() {
        section("2.6 remap: hold OK or A to continue");
        RemapHold h = new RemapHold(3000);
        check(!h.holding() && h.progress(0) == 0f && !h.due(5000), "idle");
        h.down(1000);
        check(h.holding() && Math.abs(h.progress(2500) - 0.5f) < 0.001f, "half way after 1.5 s");
        h.down(1900);
        check(Math.abs(h.progress(2500) - 0.5f) < 0.001f, "a repeat of the key does not restart it");
        check(!h.due(3999) && h.due(4000) && !h.due(4001) && !h.holding(), "fires once at 3 s");
        h.up();
        h.down(10000);
        h.up();
        check(!h.due(20000) && h.progress(20000) == 0f, "letting go starts over");
        h.down(30000);
        check(h.progress(99999) == 1f, "progress stops at 1");
        check(new RemapHold(0).ms == 1, "a zero time is one millisecond, not a divide by zero");
    }

    static void remapCmd() {
        section("2.6 deck_cmd: reset_remap and open_inputs");
        check(DeckCmd.parse("reset_remap") == DeckCmd.RESET_REMAP && DeckCmd.parse("open_inputs") == DeckCmd.OPEN_INPUTS, "the two new commands");
        check(DeckCmd.parse("reset_remap ") == DeckCmd.NONE && DeckCmd.parse("RESET_REMAP") == DeckCmd.NONE && DeckCmd.parse(" reset_remap") == DeckCmd.NONE && DeckCmd.parse("reset_remap;ls") == DeckCmd.NONE
                && DeckCmd.parse("reset") == DeckCmd.NONE && DeckCmd.parse("remap") == DeckCmd.NONE, "near misses are ignored");
        check(DeckCmd.NAMES.length >= 6 && DeckCmd.NAMES[DeckCmd.RESET_REMAP].equals("reset_remap") && DeckCmd.NAMES[DeckCmd.OPEN_INPUTS].equals("open_inputs"), "the 2.6 names are still where they were (2.7 adds two more, see Tests27)");
        check(DeckCmd.parse("reload_packs") == DeckCmd.RELOAD_PACKS && DeckCmd.parse("open_setup") == DeckCmd.OPEN_SETUP, "the old ones still work");
        boolean unique = true;
        HashSet<String> seen = new HashSet<>();
        for (String n : DeckCmd.NAMES) unique &= seen.add(n);
        check(unique && DeckCmd.NAMES[0].isEmpty(), "no duplicates");
    }

    static void remapHelp() {
        section("2.6 remap: every new row and action has a help sentence");
        boolean keys = true;
        String miss = "";
        for (RemapKey.Def d : RemapKey.all()) {
            long k = RemapKey.pack(d.code, 0, 0, 0);
            String s = RowHelp.of("settings", RemapKey.helpId(k));
            if (s == null || !s.equals(d.help)) { keys = false; miss += " " + d.code; }
        }
        check(keys, "every known button finds its sentence through its help id:" + miss);
        check(RowHelp.of("settings", RemapKey.helpId(RemapKey.pack(345, 0, 0, 0))) != null && RowHelp.of("remapadv", RemapKey.helpId(KOK)) != null, "unknown buttons and the advanced screen too");
        boolean acts = true;
        for (int a = 0; a < RemapAction.COUNT; a++) acts &= RowHelp.of("remapact", RemapAction.helpId(a)) != null && RowHelp.of("remapact", RemapAction.helpId(a)).equals(RemapAction.help(a));
        check(acts && RowHelp.of("remapact", "remap.act.same") != null, "every action row, and SAME AS SHORT PRESS, finds its sentence");
        String[] labels = {"LEARN A BUTTON", "REMAP OUTSIDE VISOR", "REMAP IN GAMES", "RESET ALL REMAPS", "ADVANCED REMAP", "SHORT PRESS", "LONG PRESS", "CLEAR THIS BUTTON", "WHERE IT WORKS",
                "IF NAVIGATION BREAKS", "SYSTEM WIDE NAVIGATION REMAP", "SWAP OK AND BACK IN VISOR", "SWAP LEFT AND RIGHT IN VISOR", "TURN OFF ADVANCED REMAP"};
        boolean rows = true;
        for (String l : labels) rows &= RowHelp.known(l);
        check(rows, "every fixed remap row has a sentence");
        check(RowHelp.of("remapkey", "SHORT PRESS") != null && !RowHelp.of("remapkey", "SHORT PRESS").equals(RowHelp.of("remapkey", "LONG PRESS")) && RowHelp.of("remapapp", "ANY APP") != null, "the key page and the app picker");
        check(RowHelp.covers("remapkey") && RowHelp.covers("remapact") && RowHelp.covers("remapapp") && RowHelp.covers("remapadv"), "the four new panel kinds show help");
        check(RowHelp.tab("REMAP") != null, "the REMAP tab has a sentence");
    }
}
