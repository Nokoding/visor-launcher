package com.nokoding.deck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.8 group C tests: control center model, prefs, layout and the touchpad volume gesture. */
final class Tests28C {
    private Tests28C() {}

    static void run() {
        model();
        prefs();
        prefsHostile();
        layoutPlacement();
        layoutMove();
        layoutScroll();
        touchVertical();
        touchSensitivity();
        touchDeadzoneAndAccumulator();
        touchGuards();
        touchKeys();
    }

    // ================================================================== model

    static void model() {
        section("2.8 control center model");
        Set<String> ids = new HashSet<>();
        int total = 0;
        String prefixes = "qadcsy";
        for (int s = 0; s < CcModel.COUNT; s++) {
            List<CcModel.Tile> l = CcModel.defaults(s);
            check(!l.isEmpty(), "section " + CcModel.SECTION_NAMES[s] + " has tiles");
            for (CcModel.Tile t : l) {
                total++;
                check(ids.add(t.id), "id is unique: " + t.id);
                check(t.id.matches("[a-z]\\.[a-z]+"), "id has the section.name form: " + t.id);
                check(t.id.charAt(0) == prefixes.charAt(s), "id prefix matches its section: " + t.id);
                check(t.section == s, "tile knows its section: " + t.id);
                check(t.span == 1 || t.span == 2, "span is 1 or 2: " + t.id);
                if (t.kind == CcModel.HEADER || t.kind == CcModel.SLIDER || t.kind == CcModel.TILE) {
                    check(t.span == 2, "header, slider and sub page tiles are full width: " + t.id);
                }
                check(t.kind >= CcModel.BUTTON && t.kind <= CcModel.HEADER, "kind is known: " + t.id);
                check(t.label != null && !t.label.isEmpty(), "label present: " + t.id);
                check(t.help != null && t.help.length() >= 10 && t.help.length() <= 90, "help is 10..90 characters: " + t.id);
                check(t.help != null && t.help.indexOf((char) 8212) < 0 && t.help.indexOf((char) 8211) < 0, "help has no dash character: " + t.id);
                check(CcModel.byId(t.id) == t, "byId round trip: " + t.id);
                check(CcModel.valid(t.id), "valid: " + t.id);
            }
        }
        check(total == CcModel.count(), "count() is the number of tiles");
        check(CcModel.SECTION_NAMES.length == CcModel.COUNT, "one name per section");
        check(CcModel.byId("nope") == null && CcModel.byId(null) == null && CcModel.byId("") == null, "unknown ids give null");
        check(!CcModel.valid("q.nope") && !CcModel.valid(null), "unknown ids are not valid");
        check(CcModel.defaults(-1).isEmpty() && CcModel.defaults(CcModel.COUNT).isEmpty(), "bad section gives an empty list");
        check(CcModel.defaults(CcModel.QUICK).get(0).id.equals("q.volume"), "QUICK starts with the volume slider");
        check(CcModel.defaults(CcModel.SYSTEM).get(0).id.equals("y.setup"), "SYSTEM starts with Setup");
        check(CcModel.byId("y.setup").span == 2 && CcModel.byId("a.closeall").span == 2, "Setup and close all are full width");
        check(CcModel.byId("q.mute").kind == CcModel.TOGGLE && CcModel.byId("q.shot").needsService, "mute is a toggle, screenshot needs the service");
        check(CcModel.byId("q.sleep").needsService && CcModel.byId("q.home").needsService && CcModel.byId("q.back").needsService
                && CcModel.byId("q.recents").needsService && CcModel.byId("y.power").needsService, "global action tiles need the service");
        check(!CcModel.byId("q.deck").needsService && !CcModel.byId("q.mute").needsService, "plain tiles do not need the service");
        boolean threw = false;
        try { CcModel.defaults(CcModel.QUICK).remove(0); } catch (UnsupportedOperationException e) { threw = true; }
        check(threw, "defaults() list cannot be changed");
        threw = false;
        try { CcModel.defaults(CcModel.QUICK).add(null); } catch (UnsupportedOperationException e) { threw = true; }
        check(threw, "defaults() list cannot grow");
    }

    // ================================================================== prefs

    static List<String> ids(List<CcModel.Tile> l) {
        List<String> o = new ArrayList<>();
        for (CcModel.Tile t : l) o.add(t.id);
        return o;
    }

    static void prefs() {
        section("2.8 control center prefs");
        CcPrefs p = CcPrefs.defaults();
        check(p.isDefault() && p.save().isEmpty(), "defaults save as an empty string");
        for (int s = 0; s < CcModel.COUNT; s++) {
            check(ids(p.order(s)).equals(ids(CcModel.defaults(s))), "default order of " + CcModel.SECTION_NAMES[s]);
            check(ids(p.visible(s)).equals(ids(CcModel.defaults(s))), "default visible of " + CcModel.SECTION_NAMES[s]);
        }
        check(CcPrefs.parse("").isDefault() && CcPrefs.parse(null).isDefault(), "empty and null parse as defaults");

        // hide and unhide
        check(p.setHidden("q.next", true) && p.hidden("q.next"), "q.next hidden");
        check(!p.isDefault() && !p.save().isEmpty(), "hiding makes the prefs non default");
        check(!ids(p.visible(CcModel.QUICK)).contains("q.next") && ids(p.order(CcModel.QUICK)).contains("q.next"), "hidden tile leaves visible() but stays in order()");
        check(p.setHidden("q.next", false) && !p.hidden("q.next") && p.isDefault() && p.save().isEmpty(), "unhide goes back to default");

        // never hidden
        check(!p.setHidden("y.setup", true) && !p.hidden("y.setup"), "y.setup can not be hidden");
        for (int s = 0; s < CcModel.COUNT; s++) {
            for (CcModel.Tile t : CcModel.defaults(s)) {
                if (t.kind == CcModel.HEADER) check(!p.setHidden(t.id, true) && !p.hidden(t.id), "header can not be hidden: " + t.id);
            }
        }
        check(!p.setHidden("zzz", true) && !p.setHidden(null, true) && !p.hidden(null) && !p.hidden("zzz"), "unknown ids are ignored");
        check(p.isDefault(), "ignored hides change nothing");
        check(CcPrefs.parse("v1||y.setup,a.header,c.battery,d.ram,y.launcher").isDefault(), "saved hides of protected tiles are dropped on load");

        // move
        check(!p.moveUp("q.volume"), "first tile can not move up");
        check(!p.moveDown("q.exitgame"), "last tile can not move down");
        check(!p.moveUp("zzz") && !p.moveDown(null), "unknown id can not move");
        check(p.moveDown("q.volume"), "volume moves down");
        List<String> q = ids(p.order(CcModel.QUICK));
        check(q.get(0).equals("q.mute") && q.get(1).equals("q.volume"), "volume and mute swapped");
        check(p.moveUp("q.volume") && p.isDefault(), "move up undoes move down");
        check(p.moveDown("q.volume") && p.moveDown("q.volume"), "move twice");
        q = ids(p.order(CcModel.QUICK));
        check(q.get(2).equals("q.volume") && q.get(0).equals("q.mute") && q.get(1).equals("q.playpause"), "volume moved two places");
        for (int s = 0; s < CcModel.COUNT; s++) {
            if (s != CcModel.QUICK) check(ids(p.order(s)).equals(ids(CcModel.defaults(s))), "other sections untouched: " + CcModel.SECTION_NAMES[s]);
        }
        // moving a hidden tile works too, and moves among all tiles including hidden ones
        p.reset();
        p.setHidden("q.mute", true);
        check(p.moveUp("q.mute"), "a hidden tile can be moved");
        check(ids(p.order(CcModel.QUICK)).get(0).equals("q.mute"), "hidden tile moved among all tiles");
        check(ids(p.visible(CcModel.QUICK)).get(0).equals("q.volume"), "visible skips the hidden tile");
        // last tile of a section moves up within the section
        p.reset();
        check(p.moveUp("a.closeall") && ids(p.order(CcModel.APPS)).get(0).equals("a.closeall"), "tiles move inside their own section");
        check(!p.moveDown("a.header"), "a.header is now last in APPS");

        // round trip
        p.reset();
        p.moveDown("q.volume");
        p.setHidden("q.next", true);
        p.setHidden("s.ui", true);
        p.moveUp("y.restart");
        p.moveDown("c.gyro");
        String saved = p.save();
        check(saved.startsWith("v1|") && saved.length() <= CcPrefs.MAX_LEN, "saved string is versioned and short: " + saved);
        check(saved.chars().allMatch(c -> c >= 32 && c < 127), "saved string is plain ASCII");
        CcPrefs r = CcPrefs.parse(saved);
        check(r.save().equals(saved), "save/parse/save is stable");
        for (int s = 0; s < CcModel.COUNT; s++) {
            check(ids(r.order(s)).equals(ids(p.order(s))), "round trip order of " + CcModel.SECTION_NAMES[s]);
            check(ids(r.visible(s)).equals(ids(p.visible(s))), "round trip visible of " + CcModel.SECTION_NAMES[s]);
        }
        check(r.hidden("q.next") && r.hidden("s.ui") && !r.hidden("q.mute"), "round trip hidden");

        // everything moved and hidden still fits
        CcPrefs big = CcPrefs.defaults();
        for (int s = 0; s < CcModel.COUNT; s++) {
            List<CcModel.Tile> d = CcModel.defaults(s);
            for (int i = d.size() - 1; i >= 0; i--) big.moveUp(d.get(i).id);
            for (CcModel.Tile t : d) big.setHidden(t.id, true);
        }
        check(big.save().length() <= CcPrefs.MAX_LEN, "the longest possible string fits in the limit: " + big.save().length());
        check(CcPrefs.parse(big.save()).save().equals(big.save()), "the longest string round trips");

        // reset
        p.reset();
        check(p.isDefault() && p.save().isEmpty() && !p.hidden("q.next"), "reset goes back to default");

        // the QUICK and SYSTEM rule
        CcPrefs h = CcPrefs.defaults();
        for (CcModel.Tile t : CcModel.defaults(CcModel.QUICK)) h.setHidden(t.id, true);
        check(ids(h.visible(CcModel.QUICK)).equals(java.util.Collections.singletonList("q.volume")), "all QUICK hidden still shows the first tile");
        h.moveDown("q.volume");
        check(ids(h.visible(CcModel.QUICK)).equals(java.util.Collections.singletonList("q.volume")), "the default first tile is shown even after a move");
        for (CcModel.Tile t : CcModel.defaults(CcModel.SYSTEM)) h.setHidden(t.id, true);
        check(!h.visible(CcModel.SYSTEM).isEmpty() && h.visible(CcModel.SYSTEM).get(0).id.equals("y.setup"), "SYSTEM always shows Setup");
        // other sections: hiding every hideable tile leaves only the headers
        for (CcModel.Tile t : CcModel.defaults(CcModel.SOUND)) h.setHidden(t.id, true);
        check(h.visible(CcModel.SOUND).isEmpty(), "a section with all tiles hidden is empty (no header in SOUND)");
        for (CcModel.Tile t : CcModel.defaults(CcModel.APPS)) h.setHidden(t.id, true);
        check(ids(h.visible(CcModel.APPS)).equals(java.util.Collections.singletonList("a.header")), "APPS keeps its header");
        check(h.visible(-1).isEmpty() && h.order(99).isEmpty(), "bad section gives empty lists");
        // lists are copies
        h.order(CcModel.QUICK).clear();
        check(h.order(CcModel.QUICK).size() == CcModel.defaults(CcModel.QUICK).size(), "order() returns a copy");
    }

    static void prefsHostile() {
        section("2.8 control center prefs, bad input");
        String[] bad = {
            "v2|q.mute,q.volume|q.next", "v0||", "q.mute,q.volume", "v1", "v1|", "v1|a|b|c", "|||", "v1|||", "V1||", " v1||",
            "v1|\u0000|\u0000", "v1|q.méte|", "\n", "v1|,,,,|,,,", "v1|q.mute,q.mute,q.mute|q.next,q.next", "v1|" + repeat("q.mute,", 100) + "|",
            "garbage", "v1|zzz.zzz|yyy", "‮", "v1|q.volume:1,q.mute:0|"
        };
        for (String s : bad) {
            CcPrefs p = null;
            boolean threw = false;
            try { p = CcPrefs.parse(s); } catch (RuntimeException e) { threw = true; }
            check(!threw && p != null, "no exception for " + show(s));
            if (p == null) continue;
            check(p.save().length() <= CcPrefs.MAX_LEN, "saved form is short for " + show(s));
            for (int sec = 0; sec < CcModel.COUNT; sec++) {
                Set<String> got = new HashSet<>(ids(p.order(sec)));
                Set<String> want = new HashSet<>(ids(CcModel.defaults(sec)));
                check(got.equals(want) && p.order(sec).size() == want.size(), "every tile once in " + CcModel.SECTION_NAMES[sec] + " for " + show(s));
            }
        }
        check(CcPrefs.parse("v2|q.mute,q.volume|q.next").isDefault(), "unknown version parses as defaults");
        check(CcPrefs.parse("v1|q.mute,q.mute,q.volume|").order(CcModel.QUICK).get(1).id.equals("q.volume"), "duplicate ids count once");
        check(CcPrefs.parse("v1|zzz,q.mute|").order(CcModel.QUICK).get(0).id.equals("q.mute"), "unknown ids are skipped, known ones kept");
        check(ids(CcPrefs.parse("v1|q.mute|").order(CcModel.QUICK)).get(0).equals("q.mute"), "a partial order keeps its ids first and fills the rest");
        check(CcPrefs.parse("v1|q.mute|").order(CcModel.QUICK).size() == CcModel.defaults(CcModel.QUICK).size(), "a partial order is completed");
        // an id from another section can not move a tile
        CcPrefs x = CcPrefs.parse("v1|s.ui,q.mute,y.android,s.volume|");
        check(ids(x.order(CcModel.SOUND)).get(0).equals("s.ui") && ids(x.order(CcModel.SOUND)).get(1).equals("s.volume"), "ids are read in their own section");
        check(x.order(CcModel.QUICK).get(0).id.equals("q.mute"), "QUICK order from a mixed string");
        check(ids(x.order(CcModel.QUICK)).size() == CcModel.defaults(CcModel.QUICK).size(), "no tile jumped into QUICK");
        // 100 KB
        StringBuilder sb = new StringBuilder("v1|");
        while (sb.length() < 100 * 1024) sb.append("q.mute,q.volume,");
        check(CcPrefs.parse(sb.toString()).isDefault(), "a 100 KB string parses as defaults");
        check(CcPrefs.parse(repeat("x", 100 * 1024)).isDefault(), "100 KB of junk parses as defaults");
        String justOver = "v1|" + repeat("q.mute,", 150) + "|";
        check(justOver.length() > CcPrefs.MAX_LEN && CcPrefs.parse(justOver).isDefault(), "a string over the limit parses as defaults");
    }

    static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    static String show(String s) {
        String t = s.length() > 30 ? s.substring(0, 30) + "..." : s;
        StringBuilder b = new StringBuilder();
        for (char c : t.toCharArray()) b.append(c >= 32 && c < 127 ? String.valueOf(c) : "?");
        return "\"" + b + "\"";
    }

    // ================================================================== layout

    static boolean[] all(int n) {
        boolean[] f = new boolean[n];
        java.util.Arrays.fill(f, true);
        return f;
    }

    static void layoutPlacement() {
        section("2.8 control center layout: rows and columns");
        int[] s = {1, 1, 1, 2, 1, 1};
        check(java.util.Arrays.equals(CcLayout.rowOf(s), new int[]{0, 0, 1, 2, 3, 3}), "rows of 1,1,1,2,1,1");
        check(java.util.Arrays.equals(CcLayout.colOf(s), new int[]{0, 1, 0, 0, 0, 1}), "columns of 1,1,1,2,1,1");
        check(CcLayout.rowCount(s) == 4, "four rows");
        int[] t = {2, 2, 2};
        check(java.util.Arrays.equals(CcLayout.rowOf(t), new int[]{0, 1, 2}) && CcLayout.rowCount(t) == 3, "full width rows");
        check(java.util.Arrays.equals(CcLayout.colOf(t), new int[]{0, 0, 0}), "full width items are column 0");
        int[] u = {1, 2, 1};
        check(java.util.Arrays.equals(CcLayout.rowOf(u), new int[]{0, 1, 2}) && java.util.Arrays.equals(CcLayout.colOf(u), new int[]{0, 0, 0}), "a lone half width item before a full width one leaves column 1 empty");
        check(CcLayout.rowCount(u) == 3, "three rows");
        int[] v = {1, 1, 1, 1};
        check(CcLayout.rowCount(v) == 2, "four halves make two rows");
        check(CcLayout.rowCount(new int[]{1}) == 1 && CcLayout.rowCount(new int[]{1, 1, 1}) == 2, "odd count rounds up");
        check(CcLayout.rowCount(new int[0]) == 0 && CcLayout.rowCount(null) == 0, "empty page has no rows");
        check(CcLayout.rowOf(new int[0]).length == 0 && CcLayout.colOf(null).length == 0, "empty arrays");
        check(CcLayout.rowOf(new int[]{0, 7, -3}).length == 3, "odd span values are read as half width");
        check(java.util.Arrays.equals(CcLayout.colOf(new int[]{0, 7, -3}), new int[]{0, 1, 0}), "odd span values act as span 1");
    }

    static void layoutMove() {
        section("2.8 control center layout: focus movement");
        int[] s = {1, 1, 1, 2, 1, 1};
        boolean[] f = all(6);
        check(CcLayout.move(s, f, 0, 0, 1) == 2, "down from r0c0 goes to the item below");
        check(CcLayout.move(s, f, 1, 0, 1) == 2, "down from r0c1 goes to the lone item in the next row");
        check(CcLayout.move(s, f, 2, 0, 1) == 3, "down into a full width item");
        check(CcLayout.move(s, f, 3, 0, 1) == 4, "down from a full width item lands in column 0");
        check(CcLayout.move(s, f, 4, 0, 1) == 4 && CcLayout.move(s, f, 5, 0, 1) == 5, "down from the last row stays");
        check(CcLayout.move(s, f, 5, 0, -1) == 3 && CcLayout.move(s, f, 4, 0, -1) == 3, "up into a full width item from either column");
        check(CcLayout.move(s, f, 3, 0, -1) == 2, "up from a full width item");
        check(CcLayout.move(s, f, 2, 0, -1) == 0, "up lands in the same column");
        check(CcLayout.move(s, f, 0, 0, -1) == CcLayout.STRIP && CcLayout.move(s, f, 1, 0, -1) == CcLayout.STRIP, "up from the first row goes to the strip");
        check(CcLayout.move(s, f, 0, 1, 0) == 1 && CcLayout.move(s, f, 1, -1, 0) == 0, "left and right inside a row");
        check(CcLayout.move(s, f, 1, 1, 0) == 1 && CcLayout.move(s, f, 0, -1, 0) == 0, "left and right at the edge stay");
        check(CcLayout.move(s, f, 2, 1, 0) == 2 && CcLayout.move(s, f, 2, -1, 0) == 2, "a lone item has no neighbour");
        check(CcLayout.move(s, f, 3, 1, 0) == 3 && CcLayout.move(s, f, 3, -1, 0) == 3, "a full width item has no left or right neighbour");
        check(CcLayout.move(s, f, 4, 1, 0) == 5 && CcLayout.move(s, f, 5, -1, 0) == 4, "left and right in the last row");
        check(CcLayout.move(s, f, 4, -1, 0) == 4 && CcLayout.move(s, f, 5, 1, 0) == 5, "no wrap to another row");
        check(CcLayout.move(s, f, 0, 0, 0) == 0, "no direction stays");
        check(CcLayout.move(s, f, CcLayout.STRIP, 0, 1) == 0, "down from the strip goes to the first item");
        check(CcLayout.move(s, f, CcLayout.STRIP, 0, -1) == CcLayout.STRIP && CcLayout.move(s, f, CcLayout.STRIP, 1, 0) == CcLayout.STRIP, "other moves from the strip stay on it");
        check(CcLayout.move(s, f, 99, 0, 1) == 99, "a bad index is returned unchanged");
        check(CcLayout.EDGE != CcLayout.STRIP && CcLayout.EDGE < 0, "EDGE is its own negative marker");

        // a header at the top is skipped
        int[] h = {2, 1, 1, 2, 1};
        boolean[] hf = {false, true, true, true, true};
        check(CcLayout.move(h, hf, 1, 0, -1) == CcLayout.STRIP && CcLayout.move(h, hf, 2, 0, -1) == CcLayout.STRIP, "the header above is skipped, up goes to the strip");
        check(CcLayout.move(h, hf, CcLayout.STRIP, 0, 1) == 1, "down from the strip skips the header");
        check(CcLayout.move(h, hf, 3, 0, -1) == 1, "up from row 2 goes to row 1");
        check(CcLayout.firstFocusable(hf) == 1, "first focusable skips the header");
        // a header in the middle
        int[] m = {1, 1, 2, 1, 1};
        boolean[] mf = {true, true, false, true, true};
        check(CcLayout.move(m, mf, 0, 0, 1) == 3 && CcLayout.move(m, mf, 1, 0, 1) == 4, "down jumps over a middle header keeping the column");
        check(CcLayout.move(m, mf, 3, 0, -1) == 0 && CcLayout.move(m, mf, 4, 0, -1) == 1, "up jumps over a middle header keeping the column");
        // a header at the end
        int[] e = {1, 1, 2};
        boolean[] ef = {true, true, false};
        check(CcLayout.move(e, ef, 0, 0, 1) == 0, "nothing focusable below stays");
        // an unfocusable half width item
        int[] p = {1, 1, 1, 1};
        boolean[] pf = {true, false, true, true};
        check(CcLayout.move(p, pf, 0, 1, 0) == 0, "an unfocusable neighbour is not entered");
        check(CcLayout.move(p, pf, 2, 0, -1) == 0 && CcLayout.move(p, pf, 3, 0, -1) == 0, "up from column 1 falls back to the other column of the row");
        check(CcLayout.move(p, pf, 0, 0, 1) == 2, "down by column");
        // all unfocusable
        check(CcLayout.move(p, new boolean[4], 0, 0, 1) == 0, "nothing focusable below");
        check(CcLayout.move(p, new boolean[4], CcLayout.STRIP, 0, 1) == CcLayout.STRIP, "down from the strip with nothing focusable stays on the strip");
        // empty page and one item
        check(CcLayout.move(new int[0], new boolean[0], CcLayout.STRIP, 0, 1) == CcLayout.STRIP, "empty page: down from the strip stays");
        check(CcLayout.move(new int[0], new boolean[0], 0, 0, 1) == 0, "empty page: bad index returned unchanged");
        check(CcLayout.firstFocusable(new boolean[0]) == CcLayout.STRIP && CcLayout.firstFocusable(null) == CcLayout.STRIP, "no first focusable on an empty page");
        int[] one = {2};
        boolean[] of = {true};
        check(CcLayout.move(one, of, 0, 0, 1) == 0 && CcLayout.move(one, of, 0, 1, 0) == 0 && CcLayout.move(one, of, 0, 0, -1) == CcLayout.STRIP, "one item");
        check(CcLayout.move(null, null, 0, 0, 1) == CcLayout.STRIP, "null page does not throw");
        check(CcLayout.move(s, null, 0, 0, 1) == 0, "null focusable array means nothing focusable");
        check(CcLayout.move(s, new boolean[2], 0, 1, 0) == 0, "a short focusable array is read as unfocusable past its end");

        // walk the whole page down and back up
        int cur = CcLayout.STRIP;
        int[] walk = new int[8];
        int n = 0;
        cur = CcLayout.move(s, f, cur, 0, 1);
        while (n < 8) { walk[n++] = cur; int nx = CcLayout.move(s, f, cur, 0, 1); if (nx == cur) break; cur = nx; }
        check(walk[0] == 0 && cur == 4, "walking down visits the first item and ends on the last row");

        // remember
        check(CcLayout.remember(s, f, 3) == 3, "a good remembered index is kept");
        check(CcLayout.remember(s, f, 9) == 0 && CcLayout.remember(s, f, -1) == 0 && CcLayout.remember(s, f, -2) == 0, "an out of range index falls back to the first");
        check(CcLayout.remember(h, hf, 0) == 1, "a remembered header falls back to the first focusable");
        check(CcLayout.remember(h, hf, 99) == 1, "out of range with a header first");
        check(CcLayout.remember(p, new boolean[4], 2) == CcLayout.STRIP, "nothing focusable gives the strip");
        check(CcLayout.remember(new int[0], new boolean[0], 0) == CcLayout.STRIP && CcLayout.remember(null, null, 0) == CcLayout.STRIP, "empty page gives the strip");
        check(CcLayout.remember(s, new boolean[]{true}, 3) == 0, "a remembered index past a short focusable array falls back");
    }

    static void layoutScroll() {
        section("2.8 control center layout: scrolling");
        check(CcLayout.scrollTop(10, 4, 0, 0) == 0, "focus at the top stays at the top");
        check(CcLayout.scrollTop(10, 4, 3, 0) == 0, "focus on the last visible row does not scroll");
        check(CcLayout.scrollTop(10, 4, 4, 0) == 1, "focus one below scrolls one row");
        check(CcLayout.scrollTop(10, 4, 5, 0) == 2, "smallest change to bring the focus in at the bottom");
        check(CcLayout.scrollTop(10, 4, 2, 5) == 2, "focus above scrolls up just enough");
        check(CcLayout.scrollTop(10, 4, 6, 5) == 5, "focus in view keeps the top");
        check(CcLayout.scrollTop(10, 4, 9, 0) == 6, "the last row is clamped to rows minus visible");
        check(CcLayout.scrollTop(10, 4, 3, 100) == 3, "a stale top beyond the end is pulled back to the focus");
        check(CcLayout.scrollTop(10, 4, 9, 100) == 6, "a stale top beyond the end is clamped");
        check(CcLayout.scrollTop(3, 5, 2, 4) == 0, "fewer rows than the window never scrolls");
        check(CcLayout.scrollTop(0, 5, 0, 0) == 0, "no rows");
        check(CcLayout.scrollTop(5, 5, 4, 0) == 0, "rows equal to the window");
        check(CcLayout.scrollTop(10, 4, 5, -3) == 2, "a negative old top is handled");
        check(CcLayout.scrollTop(10, 0, 5, 0) == 5, "a window of 0 is read as 1");
        check(CcLayout.scrollTop(10, 4, -1, 3) == 0, "a negative focus row clamps to 0");
        // walking down one row at a time never jumps
        int top = 0;
        for (int r = 0; r < 10; r++) {
            int nt = CcLayout.scrollTop(10, 4, r, top);
            check(nt - top <= 1 && r >= nt && r < nt + 4, "walking down keeps row " + r + " in view");
            top = nt;
        }
        check(top == 6, "walking down ends at the clamped bottom");
    }

    // ================================================================== touchpad volume

    static int drag(TouchVol tv, float x0, float y0, float x1, float y1, int n) {
        tv.begin(x0, y0);
        int sum = 0;
        for (int i = 1; i <= n; i++) sum += tv.move(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n);
        return sum;
    }

    static void touchVertical() {
        section("2.8 touchpad volume: directions");
        TouchVol tv = new TouchVol();
        tv.config(TouchVol.VERTICAL, false, 1);
        int up = drag(tv, 0.5f, 0.9f, 0.5f, 0.1f, 80);
        check(up >= 8 && up <= 10, "a long swipe up is louder, 12 steps per pad: " + up);
        int down = drag(tv, 0.5f, 0.1f, 0.5f, 0.9f, 80);
        check(down <= -8 && down >= -10, "a long swipe down is quieter: " + down);
        check(drag(tv, 0.2f, 0.5f, 0.9f, 0.5f, 60) == 0, "sideways movement on a vertical pad does nothing");
        tv.config(TouchVol.VERTICAL, true, 1);
        check(drag(tv, 0.5f, 0.9f, 0.5f, 0.1f, 80) < 0 && drag(tv, 0.5f, 0.1f, 0.5f, 0.9f, 80) > 0, "invert flips a vertical pad");
        tv.config(TouchVol.HORIZONTAL, false, 1);
        int right = drag(tv, 0.1f, 0.5f, 0.9f, 0.5f, 80);
        check(right >= 8 && right <= 10, "right on a horizontal pad is louder: " + right);
        int left = drag(tv, 0.9f, 0.5f, 0.1f, 0.5f, 80);
        check(left <= -8 && left >= -10, "left on a horizontal pad is quieter: " + left);
        check(drag(tv, 0.5f, 0.1f, 0.5f, 0.9f, 60) == 0, "vertical movement on a horizontal pad does nothing");
        tv.config(TouchVol.HORIZONTAL, true, 1);
        check(drag(tv, 0.1f, 0.5f, 0.9f, 0.5f, 80) < 0 && drag(tv, 0.9f, 0.5f, 0.1f, 0.5f, 80) > 0, "invert flips a horizontal pad");
        // exact small move on a vertical pad
        tv.config(TouchVol.VERTICAL, false, 1);
        tv.begin(0.5f, 0.8f);
        check(tv.move(0.5f, 0.7f) == 0, "leaving the dead zone returns 0");
        check(tv.move(0.5f, 0.61f) == 1, "0.09 of the pad at 12 steps is one step up");
        check(tv.move(0.5f, 0.70f) == -1, "0.09 back down is one step down");
    }

    static void touchSensitivity() {
        section("2.8 touchpad volume: sensitivity");
        check(TouchVol.SENS_STEPS.length == 4 && TouchVol.SENS_STEPS[0] == 8 && TouchVol.SENS_STEPS[1] == 12
                && TouchVol.SENS_STEPS[2] == 16 && TouchVol.SENS_STEPS[3] == 24, "sensitivity steps");
        check(TouchVol.SENS_DEFAULT == 1 && TouchVol.SENS_STEPS[TouchVol.SENS_DEFAULT] == 12, "default is 12 steps");
        check(Math.abs(TouchVol.DEADZONE - 0.02f) < 1e-9f, "dead zone is 0.02");
        TouchVol tv = new TouchVol();
        for (int i = 0; i < TouchVol.SENS_STEPS.length; i++) {
            int n = TouchVol.SENS_STEPS[i];
            tv.config(TouchVol.VERTICAL, false, i);
            int got = drag(tv, 0.5f, 0.95f, 0.5f, 0.05f, 90);
            double travel = 0.9 - TouchVol.DEADZONE;
            check(got >= n * travel - 1.2 && got <= n * travel + 0.5, "sensitivity " + n + " steps gives about " + (n * travel) + " for a long swipe, got " + got);
            tv.config(TouchVol.VERTICAL, false, i);
            int whole = drag(tv, 0.5f, 1.0f, 0.5f, 0.0f, 100);
            check(whole >= n - 1 && whole <= n, "a swipe across the whole pad gives about " + n + " steps, got " + whole);
            check(TouchVol.sensName(i).equals(n + " STEPS"), "sensName " + i);
        }
        check(TouchVol.sensName(-1).equals("12 STEPS") && TouchVol.sensName(99).equals("12 STEPS"), "bad sensitivity index names the default");
        // bad config falls back to vertical and the default
        tv.config(17, false, 99);
        int bad = drag(tv, 0.5f, 1.0f, 0.5f, 0.0f, 100);
        check(bad >= 11 && bad <= 12, "bad config acts as vertical with 12 steps: " + bad);
        tv.config(-5, false, -1);
        check(drag(tv, 0.5f, 1.0f, 0.5f, 0.0f, 100) > 0, "negative values also fall back to the defaults");
        check(TouchVol.directionName(TouchVol.VERTICAL).equals("VERTICAL") && TouchVol.directionName(TouchVol.HORIZONTAL).equals("HORIZONTAL")
                && TouchVol.directionName(9).equals("VERTICAL"), "direction names");
    }

    static void touchDeadzoneAndAccumulator() {
        section("2.8 touchpad volume: dead zone, accumulator, reversal");
        TouchVol tv = new TouchVol();
        tv.config(TouchVol.VERTICAL, false, 1);
        // inside the dead zone nothing happens
        tv.begin(0.5f, 0.5f);
        int s = 0;
        for (float y = 0.5f; y > 0.485f; y -= 0.001f) s += tv.move(0.5f, y);
        for (float y = 0.485f; y < 0.515f; y += 0.001f) s += tv.move(0.5f, y);
        check(s == 0, "wobble inside the dead zone gives nothing");
        // leaving it counts from the exit point
        tv.begin(0.5f, 0.5f);
        check(tv.move(0.5f, 0.45f) == 0, "the move that leaves the dead zone returns 0");
        check(tv.move(0.5f, 0.45f) == 0, "staying put gives 0");
        check(tv.move(0.5f, 0.36f) == 1, "counting starts from where the finger left the dead zone");
        // slow drag: tiny moves still add up
        tv.begin(0.5f, 0.9f);
        int total = 0, maxOne = 0;
        for (int i = 0; i < 100; i++) {
            int r = tv.move(0.5f, 0.9f - 0.005f * (i + 1));
            total += r; maxOne = Math.max(maxOne, Math.abs(r));
        }
        check(maxOne <= 1, "slow drag steps one at a time");
        check(total >= 5 && total <= 6, "0.5 of travel at 12 steps (minus the dead zone) is about 5.7 steps: " + total);
        // very slow: 0.001 per call
        tv.begin(0.5f, 0.9f);
        total = 0;
        for (int i = 0; i < 400; i++) total += tv.move(0.5f, 0.9f - 0.001f * (i + 1));
        check(total >= 4 && total <= 5, "a very slow drag still steps: " + total);
        // reversal: a partial step comes back out
        tv.begin(0.5f, 0.5f);
        tv.move(0.5f, 0.45f);
        int a = tv.move(0.5f, 0.40f);
        int b = tv.move(0.5f, 0.45f);
        check(a == 0 && b == 0, "a partial step up and back down gives nothing");
        // reversal after a whole step
        tv.begin(0.5f, 0.5f);
        tv.move(0.5f, 0.45f);
        int up = tv.move(0.5f, 0.35f);
        int back = tv.move(0.5f, 0.45f);
        check(up == 1 && back == -1, "a full step up then the same distance down gives +1 then -1");
        // no movement lost between calls: the same path in one call or many gives the same total
        TouchVol one = new TouchVol(), many = new TouchVol();
        one.config(TouchVol.VERTICAL, false, 2);
        many.config(TouchVol.VERTICAL, false, 2);
        one.begin(0.5f, 0.9f); many.begin(0.5f, 0.9f);
        one.move(0.5f, 0.8f); many.move(0.5f, 0.8f);
        int tOne = one.move(0.5f, 0.5f);
        int tMany = 0;
        for (int i = 1; i <= 30; i++) tMany += many.move(0.5f, 0.8f - 0.01f * i);
        check(tOne == tMany, "one call or thirty calls give the same total: " + tOne + " vs " + tMany);
    }

    static void touchGuards() {
        section("2.8 touchpad volume: jump cap, NaN, reset");
        TouchVol tv = new TouchVol();
        tv.config(TouchVol.VERTICAL, false, 3);
        tv.begin(0.5f, 0.95f);
        tv.move(0.5f, 0.9f);
        check(tv.move(0.5f, 0.1f) == TouchVol.MAX_JUMP && TouchVol.MAX_JUMP == 6, "a huge jump up is capped at 6");
        check(tv.move(0.5f, 0.1f) == 0, "the rest of the jump is dropped, not carried");
        check(tv.move(0.5f, 0.95f) == -TouchVol.MAX_JUMP, "a huge jump down is capped at -6");
        check(tv.move(0.5f, 0.95f) == 0, "nothing carried after the down jump");
        // NaN and infinity are ignored and do not disturb the gesture
        tv.config(TouchVol.VERTICAL, false, 1);
        tv.begin(0.5f, 0.8f);
        tv.move(0.5f, 0.7f);
        check(tv.move(Float.NaN, 0.5f) == 0 && tv.move(0.5f, Float.NaN) == 0, "NaN gives 0");
        check(tv.move(Float.POSITIVE_INFINITY, 0.5f) == 0 && tv.move(0.5f, Float.NEGATIVE_INFINITY) == 0, "infinity gives 0");
        check(tv.move(0.5f, 0.61f) == 1, "the gesture carries on after NaN");
        TouchVol nb = new TouchVol();
        nb.begin(Float.NaN, 0.5f);
        check(nb.move(0.5f, 0.2f) == 0 && nb.move(0.5f, 0.1f) == 0, "begin with NaN does not throw and the next move starts a gesture");
        // begin and end reset the accumulator
        tv.config(TouchVol.VERTICAL, false, 1);
        tv.begin(0.5f, 0.9f);
        tv.move(0.5f, 0.8f);
        tv.move(0.5f, 0.74f);
        tv.end();
        check(tv.move(0.5f, 0.5f) == 0, "after end the next move only starts a new gesture");
        check(tv.move(0.5f, 0.4f) == 0, "and then needs to leave the dead zone first");
        tv.begin(0.5f, 0.9f);
        tv.move(0.5f, 0.8f);
        tv.move(0.5f, 0.75f);
        tv.begin(0.5f, 0.9f);
        tv.move(0.5f, 0.8f);
        check(tv.move(0.5f, 0.8f) == 0, "begin again discards the leftover");
        tv.end();
        tv.end();
        check(tv.move(0.1f, 0.1f) == 0, "end twice is fine");
        // config resets
        tv.begin(0.5f, 0.9f);
        tv.move(0.5f, 0.8f);
        tv.config(TouchVol.VERTICAL, false, 1);
        check(tv.move(0.5f, 0.5f) == 0, "config resets the gesture");
        // extreme coordinates do not throw
        tv.begin(0.5f, 0.5f);
        tv.move(0.5f, 0.4f);
        int big = tv.move(0.5f, 1e30f);
        check(big >= -6 && big <= 6, "huge coordinates stay inside the cap");
        big = tv.move(0.5f, -1e30f);
        check(big >= -6 && big <= 6, "huge negative coordinates stay inside the cap");
    }

    static void touchKeys() {
        section("2.8 touchpad volume: D-pad fallback and hold keys");
        int up = 19, down = 20, left = 21, right = 22;
        check(TouchVol.dpadSteps(TouchVol.VERTICAL, false, up) == 1 && TouchVol.dpadSteps(TouchVol.VERTICAL, false, down) == -1, "vertical: up louder, down quieter");
        check(TouchVol.dpadSteps(TouchVol.VERTICAL, false, left) == 0 && TouchVol.dpadSteps(TouchVol.VERTICAL, false, right) == 0, "vertical ignores left and right");
        check(TouchVol.dpadSteps(TouchVol.HORIZONTAL, false, right) == 1 && TouchVol.dpadSteps(TouchVol.HORIZONTAL, false, left) == -1, "horizontal: right louder, left quieter");
        check(TouchVol.dpadSteps(TouchVol.HORIZONTAL, false, up) == 0 && TouchVol.dpadSteps(TouchVol.HORIZONTAL, false, down) == 0, "horizontal ignores up and down");
        check(TouchVol.dpadSteps(TouchVol.VERTICAL, true, up) == -1 && TouchVol.dpadSteps(TouchVol.VERTICAL, true, down) == 1, "invert flips vertical");
        check(TouchVol.dpadSteps(TouchVol.HORIZONTAL, true, right) == -1 && TouchVol.dpadSteps(TouchVol.HORIZONTAL, true, left) == 1, "invert flips horizontal");
        check(TouchVol.dpadSteps(TouchVol.VERTICAL, true, 23) == 0 && TouchVol.dpadSteps(TouchVol.HORIZONTAL, true, 0) == 0, "other keys give 0 even inverted");
        check(TouchVol.dpadSteps(9, false, up) == 1, "a bad direction acts as vertical");

        int[] good = {102, 103, 104, 105, 108, 109};
        String[] names = {"L1", "R1", "L2", "R2", "OPTIONS", "CREATE"};
        for (int i = 0; i < good.length; i++) {
            check(TouchVol.validHoldKey(good[i]), "hold key allowed: " + names[i]);
            check(TouchVol.holdKeyName(good[i]).equals(names[i]), "hold key name " + names[i]);
        }
        int[] badKeys = {110, 3, 4, 0, -1, 19, 20, 21, 22, 23, 96, 97, 99, 100, 106, 107, 111, 82, 66, 164, 1 << 20};
        for (int k : badKeys) {
            check(!TouchVol.validHoldKey(k), "hold key not allowed: " + k);
            check(TouchVol.holdKeyName(k).equals("NONE"), "hold key name NONE for " + k);
        }
    }
}
