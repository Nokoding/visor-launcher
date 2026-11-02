package com.nokoding.deck;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.9 group A tests: button combos (the allowed buttons, the saved table, the press tracking, the learn screen). */
final class Tests29A {
    private Tests29A() {}

    private static final long L1 = 102, R1 = 103, X = 99, Y = 100, L2 = 104, R2 = 105, TL = 106, TR = 107, START = 108, SELECT = 109;
    private static final long CH_UP = 166, CH_DOWN = 167, TV = 170, INPUT = 178, RED = 183;
    private static final long SCAN = RemapKey.pack(0, 233, 4012, 9001);
    private static final String D = "com.nokoding.deck", G = "com.game.one", X_APP = "com.other.app";

    static void run() {
        allowed();
        why();
        actions();
        tableBasics();
        tableSaveLoad();
        tableHostile();
        tableEdit();
        words();
        gateLife();
        gateRules();
        gateContext();
        gateOdd();
        learn();
    }

    private static ComboTable.Combo combo(long a, long b, int action) {
        ComboTable.Combo c = new ComboTable.Combo(a, b);
        c.action = action;
        return c;
    }

    private static ComboTable one(long a, long b, int action) {
        return ComboTable.EMPTY.with(combo(a, b, action));
    }

    // ================================================================== allowed keys

    static void allowed() {
        section("2.9 combos: which buttons");
        for (long k : new long[]{X, Y, L1, R1, L2, R2, TL, TR, START, SELECT}) check(ComboTable.allowedKey(k), "gamepad button " + k + " allowed");
        for (long k : new long[]{CH_UP, CH_DOWN, TV, INPUT, 288, 172, 165, 183, 184, 185, 186, 313}) check(ComboTable.allowedKey(k), "spare button " + k + " allowed");
        check(ComboTable.allowedKey(SCAN), "a scan code only button is allowed");
        check(RemapKey.cls(SCAN) == RemapKey.STANDARD, "the scan code button is STANDARD");
        check(ComboTable.allowedKey(345), "an unknown spare code is allowed");
        for (long k : new long[]{19, 20, 21, 22, 23}) check(!ComboTable.allowedKey(k), "D-pad " + k + " not allowed");
        check(!ComboTable.allowedKey(4), "Back not allowed");
        check(!ComboTable.allowedKey(3), "Home not allowed");
        check(!ComboTable.allowedKey(26), "Power not allowed");
        check(!ComboTable.allowedKey(110), "the PS button not allowed");
        check(!ComboTable.allowedKey(96), "A not allowed");
        check(!ComboTable.allowedKey(97), "B not allowed");
        check(!ComboTable.allowedKey(98) && !ComboTable.allowedKey(101), "C and Z not allowed");
        check(!ComboTable.allowedKey(66) && !ComboTable.allowedKey(111), "Enter and Escape not allowed");
        for (long k = 29; k <= 54; k++) check(!ComboTable.allowedKey(k), "letter key " + k + " not allowed");
        for (long k = 7; k <= 16; k++) check(!ComboTable.allowedKey(k), "digit key " + k + " not allowed");
        check(!ComboTable.allowedKey(82), "Menu (advanced) not allowed");
        check(!ComboTable.allowedKey(85) && !ComboTable.allowedKey(24) && !ComboTable.allowedKey(25) && !ComboTable.allowedKey(164), "media and volume keys (advanced) not allowed");
        check(!ComboTable.allowedKey(219), "assistant (advanced) not allowed");
        check(!ComboTable.allowedKey(188) && !ComboTable.allowedKey(203), "other game buttons not allowed");
        check(!ComboTable.allowedKey(RemapKey.INVALID), "an invalid key not allowed");
        check(!ComboTable.allowedKey(0) && !ComboTable.allowedKey(-5), "zero and negative not allowed");
        check(!ComboTable.allowedKey(RemapKey.pack(0, 0, 1, 1)), "code 0 without a scan code is invalid");
        check(!ComboTable.allowedKey(RemapKey.pack(70000, 0, 0, 0)), "a code above the limit is invalid");
    }

    static void why() {
        section("2.9 combos: why not");
        check(ComboTable.whyNot(L1) == null && ComboTable.whyNot(CH_UP) == null && ComboTable.whyNot(SCAN) == null, "no reason for allowed buttons");
        check("THAT BUTTON IS NEEDED TO MOVE AROUND".equals(ComboTable.whyNot(19)), "D-pad reason");
        check("THAT BUTTON IS NEEDED TO MOVE AROUND".equals(ComboTable.whyNot(23)), "OK reason");
        check("THAT BUTTON IS NEEDED TO MOVE AROUND".equals(ComboTable.whyNot(4)), "Back reason");
        check("THAT BUTTON IS NEEDED TO MOVE AROUND".equals(ComboTable.whyNot(96)), "A reason");
        check("THAT BUTTON IS NEEDED TO MOVE AROUND".equals(ComboTable.whyNot(97)), "B reason");
        check("THE PS BUTTON AND HOME CANNOT BE USED".equals(ComboTable.whyNot(110)), "PS button reason");
        check("THE PS BUTTON AND HOME CANNOT BE USED".equals(ComboTable.whyNot(3)), "Home reason");
        check("THE PS BUTTON AND HOME CANNOT BE USED".equals(ComboTable.whyNot(26)), "Power reason");
        check("THAT BUTTON NEEDS ADVANCED REMAP".equals(ComboTable.whyNot(82)), "Menu reason");
        check("THAT BUTTON NEEDS ADVANCED REMAP".equals(ComboTable.whyNot(85)), "media reason");
        check("NOT A VALID BUTTON".equals(ComboTable.whyNot(RemapKey.INVALID)), "invalid reason");
        check("NOT A VALID BUTTON".equals(ComboTable.whyNot(0)), "zero reason");
        String typing = ComboTable.whyNot(29);
        check(typing != null && !typing.isEmpty(), "a typing key has a reason");
        for (long k = 1; k < 400; k++) {
            String w = ComboTable.whyNot(k);
            check((w == null) == ComboTable.allowedKey(k), "whyNot null exactly when allowed, key " + k);
            if (w != null) check(w.equals(w.toUpperCase()) && w.indexOf('—') < 0, "reason is plain capitals, key " + k);
        }
    }

    static void actions() {
        section("2.9 combos: valid actions");
        check(ComboTable.validAction(RemapAction.DECK_PS, "", L1, R1), "VISOR PS ok");
        check(ComboTable.validAction(RemapAction.SCREENSHOT, "", L1, R1), "SCREENSHOT ok");
        check(ComboTable.validAction(RemapAction.TOGGLE_PROFILE, "", L1, R1), "TOGGLE PROFILE ok");
        check(ComboTable.validAction(RemapAction.DECK_PS, null, L1, R1), "a null arg is fine for a non-app action");
        check(!ComboTable.validAction(RemapAction.DEFAULT, "", L1, R1), "DEFAULT not ok");
        check(!ComboTable.validAction(RemapAction.NOTHING, "", L1, R1), "NOTHING not ok");
        for (int a = RemapAction.KEY_UP; a <= RemapAction.KEY_BACK; a++) check(!ComboTable.validAction(a, "", L1, R1), "deck key action " + a + " not ok");
        check(!ComboTable.validAction(-1, "", L1, R1) && !ComboTable.validAction(RemapAction.COUNT, "", L1, R1), "out of range action not ok");
        check(!ComboTable.validAction(RemapAction.APP, "", L1, R1), "APP without a package not ok");
        check(!ComboTable.validAction(RemapAction.APP, null, L1, R1), "APP with null not ok");
        check(!ComboTable.validAction(RemapAction.APP, "not a package", L1, R1), "APP with spaces not ok");
        check(!ComboTable.validAction(RemapAction.APP, "com.a;b", L1, R1), "APP with a semicolon not ok");
        check(!ComboTable.validAction(RemapAction.APP, "com.a|b", L1, R1), "APP with a bar not ok");
        check(!ComboTable.validAction(RemapAction.APP, "1com.a", L1, R1), "APP starting with a digit not ok");
        check(ComboTable.validAction(RemapAction.APP, "com.netflix.ninja", L1, R1), "APP with a good package ok");
        for (int a = 0; a < RemapAction.COUNT; a++) {
            boolean expect = a != RemapAction.DEFAULT && a != RemapAction.NOTHING && a != RemapAction.APP && !RemapAction.deckKey(a);
            check(ComboTable.validAction(a, "", L1, R1) == expect, "action " + RemapAction.id(a) + " valid is " + expect);
        }
    }

    // ================================================================== table

    static void tableBasics() {
        section("2.9 combos: table basics");
        check(ComboTable.EMPTY.size() == 0 && "".equals(ComboTable.EMPTY.save()), "empty table is empty and saves as nothing");
        check(ComboTable.MAX == 12 && "v1".equals(ComboTable.VERSION) && "combos".equals(ComboTable.PREF), "constants");
        ComboTable.Combo c = new ComboTable.Combo(R1, L1);
        check(c.a == L1 && c.b == R1, "a is the smaller key");
        c = new ComboTable.Combo(L1, R1);
        check(c.a == L1 && c.b == R1, "already ordered stays");
        c.action = RemapAction.DECK_PS;
        c.arg = "x";
        c.label = "lbl";
        ComboTable.Combo d = c.copy();
        d.action = 1;
        d.arg = "y";
        check(c.action == RemapAction.DECK_PS && "x".equals(c.arg) && d.a == c.a && d.b == c.b && "lbl".equals(d.label), "copy is independent");
        ComboTable t = one(L1, R1, RemapAction.DECK_PS);
        check(t.size() == 1, "one entry");
        check(t.find(L1, R1) != null && t.find(R1, L1) != null, "find works in both orders");
        check(t.find(L1, R1).action == RemapAction.DECK_PS, "found combo has the action");
        check(t.find(L1, L2) == null && t.find(CH_UP, CH_DOWN) == null, "unknown pair not found");
        check(t.involves(L1) && t.involves(R1) && !t.involves(L2) && !t.involves(0), "involves");
        ComboTable.Combo g = t.get(0);
        g.action = RemapAction.HOME;
        check(t.get(0).action == RemapAction.DECK_PS, "get returns a copy");
        check(t.get(-1) == null && t.get(1) == null, "get out of range is null");
        check(ComboTable.EMPTY.find(L1, R1) == null && !ComboTable.EMPTY.involves(L1), "empty finds nothing");
    }

    static void tableSaveLoad() {
        section("2.9 combos: save and load");
        ComboTable t = ComboTable.EMPTY;
        ComboTable.Combo a = combo(L1, R1, RemapAction.DECK_PS);
        a.label = "Menu (PS)";
        ComboTable.Combo b = combo(CH_UP, CH_DOWN, RemapAction.APP);
        b.arg = "com.netflix.ninja";
        ComboTable.Combo c = combo(SCAN, START, RemapAction.SCREENSHOT);
        t = t.with(a).with(b).with(c);
        check(t.size() == 3, "three entries");
        String s = t.save();
        check(s.startsWith("v1;"), "saved text starts with the version");
        check(s.length() <= 2000, "saved text within 2000 characters");
        boolean ascii = true;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 32 || s.charAt(i) > 126) ascii = false;
        check(ascii, "saved text is plain ASCII");
        check(s.indexOf('\n') < 0, "saved text is one line");
        ComboTable u = ComboTable.parse(s);
        check(u.size() == 3 && s.equals(u.save()), "round trip gives the same text");
        check(u.find(R1, L1).action == RemapAction.DECK_PS && "Menu (PS)".equals(u.find(L1, R1).label), "round trip keeps action and label");
        check("com.netflix.ninja".equals(u.find(CH_DOWN, CH_UP).arg) && u.find(CH_UP, CH_DOWN).action == RemapAction.APP, "round trip keeps the app");
        check(u.find(START, SCAN) != null && u.find(SCAN, START).action == RemapAction.SCREENSHOT, "round trip keeps the scan code button");
        check(ComboTable.parse(ComboTable.EMPTY.save()).size() == 0, "empty round trip");
        check("v1;kc:102+kc:103|deck_ps||".equals(one(R1, L1, RemapAction.DECK_PS).save()), "exact format of one entry");
        ComboTable.Combo withArg = combo(L1, R1, RemapAction.DECK_PS);
        withArg.arg = "com.junk";
        check("".equals(one(L1, R1, RemapAction.DECK_PS).with(withArg).get(0).arg), "a non-app action drops its arg");
        // saved text with entries in the other key order still loads
        ComboTable v = ComboTable.parse("v1;kc:103+kc:102|deck_ps||x");
        check(v.size() == 1 && v.find(L1, R1) != null && "x".equals(v.get(0).label), "keys saved in the other order load");
        check(v.get(0).a == L1, "loaded combo is normalized");
        // a table of twelve fits in 2000 characters even with long labels
        ComboTable big = ComboTable.EMPTY;
        long[] ks = {L1, R1, L2, R2, X, Y, TL, TR, START, SELECT, CH_UP, CH_DOWN, TV, INPUT};
        for (int i = 0; i + 1 < ks.length; i++) {
            ComboTable.Combo x = combo(ks[i], ks[i + 1], RemapAction.APP);
            x.arg = "com.a.b.c.d.e.f.g.h.i.j.k";
            x.label = "A label that is much longer than forty characters for sure";
            big = big.with(x);
        }
        check(big.size() == 12 && big.save().length() <= 2000, "twelve long entries fit");
        check(ComboTable.parse(big.save()).size() == 12, "twelve long entries load back");
        check(ComboTable.parse(big.save()).get(0).label.length() <= 40, "labels cut to 40");
    }

    private static void survives(String s, String what) {
        ComboTable t = null;
        try { t = ComboTable.parse(s); } catch (RuntimeException e) { check(false, what + " threw " + e); }
        check(t != null, what + " gives a table");
        if (t != null) check(t.size() <= ComboTable.MAX && t.save().length() <= 2000, what + " is bounded");
    }

    static void tableHostile() {
        section("2.9 combos: hostile saved text");
        survives(null, "null");
        check(ComboTable.parse(null).size() == 0, "null is empty");
        check(ComboTable.parse("").size() == 0, "empty string is empty");
        check(ComboTable.parse("v1").size() == 0 && ComboTable.parse("v1;").size() == 0 && ComboTable.parse("v1;;;").size() == 0, "only the version is empty");
        StringBuilder huge = new StringBuilder("v1;");
        while (huge.length() < 100 * 1024) huge.append("kc:102+kc:103|deck_ps||;");
        survives(huge.toString(), "100 KB");
        check(ComboTable.parse(huge.toString()).size() == 0, "100 KB is dropped");
        String good = "kc:102+kc:103|deck_ps||";
        StringBuilder exact = new StringBuilder("v1;" + good);
        while (exact.length() < 2000) exact.append(' ');
        check(exact.length() == 2000, "test text is exactly 2000");
        check(ComboTable.parse(exact.toString()).size() == 1, "text of exactly 2000 characters is read (the blank label is trimmed)");
        check(ComboTable.parse("v1;" + good + "x".repeat(2000)).size() == 0, "over 2000 characters dropped");
        check(ComboTable.parse("v2;" + good).size() == 0, "wrong version");
        check(ComboTable.parse("V1;" + good).size() == 0, "wrong case version");
        check(ComboTable.parse(good).size() == 0, "no version");
        check(ComboTable.parse("v1 ;" + good).size() == 0, "version with a space");
        for (String s : new String[]{"garbage", "v1;garbage", "v1;|||", "v1;+|||", "v1;+", "v1;a+b|c|d|e", "\0\0\0", "v1;\u0000", ";;;;", "v1;kc:102+kc:103", "v1;kc:102+kc:103|deck_ps|",
                "v1;kc:102+kc:103|deck_ps|||", "v1;kc:102+kc:103+kc:104|deck_ps||", "v1;kc:102|deck_ps||", "v1;kc:102+|deck_ps||", "v1;+kc:103|deck_ps||"}) {
            survives(s, "junk " + s.replace('\0', '0'));
            check(ComboTable.parse(s).size() == 0, "junk dropped: " + s.replace('\0', '0'));
        }
        check(ComboTable.parse("v1;kc:102+kc:103|nothing||").size() == 0, "unknown/NOTHING: do nothing dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|default||").size() == 0, "DEFAULT dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|bogus||").size() == 0, "unknown action id dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|key_up||").size() == 0, "deck key action dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|app||").size() == 0, "app without package dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|app|bad pkg|").size() == 0, "app with bad package dropped");
        check(ComboTable.parse("v1;kc:102+kc:103|app|com.a.b|").size() == 1, "app with good package kept");
        check(ComboTable.parse("v1;kc:19+kc:103|deck_ps||").size() == 0, "D-pad in a combo dropped");
        check(ComboTable.parse("v1;kc:102+kc:110|deck_ps||").size() == 0, "PS button in a combo dropped");
        check(ComboTable.parse("v1;kc:102+kc:96|deck_ps||").size() == 0, "A in a combo dropped");
        check(ComboTable.parse("v1;kc:3+kc:103|deck_ps||").size() == 0, "Home in a combo dropped");
        check(ComboTable.parse("v1;kc:102+kc:102|deck_ps||").size() == 0, "equal keys dropped");
        check(ComboTable.parse("v1;kc:102+kc:0102|deck_ps||").size() == 0, "equal keys written two ways dropped");
        check(ComboTable.parse("v1;kc:0+kc:103|deck_ps||").size() == 0, "key zero dropped");
        check(ComboTable.parse("v1;kc:-1+kc:103|deck_ps||").size() == 0, "negative key dropped");
        check(ComboTable.parse("v1;kc:70000+kc:103|deck_ps||").size() == 0, "key over limit dropped");
        check(ComboTable.parse("v1;sc:233:4012:9001+kc:103|deck_ps||").size() == 1, "scan code key kept");
        check(ComboTable.parse("v1;sc:0:1:1+kc:103|deck_ps||").size() == 0, "scan code zero dropped");
        check(ComboTable.parse("v1;sc:1:2+kc:103|deck_ps||").size() == 0, "short scan id dropped");
        // duplicates: first wins, also across key order
        ComboTable dup = ComboTable.parse("v1;kc:102+kc:103|deck_ps||first;kc:103+kc:102|home||second;kc:102+kc:103|back||third");
        check(dup.size() == 1 && dup.get(0).action == RemapAction.DECK_PS && "first".equals(dup.get(0).label), "duplicate pair: first wins");
        // a poisoned entry in the middle keeps its neighbours
        ComboTable mid = ComboTable.parse("v1;kc:102+kc:103|deck_ps||one;kc:19+kc:20|home||bad;zzz;kc:166+kc:167|bogus||bad2;kc:104+kc:105|home||two;kc:106+kc:106|home||bad3;kc:107+kc:108|back||three");
        check(mid.size() == 3, "poisoned entries dropped, three neighbours kept: " + mid.size());
        check(mid.find(L1, R1) != null && mid.find(L2, R2) != null && mid.find(TR, START) != null, "the neighbours are the right ones");
        check("two".equals(mid.find(L2, R2).label), "neighbour label intact");
        // more than MAX: the first MAX are kept
        StringBuilder many = new StringBuilder("v1");
        int n = 0;
        for (long a = 166; a < 200 && n < 20; a++) for (long b = a + 1; b < 200 && n < 20; b++) {
            if (!ComboTable.allowedKey(a) || !ComboTable.allowedKey(b)) continue;
            many.append(";kc:").append(a).append("+kc:").append(b).append("|home||");
            n++;
        }
        check(n == 20 && many.length() <= 2000, "test builds twenty entries within the limit");
        ComboTable cap = ComboTable.parse(many.toString());
        check(cap.size() == 12, "more than twelve: twelve kept, got " + cap.size());
        check(cap.find(166, 167) != null && cap.find(166, 168) != null, "the first ones are the ones kept");
        // labels
        ComboTable lab = ComboTable.parse("v1;kc:102+kc:103|deck_ps||a" + "b".repeat(100));
        check(lab.size() == 1 && lab.get(0).label.length() == 40, "a long label is cut to 40");
        ComboTable lab2 = ComboTable.parse("v1;kc:102+kc:103|deck_ps||é中\t\n!");
        check(lab2.size() == 1 && lab2.get(0).label.indexOf(';') < 0 && lab2.get(0).label.indexOf('|') < 0, "odd label characters are cleaned");
        ComboTable.Combo semi = combo(L1, R1, RemapAction.DECK_PS);
        semi.label = "a;b|c;d";
        ComboTable ts = one(L1, R1, RemapAction.DECK_PS).with(semi);
        check(ts.get(0).label.indexOf(';') < 0 && ts.get(0).label.indexOf('|') < 0, "with() cleans ; and | out of a label");
        ComboTable back = ComboTable.parse(ts.save());
        check(back.size() == 1 && back.get(0).label.equals(ts.get(0).label), "a label with ; and | round trips");
        semi.label = null;
        check("".equals(ComboTable.EMPTY.with(semi).get(0).label), "a null label becomes empty");
        // a random fuzz never throws and never exceeds the bounds
        java.util.Random r = new java.util.Random(29);
        String alphabet = "v1;|+:kcsc0123456789 deck_ps app com.a.b home";
        for (int i = 0; i < 3000; i++) {
            StringBuilder b = new StringBuilder(r.nextBoolean() ? "v1;" : "");
            int len = r.nextInt(120);
            for (int j = 0; j < len; j++) b.append(alphabet.charAt(r.nextInt(alphabet.length())));
            try {
                ComboTable p = ComboTable.parse(b.toString());
                if (p.size() > 12) check(false, "fuzz over MAX");
                for (int j = 0; j < p.size(); j++) {
                    ComboTable.Combo cc = p.get(j);
                    if (!ComboTable.allowedKey(cc.a) || !ComboTable.allowedKey(cc.b) || cc.a >= cc.b || !ComboTable.validAction(cc.action, cc.arg, cc.a, cc.b)) check(false, "fuzz let a bad entry through: " + b);
                }
            } catch (RuntimeException e) {
                check(false, "fuzz threw " + e + " on " + b);
            }
        }
        check(true, "fuzz done");
    }

    static void tableEdit() {
        section("2.9 combos: add, replace, remove");
        ComboTable t = one(L1, R1, RemapAction.DECK_PS);
        ComboTable r = t.with(combo(R1, L1, RemapAction.HOME));
        check(r.size() == 1 && r.get(0).action == RemapAction.HOME, "same pair in the other order replaces");
        check(t.get(0).action == RemapAction.DECK_PS, "the old table is not changed (immutable)");
        ComboTable two = t.with(combo(L2, R2, RemapAction.BACK));
        check(two.size() == 2 && t.size() == 1, "added to a new table");
        check(two.get(0).a == L1 && two.get(1).a == L2, "order of entries kept");
        ComboTable rep = two.with(combo(L1, R1, RemapAction.MUTE));
        check(rep.size() == 2 && rep.get(0).action == RemapAction.MUTE && rep.get(1).action == RemapAction.BACK, "replace keeps the position");
        check(two.without(R1, L1).size() == 1 && two.without(R1, L1).find(L2, R2) != null, "without removes in either order");
        check(two.without(L1, R1).find(L1, R1) == null, "removed pair is gone");
        check(two.without(CH_UP, CH_DOWN) == two, "without an unknown pair returns the same table");
        check(t.without(L1, R1).size() == 0 && t.without(L1, R1).save().isEmpty(), "removing the last gives an empty table");
        // rejects
        check(t.with(combo(L1, L1, RemapAction.HOME)) == t, "equal keys: same table");
        check(t.with(combo(19, R1, RemapAction.HOME)) == t, "D-pad: same table");
        check(t.with(combo(L2, R2, RemapAction.NOTHING)) == t, "NOTHING: same table");
        check(t.with(combo(L2, R2, RemapAction.DEFAULT)) == t, "DEFAULT: same table");
        check(t.with(combo(L2, R2, RemapAction.KEY_OK)) == t, "deck key: same table");
        check(t.with(combo(L2, R2, RemapAction.APP)) == t, "app without package: same table");
        check(t.with(null) == t, "null combo: same table");
        // canAdd
        check(ComboTable.canAdd(ComboTable.EMPTY, L1, R1), "canAdd a good pair");
        check(ComboTable.canAdd(ComboTable.EMPTY, R1, L1), "canAdd in the other order");
        check(!ComboTable.canAdd(ComboTable.EMPTY, L1, L1), "canAdd false for equal keys");
        check(!ComboTable.canAdd(ComboTable.EMPTY, L1, 19), "canAdd false for D-pad");
        check(!ComboTable.canAdd(ComboTable.EMPTY, 110, L1), "canAdd false for the PS button");
        check(!ComboTable.canAdd(ComboTable.EMPTY, RemapKey.INVALID, L1), "canAdd false for an invalid key");
        check(ComboTable.canAdd(null, L1, R1), "canAdd with a null table works");
        ComboTable full = ComboTable.EMPTY;
        for (long a = 166; full.size() < 12; a++) full = full.with(combo(a, 300, RemapAction.HOME));
        check(full.size() == 12, "table of twelve");
        check(!ComboTable.canAdd(full, L1, R1), "full table: a new pair cannot be added");
        check(ComboTable.canAdd(full, 166, 300), "full table: an existing pair can be replaced");
        check(full.with(combo(L1, R1, RemapAction.HOME)) == full, "full table: with returns the same table");
        check(full.with(combo(300, 166, RemapAction.MUTE)).get(0).action == RemapAction.MUTE, "full table: replace works");
        check(full.without(166, 300).size() == 11 && ComboTable.canAdd(full.without(166, 300), L1, R1), "after a removal there is room");
        check(ComboTable.canSet(ComboTable.EMPTY, combo(L1, R1, RemapAction.HOME)), "canSet a good combo");
        check(!ComboTable.canSet(ComboTable.EMPTY, combo(L1, R1, RemapAction.NOTHING)), "canSet false for a bad action");
        check(!ComboTable.canSet(ComboTable.EMPTY, null), "canSet false for null");
        // the size limit: entries that would push the line over 2000 characters are refused
        ComboTable fat = ComboTable.EMPTY;
        String pkg = "com." + "a".repeat(190);
        long[] ks = {L1, R1, L2, R2, X, Y, TL, TR, START, SELECT, CH_UP, CH_DOWN, TV};
        for (int i = 0; i + 1 < ks.length; i++) {
            ComboTable.Combo x = combo(ks[i], ks[i + 1], RemapAction.APP);
            x.arg = pkg;
            x.label = "l".repeat(40);
            ComboTable n = fat.with(x);
            if (!ComboTable.canSet(fat, x)) check(n == fat, "refused by size: same table");
            fat = n;
        }
        check(fat.save().length() <= 2000, "the saved line never exceeds 2000 characters");
        check(fat.size() < 12, "long app names stop the table before twelve");
        check(ComboTable.parse(fat.save()).size() == fat.size(), "such a table still loads whole");
    }

    static void words() {
        section("2.9 combos: describe and line");
        ComboTable.Combo c = combo(R1, L1, RemapAction.DECK_PS);
        check("L1 + R1".equals(ComboTable.describe(c, k -> k == L1 ? "L1" : "R1")), "describe uses the namer, smaller key first");
        check("KEY 102 + KEY 103".equals(ComboTable.describe(c, null)), "null namer falls back to RemapKey.name");
        check("KEY 102 + KEY 103".equals(ComboTable.describe(c, k -> null)), "a namer returning null falls back");
        check("KEY 102 + KEY 103".equals(ComboTable.describe(c, k -> "")), "a namer returning empty falls back");
        check("KEY 102 + KEY 103".equals(ComboTable.describe(c, k -> { throw new IllegalStateException(); })), "a failing namer falls back");
        check("CHANNEL UP + CHANNEL DOWN".equals(ComboTable.describe(combo(CH_DOWN, CH_UP, RemapAction.HOME), null)), "known names");
        check(ComboTable.describe(combo(SCAN, L1, RemapAction.HOME), null).contains("SCAN 233"), "scan code name");
        check("L1 + R1  >  OPEN VISOR PS MENU".equals(ComboTable.line(c, k -> k == L1 ? "L1" : "R1")), "line text");
        ComboTable.Combo app = combo(L1, R1, RemapAction.APP);
        app.arg = "com.netflix.ninja";
        check(ComboTable.line(app, null).endsWith("LAUNCH APP: com.netflix.ninja"), "line for an app carries the package");
        app.arg = "";
        check(ComboTable.line(app, null).endsWith("LAUNCH APP"), "line for an app without package has no suffix");
        check(ComboTable.line(combo(L1, R1, RemapAction.SCREENSHOT), null).endsWith("  >  SCREENSHOT"), "line separator");
    }

    // ================================================================== gate

    private static ComboGate gate(ComboTable t) {
        ComboGate g = new ComboGate(D);
        g.table = t;
        return g;
    }

    static void gateLife() {
        section("2.9 combos: press tracking");
        ComboGate g = gate(one(L1, R1, RemapAction.DECK_PS));
        long t = 1000;
        check(g.onKey(L1, true, 0, t, G) == ComboGate.PASS, "first key down passes");
        check(g.onKey(R1, true, 0, t + 50, G) == ComboGate.ACT, "second key down acts");
        check(g.act == RemapAction.DECK_PS && "".equals(g.arg), "action and arg set");
        check(g.onKey(R1, true, 1, t + 100, G) == ComboGate.SWALLOW, "second key repeat swallowed");
        check(g.onKey(R1, true, 2, t + 150, G) == ComboGate.SWALLOW, "second key repeat swallowed again");
        check(g.onKey(L1, true, 1, t + 160, G) == ComboGate.PASS, "first key repeat passes");
        check(g.onKey(R1, false, 0, t + 200, G) == ComboGate.SWALLOW, "second key up swallowed");
        check(g.onKey(L1, false, 0, t + 220, G) == ComboGate.PASS, "first key up passes");
        check(g.act == RemapAction.DEFAULT, "act cleared on a later key");
        // the other order
        check(g.onKey(R1, true, 0, t + 1000, G) == ComboGate.PASS, "other order: R1 first passes");
        check(g.onKey(L1, true, 0, t + 1010, G) == ComboGate.ACT, "other order: L1 second acts");
        check(g.onKey(L1, true, 1, t + 1100, G) == ComboGate.SWALLOW, "other order: repeat swallowed");
        check(g.onKey(L1, false, 0, t + 1200, G) == ComboGate.SWALLOW, "other order: up swallowed");
        check(g.onKey(R1, false, 0, t + 1300, G) == ComboGate.PASS, "other order: partner up passes");
        // releasing the first key first, then the second
        check(g.onKey(L1, true, 0, t + 3000, G) == ComboGate.PASS, "again: first");
        check(g.onKey(R1, true, 0, t + 3050, G) == ComboGate.ACT, "again: second acts");
        check(g.onKey(L1, false, 0, t + 3100, G) == ComboGate.PASS, "first released first passes");
        check(g.onKey(R1, true, 1, t + 3150, G) == ComboGate.SWALLOW, "second still held: repeat swallowed");
        check(g.onKey(R1, false, 0, t + 3200, G) == ComboGate.SWALLOW, "second up swallowed after the first was released");
        check(g.onKey(R1, false, 0, t + 3300, G) == ComboGate.PASS, "a stray second up afterwards passes");
        // the first key alone leaves nothing taken
        check(g.onKey(L1, true, 0, t + 5000, G) == ComboGate.PASS && g.onKey(L1, false, 0, t + 5100, G) == ComboGate.PASS, "first key alone passes whole");
        check(g.onKey(R1, true, 0, t + 5200, G) == ComboGate.PASS && g.onKey(R1, false, 0, t + 5300, G) == ComboGate.PASS, "second key alone (partner not held) passes whole");
        // an app action
        ComboTable.Combo app = combo(CH_UP, CH_DOWN, RemapAction.APP);
        app.arg = "com.netflix.ninja";
        ComboGate h = gate(ComboTable.EMPTY.with(app));
        h.onKey(CH_DOWN, true, 0, t, G);
        check(h.onKey(CH_UP, true, 0, t + 10, G) == ComboGate.ACT && h.act == RemapAction.APP && "com.netflix.ninja".equals(h.arg), "app action carries the package");
        // the scan code button works as a member
        ComboGate s = gate(one(SCAN, X, RemapAction.HOME));
        s.onKey(X, true, 0, t, G);
        check(s.onKey(SCAN, true, 0, t + 10, G) == ComboGate.ACT && s.act == RemapAction.HOME, "scan code button completes a pair");
    }

    static void gateRules() {
        section("2.9 combos: gate rules");
        long t = 10000;
        // third key
        ComboTable tab = one(L1, R1, RemapAction.DECK_PS).with(combo(R1, L2, RemapAction.HOME));
        ComboGate g = gate(tab);
        check(g.onKey(L1, true, 0, t, G) == ComboGate.PASS, "L1 down");
        check(g.onKey(CH_UP, true, 0, t + 10, G) == ComboGate.PASS, "an unrelated third key passes");
        check(g.onKey(R1, true, 0, t + 20, G) == ComboGate.ACT && g.act == RemapAction.DECK_PS, "pair completes with an unrelated key held");
        check(g.onKey(CH_UP, false, 0, t + 30, G) == ComboGate.PASS, "the unrelated key up passes");
        check(g.onKey(L2, true, 0, t + 40, G) == ComboGate.ACT && g.act == RemapAction.HOME, "a second combo using the held taken key completes");
        check(g.onKey(L2, false, 0, t + 50, G) == ComboGate.SWALLOW, "its up is swallowed");
        check(g.onKey(R1, false, 0, t + 60, G) == ComboGate.SWALLOW, "the first completed key's up still swallowed");
        check(g.onKey(L1, false, 0, t + 70, G) == ComboGate.PASS, "partner up passes");
        // a third key held that is in another combo does not break the pair
        ComboGate g3 = gate(tab);
        g3.onKey(L1, true, 0, t, G);
        g3.onKey(L2, true, 0, t + 5, G);   // in a combo, but its partner R1 is not held
        check(g3.onKey(R1, true, 0, t + 20, G) == ComboGate.ACT, "R1 completes with L1 (first match wins)");
        // MIN_GAP: second key again while the first is held
        ComboGate m = gate(one(L1, R1, RemapAction.DECK_PS));
        m.onKey(L1, true, 0, t, G);
        check(m.onKey(R1, true, 0, t + 10, G) == ComboGate.ACT, "fires");
        check(m.onKey(R1, false, 0, t + 100, G) == ComboGate.SWALLOW, "up swallowed");
        check(m.onKey(R1, true, 0, t + 200, G) == ComboGate.SWALLOW, "pressed again within the gap: swallowed, no action");
        check(m.act == RemapAction.DEFAULT, "no action on a swallowed repeat firing");
        check(m.onKey(R1, true, 1, t + 250, G) == ComboGate.SWALLOW, "and its repeats are swallowed");
        check(m.onKey(R1, false, 0, t + 280, G) == ComboGate.SWALLOW, "and its up");
        check(m.onKey(R1, true, 0, t + 10 + ComboGate.MIN_GAP_MS, G) == ComboGate.ACT, "after the gap it fires again with the first key still held");
        check(m.onKey(R1, false, 0, t + 400, G) == ComboGate.SWALLOW, "up swallowed");
        check(m.onKey(L1, false, 0, t + 410, G) == ComboGate.PASS, "first up passes");
        // a new first press of a held key clears the old entry
        ComboGate n = gate(one(L1, R1, RemapAction.DECK_PS));
        n.onKey(L1, true, 0, t, G);
        n.onKey(R1, true, 0, t + 10, G);       // taken, its up never comes
        check(n.onKey(R1, true, 0, t + 2000, G) == ComboGate.ACT, "a new first press of a taken key (up lost) starts again and fires");
        check(n.onKey(R1, false, 0, t + 2100, G) == ComboGate.SWALLOW && n.onKey(R1, false, 0, t + 2200, G) == ComboGate.PASS, "and ends cleanly");
        n.reset();
        n.onKey(L1, true, 0, t, G);
        n.onKey(R1, true, 0, t + 10, G);
        n.onKey(L1, false, 0, t + 20, G);
        check(n.onKey(R1, true, 0, t + 5000, G) == ComboGate.PASS, "after the partner was released a new R1 press passes");
        check(n.onKey(R1, false, 0, t + 5100, G) == ComboGate.PASS, "its up passes (nothing taken)");
        // the table swapped under a held taken key
        ComboGate sw = gate(one(L1, R1, RemapAction.DECK_PS));
        sw.onKey(L1, true, 0, t, G);
        sw.onKey(R1, true, 0, t + 10, G);
        sw.table = ComboTable.EMPTY;
        check(sw.onKey(R1, true, 1, t + 20, G) == ComboGate.SWALLOW, "table emptied: repeat of a taken key still swallowed");
        check(sw.onKey(R1, false, 0, t + 30, G) == ComboGate.SWALLOW, "table emptied: its up still swallowed");
        check(sw.onKey(L1, false, 0, t + 40, G) == ComboGate.PASS, "table emptied: partner up passes");
        check(sw.onKey(R1, true, 0, t + 3000, G) == ComboGate.PASS, "table emptied: later press passes");
        sw.table = one(L1, R1, RemapAction.HOME);
        sw.onKey(R1, false, 0, t + 3010, G);
        sw.onKey(L1, true, 0, t + 4000, G);
        check(sw.onKey(R1, true, 0, t + 4010, G) == ComboGate.ACT && sw.act == RemapAction.HOME, "a new table works at once");
        sw.table = one(CH_UP, CH_DOWN, RemapAction.HOME);
        check(sw.onKey(R1, false, 0, t + 4100, G) == ComboGate.SWALLOW, "table replaced by another: the taken up is swallowed");
        // reset
        ComboGate rs = gate(one(L1, R1, RemapAction.DECK_PS));
        rs.onKey(L1, true, 0, t, G);
        rs.onKey(R1, true, 0, t + 10, G);
        rs.reset();
        check(rs.onKey(R1, false, 0, t + 20, G) == ComboGate.PASS, "after reset the up of a taken key passes");
        check(rs.onKey(R1, true, 0, t + 30, G) == ComboGate.PASS, "after reset the first key is forgotten");
        check(rs.onKey(R1, false, 0, t + 40, G) == ComboGate.PASS, "up passes");
        rs.onKey(L1, true, 0, t + 50, G);
        check(rs.onKey(R1, true, 0, t + 60, G) == ComboGate.ACT, "and the repeat guard is cleared by reset");
        rs.reset();
        rs.onKey(L1, true, 0, t + 70, G);
        check(rs.onKey(R1, true, 0, t + 80, G) == ComboGate.ACT, "fires right after a reset though the pair fired 20 ms ago");
        // stale
        ComboGate st = gate(one(L1, R1, RemapAction.DECK_PS));
        st.onKey(L1, true, 0, t, G);
        check(st.onKey(R1, true, 0, t + ComboGate.STALE_MS - 1, G) == ComboGate.ACT, "a held key just under STALE_MS still counts");
        st.reset();
        st.onKey(L1, true, 0, t, G);
        check(st.onKey(R1, true, 0, t + ComboGate.STALE_MS, G) == ComboGate.PASS, "a held key at STALE_MS is forgotten");
        check(st.onKey(R1, false, 0, t + ComboGate.STALE_MS + 10, G) == ComboGate.PASS, "up passes");
        st.onKey(L1, true, 0, t + 100000, G);
        st.onKey(R1, true, 0, t + 100010, G);
        check(st.onKey(R1, true, 1, t + 100000 + 40000, G) == ComboGate.PASS, "a taken key with no event for 30 s is swept");
        // an empty table never takes anything
        ComboGate em = gate(ComboTable.EMPTY);
        long[] all = {L1, R1, CH_UP, 19, 23, 4, 3, 110, 96, 29, SCAN, RemapKey.INVALID, 0};
        boolean clean = true;
        for (int i = 0; i < 40; i++) for (long k : all) {
            if (em.onKey(k, true, 0, t + i, G) != ComboGate.PASS) clean = false;
            if (em.onKey(k, true, 1, t + i, G) != ComboGate.PASS) clean = false;
            if (em.onKey(k, false, 0, t + i, G) != ComboGate.PASS) clean = false;
        }
        check(clean, "an empty table passes everything");
        // a key with no combo is always PASS and leaves no trace: fill up the eight slots many times
        ComboGate nt = gate(one(L1, R1, RemapAction.DECK_PS));
        boolean pass = true;
        for (int i = 0; i < 100; i++) {
            long k = 200 + i;
            if (nt.onKey(k, true, 0, t + i, G) != ComboGate.PASS) pass = false;   // never an up in between
        }
        check(pass, "100 keys that are in no combo, never released: all pass");
        nt.onKey(L1, true, 0, t + 500, G);
        check(nt.onKey(R1, true, 0, t + 510, G) == ComboGate.ACT, "and the combo still works (they left no trace)");
        check(nt.onKey(250, false, 0, t + 520, G) == ComboGate.PASS && nt.onKey(250, true, 3, t + 530, G) == ComboGate.PASS, "up and repeat of an unfollowed key pass");
    }

    static void gateContext() {
        section("2.9 combos: where combos fire");
        long t = 50000;
        Set<String> games = new HashSet<>(Arrays.asList(G));
        ComboGate g = gate(one(L1, R1, RemapAction.DECK_PS));
        g.pass = games;
        g.onKey(L1, true, 0, t, G);
        check(g.onKey(R1, true, 0, t + 10, G) == ComboGate.PASS, "in a game without REMAP IN GAMES: no combo");
        check(g.onKey(R1, true, 1, t + 20, G) == ComboGate.PASS && g.onKey(R1, false, 0, t + 30, G) == ComboGate.PASS, "the game sees a whole press: repeat and up pass");
        g.onKey(L1, false, 0, t + 40, G);
        g.flags = RemapGate.F_GAMES;
        g.onKey(L1, true, 0, t + 1000, G);
        check(g.onKey(R1, true, 0, t + 1010, G) == ComboGate.ACT, "in a game with REMAP IN GAMES: combo fires");
        g.onKey(R1, false, 0, t + 1100, G);
        g.onKey(L1, false, 0, t + 1110, G);
        g.flags = RemapGate.F_ADVANCED | RemapGate.F_NAV;
        g.onKey(L1, true, 0, t + 2000, G);
        check(g.onKey(R1, true, 0, t + 2010, G) == ComboGate.PASS, "other flags do not count as REMAP IN GAMES");
        g.onKey(L1, false, 0, t + 2020, G);
        g.onKey(R1, false, 0, t + 2030, G);
        g.flags = 0;
        // other apps are fine
        g.onKey(L1, true, 0, t + 3000, X_APP);
        check(g.onKey(R1, true, 0, t + 3010, X_APP) == ComboGate.ACT, "an app that is not a game: fires");
        g.onKey(R1, false, 0, t + 3020, X_APP);
        g.onKey(L1, false, 0, t + 3030, X_APP);
        // Deck in front: the game list is ignored
        g.pass = new HashSet<>(Arrays.asList(D, G));
        g.onKey(L1, true, 0, t + 4000, D);
        check(g.onKey(R1, true, 0, t + 4010, D) == ComboGate.ACT, "Visor in front always fires");
        g.onKey(R1, false, 0, t + 4020, D);
        g.onKey(L1, false, 0, t + 4030, D);
        // fg unknown
        g.pass = Collections.emptySet();
        g.onKey(L1, true, 0, t + 5000, null);
        check(g.onKey(R1, true, 0, t + 5010, null) == ComboGate.PASS, "unknown foreground (null): no combo");
        g.onKey(R1, false, 0, t + 5020, null);
        g.onKey(L1, false, 0, t + 5030, null);
        g.onKey(L1, true, 0, t + 6000, "");
        check(g.onKey(R1, true, 0, t + 6010, "") == ComboGate.PASS, "unknown foreground (empty): no combo");
        check(g.onKey(R1, false, 0, t + 6020, "") == ComboGate.PASS, "and its up passes");
        g.onKey(L1, false, 0, t + 6030, "");
        g.pass = null;
        g.onKey(L1, true, 0, t + 7000, G);
        check(g.onKey(R1, true, 0, t + 7010, G) == ComboGate.ACT, "a null pass set counts as empty");
        // a first key that passed in a game can complete once the app changes
        ComboGate sw = gate(one(L1, R1, RemapAction.DECK_PS));
        sw.pass = games;
        sw.onKey(L1, true, 0, t, G);
        check(sw.onKey(R1, true, 0, t + 10, X_APP) == ComboGate.ACT, "the foreground at the second press decides");
    }

    static void gateOdd() {
        section("2.9 combos: gate odd input");
        long t = 90000;
        ComboGate g = gate(one(L1, R1, RemapAction.DECK_PS));
        check(g.onKey(RemapKey.INVALID, true, 0, t, G) == ComboGate.PASS && g.onKey(RemapKey.INVALID, false, 0, t, G) == ComboGate.PASS, "invalid key passes");
        check(g.onKey(0, true, 0, t, G) == ComboGate.PASS, "key zero passes");
        // a poisoned table: built by hand through parse can never hold a forbidden key; and the gate also checks
        ComboTable poisoned = ComboTable.parse("v1;kc:19+kc:20|deck_ps||;kc:110+kc:102|deck_ps||;kc:96+kc:97|home||");
        check(poisoned.size() == 0, "the table refuses forbidden keys");
        ComboGate p = gate(poisoned);
        p.onKey(19, true, 0, t, G);
        check(p.onKey(20, true, 0, t + 10, G) == ComboGate.PASS, "forbidden pair never fires");
        // forbidden key presses never get recorded or taken even next to a good table
        ComboGate q = gate(one(L1, R1, RemapAction.DECK_PS));
        check(q.onKey(23, true, 0, t, G) == ComboGate.PASS && q.onKey(23, true, 1, t + 1, G) == ComboGate.PASS && q.onKey(23, false, 0, t + 2, G) == ComboGate.PASS, "OK key passes whole");
        // eight held combo keys fill the slots; a ninth is left alone
        long[] ks = {L1, R1, L2, R2, X, Y, TL, TR, START, SELECT};
        ComboTable big = ComboTable.EMPTY;
        for (int i = 0; i + 1 < ks.length; i += 2) big = big.with(combo(ks[i], ks[i + 1], RemapAction.HOME));
        big = big.with(combo(L1, L2, RemapAction.BACK));
        ComboGate w = gate(big);
        boolean fine = true;
        for (long k : ks) { int r = w.onKey(k, true, 0, t, G); if (r != ComboGate.PASS && r != ComboGate.ACT) fine = false; }
        check(fine, "many keys at once never crash or return odd values");
        for (long k : ks) w.onKey(k, false, 0, t + 1000, G);
        check(w.onKey(L1, true, 0, t + 2000, G) == ComboGate.PASS, "after all ups the slots are free again");
        // act and arg are cleared on every call
        ComboGate a = gate(one(L1, R1, RemapAction.DECK_PS));
        a.onKey(L1, true, 0, t, G);
        a.onKey(R1, true, 0, t + 10, G);
        a.onKey(R1, true, 1, t + 20, G);
        check(a.act == RemapAction.DEFAULT && "".equals(a.arg), "act and arg are cleared when a later key swallows");
        check(a.self.equals(D), "self is kept");
        check(a.table == a.table && a.flags == 0 && a.pass.isEmpty(), "fresh gate defaults");
        // the key up of an unfollowed key never makes a held entry vanish
        ComboGate b = gate(one(L1, R1, RemapAction.DECK_PS));
        b.onKey(L1, true, 0, t, G);
        b.onKey(CH_UP, false, 0, t + 5, G);
        check(b.onKey(R1, true, 0, t + 10, G) == ComboGate.ACT, "another key's up does not clear the held first key");
        // time going backwards does not make the repeat guard stick
        ComboGate c = gate(one(L1, R1, RemapAction.DECK_PS));
        c.onKey(L1, true, 0, 100000, G);
        c.onKey(R1, true, 0, 100010, G);
        c.onKey(R1, false, 0, 100020, G);
        check(c.onKey(R1, true, 0, 5, G) == ComboGate.ACT, "a clock that went back does not block the combo");
    }

    // ================================================================== learn

    static void learn() {
        section("2.9 combos: make a combo");
        ComboLearn l = new ComboLearn();
        check(!l.finished() && l.state() == ComboLearn.WAIT_FIRST, "a new learner waits for the first key");
        check(l.onKey(L1, true, 0) == ComboLearn.WAIT_FIRST && l.a == 0, "keys before start() are ignored");
        l.start(1000);
        check(l.onKey(L1, true, 1100) == ComboLearn.WAIT_SECOND, "first key moves on");
        check(l.onKey(L1, true, 1150) == ComboLearn.WAIT_SECOND, "the same key again is ignored");
        check(l.onKey(R1, true, 1200) == ComboLearn.DONE, "second key finishes");
        check(l.a == L1 && l.b == R1 && l.finished(), "pair set");
        check(l.onKey(CH_UP, true, 1300) == ComboLearn.DONE && l.a == L1 && l.b == R1, "after DONE further keys are ignored");
        check(l.poll(100000) == ComboLearn.DONE, "after DONE a poll does not time out");
        // normalized
        l.start(2000);
        l.onKey(R1, true, 2010);
        check(l.onKey(L1, true, 2020) == ComboLearn.DONE && l.a == L1 && l.b == R1, "pair normalized so a < b");
        // restart clears
        l.start(3000);
        check(l.state() == ComboLearn.WAIT_FIRST && l.a == 0 && l.b == 0 && l.why.isEmpty() && !l.finished(), "start() clears the old result");
        // rejects
        check(l.onKey(19, true, 3010) == ComboLearn.REJECTED && "THAT BUTTON IS NEEDED TO MOVE AROUND".equals(l.why), "D-pad as first key rejected with a reason");
        check(l.finished(), "rejected is finished");
        check(l.onKey(L1, true, 3020) == ComboLearn.REJECTED, "rejected ignores further keys");
        l.start(4000);
        l.onKey(L1, true, 4010);
        check(l.onKey(110, true, 4020) == ComboLearn.REJECTED && "THE PS BUTTON AND HOME CANNOT BE USED".equals(l.why), "PS button as second key rejected");
        l.start(5000);
        check(l.onKey(82, true, 5010) == ComboLearn.REJECTED && "THAT BUTTON NEEDS ADVANCED REMAP".equals(l.why), "Menu rejected");
        l.start(5100);
        check(l.onKey(RemapKey.INVALID, true, 5110) == ComboLearn.REJECTED && "NOT A VALID BUTTON".equals(l.why), "invalid key rejected");
        l.start(5200);
        check(l.onKey(96, true, 5210) == ComboLearn.REJECTED && !l.why.isEmpty(), "A rejected");
        // first key released: back to waiting for the first
        l.start(6000);
        l.onKey(L1, true, 6010);
        check(l.onKey(L1, false, 6100) == ComboLearn.WAIT_FIRST, "first key released: back to the start");
        check(l.onKey(R1, true, 6200) == ComboLearn.WAIT_SECOND, "a new first key is taken");
        check(l.onKey(CH_UP, false, 6250) == ComboLearn.WAIT_SECOND, "an unrelated up is ignored");
        check(l.onKey(R1, false, 6300) == ComboLearn.WAIT_FIRST, "that first key released too");
        check(l.onKey(L1, true, 6400) == ComboLearn.WAIT_SECOND, "then another first key");
        check(l.onKey(TL, true, 6450) == ComboLearn.DONE && l.a == L1 && l.b == TL, "and a second finishes");
        // an up in the first state is ignored
        l.start(7000);
        check(l.onKey(L1, false, 7010) == ComboLearn.WAIT_FIRST, "an up while waiting for the first key is ignored");
        // timeout
        l.start(8000);
        check(l.poll(8000 + ComboLearn.TIMEOUT_MS) == ComboLearn.WAIT_FIRST, "exactly 8000 ms is not yet a timeout");
        check(l.poll(8000 + ComboLearn.TIMEOUT_MS + 1) == ComboLearn.TIMEOUT && l.finished(), "more than 8000 ms with nothing: timeout");
        check(l.onKey(L1, true, 20000) == ComboLearn.TIMEOUT, "after TIMEOUT keys are ignored");
        l.start(30000);
        l.onKey(L1, true, 30100);
        check(l.poll(30000 + 8001) == ComboLearn.TIMEOUT, "a first key without a second times out");
        l.start(40000);
        l.onKey(L1, true, 40100);
        check(l.onKey(R1, true, 49000) == ComboLearn.TIMEOUT && l.a == 0, "a second key that comes too late does not finish");
        l.start(50000);
        l.onKey(L1, true, 50100);
        check(l.onKey(R1, true, 50000 + 8000) == ComboLearn.DONE, "a second key at exactly 8000 ms still finishes");
        // reset
        l.start(60000);
        l.onKey(L1, true, 60010);
        l.reset();
        check(l.state() == ComboLearn.WAIT_FIRST && l.onKey(R1, true, 60020) == ComboLearn.WAIT_FIRST, "reset: keys are ignored until start()");
        check(l.poll(10000000) == ComboLearn.WAIT_FIRST, "reset: no timeout before start()");
        // what was learned can be saved
        l.start(70000);
        l.onKey(SCAN, true, 70010);
        l.onKey(START, true, 70020);
        check(l.state() == ComboLearn.DONE && ComboTable.canAdd(ComboTable.EMPTY, l.a, l.b), "a learned pair is accepted by canAdd");
        check(ComboTable.EMPTY.with(combo(l.a, l.b, RemapAction.DECK_PS)).find(SCAN, START) != null, "and can be stored");
    }
}
