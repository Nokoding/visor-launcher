package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.nokoding.deck.LogicTests.check;
import static com.nokoding.deck.LogicTests.section;

/** 2.8 group A tests: task manager (TaskList, KillCmd, ForceStopMatcher). */
final class Tests28A {
    private Tests28A() {}

    static void run() {
        validPackage();
        commands();
        taskBuild();
        taskKillable();
        taskAgo();
        protectList();
        withDefaults();
        matcherPage();
        matcherDialog();
        matcherEdge();
    }

    // ================================================================== KillCmd

    static String rep(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    static void validPackage() {
        section("2.8 KillCmd.validPackage");
        for (String ok : new String[]{"com.nokoding.deck", "com.roblox.client", "a_b.c1", "A.B", "a.b", "com.Foo_bar.x9"}) {
            check(KillCmd.validPackage(ok), "valid: " + ok);
        }
        String long255 = "a." + rep('b', 253);
        check(long255.length() == 255 && KillCmd.validPackage(long255), "255 chars is valid");
        String[] bad = {"", null, "nodot", ".a.b", "a.b.", "a..b", "a b.c", "a.b;rm", "a.b$(x)", "1a.b", "a.1b", "a._b",
                "_a.b", "a.b\n", " a.b", "a.b ", "a.b&c", "a.b|c", "a.b`x`", "a.b/c", "a.b-c", "a.b'c", "a.\"b", ".", "..",
                "а.b", "a.bé", "ａ.b", "a.b\u0000", "a.b\t", "a.b>x", "a.b*", "a.b\\c"};
        for (String b : bad) check(!KillCmd.validPackage(b), "invalid: " + (b == null ? "null" : b.replace("\n", "\\n")));
        check(!KillCmd.validPackage("a." + rep('b', 254)), "256 chars is invalid");
        check(!KillCmd.validPackage(rep('a', 300) + ".b"), "300 chars is invalid");
    }

    static void commands() {
        section("2.8 KillCmd commands");
        check("am force-stop com.nokoding.deck".equals(KillCmd.forceStopShell("com.nokoding.deck")), "forceStopShell exact");
        check("adb shell am force-stop com.roblox.client".equals(KillCmd.forceStopAdb("com.roblox.client")), "forceStopAdb exact");
        check("am kill a_b.c1".equals(KillCmd.killBackgroundShell("a_b.c1")), "killBackgroundShell exact");
        String[] hostile = {null, "", "a.b;rm -rf /", "a.b && reboot", "$(id).x", "a.b\nreboot", "nodot", "a b.c"};
        for (String h : hostile) {
            check(KillCmd.forceStopShell(h) == null, "forceStopShell null for bad");
            check(KillCmd.forceStopAdb(h) == null, "forceStopAdb null for bad");
            check(KillCmd.killBackgroundShell(h) == null, "killBackgroundShell null for bad");
        }
    }

    // ================================================================== TaskList

    static TaskList.Entry e(String pkg, String label, long t) { return new TaskList.Entry(pkg, pkg + "/Main", label, t); }

    static Set<String> set(String... s) { return new LinkedHashSet<>(Arrays.asList(s)); }

    static void taskBuild() {
        section("2.8 TaskList.build");
        List<TaskList.Entry> in = new ArrayList<>();
        in.add(e("com.a.one", "One", 1000));
        in.add(e("com.b.two", "Two", 3000));
        in.add(e("com.c.three", "Three", 2000));
        List<TaskList.Entry> out = TaskList.build(in, null, "com.nokoding.deck", null, 10);
        check(out.size() == 3, "three kept");
        check(out.get(0).pkg.equals("com.b.two") && out.get(1).pkg.equals("com.c.three") && out.get(2).pkg.equals("com.a.one"), "newest first");
        check(!out.get(0).foreground && !out.get(0).protectedApp, "nothing marked without foreground or protect");

        in.add(e("com.a.one", "One", 5000));
        in.add(e("com.a.one", "One old", 10));
        out = TaskList.build(in, null, "x.y", set(), 10);
        check(out.size() == 3, "duplicates collapse to one");
        check(out.get(0).pkg.equals("com.a.one") && out.get(0).lastUsed == 5000, "the duplicate keeps the newest time");

        in.add(e("com.nokoding.deck", "Visor", 9000));
        out = TaskList.build(in, null, "com.nokoding.deck", null, 10);
        boolean self = false;
        for (TaskList.Entry x : out) if (x.pkg.equals("com.nokoding.deck")) self = true;
        check(!self, "self package dropped");

        out = TaskList.build(in, "com.c.three", "com.nokoding.deck", set("com.b.two", "not valid"), 10);
        for (TaskList.Entry x : out) {
            if (x.pkg.equals("com.c.three")) check(x.foreground && x.protectedApp, "foreground is marked and protected");
            else if (x.pkg.equals("com.b.two")) check(!x.foreground && x.protectedApp, "protect set marks protectedApp");
            else check(!x.foreground && !x.protectedApp, "others unmarked: " + x.pkg);
        }

        // cut to max, clamp
        List<TaskList.Entry> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) many.add(e("com.app.p" + i, "P" + i, 1000 + i));
        check(TaskList.build(many, null, null, null, 5).size() == 5, "cut to max 5");
        check(TaskList.build(many, null, null, null, 5).get(0).pkg.equals("com.app.p49"), "cut keeps the newest");
        check(TaskList.build(many, null, null, null, 0).size() == 1, "max 0 clamps to 1");
        check(TaskList.build(many, null, null, null, -7).size() == 1, "negative max clamps to 1");
        check(TaskList.build(many, null, null, null, 1000).size() == 30, "big max clamps to 30");
        check(TaskList.build(many, null, null, null, Integer.MAX_VALUE).size() == 30, "MAX_VALUE clamps to 30");
        check(TaskList.build(many, null, null, null, Integer.MIN_VALUE).size() == 1, "MIN_VALUE clamps to 1");

        // nulls and invalid packages
        List<TaskList.Entry> dirty = new ArrayList<>();
        dirty.add(null);
        dirty.add(e(null, "Null pkg", 5));
        dirty.add(e("", "Empty", 5));
        dirty.add(e("nodot", "Nodot", 5));
        dirty.add(e("a.b;rm", "Hostile", 5));
        dirty.add(e("com.ok.app", null, 5));
        out = TaskList.build(dirty, null, null, null, 10);
        check(out.size() == 1 && out.get(0).pkg.equals("com.ok.app"), "null, invalid and hostile entries dropped");
        check(TaskList.build(null, null, null, null, 10).isEmpty(), "null list gives empty");
        check(TaskList.build(new ArrayList<TaskList.Entry>(), "a.b", "c.d", set("e.f"), 3).isEmpty(), "empty list gives empty");

        // ties: same time, by label then pkg
        List<TaskList.Entry> tie = new ArrayList<>();
        tie.add(e("com.z.last", "Alpha", 100));
        tie.add(e("com.a.first", "Beta", 100));
        tie.add(e("com.m.mid", "Alpha", 100));
        tie.add(e("com.n.nolabel", null, 100));
        out = TaskList.build(tie, null, null, null, 10);
        check(out.get(0).pkg.equals("com.n.nolabel"), "null label sorts first among ties");
        check(out.get(1).pkg.equals("com.m.mid") && out.get(2).pkg.equals("com.z.last"), "same label ties by package");
        check(out.get(3).pkg.equals("com.a.first"), "label Beta after Alpha");
        List<TaskList.Entry> rev = new ArrayList<>(tie);
        java.util.Collections.reverse(rev);
        List<TaskList.Entry> out2 = TaskList.build(rev, null, null, null, 10);
        boolean same = out.size() == out2.size();
        for (int i = 0; same && i < out.size(); i++) same = out.get(i).pkg.equals(out2.get(i).pkg);
        check(same, "ties are deterministic whatever the input order");
        // exact duplicate (same time) is deterministic as well
        List<TaskList.Entry> dup = new ArrayList<>();
        dup.add(e("com.d.d", "Zed", 50));
        dup.add(e("com.d.d", "Abe", 50));
        check(TaskList.build(dup, null, null, null, 5).get(0).label.equals("Abe"), "duplicate with equal time keeps the first by label");
    }

    static void taskKillable() {
        section("2.8 TaskList.killable");
        List<TaskList.Entry> in = new ArrayList<>();
        in.add(e("com.fg.app", "Fg", 900));
        in.add(e("com.keep.app", "Keep", 800));
        in.add(e("com.close.one", "One", 700));
        in.add(e("com.close.two", "Two", 600));
        in.add(e("com.nokoding.deck", "Visor", 950));
        List<TaskList.Entry> list = TaskList.build(in, "com.fg.app", "com.nokoding.deck", set("com.keep.app"), 10);
        List<TaskList.Entry> k = TaskList.killable(list);
        check(k.size() == 2, "two killable");
        for (TaskList.Entry x : k) {
            check(!x.foreground && !x.protectedApp, "killable is never foreground or protected");
            check(!x.pkg.equals("com.fg.app") && !x.pkg.equals("com.keep.app") && !x.pkg.equals("com.nokoding.deck"), "killable never lists fg, protected or self: " + x.pkg);
        }
        check(k.get(0).pkg.equals("com.close.one"), "killable keeps the order");
        check(TaskList.killable(null).isEmpty(), "killable of null is empty");
        List<TaskList.Entry> withNull = new ArrayList<>(list);
        withNull.add(null);
        check(TaskList.killable(withNull).size() == 2, "killable skips null entries");
        List<TaskList.Entry> allProt = TaskList.build(in, "com.fg.app", "x.y", set("com.keep.app", "com.close.one", "com.close.two", "com.nokoding.deck"), 10);
        check(TaskList.killable(allProt).isEmpty(), "all protected leaves nothing to close");
    }

    static void taskAgo() {
        section("2.8 TaskList.ago");
        long now = 10_000_000_000L;
        long s = 1000, m = 60 * s, h = 60 * m, d = 24 * h;
        check(TaskList.ago(now, now).equals("NOW"), "same time is NOW");
        check(TaskList.ago(now, now - 59 * s).equals("NOW"), "59 s is NOW");
        check(TaskList.ago(now, now - 59 * s - 999).equals("NOW"), "59.999 s is NOW");
        check(TaskList.ago(now, now - 60 * s).equals("1 MIN AGO"), "60 s is 1 MIN AGO");
        check(TaskList.ago(now, now - 5 * m).equals("5 MIN AGO"), "5 min");
        check(TaskList.ago(now, now - 59 * m).equals("59 MIN AGO"), "59 min");
        check(TaskList.ago(now, now - 59 * m - 59 * s).equals("59 MIN AGO"), "59 min 59 s");
        check(TaskList.ago(now, now - 60 * m).equals("1 H AGO"), "60 min is 1 H AGO");
        check(TaskList.ago(now, now - 23 * h).equals("23 H AGO"), "23 h");
        check(TaskList.ago(now, now - 23 * h - 59 * m).equals("23 H AGO"), "23 h 59 min");
        check(TaskList.ago(now, now - 24 * h).equals("1 D AGO"), "24 h is 1 D AGO");
        check(TaskList.ago(now, now - 5 * d).equals("5 D AGO"), "5 d");
        check(TaskList.ago(now, now + 1).isEmpty(), "future is empty");
        check(TaskList.ago(now, now + 10 * d).isEmpty(), "far future is empty");
        check(TaskList.ago(now, 0).isEmpty(), "zero is empty");
        check(TaskList.ago(now, -5).isEmpty(), "negative is empty");
        check(TaskList.ago(0, 0).isEmpty(), "zero and zero is empty");
        check(TaskList.ago(Long.MAX_VALUE, 1).endsWith("D AGO"), "huge span does not overflow");
    }

    static void protectList() {
        section("2.8 TaskList protect list");
        Set<String> p = TaskList.parseProtect("com.a.one,com.b.two");
        check(p.size() == 2 && p.contains("com.a.one") && p.contains("com.b.two"), "parse two");
        check(new ArrayList<>(p).get(0).equals("com.a.one"), "parse keeps insertion order");
        check(TaskList.parseProtect(null).isEmpty(), "parse null");
        check(TaskList.parseProtect("").isEmpty(), "parse empty");
        check(TaskList.parseProtect(",,,,").isEmpty(), "commas only");
        check(TaskList.parseProtect("   ").isEmpty(), "spaces only");
        check(TaskList.parseProtect("garbage").isEmpty(), "garbage");
        check(TaskList.parseProtect("a.b;rm -rf /,$(id).x,`x`.y,a b.c").isEmpty(), "shell characters rejected");
        check(TaskList.parseProtect("\u0000\u0001,\n,\t").isEmpty(), "control characters rejected");
        Set<String> sp = TaskList.parseProtect(" com.a.one , com.b.two ,");
        check(sp.size() == 2 && sp.contains("com.a.one"), "spaces around valid names are trimmed");
        Set<String> dup = TaskList.parseProtect("com.a.one,com.a.one,com.a.one");
        check(dup.size() == 1, "duplicates collapse");
        Set<String> mixed = TaskList.parseProtect("bad,com.ok.app,,;;;,com.two.app");
        check(mixed.size() == 2 && mixed.contains("com.ok.app") && mixed.contains("com.two.app"), "bad entries are skipped, good kept");
        check(TaskList.parseProtect(rep('a', 10000)).isEmpty(), "10000 chars without dot");
        check(TaskList.parseProtect(rep('a', 5000) + "." + rep('b', 5000)).isEmpty(), "one 10000 char name is rejected");
        check(TaskList.parseProtect(rep(',', 10000)).isEmpty(), "10000 commas");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 500; i++) many.append("com.app.p").append(i).append(',');
        Set<String> capped = TaskList.parseProtect(many.toString());
        check(capped.size() == 64, "500 entries are cut to 64");
        check(capped.contains("com.app.p0") && capped.contains("com.app.p63") && !capped.contains("com.app.p64"), "the first 64 are kept");

        check(TaskList.saveProtect(null).isEmpty(), "save null");
        check(TaskList.saveProtect(new LinkedHashSet<String>()).isEmpty(), "save empty");
        check(TaskList.saveProtect(set("com.z.z", "com.a.a", "bad", "x;y.z", "com.m.m")).equals("com.a.a,com.m.m,com.z.z"), "save sorts and drops invalid");
        Set<String> withNull = new LinkedHashSet<>();
        withNull.add(null);
        withNull.add("com.a.a");
        check(TaskList.saveProtect(withNull).equals("com.a.a"), "save skips a null member");
        Set<String> big = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) big.add("com.app.p" + i);
        check(TaskList.parseProtect(TaskList.saveProtect(big)).size() == 64, "save is capped at 64 names");
        Set<String> round = set("com.q.q", "com.b.b");
        check(TaskList.parseProtect(TaskList.saveProtect(round)).equals(round), "round trip");
        String s1 = TaskList.saveProtect(set("com.q.q", "com.b.b"));
        String s2 = TaskList.saveProtect(set("com.b.b", "com.q.q"));
        check(s1.equals(s2), "save does not depend on the insertion order");
    }

    static void withDefaults() {
        section("2.8 TaskList.withDefaults");
        Set<String> user = set("com.user.one");
        Set<String> r = TaskList.withDefaults(user, "com.nokoding.deck", "com.google.android.tvlauncher", "com.fg.app");
        check(r.size() == 4 && r.contains("com.user.one") && r.contains("com.nokoding.deck")
                && r.contains("com.google.android.tvlauncher") && r.contains("com.fg.app"), "adds self, home and foreground");
        check(user.size() == 1, "the user set is not changed (copy)");
        check(r != user, "result is a copy");
        r = TaskList.withDefaults(user, null, "bad", "a b.c");
        check(r.size() == 1 && r.contains("com.user.one"), "null and invalid defaults are skipped");
        r = TaskList.withDefaults(null, "com.a.a", null, null);
        check(r.size() == 1 && r.contains("com.a.a"), "null user gives just the defaults");
        r = TaskList.withDefaults(user, "com.user.one", "com.user.one", "com.user.one");
        check(r.size() == 1, "same names do not duplicate");
        check(TaskList.withDefaults(null, null, null, null).isEmpty(), "all null gives empty");
    }

    // ================================================================== ForceStopMatcher

    /** A fake accessibility node. */
    static final class N implements ForceStopMatcher.Node {
        String id, text, desc;
        boolean clickable, enabled = true;
        final List<N> kids = new ArrayList<>();

        N(String id, String text, boolean clickable) { this.id = id; this.text = text; this.clickable = clickable; }
        N add(N k) { kids.add(k); return this; }
        N off() { enabled = false; return this; }
        N desc(String d) { desc = d; return this; }

        @Override public String id() { return id; }
        @Override public String text() { return text; }
        @Override public String desc() { return desc; }
        @Override public boolean clickable() { return clickable; }
        @Override public boolean enabled() { return enabled; }
        @Override public int childCount() { return kids.size(); }
        @Override public ForceStopMatcher.Node child(int i) { return kids.get(i); }
    }

    static N box() { return new N("android:id/content", null, false); }

    static N appInfoPage(N forceStop) {
        N root = box();
        root.add(new N("com.android.settings:id/title", "Roblox", false));
        root.add(new N("com.android.settings:id/open_button", "Open", true));
        root.add(new N("com.android.settings:id/uninstall_button", "Uninstall", true));
        root.add(forceStop);
        return root;
    }

    static N fsButton() { return new N("com.android.settings:id/force_stop_button", "Force stop", true); }

    static N dialog(boolean ids) {
        N d = new N("android:id/parentPanel", null, false);
        d.add(new N("android:id/alertTitle", "Force stop?", false));
        d.add(new N("android:id/message", "If you force stop an app, it may misbehave.", false));
        d.add(new N(ids ? "android:id/button2" : null, "Cancel", true));
        d.add(new N(ids ? "android:id/button1" : null, "OK", true));
        return d;
    }

    static void matcherPage() {
        section("2.8 ForceStopMatcher: App Info pages");
        N fs = fsButton();
        N page = appInfoPage(fs);
        check(ForceStopMatcher.findForceStop(page) == fs, "finds the force stop button by id");
        check(ForceStopMatcher.state(page) == ForceStopMatcher.APP_INFO, "plain App Info page is APP_INFO");
        check(ForceStopMatcher.findConfirm(page) == fs || ForceStopMatcher.findConfirm(page) == null, "no confirm dialog on a plain page (text force stop is the page button)");

        // by text only
        N fs2 = new N(null, "FORCE STOP", true);
        check(ForceStopMatcher.findForceStop(appInfoPage(fs2)) == fs2, "finds by text ignoring case");
        N fs3 = new N("x:id/btn", null, true).desc("Force Stop");
        check(ForceStopMatcher.findForceStop(appInfoPage(fs3)) == fs3, "finds by content description");
        N fs4 = new N(null, "  Force stop ", true);
        check(ForceStopMatcher.findForceStop(appInfoPage(fs4)) == fs4, "text is trimmed");
        N fs5 = new N(null, "ForceStop", true);
        check(ForceStopMatcher.findForceStop(appInfoPage(fs5)) == fs5, "forcestop spelling");
        check(ForceStopMatcher.findForceStop(appInfoPage(new N(null, "Force close", true))) == null, "force close is not accepted");
        check(ForceStopMatcher.findForceStop(appInfoPage(new N(null, "Stop", true))) == null, "plain Stop is not accepted");
        check(ForceStopMatcher.findForceStop(appInfoPage(new N(null, "Force stop all", true))) == null, "text must be equal, not contain");
        // id variants
        for (String id : new String[]{"com.android.settings:id/force_stop", "com.android.settings:id/button_force_stop",
                "com.android.tv.settings:id/force_stop_action", "x:id/FORCE_STOP_BUTTON"}) {
            N b = new N(id, "label", true);
            check(ForceStopMatcher.findForceStop(appInfoPage(b)) == b, "id variant: " + id);
        }
        // an id match beats an earlier text match
        N textFirst = new N(null, "Force stop", true);
        N idLater = new N("a:id/force_stop_button", "Stop it", true);
        N both = box().add(textFirst).add(idLater);
        check(ForceStopMatcher.findForceStop(both) == idLater, "id match beats text match");

        // disabled
        N off = fsButton().off();
        N disabledPage = appInfoPage(off);
        check(ForceStopMatcher.findForceStop(disabledPage) == off, "disabled button is returned");
        check(ForceStopMatcher.state(disabledPage) == ForceStopMatcher.ALREADY_STOPPED, "disabled Force stop is ALREADY_STOPPED");

        // neither
        N none = box().add(new N("a:id/title", "Settings", false)).add(new N("a:id/row", "Notifications", true));
        check(ForceStopMatcher.findForceStop(none) == null, "no force stop on another page");
        check(ForceStopMatcher.findConfirm(none) == null, "no confirm on another page");
        check(ForceStopMatcher.state(none) == ForceStopMatcher.UNKNOWN, "a page with neither is UNKNOWN");

        // Google TV style: text in a non clickable TextView inside a clickable row
        N row = new N("com.android.tv.settings:id/guidedactions_item_content", null, true);
        row.add(new N("com.android.tv.settings:id/guidedactions_item_title", "Force stop", false));
        N tvPage = box().add(new N("a:id/title", "App info", false)).add(row);
        check(ForceStopMatcher.findForceStop(tvPage) == row, "a text node inside a clickable row returns the row");
        check(ForceStopMatcher.state(tvPage) == ForceStopMatcher.APP_INFO, "TV style page is APP_INFO");
        N deepRow = new N(null, null, true);
        N mid = new N(null, null, false);
        N txt = new N(null, "Force stop", false);
        deepRow.add(mid.add(txt));
        check(ForceStopMatcher.findForceStop(box().add(deepRow)) == deepRow, "the nearest clickable ancestor, two levels up");
        N nearer = new N(null, null, true);
        N outer = new N(null, null, true);
        outer.add(nearer.add(new N(null, "Force stop", false)));
        check(ForceStopMatcher.findForceStop(box().add(outer)) == nearer, "the nearest clickable ancestor wins");
        check(ForceStopMatcher.findForceStop(box().add(new N(null, "Force stop", false))) == null, "text without any clickable ancestor gives null");
        N disabledRow = new N(null, null, true).off();
        disabledRow.add(new N(null, "Force stop", false));
        check(ForceStopMatcher.state(box().add(disabledRow)) == ForceStopMatcher.ALREADY_STOPPED, "TV style disabled row is ALREADY_STOPPED");
    }

    static void matcherDialog() {
        section("2.8 ForceStopMatcher: confirm dialog");
        N d = dialog(true);
        N root = box().add(d);
        ForceStopMatcher.Node c = ForceStopMatcher.findConfirm(root);
        check(c == d.kids.get(3), "finds button1 and not button2");
        check(ForceStopMatcher.findForceStop(root) == null, "dialog alone has no force stop button");
        check(ForceStopMatcher.state(root) == ForceStopMatcher.CONFIRM, "dialog alone is CONFIRM");

        // dialog over an enabled page
        N fs = fsButton();
        N page = appInfoPage(fs);
        page.add(dialog(true));
        check(ForceStopMatcher.state(page) == ForceStopMatcher.CONFIRM, "dialog over an enabled page is CONFIRM");
        check(ForceStopMatcher.findConfirm(page) == page.kids.get(4).kids.get(3), "confirm is the dialog button, not the page button");

        // dialog over a disabled page
        N fsOff = fsButton().off();
        N page2 = appInfoPage(fsOff);
        page2.add(dialog(true));
        check(ForceStopMatcher.state(page2) == ForceStopMatcher.CONFIRM, "dialog over a disabled page is CONFIRM");

        // dialog whose positive button says Force stop, with ids, page behind
        N page3 = appInfoPage(fsButton());
        N d3 = new N("android:id/parentPanel", null, false);
        d3.add(new N("android:id/button2", "Cancel", true));
        d3.add(new N("android:id/button1", "Force stop", true));
        page3.add(d3);
        check(ForceStopMatcher.state(page3) == ForceStopMatcher.CONFIRM, "a Force stop labelled button1 is CONFIRM");
        check(ForceStopMatcher.findConfirm(page3) == d3.kids.get(1), "the id match is preferred over the page button text");

        // dialog without ids, over a disabled (greyed) page
        N page4 = appInfoPage(fsButton().off());
        page4.add(dialog(false));
        check(ForceStopMatcher.state(page4) == ForceStopMatcher.CONFIRM, "id-less dialog over a greyed page is CONFIRM");
        check(ForceStopMatcher.findConfirm(page4) == page4.kids.get(4).kids.get(3), "id-less confirm found by the text OK");

        // text based confirm without the page
        for (String t : new String[]{"ok", "OK", "Yes", "CONFIRM", "force stop", "Force Stop"}) {
            N b = new N(null, t, true);
            check(ForceStopMatcher.findConfirm(box().add(b)) == b, "text confirm: " + t);
        }
        N noText = new N(null, "OK", false);
        check(ForceStopMatcher.findConfirm(box().add(noText)) == null, "a non clickable OK text is not a confirm");
        // negative buttons are never returned
        N neg = box().add(new N("android:id/button2", "Cancel", true)).add(new N(null, "No", true)).add(new N(null, "Cancel", true));
        check(ForceStopMatcher.findConfirm(neg) == null, "cancel, no and button2 are never the confirm");
        check(ForceStopMatcher.state(neg) == ForceStopMatcher.UNKNOWN, "only negative buttons is UNKNOWN");
        // other id names
        for (String id : new String[]{"a:id/dialog_button_positive", "a:id/positive_button", "a:id/button_ok", "a:id/button1"}) {
            N b = new N(id, "Whatever", true);
            check(ForceStopMatcher.findConfirm(box().add(b)) == b, "confirm id: " + id);
        }
        // a button1 dialog about something else is not a force stop confirm
        N other = box().add(new N("a:id/message", "Delete this file?", false)).add(new N("android:id/button1", "Delete", true));
        check(ForceStopMatcher.state(other) == ForceStopMatcher.CONFIRM || ForceStopMatcher.state(other) == ForceStopMatcher.UNKNOWN, "unrelated dialog gives a defined answer");
        check(ForceStopMatcher.state(other) == ForceStopMatcher.CONFIRM, "unrelated dialog still has a confirm node (rule 5), caller checks the page");
    }

    static N chain(int depth, N leaf) {
        N top = box();
        N cur = top;
        for (int i = 0; i < depth; i++) {
            N n = new N("a:id/level" + i, null, false);
            cur.add(n);
            cur = n;
        }
        cur.add(leaf);
        return top;
    }

    static void matcherEdge() {
        section("2.8 ForceStopMatcher: edges and caps");
        check(ForceStopMatcher.findForceStop(null) == null, "null root: findForceStop");
        check(ForceStopMatcher.findConfirm(null) == null, "null root: findConfirm");
        check(ForceStopMatcher.state(null) == ForceStopMatcher.UNKNOWN, "null root: state");

        N bare = new N(null, null, true);
        check(ForceStopMatcher.findForceStop(bare) == null, "null id and text: no force stop");
        check(ForceStopMatcher.findConfirm(bare) == null, "null id and text: no confirm");
        check(ForceStopMatcher.state(bare) == ForceStopMatcher.UNKNOWN, "null id and text: UNKNOWN");
        N emptyId = new N("", "", true);
        check(ForceStopMatcher.state(emptyId) == ForceStopMatcher.UNKNOWN, "empty strings: UNKNOWN");
        check(ForceStopMatcher.state(box().add(new N("no_slash_id", "x", true))) == ForceStopMatcher.UNKNOWN, "id without a path is fine");
        N plainId = new N("force_stop_button", null, true);
        check(ForceStopMatcher.findForceStop(box().add(plainId)) == plainId, "an id without package prefix still matches");

        // children that are null
        N withNullKid = box();
        withNullKid.kids.add(null);
        N fs = fsButton();
        withNullKid.add(fs);
        check(ForceStopMatcher.findForceStop(withNullKid) == fs, "a null child is skipped");

        // 6000 nodes: the cap holds and it returns
        N wide = box();
        for (int i = 0; i < 6000; i++) wide.add(new N("a:id/item" + i, "Item " + i, true));
        N late = fsButton();
        wide.add(late);
        long t0 = System.nanoTime();
        check(ForceStopMatcher.findForceStop(wide) == null, "a force stop button past 5000 nodes is not found");
        check(ForceStopMatcher.findConfirm(wide) == null, "findConfirm on 6000 nodes returns null");
        check(ForceStopMatcher.state(wide) == ForceStopMatcher.UNKNOWN, "state on 6000 nodes returns UNKNOWN");
        check((System.nanoTime() - t0) < 2_000_000_000L, "6000 nodes finish quickly");
        N wide2 = box();
        for (int i = 0; i < 4000; i++) wide2.add(new N("a:id/item" + i, "Item " + i, true));
        N inCap = fsButton();
        wide2.add(inCap);
        check(ForceStopMatcher.findForceStop(wide2) == inCap, "a button inside the 5000 node cap is found");

        // 6000 nodes as a tree (fan out), counted in total not per level
        N tree = box();
        for (int i = 0; i < 60; i++) {
            N g = new N("a:id/group" + i, null, false);
            for (int j = 0; j < 100; j++) g.add(new N(null, "t", false));
            tree.add(g);
        }
        tree.add(fsButton());
        check(ForceStopMatcher.findForceStop(tree) == null, "the node cap counts the whole tree");

        // depth caps
        N shallow = fsButton();
        check(ForceStopMatcher.findForceStop(chain(10, shallow)) == shallow, "a button 10 levels deep is found");
        N at40 = fsButton();
        check(ForceStopMatcher.findForceStop(chain(39, at40)) == at40, "a button at level 40 is found");
        N tooDeep = fsButton();
        check(ForceStopMatcher.findForceStop(chain(100, tooDeep)) == null, "a button 100 levels deep is not found");
        check(ForceStopMatcher.findForceStop(chain(41, fsButton())) == null, "a button at level 42 is not found");
        check(ForceStopMatcher.findConfirm(chain(100, new N("a:id/button1", "OK", true))) == null, "confirm 100 levels deep is not found");
        check(ForceStopMatcher.state(chain(100, fsButton())) == ForceStopMatcher.UNKNOWN, "state on a 100 deep chain is UNKNOWN");
        N hugeChain = chain(100000 / 100, fsButton());
        check(ForceStopMatcher.state(hugeChain) == ForceStopMatcher.UNKNOWN, "a long chain does not overflow the stack");
        // very deep chain would overflow a naive recursion
        N top = box();
        N cur = top;
        for (int i = 0; i < 20000; i++) {
            N n = new N(null, null, false);
            cur.add(n);
            cur = n;
        }
        check(ForceStopMatcher.state(top) == ForceStopMatcher.UNKNOWN, "a 20000 deep chain returns without a stack overflow");

        // clickable ancestor above the depth cap still counts for a deep text node
        N clickTop = new N(null, null, true);
        clickTop.add(new N(null, "Force stop", false));
        check(ForceStopMatcher.findForceStop(clickTop) == clickTop, "root can be the clickable ancestor");
        check(ForceStopMatcher.findForceStop(new N(null, "Force stop", false)) == null, "root text without clickable");
    }
}
