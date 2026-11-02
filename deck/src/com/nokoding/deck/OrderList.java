package com.nokoding.deck;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Ordered key lists (app order, dock pins) and their persisted form. Pure Java. */
final class OrderList {
    private OrderList() {}

    static void split(String s, List<String> out) {
        out.clear();
        if (s == null || s.isEmpty()) return;
        for (String part : s.split("\n")) if (!part.isEmpty() && !out.contains(part)) out.add(part);
    }

    static List<String> splitSet(String s) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        split(s, out);
        return out;
    }

    static String join(Iterable<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }

    static boolean move(List<String> list, String key, int delta) {
        int i = list.indexOf(key), j = i + delta;
        if (i < 0 || j < 0 || j >= list.size()) return false;
        Collections.swap(list, i, j);
        return true;
    }

    /** @return 1 added, 0 already pinned, -1 full. Only keys still in live count toward max. */
    static int pin(List<String> pinned, String key, int max, Collection<String> live) {
        if (pinned.contains(key)) return 0;
        int n = 0;
        for (String k : pinned) if (live == null || live.contains(k)) n++;
        if (n >= max) return -1;
        pinned.add(key);
        return 1;
    }

    static boolean unpin(List<String> pinned, String key) {
        return pinned.remove(key);
    }
}
