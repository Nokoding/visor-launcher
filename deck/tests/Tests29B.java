package com.nokoding.deck;

import java.util.HashSet;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.9 group B tests: the tutorial lessons, the TRY IT helper and the saved progress. */
final class Tests29B {
    private Tests29B() {}

    private static final String[] IDS = {"welcome", "move", "wheel", "psmenu", "apps", "remap", "combos",
            "escape", "profiles", "touchpad", "look", "done"};

    static void run() {
        lessons();
        lessonText();
        tries();
        pressState();
        comboState();
        progressRoundTrip();
        progressHostile();
        progressMarks();
        progressOffer();
        progressResume();
    }

    private static String all(TutorialModel.Lesson l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l.lines) sb.append(s).append('\n');
        return sb.toString();
    }

    private static boolean has(String id, String needle) {
        return all(TutorialModel.byId(id)).contains(needle);
    }

    // ================================================================== lessons

    static void lessons() {
        section("2.9 tutorial lessons");
        check(TutorialModel.count() == 12, "12 lessons");
        boolean order = true;
        for (int i = 0; i < IDS.length; i++) {
            TutorialModel.Lesson l = TutorialModel.get(i);
            if (l == null || !IDS[i].equals(l.id)) order = false;
        }
        check(order, "lesson ids are in the agreed order");
        check(TutorialModel.get(-1) == null && TutorialModel.get(12) == null, "get out of range is null");
        check(TutorialModel.byId("nope") == null && TutorialModel.byId(null) == null, "byId unknown is null");
        check(TutorialModel.indexOf("combos") == 6 && TutorialModel.indexOf("x") == -1
                && TutorialModel.indexOf(null) == -1, "indexOf");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < TutorialModel.count(); i++) {
            TutorialModel.Lesson l = TutorialModel.get(i);
            check(ids.add(l.id), l.id + " id is unique");
            check(l.id.equals(l.id.toLowerCase(java.util.Locale.ROOT)) && !l.id.isEmpty(), l.id + " id is lowercase");
            check(TutorialModel.byId(l.id) == l, l.id + " byId finds it");
            check(TutorialModel.indexOf(l.id) == i, l.id + " indexOf");
            check(l.title != null && !l.title.isEmpty() && l.title.length() <= 30, l.id + " title length");
            check(l.lines.length >= 3 && l.lines.length <= 8, l.id + " has 3 to 8 lines");
            check(l.tryIt != null, l.id + " has a try object");
            check(TutorialModel.visible(l, true) && TutorialModel.visible(l, false), l.id + " is visible");
        }
        check(!TutorialModel.visible(null, true), "visible null is false");
        check(TutorialModel.byId("combos").gamepad && TutorialModel.byId("wheel").gamepad
                && TutorialModel.byId("psmenu").gamepad && TutorialModel.byId("touchpad").gamepad, "controller lessons are marked");
        check(!TutorialModel.byId("welcome").gamepad && !TutorialModel.byId("escape").gamepad, "plain lessons are not marked");
    }

    static void lessonText() {
        section("2.9 tutorial text");
        for (int i = 0; i < TutorialModel.count(); i++) {
            TutorialModel.Lesson l = TutorialModel.get(i);
            for (String s : l.lines) {
                check(s != null && !s.trim().isEmpty(), l.id + " line not empty");
                check(s.length() <= 70, l.id + " line fits 70: " + s);
                check(s.indexOf('\u2014') < 0 && s.indexOf('\u2013') < 0, l.id + " no em or en dash");
                check(s.indexOf(';') < 0, l.id + " no semicolon");
            }
            check(l.title.indexOf('\u2014') < 0 && l.title.indexOf('\u2013') < 0 && l.title.indexOf(';') < 0,
                    l.id + " title has no dash or semicolon");
        }
        check(has("combos", "COMBOS"), "combos mentions COMBOS");
        check(has("combos", "REMAP IN GAMES"), "combos mentions REMAP IN GAMES");
        check(has("escape", "6 seconds"), "escape mentions 6 seconds");
        check(has("escape", "RESET ALL REMAPS"), "escape mentions RESET ALL REMAPS");
        check(has("escape", "4 seconds") && has("escape", "2 seconds"), "escape mentions 4 and 2 seconds");
        check(has("escape", "restore_display") && has("escape", "wm size reset"), "escape has the display way out");
        check(has("escape", "Do not try"), "escape says not to try them now");
        check(has("done", "open_tutorial"), "done mentions open_tutorial");
        check(has("done", "SYSTEM, TUTORIAL"), "done says where to find it");
        check(has("welcome", "Nothing here has run on the TV"), "welcome says nothing has run on the TV");
        check(has("welcome", "EXIT") && has("welcome", "Back"), "welcome says EXIT or Back leaves");
        check(has("psmenu", "overlay service"), "psmenu mentions the overlay service");
        check(has("apps", "experimental"), "apps says force stop is experimental");
        check(has("profiles", "does not help memory"), "profiles is honest about memory");
        check(has("profiles", "RESTORE DISPLAY"), "profiles mentions RESTORE DISPLAY");
        check(has("remap", "LEARN A BUTTON"), "remap mentions LEARN A BUTTON");
        check(has("touchpad", "D-pad"), "touchpad has the D-pad fallback");
        check(has("look", "REDUCE EFFECTS"), "look mentions REDUCE EFFECTS");
    }

    static void tries() {
        section("2.9 tutorial try steps");
        for (int i = 0; i < TutorialModel.count(); i++) {
            TutorialModel.Lesson l = TutorialModel.get(i);
            TutorialModel.Try t = l.tryIt;
            check(t.kind == TutorialModel.PRESS || t.kind == TutorialModel.COMBO || t.kind == TutorialModel.NONE,
                    l.id + " try kind is known");
            if (t.kind == TutorialModel.NONE) continue;
            check(t.prompt.length() > 0 && t.prompt.length() <= 60, l.id + " prompt length");
            check(t.done.length() > 0 && t.done.length() <= 60, l.id + " done length");
            check(t.prompt.equals(t.prompt.toUpperCase(java.util.Locale.ROOT)), l.id + " prompt in capitals");
            check(t.done.equals(t.done.toUpperCase(java.util.Locale.ROOT)), l.id + " done in capitals");
            check(t.prompt.indexOf('\u2014') < 0 && t.done.indexOf('\u2014') < 0, l.id + " try has no em dash");
            check(t.keys.length > 0, l.id + " try has keys");
            if (t.kind == TutorialModel.COMBO) check(t.keys.length == 2, l.id + " combo has two keys");
        }
        check(TutorialModel.byId("escape").tryIt.kind == TutorialModel.NONE, "escape has no try step");
        TutorialModel.Try c = TutorialModel.byId("combos").tryIt;
        check(c.kind == TutorialModel.COMBO && c.keys.length == 2 && c.keys[0] == 99 && c.keys[1] == 100, "combos try is X and Y (not the page keys)");
        check("HOLD X THEN PRESS Y".equals(c.prompt), "combos prompt");
        TutorialModel.Try m = TutorialModel.byId("move").tryIt;
        check(m.kind == TutorialModel.PRESS && TutorialModel.checkPress(m, 23) && TutorialModel.checkPress(m, 66)
                && TutorialModel.checkPress(m, 96) && !TutorialModel.checkPress(m, 97), "move try is OK, Enter or A");
        TutorialModel.Try w = TutorialModel.byId("wheel").tryIt;
        check(TutorialModel.checkPress(w, 102) && TutorialModel.checkPress(w, 103) && !TutorialModel.checkPress(w, 104), "wheel try is L1 or R1");
        TutorialModel.Try p = TutorialModel.byId("psmenu").tryIt;
        check(TutorialModel.checkPress(p, 110) && !TutorialModel.checkPress(p, 96), "psmenu try is the PS button");
        check(!TutorialModel.checkPress(c, 102), "checkPress ignores combo steps");
        check(!TutorialModel.checkPress(null, 23), "checkPress null step");
        int tried = 0;
        for (int i = 0; i < TutorialModel.count(); i++) if (TutorialModel.get(i).tryIt.kind != TutorialModel.NONE) tried++;
        check(tried == 4, "four lessons have a try step");
    }

    // ================================================================== TryState

    static void pressState() {
        section("2.9 tutorial try state, press");
        TutorialModel.Try t = TutorialModel.byId("move").tryIt;
        for (int k : t.keys) {
            TutorialModel.TryState s = new TutorialModel.TryState(t);
            check(!s.done(), "not done at start");
            check(!s.onKey(k, false), "an up does nothing");
            check(!s.done(), "still not done after an up");
            check(s.onKey(k, true), "key " + k + " completes");
            check(s.done(), "done after key " + k);
            check(!s.onKey(k, true), "a second press does not complete again");
            check(s.done(), "done stays true");
        }
        TutorialModel.TryState s = new TutorialModel.TryState(t);
        check(!s.onKey(97, true) && !s.onKey(19, true) && !s.onKey(0, true) && !s.onKey(-5, true), "wrong keys do nothing");
        check(!s.done(), "wrong keys leave it open");
        check(s.onKey(66, true), "then a right key completes");
        s.reset();
        check(!s.done(), "reset clears done");
        check(s.onKey(23, true), "completes again after reset");
        TutorialModel.TryState n = new TutorialModel.TryState(TutorialModel.byId("escape").tryIt);
        check(!n.onKey(4, true) && !n.onKey(23, true) && !n.done(), "NONE never completes");
        TutorialModel.TryState nul = new TutorialModel.TryState(null);
        check(!nul.onKey(23, true) && !nul.done(), "null step never completes");
    }

    static void comboState() {
        section("2.9 tutorial try state, combo");
        TutorialModel.Try t = new TutorialModel.Try(TutorialModel.COMBO, new int[]{102, 103}, "A", "B");   // the logic is the same for any two keys
        TutorialModel.TryState s = new TutorialModel.TryState(t);
        check(!s.onKey(102, true), "L1 alone does not complete");
        check(!s.done(), "not done with one key");
        check(s.onKey(103, true), "L1 held then R1 completes");
        check(s.done(), "done");
        check(!s.onKey(103, false) && !s.onKey(102, false), "releasing does not complete again");
        check(s.done(), "done stays true after release");
        s.reset();
        check(!s.done(), "reset clears done");
        check(!s.onKey(103, true), "R1 alone does not complete");
        check(s.onKey(102, true), "R1 held then L1 completes (other order)");
        s.reset();
        check(!s.onKey(102, true), "L1 down");
        check(!s.onKey(102, false), "L1 up");
        check(!s.onKey(103, true), "R1 pressed after L1 was released does not complete");
        check(!s.done(), "released first key does not count");
        check(!s.onKey(103, false), "R1 up");
        check(!s.onKey(104, true) && !s.onKey(97, true), "other keys are ignored");
        check(!s.onKey(102, true), "a fresh L1 hold");
        check(s.onKey(103, true), "a fresh hold afterwards still completes");
        TutorialModel.TryState s2 = new TutorialModel.TryState(t);
        check(!s2.onKey(102, true) && !s2.onKey(104, true), "an unrelated key between does nothing");
        check(s2.onKey(103, true), "still completes with the first key held");
        TutorialModel.TryState bad = new TutorialModel.TryState(new TutorialModel.Try(TutorialModel.COMBO, new int[]{102}, "X", "Y"));
        check(!bad.onKey(102, true) && !bad.done(), "a combo with one key never completes");
    }

    // ================================================================== progress

    static void progressRoundTrip() {
        section("2.9 tutorial progress");
        TutorialProgress p = TutorialProgress.parse(null);
        check("tutorial".equals(TutorialProgress.PREF), "pref name");
        check(p.doneCount() == 0 && !p.allSeen() && !p.dismissed(), "empty start");
        p.mark("move");
        p.mark("welcome");
        p.mark("look");
        p.dismiss();
        String s = p.save();
        check(s.startsWith("v1|"), "save starts with the version");
        check("v1|0|1|welcome,move,look".equals(s), "save is in lesson order: " + s);
        TutorialProgress q = TutorialProgress.parse(s);
        check(q.seen("welcome") && q.seen("move") && q.seen("look") && !q.seen("done"), "round trip seen");
        check(q.doneCount() == 3 && q.dismissed() && q.save().equals(s), "round trip state and string");
        p.finish();
        TutorialProgress r = TutorialProgress.parse(p.save());
        check(!r.shouldOffer() && r.save().equals(p.save()), "finished state round trips");
        TutorialProgress all = TutorialProgress.parse(null);
        for (int i = 0; i < TutorialModel.count(); i++) all.mark(TutorialModel.get(i).id);
        check(TutorialProgress.parse(all.save()).allSeen(), "all seen round trips");
    }

    static void progressHostile() {
        section("2.9 tutorial progress, hostile input");
        StringBuilder big = new StringBuilder("v1|0|0|");
        for (int i = 0; i < 20000; i++) big.append("welcome,");
        StringBuilder hundredK = new StringBuilder();
        for (int i = 0; i < 100000; i++) hundredK.append('a');
        String[] bad = {null, "", " ", "garbage", "v1", "v1|", "v1|0|0", "v1|0|0|a|b", "v2|1|0|welcome", "v1|x|0|welcome",
                "v1|-1|0|welcome", "v1|1|2|welcome", "v1||0|welcome", "v1|99999999999|0|welcome", "|||", "||||||",
                "{\"a\":1}", hundredK.toString(), big.toString(),
                "V1|1|0|welcome", "\uFFFF\uD800"};
        for (String b : bad) {
            try {
                TutorialProgress p = TutorialProgress.parse(b);
                String label = b == null ? "null" : (b.length() > 20 ? b.substring(0, 20) : b);
                check(p != null, "parse gives an object for " + label);
                check(p.doneCount() == 0 && !p.allSeen(), "nothing seen for " + label);
                check(p.shouldOffer() && !p.dismissed(), "first run state for " + label);
                check(p.save().startsWith("v1|"), "save works after " + label);
            } catch (Throwable t) {
                check(false, "parse threw " + t);
            }
        }
        TutorialProgress mixed = TutorialProgress.parse("v1|0|0|welcome,welcome,nope,,move,MOVE, done ,done");
        check(mixed.doneCount() == 3 && mixed.seen("welcome") && mixed.seen("move") && mixed.seen("done"),
                "unknown ids dropped and duplicates merged");
        check("v1|0|0|welcome,move,done".equals(mixed.save()), "clean save after hostile ids");
        TutorialProgress junk = TutorialProgress.parse("v1|1|0|welcome\n");
        check(junk.doneCount() == 0 && !junk.shouldOffer(), "an id with junk after it is dropped, the rest is kept");
        TutorialProgress ctl = TutorialProgress.parse("v1|0|0|\u0000\u0001");
        check(ctl.doneCount() == 0 && ctl.shouldOffer(), "control characters give nothing seen");
        TutorialProgress done = TutorialProgress.parse("v1|1|1|");
        check(done.dismissed() && !done.shouldOffer() && done.doneCount() == 0, "empty id list is fine");
        TutorialProgress newer = TutorialProgress.parse("v1|7|0|welcome");
        check(!newer.shouldOffer(), "a finished version above this one does not offer");
    }

    static void progressMarks() {
        section("2.9 tutorial progress, mark and reset");
        TutorialProgress p = TutorialProgress.parse("");
        check(!p.seen("welcome") && !p.seen(null) && !p.seen(""), "nothing seen");
        p.mark("welcome");
        p.mark("welcome");
        check(p.seen("welcome") && p.doneCount() == 1, "mark once");
        p.mark("nope");
        p.mark(null);
        check(p.doneCount() == 1 && !p.seen("nope"), "unknown ids are ignored");
        check(!p.allSeen(), "not all seen");
        for (int i = 0; i < TutorialModel.count(); i++) p.mark(TutorialModel.get(i).id);
        check(p.allSeen() && p.doneCount() == 12, "all seen after marking every lesson");
        p.finish();
        p.reset();
        check(p.doneCount() == 0 && !p.allSeen() && !p.seen("welcome"), "reset forgets the lessons");
        check(!p.shouldOffer(), "reset keeps the finished state");
    }

    static void progressOffer() {
        section("2.9 tutorial progress, first run rule");
        check(TutorialProgress.CONTENT_VERSION == 1, "content version is 1");
        TutorialProgress p = TutorialProgress.parse(null);
        check(p.shouldOffer(), "offered on a fresh start");
        p.mark("welcome");
        check(p.shouldOffer(), "seeing a lesson does not stop the offer");
        p.finish();
        check(!p.shouldOffer(), "not offered after finish");
        TutorialProgress d = TutorialProgress.parse(null);
        d.mark("welcome");
        d.mark("move");
        d.dismiss();
        check(d.dismissed() && !d.shouldOffer(), "not offered after dismiss");
        check(d.doneCount() == 2 && !d.allSeen(), "dismiss leaves what was seen");
        TutorialProgress d2 = TutorialProgress.parse(d.save());
        check(d2.dismissed() && !d2.shouldOffer() && d2.doneCount() == 2, "dismiss survives save and load");
        TutorialProgress full = TutorialProgress.parse(null);
        for (int i = 0; i < TutorialModel.count(); i++) full.mark(TutorialModel.get(i).id);
        full.dismiss();
        check(full.allSeen(), "dismiss keeps allSeen");
        TutorialProgress old = TutorialProgress.parse("v1|0|0|welcome,move");
        check(old.shouldOffer(), "finished version 0 is below the content version");
    }

    static void progressResume() {
        section("2.9 tutorial progress, resume");
        TutorialProgress p = TutorialProgress.parse(null);
        check(p.resumeIndex() == 0, "none seen resumes at 0");
        p.mark("welcome");
        check(p.resumeIndex() == 1, "welcome seen resumes at 1");
        p.mark("move");
        p.mark("wheel");
        check(p.resumeIndex() == 3, "three seen resumes at 3");
        p.mark("look");
        check(p.resumeIndex() == 3, "a later lesson seen does not move the first gap");
        for (int i = 0; i < TutorialModel.count(); i++) p.mark(TutorialModel.get(i).id);
        check(p.resumeIndex() == 0, "all seen resumes at 0");
        TutorialProgress g = TutorialProgress.parse(null);
        g.mark("move");
        check(g.resumeIndex() == 0, "first lesson missing resumes at 0");
        for (int i = 0; i < 100; i++) {
            TutorialProgress h = TutorialProgress.parse(null);
            h.mark(TutorialModel.get(i % 12).id);
            int r = h.resumeIndex();
            check(r >= 0 && r < TutorialModel.count(), "resume index in range");
        }
    }
}
