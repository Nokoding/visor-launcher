package com.nokoding.deck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.7 tests: clock seconds, wallpapers, the screensaver. Called from LogicTests.main. */
final class Tests27 {
    private Tests27() {}

    static void run() {
        clockFmt();
        clockZones();
        wallpaperSpec();
        wallpaperSpecHostile();
        wallpaperLayerValues();
        wallpaperOptions();
        wallpapersFolder();
        wallpaperZip();
        parallax();
        overlay();
        wallpaperMem();
        animPlay();
        burnIn();
        dreamRules();
        screensaverCmd();
        deckCmd27();
    }

    static Set<String> names(String... n) { return new HashSet<>(Arrays.asList(n)); }

    // ================================================================== clock

    static void clockFmt() {
        section("2.7 clock: tick delay and formats");
        long min = 60000, sec = 1000;
        // minute ticks
        check(ClockFmt.nextDelay(0, 0, false) == min + ClockFmt.SLACK_MS, "on a minute boundary the next minute tick is a full minute away");
        check(ClockFmt.nextDelay(59999, 0, false) == 1 + ClockFmt.SLACK_MS, "1 ms before the minute the tick is 1 ms plus the slack away");
        check(ClockFmt.nextDelay(30000, 0, false) == 30000 + ClockFmt.SLACK_MS, "half way through a minute");
        // second ticks
        check(ClockFmt.nextDelay(0, 0, true) == sec + ClockFmt.SLACK_MS, "on a second boundary the next second tick is a second away");
        check(ClockFmt.nextDelay(999, 0, true) == 1 + ClockFmt.SLACK_MS, "1 ms before the second");
        check(ClockFmt.nextDelay(1234567, 0, true) == 1000 - 567 + ClockFmt.SLACK_MS, "mid second");
        // a zone offset that is not a whole minute shifts the boundary, a whole-minute offset does not
        check(ClockFmt.nextDelay(1000, 5 * 3600000L + 45 * 60000L, false) == ClockFmt.nextDelay(1000, 0, false), "a whole minute offset (Nepal) keeps the minute boundary");
        check(ClockFmt.nextDelay(1000, 17000, false) == (min - ((1000 + 17000) % min)) + ClockFmt.SLACK_MS, "an odd offset moves the boundary");
        // negative times (before 1970)
        check(ClockFmt.nextDelay(-1, 0, true) == 1 + ClockFmt.SLACK_MS && ClockFmt.nextDelay(-60001, 0, false) == 1 + ClockFmt.SLACK_MS && ClockFmt.nextDelay(-30000, 0, false) == 30000 + ClockFmt.SLACK_MS, "times before 1970 still land on the boundary");
        // always in range
        boolean range = true;
        Random rnd = new Random(7);
        for (int i = 0; i < 20000; i++) {
            long t = rnd.nextLong() % 4000000000000L, off = (rnd.nextInt(50) - 25) * 1800000L;
            long d1 = ClockFmt.nextDelay(t, off, true), d2 = ClockFmt.nextDelay(t, off, false);
            range &= d1 > ClockFmt.SLACK_MS - 1 && d1 <= sec + ClockFmt.SLACK_MS && d2 > ClockFmt.SLACK_MS - 1 && d2 <= min + ClockFmt.SLACK_MS;
        }
        check(range, "the delay is always between the slack and one period plus the slack");
        // ticking never skips or repeats a second
        long t = 1700000000123L;
        int last = ClockFmt.second(t, 0);
        boolean steady = true;
        for (int i = 0; i < 300; i++) {
            t += ClockFmt.nextDelay(t, 0, true);
            int s = ClockFmt.second(t, 0);
            steady &= s == (last + 1) % 60;
            last = s;
        }
        check(steady, "300 second ticks in a row each show the next second (no skip, no repeat)");
        t = 1700000000123L;
        int lm = ClockFmt.minute(t, 0);
        boolean steadyM = true;
        for (int i = 0; i < 200; i++) {
            t += ClockFmt.nextDelay(t, 0, false);
            int m = ClockFmt.minute(t, 0);
            steadyM &= m == (lm + 1) % 60 && ClockFmt.second(t, 0) == 0;
            lm = m;
        }
        check(steadyM, "200 minute ticks in a row each show the next minute at second 0 (nothing runs per second at rest)");
        // fields
        long noon = 12L * 3600000;
        check(ClockFmt.hour(noon, 0) == 12 && ClockFmt.minute(noon + 5 * 60000 + 9000, 0) == 5 && ClockFmt.second(noon + 5 * 60000 + 9000, 0) == 9, "hour, minute and second from an instant");
        check(ClockFmt.hour(0, -5 * 3600000L) == 19 && ClockFmt.hour(23 * 3600000L, 2 * 3600000L) == 1, "the zone offset crosses midnight both ways");
        check(ClockFmt.hour(-1, 0) == 23 && ClockFmt.second(-1, 0) == 59, "one ms before 1970 is 23:59:59");
        // formats
        check(ClockFmt.format(0, 0, true, false).equals("00:00") && ClockFmt.format(0, 0, true, true).equals("00:00:00"), "24 hour midnight");
        check(ClockFmt.format(0, 0, false, false).equals("12:00 AM") && ClockFmt.format(0, 0, false, true).equals("12:00:00 AM"), "12 hour midnight is 12:00 AM");
        long t1 = (9 * 3600 + 59 * 60 + 59) * 1000L;
        check(ClockFmt.format(t1, 0, false, true).equals(" 9:59:59 AM") && ClockFmt.format(t1 + 1000, 0, false, true).equals("10:00:00 AM"), "9:59:59 AM, then 10:00:00 AM");
        check(ClockFmt.format(t1, 0, false, false).equals(" 9:59 AM") && ClockFmt.format(t1, 0, true, false).equals("09:59") && ClockFmt.format(t1, 0, true, true).equals("09:59:59"), "the other combinations");
        long t2 = (12 * 3600 + 5) * 1000L, t3 = (23 * 3600 + 59 * 60 + 59) * 1000L, t4 = 13 * 3600000L;
        check(ClockFmt.format(t2, 0, false, true).equals("12:00:05 PM") && ClockFmt.format(t3, 0, false, true).equals("11:59:59 PM") && ClockFmt.format(t4, 0, false, false).equals(" 1:00 PM"), "noon, 11:59:59 PM and 1 PM");
        check(ClockFmt.format(t3, 0, true, true).equals("23:59:59"), "24 hour 23:59:59");
        // fixed width: the text is the same length all day in each mode, so a right aligned clock never moves
        boolean fixed = true;
        int[] len = new int[4];
        Arrays.fill(len, -1);
        char[] buf = new char[ClockFmt.MAX_CHARS];
        for (int s = 0; s < 86400; s++) {
            for (int m = 0; m < 4; m++) {
                int n = ClockFmt.format(buf, s / 3600, s / 60 % 60, s % 60, (m & 1) == 1, (m & 2) == 2);
                if (len[m] < 0) len[m] = n;
                fixed &= n == len[m] && n <= ClockFmt.MAX_CHARS;
                // every char is a digit, colon, space, A, P or M
                for (int k = 0; k < n; k++) { char c = buf[k]; fixed &= (c >= '0' && c <= '9') || c == ':' || c == ' ' || c == 'A' || c == 'P' || c == 'M'; }
            }
        }
        check(fixed && len[0] == 8 && len[1] == 5 && len[2] == 11 && len[3] == 8, "all 86400 seconds: the text keeps one length per mode (8, 5, 11, 8) and only clock characters");
        int n = ClockFmt.format(buf, 99, -4, 70, true, true);
        check(new String(buf, 0, n).equals("23:00:59"), "bad numbers are clamped");
    }

    static void clockZones() {
        section("2.7 clock: the second clock line");
        check(ClockFmt.ZONE_IDS.length == ClockFmt.ZONE_NAMES.length && ClockFmt.ZONE_IDS.length >= 10, "a list of common zones with a name each");
        HashSet<String> ids = new HashSet<>(), nm = new HashSet<>();
        boolean known = true, caps = true;
        for (int i = 0; i < ClockFmt.ZONE_IDS.length; i++) {
            known &= ids.add(ClockFmt.ZONE_IDS[i]) && nm.add(ClockFmt.ZONE_NAMES[i]);
            TimeZone z = TimeZone.getTimeZone(ClockFmt.ZONE_IDS[i]);
            known &= z.getID().equals(ClockFmt.ZONE_IDS[i]);   // an unknown id would come back as GMT
            caps &= ClockFmt.ZONE_NAMES[i].equals(ClockFmt.ZONE_NAMES[i].toUpperCase(Locale.ROOT)) && ClockFmt.ZONE_NAMES[i].length() <= 16;
        }
        check(known, "every id is unique and a time zone the JVM knows");
        check(caps, "names are capitals and fit the line");
        check(ClockFmt.zoneIndex("Europe/London") >= 0 && ClockFmt.zoneIndex("") == -1 && ClockFmt.zoneIndex(null) == -1 && ClockFmt.zoneIndex("Mars/Base") == -1
                && ClockFmt.zoneIndex("europe/london") == -1 && ClockFmt.zoneIndex("GMT+5") == -1, "only listed ids count (a stray pref never builds a zone)");
        check(ClockFmt.zone("Mars/Base") == null && ClockFmt.zone("Asia/Tokyo").getID().equals("Asia/Tokyo"), "zone() returns null for OFF and a zone for a listed id");
        long winter = 1700000000000L, summer = 1720000000000L;   // 2023-11-14 22:13 UTC and 2024-07-03 09:46 UTC
        String w = ClockFmt.zoneLine("Europe/London", winter, true, false), s = ClockFmt.zoneLine("Europe/London", summer, true, false);
        check(w.equals("LONDON  22:13") && s.equals("LONDON  10:46"), "London is UTC in winter and UTC+1 in summer: " + w + " / " + s);
        check(ClockFmt.zoneLine("Asia/Kolkata", winter, true, true).startsWith("MUMBAI  03:43:20"), "Mumbai is UTC+5:30: " + ClockFmt.zoneLine("Asia/Kolkata", winter, true, true));
        check(ClockFmt.zoneLine("America/New_York", winter, false, false).equals("NEW YORK  5:13 PM"), "12 hour has no leading space inside the line: " + ClockFmt.zoneLine("America/New_York", winter, false, false));
        check(ClockFmt.zoneLine("America/New_York", winter, false, true).equals("NEW YORK  5:13:20 PM"), "12 hour with seconds");
        check(ClockFmt.zoneLine("", winter, true, false).isEmpty() && ClockFmt.zoneLine("nope", winter, true, false).isEmpty() && ClockFmt.zoneLine(null, winter, true, false).isEmpty(), "OFF and bad ids give no line");
        boolean fits = true;
        char[] line = new char[ClockFmt.ZONE_LINE_CHARS];
        for (String id : ClockFmt.ZONE_IDS) for (int m = 0; m < 4; m++) fits &= ClockFmt.zoneLine(line, id, summer, (m & 1) == 1, (m & 2) == 2) <= ClockFmt.ZONE_LINE_CHARS;
        check(fits, "every zone line fits its buffer");
        check(ClockFmt.zoneLine(new char[3], "UTC", winter, true, false) == 0, "a too small buffer gives nothing instead of crashing");
    }

    // ================================================================== wallpaper.json

    static final String IMG = "{\"name\":\"Plain\",\"author\":\"me\",\"version\":2,\"type\":\"image\",\"file\":\"bg.png\"}";

    static void wallpaperSpec() {
        section("2.7 wallpaper.json: good files");
        WallpaperSpec s = WallpaperSpec.parse(IMG, names("bg.png", "wallpaper.json"), "folder");
        check(s.error == null && s.type == WallpaperSpec.IMAGE && s.file.equals("bg.png") && s.name.equals("Plain") && s.author.equals("me") && s.version.equals("2"), "an image pack: " + s.error);
        check(s.files().size() == 1 && s.options.options.isEmpty(), "one file, no options");
        s = WallpaperSpec.parse("{\"type\":\"image\",\"file\":\"BG.PNG\"}", names("bg.png"), "My Folder");
        check(s.error == null && s.name.equals("My Folder"), "file names match in any case, a missing name uses the folder name");
        // layers
        String lay = "{\"type\":\"layers\",\"name\":\"Neon\",\"layers\":["
                + "{\"file\":\"sky.png\",\"parallax\":0.1,\"scale\":1.1,\"opacity\":1},"
                + "{\"file\":\"mid.png\",\"parallax\":0.6},"
                + "{\"file\":\"near.png\",\"parallax\":1.4,\"scale\":1.3,\"opacity\":0.8}]}";
        s = WallpaperSpec.parse(lay, names("sky.png", "mid.png", "near.png"), "x");
        check(s.error == null && s.type == WallpaperSpec.LAYERS && s.layers.size() == 3 && s.files().size() == 3, "a layered pack: " + s.error);
        check(s.layers.get(0).parallax.value == 0.1 && s.layers.get(1).scale.value == 1.0 && s.layers.get(1).opacity.value == 1.0 && s.layers.get(2).opacity.value == 0.8, "layer numbers and defaults");
        check(s.files().get(0).equals("sky.png") && s.files().get(2).equals("near.png"), "layers stay in draw order, back to front");
        // animated
        s = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"loop.gif\",\"fps\":24,\"speed\":0.5}", names("loop.gif"), "x");
        check(s.error == null && s.type == WallpaperSpec.ANIMATED && s.fps == 24 && s.speed == 0.5, "an animated pack: " + s.error);
        s = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"loop.webp\"}", names("loop.webp"), "x");
        check(s.fps == WallpaperSpec.FPS_DEFAULT && s.speed == 1.0, "animated defaults: 20 fps, full speed");
        s = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"loop.gif\",\"fps\":500,\"speed\":3}", names("loop.gif"), "x");
        check(s.error == null && s.fps == WallpaperSpec.FPS_MAX && s.speed == 1.0 && s.notes.size() == 2, "fps and speed are clamped and the pack says so in notes: " + s.notes);
        s = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"loop.gif\",\"fps\":1,\"speed\":0.01}", names("loop.gif"), "x");
        check(s.fps == WallpaperSpec.FPS_MIN && s.speed == WallpaperSpec.SPEED_MIN, "fps and speed clamp from below too");
        s = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"loop.gif\",\"fps\":\"fast\",\"speed\":true}", names("loop.gif"), "x");
        check(s.error == null && s.fps == 20 && s.speed == 1.0, "text where a number should be falls back to the defaults");
        // unknown keys and case
        s = WallpaperSpec.parse("{\"type\":\"IMAGE\",\"file\":\"bg.png\",\"future\":{\"a\":[1,2,3]},\"x\":null}", names("bg.png"), "x");
        check(s.error == null, "unknown keys are ignored and the type is not case sensitive");
        // numbers out of range
        s = WallpaperSpec.parse("{\"type\":\"layers\",\"layers\":[{\"file\":\"a.png\",\"parallax\":99,\"scale\":0.2,\"opacity\":-4},{\"file\":\"b.png\",\"parallax\":\"high\",\"scale\":null}]}", names("a.png", "b.png"), "x");
        check(s.error == null && s.layers.get(0).parallax.value == 2.0 && s.layers.get(0).scale.value == 1.0 && s.layers.get(0).opacity.value == 0.0, "numbers are clamped: parallax 2, scale at least 1, opacity 0 to 1");
        check(s.layers.get(1).parallax.value == 0.5 && s.layers.get(1).scale.value == 1.0, "bad numbers fall back to the defaults");
        // fallback pack without a json
        s = WallpaperSpec.fallback(names("only.png"), "Folder");
        check(s.error == null && s.type == WallpaperSpec.IMAGE && s.file.equals("only.png") && s.name.equals("Folder"), "no json and one image: a plain image");
        s = WallpaperSpec.fallback(names("only.gif"), "Folder");
        check(s.type == WallpaperSpec.ANIMATED, "no json and one gif: animated");
        check(WallpaperSpec.fallback(names("a.png", "b.png"), "F").error != null && WallpaperSpec.fallback(names(), "F").error != null, "no json and two images, or none, is an error");
        check(WallpaperSpec.fallback(names("wallpaper.json"), "F").error != null, "a json alone is not a wallpaper");
    }

    static void wallpaperSpecHostile() {
        section("2.7 wallpaper.json: bad and hostile files");
        Set<String> f = names("bg.png", "a.png");
        String[] bad = {null, "", "   ", "not json", "[1,2,3]", "\"text\"", "42", "{", "{\"type\":", "{}", "{\"type\":\"video\",\"file\":\"bg.png\"}", "{\"type\":null}", "{\"type\":5}",
                "{\"type\":\"image\"}", "{\"type\":\"image\",\"file\":\"\"}", "{\"type\":\"image\",\"file\":\"nothere.png\"}", "{\"type\":\"image\",\"file\":5}",
                "{\"type\":\"layers\"}", "{\"type\":\"layers\",\"layers\":[]}", "{\"type\":\"layers\",\"layers\":\"bg.png\"}", "{\"type\":\"layers\",\"layers\":[1,2,\"x\",null]}",
                "{\"type\":\"layers\",\"layers\":[{\"file\":\"missing.png\"}]}", "{\"type\":\"animated\"}", "{\"format\":2,\"type\":\"image\",\"file\":\"bg.png\"}"};
        boolean allBad = true;
        String which = "";
        for (String b : bad) {
            WallpaperSpec s = WallpaperSpec.parse(b, f, "x");
            if (s.error == null) { allBad = false; which += " [" + b + "]"; }
        }
        check(allBad, "every unusable file gets an error text:" + which);
        // paths and file types
        String[] files = {"../bg.png", "/etc/passwd", "a/b.png", "..\\x.png", "C:\\x.png", ".hidden.png", "bg.png/", "x\u0000.png", "run.sh", "classes.dex", "wallpaper.json", "bg", "bg.png.exe", "a..png"};
        boolean refused = true;
        for (String fn : files) {
            String js = "{\"type\":\"image\",\"file\":\"" + fn.replace("\\", "\\\\").replace("\u0000", "\\u0000") + "\"}";
            WallpaperSpec s = WallpaperSpec.parse(js, names(fn, "bg.png"), "x");
            if (s.error == null) { refused = false; which += " [" + fn + "]"; }
        }
        check(refused, "paths, hidden files and other file types are refused as a wallpaper file:" + which);
        // errors are plain text in capitals-ish, short
        WallpaperSpec e = WallpaperSpec.parse("{\"type\":\"image\",\"file\":\"nothere.png\"}", f, "x");
        check(e.error.contains("NOT IN THE PACK") && e.error.length() < 80, "the error names the missing file: " + e.error);
        // size
        StringBuilder big = new StringBuilder("{\"type\":\"image\",\"file\":\"bg.png\",\"pad\":\"");
        for (int i = 0; i < WallpaperSpec.MAX_JSON; i++) big.append('x');
        big.append("\"}");
        check(WallpaperSpec.parse(big.toString(), f, "x").error != null, "a json over 64 KB is refused");
        // deep nesting must not crash
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 30000; i++) deep.append('[');
        WallpaperSpec d = WallpaperSpec.parse(deep.toString(), f, "x");
        check(d.error != null, "30000 nested brackets give an error, not a crash");
        StringBuilder deep2 = new StringBuilder("{\"type\":\"image\",\"file\":\"bg.png\",\"x\":");
        for (int i = 0; i < 20000; i++) deep2.append('[');
        d = WallpaperSpec.parse(deep2.toString(), f, "x");
        check(d.error != null, "deep nesting inside an object gives an error, not a crash");
        // too many layers
        StringBuilder many = new StringBuilder("{\"type\":\"layers\",\"layers\":[");
        for (int i = 0; i < 40; i++) many.append(i > 0 ? "," : "").append("{\"file\":\"a.png\"}");
        many.append("]}");
        WallpaperSpec m = WallpaperSpec.parse(many.toString(), f, "x");
        check(m.error == null && m.layers.size() == WallpaperSpec.MAX_LAYERS, "layers are capped at " + WallpaperSpec.MAX_LAYERS);
        // some layers bad: the good ones stay
        WallpaperSpec g = WallpaperSpec.parse("{\"type\":\"layers\",\"layers\":[{\"file\":\"gone.png\"},{\"file\":\"a.png\"},7]}", f, "x");
        check(g.error == null && g.layers.size() == 1 && g.notes.size() == 2, "a layer without its file is skipped and noted: " + g.notes);
        // names are cleaned
        WallpaperSpec nm = WallpaperSpec.parse("{\"name\":\"<b>Bad\\nname\\u0007|x</b>\",\"author\":\"\\u202eevil\",\"type\":\"image\",\"file\":\"bg.png\"}", f, "x");
        boolean plain = true;
        for (char c : (nm.name + nm.author).toCharArray()) plain &= c >= 32 && c < 127;
        check(nm.error == null && plain && !nm.name.contains("|") && !nm.name.contains("<"), "names keep plain characters only: [" + nm.name + "] [" + nm.author + "]");
        // option objects that try to break the schema line
        String inj = "{\"type\":\"image\",\"file\":\"bg.png\",\"options\":[{\"key\":\"glow\",\"label\":\"A | B\\nc | CHOICE | X\",\"type\":\"INT\",\"min\":0,\"max\":10,\"default\":3}]}";
        WallpaperSpec ij = WallpaperSpec.parse(inj, f, "x");
        AnimOption o = ij.options.get("glow");
        check(ij.error == null && o != null && o.type == AnimOption.INT_RANGE && !o.label.contains("|") && !o.label.contains("\n"), "a label cannot add schema fields: [" + (o == null ? "null" : o.label) + "]");
    }

    static void wallpaperOptions() {
        section("2.7 wallpaper.json: the pack's own options");
        String js = "{\"type\":\"image\",\"file\":\"bg.png\",\"options\":["
                + "{\"key\":\"mood\",\"label\":\"Mood\",\"type\":\"CHOICE\",\"choices\":[\"Night\",\"Day\",\"Dusk\"],\"default\":\"Dusk\"},"
                + "{\"key\":\"fog\",\"label\":\"Fog\",\"type\":\"INT_RANGE\",\"min\":0,\"max\":100,\"step\":10,\"default\":40,\"unit\":\"%\"},"
                + "{\"key\":\"glow\",\"label\":\"Glow\",\"type\":\"FLOAT_RANGE\",\"min\":0,\"max\":1,\"step\":0.05,\"default\":0.5},"
                + "{\"key\":\"stars\",\"label\":\"Stars\",\"type\":\"BOOL\",\"default\":true},"
                + "{\"key\":\"accent\",\"label\":\"Accent\",\"type\":\"COLOR\",\"default\":\"#22E4FF\"}]}";
        WallpaperSpec s = WallpaperSpec.parse(js, names("bg.png"), "x");
        check(s.error == null && s.options.options.size() == 5, "all five option types are read: " + s.notes);
        AnimOption mood = s.options.get("mood"), fog = s.options.get("fog"), glow = s.options.get("glow"), stars = s.options.get("stars"), acc = s.options.get("accent");
        check(mood.type == AnimOption.CHOICE && mood.choice().equals("DUSK") && mood.choices.length == 3, "CHOICE with a default");
        check(fog.type == AnimOption.INT_RANGE && fog.i() == 40 && fog.min == 0 && fog.max == 100 && fog.step == 10 && fog.unit.equals("%"), "INT_RANGE with min, max, step, default and unit");
        check(glow.type == AnimOption.FLOAT_RANGE && Math.abs(glow.value - 0.5) < 1e-9 && glow.step == 0.05, "FLOAT_RANGE");
        check(stars.type == AnimOption.BOOL && stars.on() && acc.type == AnimOption.COLOR && acc.color() == 0xFF22E4FF, "BOOL and COLOR");
        // bad defaults and bounds fall back
        String bad = "{\"type\":\"image\",\"file\":\"bg.png\",\"options\":["
                + "{\"key\":\"a\",\"type\":\"INT\",\"min\":0,\"max\":10,\"default\":99},"
                + "{\"key\":\"b\",\"type\":\"INT\",\"min\":10,\"max\":0},"
                + "{\"key\":\"c\",\"type\":\"CHOICE\",\"choices\":[\"One\"]},"
                + "{\"key\":\"d\",\"type\":\"CHOICE\",\"choices\":[\"One\",\"Two\"],\"default\":\"Three\"},"
                + "{\"key\":\"e\",\"type\":\"COLOR\",\"default\":\"red\"},"
                + "{\"key\":\"Bad Key\",\"type\":\"BOOL\"},"
                + "{\"key\":\"f\",\"type\":\"SLIDER\"},"
                + "{\"key\":\"g\",\"type\":\"FLOAT\",\"min\":-5,\"max\":5,\"step\":-1,\"default\":\"x\"},"
                + "{\"key\":\"h\",\"type\":\"INT\",\"min\":0,\"max\":1e12},"
                + "5, null, \"x\"]}";
        WallpaperSpec b = WallpaperSpec.parse(bad, names("bg.png"), "x");
        check(b.error == null, "a pack with bad options still loads: " + b.error);
        check(b.options.get("a") != null && b.options.get("a").i() == 10, "a default above the maximum is clamped to it");
        check(b.options.get("b") == null && b.options.get("c") == null, "min above max, and a one choice list, are skipped");
        check(b.options.get("d") != null && b.options.get("d").choice().equals("ONE"), "a default that is not a choice becomes the first choice");
        check(b.options.get("e") != null && b.options.get("e").color() == 0xFFFF2E97, "a bad color default becomes the pink");
        check(b.options.get("f") == null && b.options.get("g") != null && b.options.get("g").value == -5, "an unknown type is skipped, a bad step and default fall back");
        check(b.options.get("h") == null, "an absurd range is skipped");
        check(b.notes.size() >= 5, "skipped options are noted: " + b.notes.size());
        // reserved and duplicate keys
        String res = "{\"type\":\"image\",\"file\":\"bg.png\",\"options\":[{\"key\":\"dim\",\"type\":\"BOOL\"},{\"key\":\"x\",\"type\":\"BOOL\"},{\"key\":\"x\",\"type\":\"BOOL\"},{\"key\":\"fps\",\"type\":\"BOOL\"}]}";
        WallpaperSpec r = WallpaperSpec.parse(res, names("bg.png"), "x");
        check(r.options.options.size() == 1 && r.options.get("x") != null, "Visor's own keys and duplicates cannot be reused");
        // too many
        StringBuilder many = new StringBuilder("{\"type\":\"image\",\"file\":\"bg.png\",\"options\":[");
        for (int i = 0; i < 40; i++) many.append(i > 0 ? "," : "").append("{\"key\":\"k").append(i).append("\",\"type\":\"BOOL\"}");
        many.append("]}");
        check(WallpaperSpec.parse(many.toString(), names("bg.png"), "x").options.options.size() == WallpaperSpec.MAX_OPTIONS, "options are capped at " + WallpaperSpec.MAX_OPTIONS);
        // Deck's own options
        AnimSpec still = WallpaperOptions.build("Plain", null);
        String[] keys = WallpaperOptions.COMMON_KEYS;
        boolean all = still.options.size() == keys.length;
        for (String k : keys) all &= still.get(k) != null;
        check(all, "every pack gets PARALLAX STRENGTH, PAN SPEED, DIM, TINT and ZOOM");
        check(still.get("parallax").label.equals("PARALLAX STRENGTH") && still.get("parallax").min == 0 && still.get("parallax").max == 200 && still.get("parallax").i() == 100, "parallax strength 0 to 200 percent");
        check(still.get("dim").min == 0 && still.get("dim").max == 80 && still.get("tint").max == 100 && still.get("zoom").min == 100 && still.get("zoom").max == 150 && still.get("pan").label.equals("PAN SPEED"), "dim 0 to 80, tint 0 to 100, zoom 100 to 150");
        check(still.get("speed") == null && still.get("fps") == null, "a still pack has no playback options");
        WallpaperSpec an = WallpaperSpec.parse("{\"type\":\"animated\",\"file\":\"a.gif\",\"fps\":15,\"speed\":0.5,\"options\":[{\"key\":\"extra\",\"type\":\"BOOL\"}]}", names("a.gif"), "x");
        AnimSpec anim = WallpaperOptions.build("Anim", an);
        check(anim.get("speed") != null && anim.get("speed").label.equals("PLAYBACK SPEED") && anim.get("speed").i() == 50 && anim.get("fps").label.equals("FRAME RATE CAP") && anim.get("fps").i() == 15, "an animated pack gets PLAYBACK SPEED and FRAME RATE CAP, starting at the pack's values");
        check(anim.options.size() == keys.length + 3 && anim.get("extra") != null, "the pack's own options come after Visor's");
        check(WallpaperOptions.isCommon("dim") && WallpaperOptions.isCommon("fps") && !WallpaperOptions.isCommon("extra"), "isCommon tells Visor's options from the pack's");
        // save and load through a store
        final java.util.HashMap<String, String> mem = new java.util.HashMap<>();
        AnimSpec.Store st = new AnimSpec.Store() {
            @Override public String get(String k) { return mem.get(k); }
            @Override public void put(String k, String v) { mem.put(k, v); }
            @Override public void remove(String k) { mem.remove(k); }
        };
        anim.get("dim").parse("55");
        anim.save(st, anim.get("dim"));
        check(mem.containsKey("anim.wp.Anim.dim") && mem.get("anim.wp.Anim.dim").equals("55"), "options are stored under anim.wp.PACK.KEY: " + mem.keySet());
        AnimSpec again = WallpaperOptions.build("Anim", an);
        again.load(st);
        check(again.get("dim").i() == 55 && again.get("tint").i() == 0, "a new load reads them back");
        mem.put("anim.wp.Anim.dim", "banana");
        again.load(st);
        check(again.get("dim").i() == 25, "a broken stored value falls back to the default");
        again.get("dim").parse("70");
        again.resetAll(st);
        check(again.get("dim").i() == 25 && mem.isEmpty(), "reset puts defaults back and removes the stored values");
        check(WallpaperOptions.id("a/b").equals("wp.a_b") && WallpaperOptions.id("Neon Grid").equals("wp.Neon Grid"), "pack names are cleaned for the key");
        check(WallpaperOptions.summary(WallpaperOptions.build("P", null)).equals("DEFAULT OPTIONS"), "summary of defaults");
        AnimSpec sm = WallpaperOptions.build("P", null);
        sm.get("dim").parse("50");
        check(WallpaperOptions.summary(sm).contains("DIM 50"), "summary names a changed option: " + WallpaperOptions.summary(sm));
        WallpaperOptions.Look look = new WallpaperOptions.Look().set(sm);
        check(look.dimPct == 50 && look.parallaxPct == 100 && look.zoomPct == 100 && look.fps == 20 && look.speedPct == 100, "Look reads the numbers (and defaults for the missing playback options)");
        check(new WallpaperOptions.Look().set(null).dimPct == 0, "Look from nothing is safe");
    }

    static void wallpaperLayerValues() {
        section("2.7 wallpaper.json: layer values that follow options");
        String js = "{\"type\":\"layers\",\"options\":["
                + "{\"key\":\"fog\",\"type\":\"FLOAT\",\"min\":0,\"max\":1,\"step\":0.1,\"default\":0.4},"
                + "{\"key\":\"stars\",\"type\":\"BOOL\",\"default\":false},"
                + "{\"key\":\"mood\",\"type\":\"CHOICE\",\"choices\":[\"Night\",\"Day\"],\"default\":\"Night\"},"
                + "{\"key\":\"hue\",\"type\":\"COLOR\",\"default\":\"#00FF00\"}],"
                + "\"layers\":[{\"file\":\"a.png\",\"opacity\":\"@fog\"},"
                + "{\"file\":\"b.png\",\"opacity\":\"@stars\"},"
                + "{\"file\":\"c.png\",\"when\":\"mood=Day\"},"
                + "{\"file\":\"d.png\",\"when\":\"stars=on\",\"tint\":\"@hue\",\"tintMix\":0.7},"
                + "{\"file\":\"e.png\",\"opacity\":\"@mood\",\"parallax\":\"@hue\",\"when\":\"fog=1\",\"tint\":\"#FF0000\"}]}";
        WallpaperSpec s = WallpaperSpec.parse(js, names("a.png", "b.png", "c.png", "d.png", "e.png"), "x");
        check(s.error == null && s.layers.size() == 5, "parsed: " + s.error + s.notes);
        AnimSpec o = WallpaperOptions.build("T", s);
        o.load(null);
        check(WallpaperSpec.value(s.layers.get(0).opacity, o, 0, 1) == 0.4, "opacity follows a FLOAT option");
        check(WallpaperSpec.value(s.layers.get(1).opacity, o, 0, 1) == 0.0, "opacity follows a BOOL option (off is 0)");
        o.get("stars").parse("on");
        check(WallpaperSpec.value(s.layers.get(1).opacity, o, 0, 1) == 1.0, "and on is 1");
        check(s.layers.get(4).opacity.ref == null && s.layers.get(4).parallax.ref == null, "a reference to a CHOICE or COLOR option for a number is ignored");
        check(!WallpaperSpec.layerShows(s.layers.get(2), o), "when mood=Day hides the layer while the choice is Night");
        o.get("mood").parse("Day");
        check(WallpaperSpec.layerShows(s.layers.get(2), o), "and shows it when the choice is Day");
        check(WallpaperSpec.layerShows(s.layers.get(3), o), "when stars=on shows the layer once stars is on");
        o.get("stars").parse("off");
        check(!WallpaperSpec.layerShows(s.layers.get(3), o), "and hides it again");
        check(s.layers.get(4).whenKey == null, "when on a FLOAT option is ignored");
        check(WallpaperSpec.layerShows(s.layers.get(0), o), "a layer without when always shows");
        check(WallpaperSpec.layerTint(s.layers.get(3), o) == 0xFF00FF00, "a tint can follow a COLOR option");
        o.get("hue").parse("#112233");
        check(WallpaperSpec.layerTint(s.layers.get(3), o) == 0xFF112233 && WallpaperSpec.layerTint(s.layers.get(4), o) == 0xFFFF0000, "it follows changes, a fixed tint stays fixed");
        check(WallpaperSpec.value(new WallpaperSpec.Num(Double.NaN, null), o, 0, 1) == 0 && WallpaperSpec.value(new WallpaperSpec.Num(9, null), null, 0, 1) == 1, "NaN and out of range numbers are clamped");
        check(WallpaperSpec.layerShows(s.layers.get(2), null), "no options means show");
    }

    // ================================================================== folders and zips

    static void write(File f, int bytes) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(new byte[bytes]); }
    }

    static void writeText(File f, String t) throws IOException {
        Files.write(f.toPath(), t.getBytes(StandardCharsets.UTF_8));
    }

    static void wallpapersFolder() {
        section("2.7 wallpaper pack folders");
        check(Wallpapers.normalize(null).equals("skyline") && Wallpapers.normalize("").equals("skyline") && Wallpapers.normalize("off").equals("off")
                && Wallpapers.normalize("pack:Neon").equals("pack:Neon") && Wallpapers.normalize("pack:").equals("skyline") && Wallpapers.normalize("pack:../x").equals("skyline")
                && Wallpapers.normalize("garbage").equals("skyline") && Wallpapers.normalize("pack:a/b").equals("skyline"), "a saved choice reads as skyline, off or pack:NAME, anything else is the skyline");
        check(Wallpapers.isPack("pack:A") && !Wallpapers.isPack("off") && !Wallpapers.isPack("pack:") && Wallpapers.packName("pack:A b").equals("A b") && Wallpapers.packName("off").isEmpty(), "pack choice helpers");
        String[] packs = {"Alpha", "beta"};
        String[] ch = Wallpapers.choices(packs);
        check(ch.length == 4 && ch[0].equals("BUILT-IN SKYLINE") && ch[1].equals("OFF") && ch[2].equals("ALPHA") && ch[3].equals("BETA"), "the chooser lists the skyline, OFF, then each pack");
        check(Wallpapers.choiceIndex("skyline", packs) == 0 && Wallpapers.choiceIndex("off", packs) == 1 && Wallpapers.choiceIndex("pack:beta", packs) == 3 && Wallpapers.choiceIndex("pack:gone", packs) == 0, "choice index, a pack that is gone reads as the skyline");
        check(Wallpapers.selFor(0, packs).equals("skyline") && Wallpapers.selFor(1, packs).equals("off") && Wallpapers.selFor(2, packs).equals("pack:Alpha") && Wallpapers.selFor(9, packs).equals("skyline") && Wallpapers.selFor(-1, packs).equals("skyline"), "choice index back to the saved choice");
        try {
            File base = LogicTests.tempDir();
            File dir = new File(base, "wallpapers");
            dir.mkdirs();
            check(Wallpapers.list(dir).length == 0 && Wallpapers.list(null).length == 0 && Wallpapers.list(new File(base, "nope")).length == 0, "no packs");
            new File(dir, "Zeta").mkdirs();
            new File(dir, "alpha").mkdirs();
            new File(dir, ".unpack-x").mkdirs();
            new File(dir, ".hidden").mkdirs();
            new File(dir, "bad;name").mkdirs();
            write(new File(dir, "loose.png"), 5);
            String[] l = Wallpapers.list(dir);
            check(l.length == 2 && l[0].equals("alpha") && l[1].equals("Zeta"), "folders sorted, hidden, temp and oddly named ones left out: " + Arrays.toString(l));
            check(Wallpapers.packDir(dir, "alpha") != null && Wallpapers.packDir(dir, "..") == null && Wallpapers.packDir(dir, "../wallpapers") == null && Wallpapers.packDir(dir, ".hidden") == null
                    && Wallpapers.packDir(dir, "") == null && Wallpapers.packDir(dir, null) == null && Wallpapers.packDir(dir, "gone") == null, "packDir refuses traversal, hidden, empty and missing");
            // a good pack
            File p = new File(dir, "Neon");
            p.mkdirs();
            writeText(new File(p, "wallpaper.json"), "{\"type\":\"layers\",\"name\":\"Neon\",\"layers\":[{\"file\":\"a.png\"},{\"file\":\"B.PNG\",\"parallax\":1}]}");
            write(new File(p, "a.png"), 100);
            write(new File(p, "B.PNG"), 100);
            write(new File(p, ".deck-source"), 20);
            write(new File(p, "notes.txt"), 5000000);
            WallpaperSpec s = Wallpapers.read(p);
            check(s.error == null && s.layers.size() == 2, "a folder pack reads: " + s.error);
            check(Wallpapers.file(p, "b.png") != null && Wallpapers.file(p, "b.png").getName().equals("B.PNG") && Wallpapers.file(p, "nope.png") == null && Wallpapers.file(p, null) == null, "file lookup ignores case");
            check(Wallpapers.packFiles(p).size() == 3, "pack files are the json and the images, no dot files or other types");
            // no json, one image
            File q = new File(dir, "Solo");
            q.mkdirs();
            write(new File(q, "pic.jpg"), 10);
            check(Wallpapers.read(q).error == null && Wallpapers.read(q).type == WallpaperSpec.IMAGE, "no json and one image works as an image");
            write(new File(q, "pic2.jpg"), 10);
            check(Wallpapers.read(q).error != null, "no json and two images is an error");
            // caps for a folder pushed by hand
            File c = new File(dir, "Caps");
            c.mkdirs();
            writeText(new File(c, "wallpaper.json"), "{\"type\":\"image\",\"file\":\"a.png\"}");
            write(new File(c, "a.png"), 8 * 1024 * 1024 + 1);
            WallpaperSpec cs = Wallpapers.read(c);
            check(cs.error != null && cs.error.contains("8 MB"), "an image over 8 MB is refused: " + cs.error);
            write(new File(c, "a.png"), 0);
            check(Wallpapers.read(c).error != null, "an empty image is refused");
            for (int i = 0; i < 4; i++) write(new File(c, "big" + i + ".png"), 7 * 1024 * 1024);
            write(new File(c, "a.png"), 10);
            cs = Wallpapers.read(c);
            check(cs.error != null && cs.error.contains("24 MB"), "a pack over 24 MB in total is refused: " + cs.error);
            File m = new File(dir, "Many");
            m.mkdirs();
            writeText(new File(m, "wallpaper.json"), "{\"type\":\"image\",\"file\":\"i0.png\"}");
            for (int i = 0; i < 16; i++) write(new File(m, "i" + i + ".png"), 5);
            cs = Wallpapers.read(m);
            check(cs.error != null && cs.error.contains("16"), "17 files (16 images and the json) is over the cap: " + cs.error);
            File j = new File(dir, "BigJson");
            j.mkdirs();
            writeText(new File(j, "wallpaper.json"), "{\"type\":\"image\",\"file\":\"a.png\",\"pad\":\"" + "x".repeat(70000) + "\"}");
            write(new File(j, "a.png"), 5);
            check(Wallpapers.read(j).error != null, "a json over 64 KB in a folder is refused");
            check(Wallpapers.read(null).error != null && Wallpapers.read(new File(base, "nope")).error != null, "a missing folder is an error, not a crash");
            // symlink to outside is not followed for the file set (a link is not a regular file in the listing sense here)
        } catch (IOException e) {
            check(false, "temp files failed: " + e);
        }
    }

    static void wallpaperZip() {
        section("2.7 wallpaper pack zips (PackZip WALLPAPERS rules)");
        PackZip.Rules r = PackZip.WALLPAPERS;
        check(r.maxFile == 8L * 1024 * 1024 && r.maxTotal == 24L * 1024 * 1024 && r.maxFiles == 16 && r.jsonName.equals("wallpaper.json"), "caps: 8 MB per image, 24 MB per pack, 16 files");
        check(PackZip.classify("Neon/wallpaper.json", false, r) == PackZip.KEEP && PackZip.classify("a.PNG", false, r) == PackZip.KEEP && PackZip.classify("a.jpg", false, r) == PackZip.KEEP
                && PackZip.classify("a.jpeg", false, r) == PackZip.KEEP && PackZip.classify("a.webp", false, r) == PackZip.KEEP && PackZip.classify("a.gif", false, r) == PackZip.KEEP, "json and the five image types are kept");
        check(PackZip.classify("pack.json", false, r) == PackZip.SKIP && PackZip.classify("run.sh", false, r) == PackZip.SKIP && PackZip.classify("a.wav", false, r) == PackZip.SKIP
                && PackZip.classify("a.svg", false, r) == PackZip.SKIP && PackZip.classify("a.apk", false, r) == PackZip.SKIP && PackZip.classify(".gif", false, r) == PackZip.SKIP, "other files are skipped, and pack.json is not wallpaper.json");
        check(PackZip.classify("../a.png", false, r) == PackZip.UNSAFE && PackZip.classify("/x/a.png", false, r) == PackZip.UNSAFE && PackZip.classify("..\\a.png", false, r) == PackZip.UNSAFE, "unsafe paths are rejected");
        check(PackZip.classify("pack.json", false, PackZip.SOUND) == PackZip.KEEP && PackZip.classify("wallpaper.json", false, PackZip.SOUND) == PackZip.SKIP, "sound packs still keep pack.json and not wallpaper.json");
        try {
            File base = LogicTests.tempDir();
            File packs = new File(base, "wallpapers");
            packs.mkdirs();
            byte[] json = "{\"type\":\"image\",\"file\":\"bg.png\"}".getBytes(StandardCharsets.UTF_8);
            File z1 = LogicTests.zip(packs, "Neon.zip", "wallpaper.json", json, "bg.png", new byte[300], "junk.txt", new byte[5]);
            PackZip.Result res = PackZip.extract(z1, packs, r);
            check(res.ok && res.name.equals("Neon") && res.files == 2 && new File(packs, "Neon/bg.png").length() == 300 && !new File(packs, "Neon/junk.txt").exists(), "root layout installs as the zip name: " + res);
            check(PackZip.installed(z1, packs) && PackZip.scan(packs, r).isEmpty() && Wallpapers.installZips(packs).isEmpty(), "an installed zip is not unpacked twice (the .deck-source marker)");
            check(Wallpapers.read(new File(packs, "Neon")).error == null, "what was installed reads as a pack");
            File z2 = LogicTests.zip(packs, "dl (1).zip", "Pink/", null, "Pink/wallpaper.json", json, "Pink/bg.png", new byte[10], "__MACOSX/Pink/._bg.png", new byte[4], "Pink/.DS_Store", new byte[4]);
            res = PackZip.extract(z2, packs, r);
            check(res.ok && res.name.equals("Pink") && res.files == 2, "one top folder installs as the folder name: " + res);
            res = PackZip.extract(LogicTests.zip(packs, "evil.zip", "bg.png", new byte[10], "../evil.png", new byte[10]), packs, r);
            check(!res.ok && res.error.contains("UNSAFE") && !new File(base, "evil.png").exists() && !new File(packs, "evil").exists(), "zip slip rejects the whole zip: " + res);
            res = PackZip.extract(LogicTests.zip(packs, "abs.zip", "/tmp/x.png", new byte[10]), packs, r);
            check(!res.ok && res.error.contains("UNSAFE"), "an absolute entry rejects the zip");
            res = PackZip.extract(LogicTests.zip(packs, "win.zip", "..\\evil2.png", new byte[10]), packs, r);
            check(!res.ok && !new File(base, "evil2.png").exists(), "a backslash zip slip rejects the zip");
            res = PackZip.extract(LogicTests.zip(packs, "junk.zip", "run.sh", new byte[10], "wallpaper.json", json), packs, r);
            check(!res.ok && res.error.contains("NO WALLPAPER FILES") && !new File(packs, "junk").exists(), "a json without images is refused: " + res);
            res = PackZip.extract(LogicTests.zip(packs, "two.zip", "A/bg.png", new byte[10], "B/bg.png", new byte[10]), packs, r);
            check(!res.ok && res.error.contains("ROOT OR IN ONE FOLDER"), "two top folders is refused (one pack per zip): " + res);
            Object[] many = new Object[(r.maxFiles + 1) * 2];
            for (int i = 0; i <= r.maxFiles; i++) { many[i * 2] = "f" + i + ".png"; many[i * 2 + 1] = new byte[1]; }
            res = PackZip.extract(LogicTests.zip(packs, "many.zip", many), packs, r);
            check(!res.ok && res.error.contains("16 FILES"), "17 files is refused: " + res);
            res = PackZip.extract(LogicTests.zip(packs, "big.zip", "bg.png", (int) r.maxFile + 1), packs, r);
            check(!res.ok && res.error.contains("8 MB") && !new File(packs, "big").exists(), "an image over 8 MB is refused (counted while copying): " + res);
            Object[] bomb = new Object[10];
            for (int i = 0; i < 5; i++) { bomb[i * 2] = "b" + i + ".png"; bomb[i * 2 + 1] = 7 * 1024 * 1024; }
            res = PackZip.extract(LogicTests.zip(packs, "bomb.zip", bomb), packs, r);
            check(!res.ok && res.error.contains("24 MB") && !new File(packs, "bomb").exists(), "35 MB of images (a tiny zip of zeros) is over the 24 MB total: " + res);
            check(!new File(packs, ".unpack-big").exists() && !new File(packs, ".unpack-bomb").exists() && !new File(packs, ".unpack-evil").exists(), "failed installs leave no temp folder");
            res = PackZip.extract(new File(packs, "gone.zip"), packs, r);
            check(!res.ok && res.error.contains("NOT FOUND"), "a missing zip");
            File garbage = new File(packs, "garbage.zip");
            LogicTests_write(garbage, "this is not a zip");
            res = PackZip.extract(garbage, packs, r);
            check(!res.ok && res.error.startsWith("BAD ZIP"), "a file that is not a zip: " + res);
            // exactly at the caps
            Object[] edge = new Object[6];
            edge[0] = "wallpaper.json"; edge[1] = "{\"type\":\"image\",\"file\":\"a.png\"}".getBytes(StandardCharsets.UTF_8);
            edge[2] = "a.png"; edge[3] = (int) r.maxFile;
            edge[4] = "b.png"; edge[5] = (int) r.maxFile;
            res = PackZip.extract(LogicTests.zip(packs, "edge.zip", edge), packs, r);
            check(res.ok, "two images of exactly 8 MB (16 MB) install: " + res);
            // scan installs several zips, one failing
            File dir2 = new File(base, "w2");
            dir2.mkdirs();
            LogicTests.zip(dir2, "Good.zip", "wallpaper.json", json, "bg.png", new byte[10]);
            LogicTests.zip(dir2, "Bad.zip", "x.txt", new byte[10]);
            ArrayList<PackZip.Result> all = Wallpapers.installZips(dir2);
            int ok = 0, bad = 0;
            for (PackZip.Result x : all) { if (x.ok) ok++; else bad++; }
            check(ok == 1 && bad == 1 && Wallpapers.list(dir2).length == 1, "a scan installs the good zip and reports the bad one");
        } catch (IOException e) {
            check(false, "temp files failed: " + e);
        }
    }

    static void LogicTests_write(File f, String t) throws IOException { writeText(f, t); }

    // ================================================================== layer math

    static void parallax() {
        section("2.7 parallax: layer math and clamping");
        check(ParallaxMath.shift(0, 100) == 0 && Math.abs(ParallaxMath.shift(1, 100) - 1) < 1e-3 && Math.abs(ParallaxMath.shift(-5, 100) + 5) < 0.01, "near zero the shift follows the camera one to one");
        boolean bounded = true;
        for (double v = -1e7; v <= 1e7; v += 12345.678) bounded &= Math.abs(ParallaxMath.shift(v, 80)) <= 80 + 1e-9;
        check(bounded, "the shift never passes the limit, however far the camera goes");
        check(ParallaxMath.shift(5, 0) == 0 && ParallaxMath.shift(5, -3) == 0 && ParallaxMath.shift(Double.NaN, 10) == 0 && ParallaxMath.shift(Double.POSITIVE_INFINITY, 10) == 0, "no limit, a bad limit, NaN and infinity give no shift");
        // the layer fills the view and the edges never show, for all kinds of images and cameras
        ParallaxMath.Out o = new ParallaxMath.Out();
        int[][] views = {{1920, 1080}, {1280, 720}, {3840, 2160}, {1080, 1920}, {800, 800}, {641, 359}};
        int[][] imgs = {{1920, 1080}, {1280, 720}, {4000, 1000}, {1000, 4000}, {1, 1}, {7, 3}, {5000, 5000}, {1921, 1080}, {640, 360}};
        Random rnd = new Random(11);
        boolean covered = true;
        String where = "";
        int cases = 0;
        for (int[] v : views) for (int[] im : imgs) {
            for (int k = 0; k < 400; k++) {
                double par = rnd.nextInt(5) == 0 ? rnd.nextDouble() * 4 : rnd.nextDouble() * 2;
                double us = 1 + (rnd.nextInt(3) == 0 ? rnd.nextDouble() * 2 : 0);
                double pm = rnd.nextDouble() * 2;
                double cx = (rnd.nextDouble() - 0.5) * (rnd.nextInt(4) == 0 ? 1e8 : 4000), cy = (rnd.nextDouble() - 0.5) * (rnd.nextInt(4) == 0 ? 1e7 : 400);
                ParallaxMath.place(o, v[0], v[1], im[0], im[1], us, par, pm, cx, cy);
                cases++;
                if (!ParallaxMath.covers(o, v[0], v[1])) { covered = false; where = " view " + v[0] + "x" + v[1] + " img " + im[0] + "x" + im[1] + " par " + par + " cam " + cx + "," + cy; }
            }
        }
        check(covered, "the picture covers the whole view in " + cases + " random cases (every view and image shape, parallax 0 to 4, cameras to 1e8 px):" + where);
        // parallax 0: centered and still
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 0, 1, 5000, 5000);
        check(o.left == 0 && o.top == 0 && o.scale == 1.0, "parallax 0 does not move and does not enlarge a picture that already fits");
        ParallaxMath.place(o, 1920, 1080, 1280, 720, 1, 0, 1, 0, 0);
        check(Math.abs(o.scale - 1.5) < 1e-9 && o.left == 0 && o.top == 0, "a smaller picture is scaled up to cover (cover = 1.5)");
        // scale grows so that there is room to move
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 1.0, 1, 0, 0);
        check(o.scale > 1.09 && o.scale < 1.11, "parallax 1 enlarges to about 1.10 so that 5 percent of travel fits each side: " + o.scale);
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 4.0, 1, 0, 0);
        check(o.scale <= 2.0 + 1e-9, "the automatic enlargement stops at 2x");
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1.3, 0.1, 1, 0, 0);
        check(Math.abs(o.scale - 1.3) < 1e-9, "the pack's own scale is a minimum and it wins when it is bigger");
        // moves with the camera, within the overscan
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 1.0, 1, 30, 0);
        double ov = (o.w - 1920) / 2;
        check(o.left > -ov && o.left < -ov + 60 && Math.abs((o.left - (-ov)) - 30) < 1, "30 px of camera moves the picture about 30 px: " + (o.left + ov));
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 0.5, 1, 40, 0);
        check(Math.abs((o.left + (o.w - 1920) / 2) - 20) < 1.0, "parallax 0.5 moves it about half as far (a little less near the end of its travel)");
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1, 1.0, 0, 500, 500);
        check(Math.abs(o.left + (o.w - 1920) / 2) < 1e-9, "PAN SPEED 0 holds the picture still");
        // far layer moves less than near layer
        ParallaxMath.Out a = new ParallaxMath.Out(), b = new ParallaxMath.Out();
        ParallaxMath.place(a, 1920, 1080, 2400, 1200, 1, 0.2, 1, 100, 0);
        ParallaxMath.place(b, 1920, 1080, 2400, 1200, 1, 1.2, 1, 100, 0);
        check(Math.abs(b.left - (1920 - b.w) / 2) > Math.abs(a.left - (1920 - a.w) / 2), "a near layer (high parallax) moves further than a far one");
        // degenerate input
        ParallaxMath.place(o, 0, 0, 0, 0, 1, 1, 1, 0, 0);
        check(!o.valid, "zero sizes give nothing to draw");
        ParallaxMath.place(o, 1920, 1080, 100, 100, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        check(o.valid && ParallaxMath.covers(o, 1920, 1080) && !Double.isNaN(o.left) && !Double.isNaN(o.scale), "NaN everywhere still gives a covering placement");
        ParallaxMath.place(o, 1920, 1080, -5, 100, 1, 1, 1, 0, 0);
        check(!o.valid, "a negative image size gives nothing");
        // zoom: more room
        ParallaxMath.place(o, 1920, 1080, 1920, 1080, 1.5, 1.0, 1, 1e6, 0);
        check(ParallaxMath.covers(o, 1920, 1080) && o.scale >= 1.5, "ZOOM 150 percent still covers at a huge pan");
    }

    static void overlay() {
        section("2.7 dim and tint in one draw");
        check(ParallaxMath.overlay(0, 0, 0xFFFF00FF) == 0, "no dim and no tint is no overlay");
        int d = ParallaxMath.overlay(50, 0, 0xFFFF00FF);
        check((d >>> 24) == 128 && (d & 0xFFFFFF) == 0, "dim 50 is black at half alpha: " + Integer.toHexString(d));
        int t = ParallaxMath.overlay(0, 100, 0xFFFF2E97);
        check(Math.abs((t >>> 24) - 153) <= 1 && ((t >> 16) & 255) == 255, "tint 100 mixes in 60 percent of the accent: " + Integer.toHexString(t));
        // equals tint first then dim, to within one level, over many pixels
        Random rnd = new Random(5);
        boolean close = true;
        int worst = 0;
        for (int i = 0; i < 20000; i++) {
            int dim = rnd.nextInt(81), tint = rnd.nextInt(101), accent = rnd.nextInt(0x1000000) | 0xFF000000;
            int pix = rnd.nextInt(0x1000000);
            int ov = ParallaxMath.overlay(dim, tint, accent);
            double a = (ov >>> 24) / 255.0;
            double dd = dim / 100.0, tt = tint / 100.0 * ParallaxMath.TINT_MAX;
            for (int sh = 0; sh <= 16; sh += 8) {
                double p = (pix >> sh) & 255, ac = (accent >> sh) & 255, oc = (ov >> sh) & 255;
                double once = p * (1 - a) + oc * a;
                double seq = (p * (1 - tt) + ac * tt) * (1 - dd);
                int diff = (int) Math.round(Math.abs(once - seq));
                worst = Math.max(worst, diff);
                close &= diff <= 2;
            }
        }
        check(close, "one overlay equals tint then dim to within 2 levels of 255 (worst " + worst + ")");
        check(ParallaxMath.overlay(999, -5, 0xFFFFFFFF) == ParallaxMath.overlay(80, 0, 0xFFFFFFFF), "out of range numbers are clamped");
    }

    static void wallpaperMem() {
        section("2.7 wallpaper memory plan");
        check(WallpaperMem.targetWidth(1920) == 2208 && WallpaperMem.targetWidth(1280) == 1472, "target width is the screen width times 1.15");
        check(WallpaperMem.sample(1920, 1080, 2208) == 1 && WallpaperMem.sample(4416, 2484, 2208) == 2 && WallpaperMem.sample(8832, 4968, 2208) == 4 && WallpaperMem.sample(100, 100, 2208) == 1, "sample size: the largest power of two that keeps the width at or above the target");
        check(WallpaperMem.sample(1000000, 1000000, 100) == WallpaperMem.MAX_SAMPLE && WallpaperMem.sample(0, 0, 100) == 1 && WallpaperMem.sample(100, 100, 0) == 1, "sample is capped and bad numbers give 1");
        check(WallpaperMem.sampled(1001, 2) == 501 && WallpaperMem.sampled(1000, 4) == 250, "decoded size rounds up like BitmapFactory");
        check(WallpaperMem.bytes(1000, 1000, true) == 4000000 && WallpaperMem.bytes(1000, 1000, false) == 2000000 && WallpaperMem.bytes(-1, 5, true) == 0, "ARGB_8888 is 4 bytes a pixel, RGB_565 is 2");
        // plan
        int[] sp = WallpaperMem.plan(new int[]{1920, 1920, 1920}, new int[]{1080, 1080, 1080}, new boolean[]{true, true, true}, 1920, WallpaperMem.STATIC_CAP);
        check(sp != null && sp[0] == 1 && WallpaperMem.total(new int[]{1920, 1920, 1920}, new int[]{1080, 1080, 1080}, new boolean[]{true, true, true}, sp) <= WallpaperMem.STATIC_CAP, "three full HD layers (25 MB) fit as they are");
        int[] w = {3840, 3840, 3840, 3840}, h = {2160, 2160, 2160, 2160};
        boolean[] al = {true, true, true, true};
        sp = WallpaperMem.plan(w, h, al, 1920, WallpaperMem.STATIC_CAP);
        check(sp != null && sp[0] >= 2 && WallpaperMem.total(w, h, al, sp) <= WallpaperMem.STATIC_CAP, "four 4K layers are decoded smaller until they fit: sample " + (sp == null ? -1 : sp[0]));
        long cap = WallpaperMem.STATIC_CAP;
        check(WallpaperMem.plan(new int[]{40000}, new int[]{40000}, new boolean[]{true}, 1920, cap) == null, "a 40000 x 40000 image is absurd and refused");
        check(WallpaperMem.plan(new int[]{8000}, new int[]{8000}, new boolean[]{true}, 1920, 1000) == null, "a cap that cannot be met even at the largest sample size refuses");
        check(WallpaperMem.plan(new int[0], new int[0], new boolean[0], 1920, cap) == null && WallpaperMem.plan(new int[]{1}, new int[]{1, 2}, new boolean[]{true}, 1920, cap) == null, "empty or mismatched input is refused");
        check(WallpaperMem.plan(new int[]{3840}, new int[]{2160}, new boolean[]{false}, 1920, cap)[0] <= WallpaperMem.plan(new int[]{3840}, new int[]{2160}, new boolean[]{true}, 1920, cap)[0], "a JPEG (2 bytes a pixel) never needs a bigger sample than a PNG");
        check(WallpaperMem.absurd(0, 5) && WallpaperMem.absurd(5, -1) && WallpaperMem.absurd(10000, 10000) && !WallpaperMem.absurd(8000, 8000), "absurd sizes: non positive, or over 64 million pixels");
        // animated
        int[] t = new int[2];
        check(WallpaperMem.animTarget(1920, 1080, WallpaperMem.ANIM_CAP, t) && t[0] == 1280 && t[1] == 720, "1920x1080 animated decodes at 1280x720");
        check(WallpaperMem.animTarget(640, 360, WallpaperMem.ANIM_CAP, t) && t[0] == 640 && t[1] == 360, "a small animation is not scaled up");
        check(WallpaperMem.animTarget(4000, 4000, WallpaperMem.ANIM_CAP, t) && t[0] == 720 && t[1] == 720, "a square one fits the height: 720x720");
        check(WallpaperMem.animTarget(1000, 3000, WallpaperMem.ANIM_CAP, t) && t[1] == 720 && t[0] == 240, "a tall one: 240x720");
        boolean within = true;
        Random rnd = new Random(3);
        for (int i = 0; i < 5000; i++) {
            int iw = 1 + rnd.nextInt(6000), ih = 1 + rnd.nextInt(6000);
            if (WallpaperMem.animTarget(iw, ih, WallpaperMem.ANIM_CAP, t)) {
                within &= t[0] <= 1280 && t[1] <= 720 && t[0] <= iw && t[1] <= ih && WallpaperMem.animBytes(t[0], t[1]) <= WallpaperMem.ANIM_CAP && t[0] >= 1 && t[1] >= 1;
            }
        }
        check(within, "5000 random sizes: never above 1280x720, never above the file, always inside the 24 MB estimate");
        check(WallpaperMem.animBytes(1280, 720) == 1280L * 720 * 4 * 3 && WallpaperMem.animBytes(1280, 720) <= WallpaperMem.ANIM_CAP, "the full 1280x720 estimate (11 MB) is under the cap");
        check(!WallpaperMem.animTarget(1280, 720, 100000, t), "a cap that is too small refuses even at 160 px wide");
        check(WallpaperMem.animTarget(1280, 720, 4L * 1000 * 1000, t) && t[0] < 1280 && WallpaperMem.animBytes(t[0], t[1]) <= 4000000, "a smaller cap shrinks the decode in steps");
        check(!WallpaperMem.animTarget(0, 10, WallpaperMem.ANIM_CAP, t) && !WallpaperMem.animTarget(20000, 20000, WallpaperMem.ANIM_CAP, t), "zero and absurd sizes are refused");
        check(WallpaperMem.frameTarget(1920, 1080, 1920, t) && t[0] == 1920 && !WallpaperMem.frameTarget(0, 0, 1920, t), "the first frame of an animation is sized like a still image");
    }

    static void animPlay() {
        section("2.7 animated wallpaper timing");
        check(AnimPlay.interval(20) == 50 && AnimPlay.interval(30) == 33 && AnimPlay.interval(5) == 200 && AnimPlay.interval(500) == 33 && AnimPlay.interval(0) == 200 && AnimPlay.interval(-3) == 200, "frame interval from the cap, clamped to 5..30");
        check(AnimPlay.wait(1000, 950, 20) == 0 && AnimPlay.wait(1000, 960, 20) == 10 && AnimPlay.wait(1000, 1000, 20) == 50 && AnimPlay.wait(1000, 2000, 20) == 0, "a redraw is due once the interval has passed, else the rest of it");
        // a stream of requests at 120 Hz is thinned to the cap
        int drawn = 0;
        long last = -100000;
        for (long now = 0; now < 10000; now += 8) {
            if (AnimPlay.wait(now, last, 20) == 0) { drawn++; last = now; }
        }
        check(drawn >= 150 && drawn <= 200, "120 requests a second are thinned to at most 20 a second: " + drawn + " in 10 s");
        check(AnimPlay.full(100) && AnimPlay.full(200) && !AnimPlay.full(75) && !AnimPlay.full(25), "only 100 percent plays without the duty cycle");
        check(AnimPlay.runMs(100) + AnimPlay.holdMs(100) == AnimPlay.CYCLE_MS && AnimPlay.holdMs(100) == 0, "full speed never holds");
        check(AnimPlay.runMs(50) == 150 && AnimPlay.holdMs(50) == 150 && AnimPlay.runMs(25) == 75 && AnimPlay.holdMs(25) == 225, "50 percent runs half of each cycle, 25 percent a quarter");
        boolean sums = true;
        for (int p = -50; p <= 300; p += 5) sums &= AnimPlay.runMs(p) + AnimPlay.holdMs(p) == AnimPlay.CYCLE_MS && AnimPlay.runMs(p) >= 1 && AnimPlay.holdMs(p) >= 0;
        check(sums, "run plus hold is always one cycle, whatever the setting");
        check(Math.abs(AnimPlay.share(50) - 0.5) < 1e-9 && AnimPlay.share(1) == AnimPlay.share(25) && AnimPlay.share(400) == 1.0, "the share of time running is the speed, clamped to 25..100 percent");
        check(AnimPlay.wait(10, 1000, 20) == 0, "a clock that went backwards counts as due");
    }

    // ================================================================== screensaver

    static void burnIn() {
        section("2.7 burn-in drift path");
        double[] f = new double[2], g = new double[2];
        boolean inRange = true;
        for (long k = -2000; k < 200000; k += 7) { BurnIn.slot(k, f); inRange &= f[0] >= 0 && f[0] <= 1 && f[1] >= 0 && f[1] <= 1; }
        check(inRange, "every slot is inside the free range");
        // consecutive slots differ enough for burn-in protection
        double minStep = 9;
        for (long k = 0; k < 20000; k++) {
            BurnIn.slot(k, f);
            BurnIn.slot(k + 1, g);
            minStep = Math.min(minStep, Math.hypot(g[0] - f[0], g[1] - f[1]));
        }
        check(minStep > 0.14, "the block always moves by more than 14 percent of the range between two minutes (smallest " + String.format(Locale.ROOT, "%.3f", minStep) + ")");
        // it covers the screen: all nine cells of a 3x3 grid within 60 minutes
        boolean[] cell = new boolean[9];
        for (long k = 0; k < 120; k++) { BurnIn.slot(k, f); cell[Math.min(2, (int) (f[1] * 3)) * 3 + Math.min(2, (int) (f[0] * 3))] = true; }
        int seen = 0;
        for (boolean c : cell) if (c) seen++;
        check(seen == 9, "in two hours the block has visited every ninth of the screen (" + seen + " of 9)");
        // the path does not close within a day
        BurnIn.slot(0, f);
        boolean repeat = false;
        for (long k = 1; k < 1440; k++) { BurnIn.slot(k, g); if (Math.abs(g[0] - f[0]) < 1e-9 && Math.abs(g[1] - f[1]) < 1e-9) repeat = true; }
        check(!repeat, "the path does not return to its start within a day");
        // jump mode: constant inside a minute, new place at the minute
        BurnIn.at(100, 0, false, f);
        BurnIn.at(100, 59999, false, g);
        check(f[0] == g[0] && f[1] == g[1], "without MOTION the block stays put for the whole minute");
        BurnIn.at(100, 60000, false, g);
        BurnIn.slot(101, f);
        check(g[0] == f[0] && g[1] == f[1], "and jumps to the next slot at 60 s");
        // glide mode
        BurnIn.at(100, 60000, true, g);
        BurnIn.slot(100, f);
        check(Math.abs(g[0] - f[0]) < 1e-9 && Math.abs(g[1] - f[1]) < 1e-9, "with MOTION the glide starts where the block was");
        BurnIn.at(100, 60000 + BurnIn.GLIDE_MS, true, g);
        BurnIn.slot(101, f);
        check(Math.abs(g[0] - f[0]) < 1e-9 && Math.abs(g[1] - f[1]) < 1e-9, "and ends at the next slot after 10 s");
        // continuity: no jump larger than a small step between neighbouring milliseconds
        boolean smooth = true;
        double prevX, prevY;
        BurnIn.at(5, 0, true, f);
        prevX = f[0]; prevY = f[1];
        for (long ms = 100; ms < 5 * 60000; ms += 100) {
            BurnIn.at(5, ms, true, f);
            smooth &= Math.hypot(f[0] - prevX, f[1] - prevY) < 0.02;
            prevX = f[0]; prevY = f[1];
        }
        check(smooth, "the glide has no jump bigger than 2 percent of the range per 100 ms for 5 minutes");
        double maxStep = 0;
        for (long k = 0; k < 20000; k++) { BurnIn.slot(k, f); BurnIn.slot(k + 1, g); maxStep = Math.max(maxStep, Math.hypot(g[0] - f[0], g[1] - f[1])); }
        check(maxStep < 0.6, "and no slot is further than 60 percent of the range from the last (largest " + String.format(Locale.ROOT, "%.3f", maxStep) + ")");
        BurnIn.at(5, 0, true, f);
        BurnIn.slot(5, g);
        check(f[0] == g[0] && f[1] == g[1], "the first minute is the start slot, with no glide from nowhere");
        BurnIn.at(5, -1000, true, f);
        check(f[0] == g[0] && f[1] == g[1], "a negative time counts as the start");
        check(BurnIn.startSlot(0) == 0 && BurnIn.startSlot(60000) == 1 && BurnIn.startSlot(119999) == 1 && BurnIn.startSlot(-1) == -1, "the start slot follows the wall clock minute");
        // smoothstep
        check(BurnIn.smooth(0) == 0 && BurnIn.smooth(1) == 1 && BurnIn.smooth(0.5) == 0.5 && BurnIn.smooth(-3) == 0 && BurnIn.smooth(9) == 1, "ease in and out");
        // place
        int[] p = new int[2];
        BurnIn.place(0, 0, 1920, 1080, 600, 400, 72, p);
        check(p[0] == 72 && p[1] == 72, "share 0 sits at the margin");
        BurnIn.place(1, 1, 1920, 1080, 600, 400, 72, p);
        check(p[0] == 1920 - 600 - 72 && p[1] == 1080 - 400 - 72, "share 1 ends at the margin on the far side");
        BurnIn.place(0.5, 0.5, 1920, 1080, 600, 400, 72, p);
        check(p[0] == 660 && p[1] == 340, "the middle is the middle");
        boolean inside = true;
        Random rnd = new Random(2);
        for (int i = 0; i < 20000; i++) {
            int vw = 200 + rnd.nextInt(3000), vh = 200 + rnd.nextInt(2000), bw = 1 + rnd.nextInt(vw + 500), bh = 1 + rnd.nextInt(vh + 500), mg = rnd.nextInt(150);
            BurnIn.at(rnd.nextInt(100000), (long) rnd.nextInt(1000000), rnd.nextBoolean(), f);
            BurnIn.place(f[0], f[1], vw, vh, bw, bh, mg, p);
            if (bw + 2 * mg <= vw) inside &= p[0] >= mg && p[0] + bw <= vw - mg;
            if (bh + 2 * mg <= vh) inside &= p[1] >= mg && p[1] + bh <= vh - mg;
            inside &= p[0] >= 0 && p[1] >= 0;
        }
        check(inside, "20000 random cases: a block that fits always stays inside the margins, and never goes negative");
        BurnIn.place(Double.NaN, Double.NaN, 1920, 1080, 600, 400, 72, p);
        check(p[0] == 660 && p[1] == 340, "NaN means the middle");
        BurnIn.place(0.5, 0.5, 500, 300, 900, 700, 72, p);
        check(p[0] == 0 && p[1] == 0, "a block bigger than the view sits at the corner, not at a negative place");
    }

    static void dreamRules() {
        section("2.7 screensaver rules");
        check(DreamRules.STYLE_NAMES.length == 3 && DreamRules.STYLE_NAMES[0].equals("CLOCK") && DreamRules.STYLE_NAMES[1].equals("CLOCK AND WALLPAPER") && DreamRules.STYLE_NAMES[2].equals("WALLPAPER ONLY"), "the three styles");
        check(DreamRules.hasClock(DreamRules.CLOCK) && DreamRules.hasClock(DreamRules.CLOCK_AND_WALLPAPER) && !DreamRules.hasClock(DreamRules.WALLPAPER_ONLY), "CLOCK and CLOCK AND WALLPAPER show the clock");
        check(!DreamRules.hasWallpaper(DreamRules.CLOCK) && DreamRules.hasWallpaper(DreamRules.CLOCK_AND_WALLPAPER) && DreamRules.hasWallpaper(DreamRules.WALLPAPER_ONLY), "the other two show the wallpaper");
        check(DreamRules.clampStyle(-4) == 0 && DreamRules.clampStyle(7) == 2 && DreamRules.clampStyle(1) == 1, "style clamps");
        check(DreamRules.clampDim(0) == 30 && DreamRules.clampDim(100) == 95 && DreamRules.clampDim(75) == 75 && DreamRules.clampDim(77) == 75 && DreamRules.clampDim(78) == 80 && DreamRules.DIM_DEFAULT == 75, "dim clamps to 30..95 in steps of 5, default 75 (heavily dimmed)");
        // wake rules
        check(DreamRules.grace(0) && DreamRules.grace(599) && !DreamRules.grace(600) && !DreamRules.grace(5000) && DreamRules.grace(-5), "nothing wakes it in the first 600 ms (and a clock that went back counts as inside)");
        check(!DreamRules.stickWakes(0, 0, 0, 0, 0, 0, 0, 0) && !DreamRules.stickWakes(0.29f, -0.29f, 0.1f, -0.2f, 0, 0, 0, 0), "a resting stick, even one off centre by 0.29, does not wake it");
        check(DreamRules.stickWakes(0.31f, 0, 0, 0, 0, 0, 0, 0) && DreamRules.stickWakes(0, -0.9f, 0, 0, 0, 0, 0, 0) && DreamRules.stickWakes(0, 0, 0.5f, 0, 0, 0, 0, 0) && DreamRules.stickWakes(0, 0, 0, -1f, 0, 0, 0, 0), "a push beyond the deadzone on either stick wakes it");
        check(DreamRules.stickWakes(0, 0, 0, 0, 1f, 0, 0, 0) && DreamRules.stickWakes(0, 0, 0, 0, 0, -1f, 0, 0) && !DreamRules.stickWakes(0, 0, 0, 0, 0.4f, 0, 0, 0), "the D-pad hat wakes it");
        check(DreamRules.stickWakes(0, 0, 0, 0, 0, 0, 0.9f, 0) && DreamRules.stickWakes(0, 0, 0, 0, 0, 0, 0, 0.9f) && !DreamRules.stickWakes(0, 0, 0, 0, 0, 0, 0.1f, 0.1f), "a pulled trigger wakes it, a resting one does not");
        check(!DreamRules.stickWakes(Float.NaN, Float.NaN, 0, 0, 0, 0, 0, 0), "NaN never wakes it");
        DreamRules.Pointer ptr = new DreamRules.Pointer();
        check(!ptr.moved(100, 100), "the first pointer sample only records where it is");
        check(!ptr.moved(101, 101) && !ptr.moved(102, 100), "a 1 to 2 px wobble does not wake it");
        check(ptr.moved(110, 100) && ptr.moved(110, 120), "a real move wakes it");
        ptr.reset();
        check(!ptr.moved(500, 500), "after reset the next sample is a new first sample");
        check(!ptr.moved(Float.NaN, 5) && !ptr.moved(5, Float.NaN), "NaN positions are ignored");
    }

    static void screensaverCmd() {
        section("2.7 screensaver enable commands");
        String comp = "com.nokoding.deck/com.nokoding.deck.DeckDream";
        check(ScreensaverCmd.component().equals(comp) && ScreensaverCmd.validComponent(comp), "the component is package/class");
        String[] on = ScreensaverCmd.on(0);
        check(on.length == 3 && on[0].equals("settings put secure screensaver_components " + comp) && on[1].equals("settings put secure screensaver_enabled 1")
                && on[2].equals("settings put secure screensaver_activate_on_sleep 1"), "on, without a delay: component, enabled, activate on sleep");
        String[] on15 = ScreensaverCmd.on(15);
        check(on15.length == 4 && on15[3].equals("settings put system screen_off_timeout 900000"), "on with 15 minutes also sets the idle timeout in ms");
        check(ScreensaverCmd.on(1)[3].endsWith(" 60000") && ScreensaverCmd.on(120)[3].endsWith(" 7200000"), "1 and 120 minutes are allowed");
        check(ScreensaverCmd.on(-1) == null && ScreensaverCmd.on(121) == null && ScreensaverCmd.on(Long.MAX_VALUE) == null && ScreensaverCmd.on(Long.MIN_VALUE) == null, "minutes outside 1..120 are refused");
        check(ScreensaverCmd.off().equals("settings delete secure screensaver_components"), "off drops only the component setting");
        String[] rd = ScreensaverCmd.reads();
        check(rd.length == 4 && rd[0].equals("settings get secure screensaver_components") && rd[3].equals("settings get system screen_off_timeout"), "status reads four values");
        boolean allSettings = true;
        for (String c : on15) allSettings &= c.startsWith("settings put secure screensaver_") || c.startsWith("settings put system screen_off_timeout ");
        check(allSettings, "nothing else is touched: every command writes one of the four screensaver settings");
        // hostile component strings
        String[] badComp = {null, "", "/", "a", "a/", "/b", "a/b/c", "com.x/com.x.Y;reboot", "com.x/com.x.Y reboot", "com.x/$(id)", "com.x/`id`", "com.x/a\nb", "com.x/a'b", "com.x/a\"b", "com.x /a", "com.x/a|b", "com.x/a&b", "com.x/a>b",
                "com.x/a*b", "../x/y", "com.x/y\u0000"};
        boolean refused = true;
        String which = "";
        for (String b : badComp) if (ScreensaverCmd.validComponent(b)) { refused = false; which += " [" + b + "]"; }
        check(refused, "shell characters and malformed components are refused:" + which);
        check(ScreensaverCmd.validComponent("com.foo.bar/.MyDream") && ScreensaverCmd.validComponent("com.foo/com.foo.Outer$Dream") && !ScreensaverCmd.validComponent("x".repeat(300) + "/y"), "ordinary third party components pass, over long ones do not");
        // reading values
        check(ScreensaverCmd.first("a/b,c/d").equals("a/b") && ScreensaverCmd.first(" a/b ").equals("a/b") && ScreensaverCmd.first(null).isEmpty() && ScreensaverCmd.first("null").isEmpty() && ScreensaverCmd.first("").isEmpty(), "the first entry of the list wins");
        check(ScreensaverCmd.isDeck(comp) && ScreensaverCmd.isDeck("com.nokoding.deck/.DeckDream") && !ScreensaverCmd.isDeck("com.nokoding.deck/.PsService") && !ScreensaverCmd.isDeck("other/com.nokoding.deck.DeckDream") && !ScreensaverCmd.isDeck(null), "Visor's entry in the full and the short form");
        check(ScreensaverCmd.deckIsCurrent(comp + ",x/y") && !ScreensaverCmd.deckIsCurrent("x/y," + comp) && !ScreensaverCmd.deckIsCurrent("null"), "Visor must be first to be the current one (the system uses the first valid entry)");
        String d1 = ScreensaverCmd.describe(comp, "1", "1", 900000);
        check(d1.equals("VISOR IS THE SCREENSAVER  //  ENABLED YES  //  STARTS WHEN IDLE YES  //  AFTER 15 MIN"), "status of a working setup: " + d1);
        String d2 = ScreensaverCmd.describe(null, "null", null, -1);
        check(d2.equals("NONE SET, ANDROID USES ITS OWN DEFAULT  //  ENABLED NOT SET  //  STARTS WHEN IDLE NOT SET"), "status of a box that was never set: " + d2);
        String d3 = ScreensaverCmd.describe("com.google.android.apps.tv.dreamx/.Dream", "0", "0", 3600000);
        check(d3.startsWith("NOT VISOR: COM.GOOGLE.ANDROID.APPS.TV.DREAMX/.DREAM") && d3.contains("ENABLED NO") && d3.contains("STARTS WHEN IDLE NO") && d3.endsWith("AFTER 1 H"), "status with another screensaver: " + d3);
        check(ScreensaverCmd.minutesText(90000).equals("1 MIN") && ScreensaverCmd.minutesText(30000).equals("30 S") && ScreensaverCmd.minutesText(5400000).equals("90 MIN") && ScreensaverCmd.minutesText(7200000).equals("2 H"), "minutes text");
    }

    static void deckCmd27() {
        section("2.7 deck_cmd: reload_wallpapers and preview_screensaver");
        check(DeckCmd.parse("reload_wallpapers") == DeckCmd.RELOAD_WALLPAPERS && DeckCmd.parse("preview_screensaver") == DeckCmd.PREVIEW_SAVER, "the two new commands");
        check(DeckCmd.parse("reload_wallpapers ") == DeckCmd.NONE && DeckCmd.parse("RELOAD_WALLPAPERS") == DeckCmd.NONE && DeckCmd.parse(" preview_screensaver") == DeckCmd.NONE
                && DeckCmd.parse("reload_wallpapers;ls") == DeckCmd.NONE && DeckCmd.parse("reload_wallpaper") == DeckCmd.NONE && DeckCmd.parse("preview") == DeckCmd.NONE
                && DeckCmd.parse("screensaver") == DeckCmd.NONE && DeckCmd.parse("start_screensaver") == DeckCmd.NONE, "near misses are ignored");
        check(DeckCmd.NAMES.length == 12 && DeckCmd.NAMES[DeckCmd.RELOAD_WALLPAPERS].equals("reload_wallpapers") && DeckCmd.NAMES[DeckCmd.PREVIEW_SAVER].equals("preview_screensaver"), "the whitelist is twelve entries: the empty one and eleven names (2.8 added restore_display, 2.9 open_tutorial, 2.12 exit_game, 2.17 reload_themes)");
        check(DeckCmd.parse("reload_packs") == DeckCmd.RELOAD_PACKS && DeckCmd.parse("reload_icons") == DeckCmd.RELOAD_ICONS && DeckCmd.parse("open_setup") == DeckCmd.OPEN_SETUP
                && DeckCmd.parse("reset_remap") == DeckCmd.RESET_REMAP && DeckCmd.parse("open_inputs") == DeckCmd.OPEN_INPUTS, "the old five still work");
        check(DeckCmd.parse(Integer.valueOf(6)) == DeckCmd.NONE && DeckCmd.parse(new String[]{"reload_wallpapers"}) == DeckCmd.NONE, "non string values are ignored");
    }
}
