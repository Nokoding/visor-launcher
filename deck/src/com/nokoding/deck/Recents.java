package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Recently used apps. Source A (default, no permission): launches made through Deck, kept
 * as "time TAB key" lines in SharedPreferences. Source B (UsageStatsManager) feeds the same
 * pick() with its own entries. Pure Java.
 */
final class Recents {
    static final int MAX_KEEP = 16, MAX_SHOW = 8, DEFAULT_SHOW = 4;

    static final class Entry {
        final String key;
        final long t;
        Entry(String key, long t) { this.key = key; this.t = t; }
    }

    final ArrayList<Entry> list = new ArrayList<>();

    void record(String key, long now) {
        if (key == null || key.isEmpty()) return;
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).key.equals(key)) list.remove(i);
        list.add(0, new Entry(key, now));
        while (list.size() > MAX_KEEP) list.remove(list.size() - 1);
    }

    boolean remove(String key) {
        boolean r = false;
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).key.equals(key)) { list.remove(i); r = true; }
        return r;
    }

    String save() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.t).append('\t').append(e.key);
        }
        return sb.toString();
    }

    static Recents load(String s) {
        Recents r = new Recents();
        if (s == null || s.isEmpty()) return r;
        ArrayList<Entry> tmp = new ArrayList<>();
        for (String line : s.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab == line.length() - 1) continue;
            try {
                long t = Long.parseLong(line.substring(0, tab));
                String k = line.substring(tab + 1);
                if (k.indexOf('\t') >= 0) continue;
                tmp.add(new Entry(k, t));
            } catch (NumberFormatException ignored) {
                // skip a broken line
            }
        }
        r.list.addAll(pickEntries(tmp, MAX_KEEP, null, null));
        return r;
    }

    /** Newest first, one per key, without excluded keys, only keys still installed (live), at most max. */
    static ArrayList<String> pick(List<Entry> entries, int max, Collection<String> exclude, Collection<String> live) {
        ArrayList<String> out = new ArrayList<>();
        for (Entry e : pickEntries(entries, max, exclude, live)) out.add(e.key);
        return out;
    }

    private static ArrayList<Entry> pickEntries(List<Entry> entries, int max, Collection<String> exclude, Collection<String> live) {
        ArrayList<Entry> sorted = new ArrayList<>(entries);
        // stable sort keeps the given order for equal times
        Collections.sort(sorted, (a, b) -> Long.compare(b.t, a.t));
        ArrayList<Entry> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Entry e : sorted) {
            if (out.size() >= Math.max(0, max)) break;
            if (e.key == null || !seen.add(e.key)) continue;
            if (exclude != null && exclude.contains(e.key)) continue;
            if (live != null && !live.contains(e.key)) continue;
            out.add(e);
        }
        return out;
    }

    static int clampCount(int n) { return Math.max(0, Math.min(MAX_SHOW, n)); }
}
