package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The list of recently used apps for the task manager. The caller gathers the raw entries
 * (UsageStats on the Android side), this class cleans, sorts and marks them. Pure Java,
 * never throws on null input.
 */
final class TaskList {
    private TaskList() {}

    static final int MAX_PROTECT = 64;
    static final int MAX_SHOW = 30;

    static final class Entry {
        final String pkg, key, label;
        final long lastUsed;
        boolean foreground, protectedApp;

        Entry(String pkg, String key, String label, long lastUsed) {
            this.pkg = pkg;
            this.key = key;
            this.label = label;
            this.lastUsed = lastUsed;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static final Comparator<Entry> ORDER = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) {
            if (a.lastUsed != b.lastUsed) return a.lastUsed > b.lastUsed ? -1 : 1;
            int c = nz(a.label).compareTo(nz(b.label));
            if (c != 0) return c;
            return a.pkg.compareTo(b.pkg);
        }
    };

    /**
     * Drops null entries, invalid packages, the self package and duplicate packages (the newest
     * lastUsed wins), sorts newest first (ties by label, then package), marks the foreground app
     * and the protected apps (the protect set plus the foreground app), cuts to max (clamped 1..30).
     */
    static List<Entry> build(List<Entry> in, String foregroundPkg, String selfPkg, Set<String> protect, int max) {
        int cap = Math.max(1, Math.min(MAX_SHOW, max));
        ArrayList<Entry> out = new ArrayList<>();
        if (in == null) return out;
        Map<String, Entry> best = new HashMap<>();
        for (Entry e : in) {
            if (e == null || !KillCmd.validPackage(e.pkg)) continue;
            if (e.pkg.equals(selfPkg)) continue;
            Entry old = best.get(e.pkg);
            if (old == null || ORDER.compare(e, old) < 0) best.put(e.pkg, e);
        }
        out.addAll(best.values());
        Collections.sort(out, ORDER);
        for (Entry e : out) {
            e.foreground = e.pkg.equals(foregroundPkg);
            e.protectedApp = e.foreground || (protect != null && protect.contains(e.pkg));
        }
        while (out.size() > cap) out.remove(out.size() - 1);
        return out;
    }

    /** The entries CLOSE ALL BACKGROUND may close: neither protected nor foreground. */
    static List<Entry> killable(List<Entry> list) {
        ArrayList<Entry> out = new ArrayList<>();
        if (list == null) return out;
        for (Entry e : list) if (e != null && !e.protectedApp && !e.foreground) out.add(e);
        return out;
    }

    /** "NOW", "N MIN AGO", "N H AGO", "N D AGO". Empty for a missing time (then <= 0) or a time in the future. */
    static String ago(long now, long then) {
        if (then <= 0 || then > now) return "";
        long s = (now - then) / 1000;
        if (s < 60) return "NOW";
        long m = s / 60;
        if (m < 60) return m + " MIN AGO";
        long h = m / 60;
        if (h < 24) return h + " H AGO";
        return (h / 24) + " D AGO";
    }

    /** Comma separated package names. Invalid entries are ignored, at most 64 are kept, insertion ordered. */
    static Set<String> parseProtect(String s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (s == null || s.isEmpty()) return out;
        int start = 0, n = s.length();
        while (start <= n && out.size() < MAX_PROTECT) {
            int end = s.indexOf(',', start);
            if (end < 0) end = n;
            if (end - start <= KillCmd.MAX_LEN + 8) { // do not even copy hostile giant pieces
                String p = s.substring(start, end).trim();
                if (KillCmd.validPackage(p)) out.add(p);
            }
            start = end + 1;
        }
        return out;
    }

    /** Valid names only, sorted, comma joined, at most 64. */
    static String saveProtect(Set<String> set) {
        if (set == null) return "";
        TreeSet<String> sorted = new TreeSet<>();
        for (String p : set) if (KillCmd.validPackage(p)) sorted.add(p);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String p : sorted) {
            if (n++ >= MAX_PROTECT) break;
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    /** A copy of user plus self, home and foreground package, each added only when valid. */
    static Set<String> withDefaults(Set<String> user, String selfPkg, String homePkg, String foregroundPkg) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (user != null) out.addAll(user);
        for (String p : new String[]{selfPkg, homePkg, foregroundPkg}) if (KillCmd.validPackage(p)) out.add(p);
        return out;
    }
}
