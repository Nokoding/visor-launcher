package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2.6 button remap: the rules and how they are saved. Pure Java.
 *
 * Storage: one SharedPreferences entry per button, named "rm:" plus the button id
 * ("rm:kc:166", "rm:sc:233:4012:9001"). The value is one line, versioned:
 *   v1|SHORT_ID|SHORT_ARG|LONG_ID|LONG_ARG|LABEL
 * The argument is the package name for LAUNCH APP and empty otherwise. LABEL is the name the
 * learn screen showed, so a learned button keeps a readable name. Anything that does not read
 * back cleanly is dropped on load, so bad data can never take a key: no rule means the button
 * is left alone.
 *
 * A table is a snapshot: sorted arrays, looked up by binary search with no allocation, swapped in
 * as one object when the saved rules change.
 */
final class RemapTable {
    static final String PREFIX = "rm:";
    static final String VERSION = "v1";
    static final int MAX_RULES = 300, MAX_LABEL = 40;
    /** Boolean switches saved next to the rules. */
    static final String KEY_ADV = "rmAdv", KEY_NAV = "rmNav", KEY_GAMES = "rmGames", KEY_HOME = "rmHome";

    /** What one button does. Action numbers are RemapAction's. */
    static final class Rule {
        int shortA = RemapAction.DEFAULT, longA = RemapAction.DEFAULT;
        String shortArg = "", longArg = "";
        String label = "";

        Rule() {}

        Rule(int shortA, String shortArg, int longA, String longArg, String label) {
            this.shortA = shortA; this.shortArg = shortArg == null ? "" : shortArg;
            this.longA = longA; this.longArg = longArg == null ? "" : longArg;
            this.label = label == null ? "" : label;
        }

        Rule copy() { return new Rule(shortA, shortArg, longA, longArg, label); }

        /** True if this rule changes anything (a learned button with both DEFAULT does not). */
        boolean active() { return shortA != RemapAction.DEFAULT || longA != RemapAction.DEFAULT; }

        /** True if the button is read as another one inside Deck (a DECK KEY action). */
        boolean swap() { return RemapAction.deckKey(shortA); }

        /**
         * Makes the rule valid for this button: actions the button may not have, an app action
         * without a package, a DECK KEY action next to a long press all fall back to DEFAULT.
         */
        Rule fix(long key) {
            if (!RemapAction.allowedFor(shortA, key)) { shortA = RemapAction.DEFAULT; }
            if (!RemapAction.allowedFor(longA, key) || RemapAction.deckKey(longA)) { longA = RemapAction.DEFAULT; }
            if (shortA == RemapAction.APP && !PsRules.plainPackage(shortArg)) shortA = RemapAction.DEFAULT;
            if (longA == RemapAction.APP && !PsRules.plainPackage(longArg)) longA = RemapAction.DEFAULT;
            if (shortA != RemapAction.APP) shortArg = "";
            if (longA != RemapAction.APP) longArg = "";
            if (RemapAction.deckKey(shortA)) { longA = RemapAction.DEFAULT; longArg = ""; }
            label = cleanLabel(label);
            return this;
        }

        /** Looks an app up by package name for the Setup list. Return null when unknown. */
        interface Namer { String name(String pkg); }

        /** One line for the Setup list: "DEFAULT", "SHORT: VOLUME UP", "SHORT: HOME  LONG: SCREENSHOT". */
        String summary() { return summary(null); }

        String summary(Namer namer) {
            if (!active()) return "DEFAULT";
            StringBuilder b = new StringBuilder();
            if (shortA != RemapAction.DEFAULT) b.append("SHORT: ").append(shown(shortA, shortArg, namer));
            if (longA != RemapAction.DEFAULT) {
                if (b.length() > 0) b.append("  ");
                b.append("LONG: ").append(shown(longA, longArg, namer));
            }
            return b.toString();
        }

        /** The action as the list shows it: LAUNCH APP carries the app's name, or its package if no name is known. */
        static String shown(int a, String arg, Namer namer) {
            if (a != RemapAction.APP) return RemapAction.name(a);
            String n = null;
            try { n = namer == null ? null : namer.name(arg); } catch (RuntimeException ignored) {}
            if (n == null || n.isEmpty()) n = arg;
            return "APP " + n.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private final long[] keys;
    private final Rule[] rules;

    static final RemapTable EMPTY = new RemapTable(new long[0], new Rule[0]);

    private RemapTable(long[] keys, Rule[] rules) { this.keys = keys; this.rules = rules; }

    int size() { return keys.length; }

    long keyAt(int i) { return keys[i]; }

    Rule ruleAt(int i) { return rules[i]; }

    /** The rule for a button, or null. No allocation. */
    Rule get(long key) {
        int lo = 0, hi = keys.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = keys[mid];
            if (v < key) lo = mid + 1; else if (v > key) hi = mid - 1; else return rules[mid];
        }
        return null;
    }

    /** True if some rule changes something for a button (any button, any press). */
    boolean any() {
        for (Rule r : rules) if (r.active()) return true;
        return false;
    }

    // ---------------------------------------------------------------- text form

    /** Plain label: letters, digits, space and a few marks, cut to MAX_LABEL, no '|'. */
    static String cleanLabel(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_LABEL; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ':' || c == '_' || c == '(' || c == ')';
            b.append(ok ? c : '?');
        }
        return b.toString().trim();
    }

    static String write(Rule r) {
        return VERSION + "|" + RemapAction.id(r.shortA) + "|" + r.shortArg + "|" + RemapAction.id(r.longA) + "|" + r.longArg + "|" + cleanLabel(r.label);
    }

    /** @return the rule, or null if the text is not a v1 rule. Unknown action ids read as DEFAULT. */
    static Rule parse(String s) {
        if (s == null || s.length() > 400) return null;
        String[] p = s.split("\\|", -1);
        if (p.length != 6 || !VERSION.equals(p[0])) return null;
        int a = RemapAction.byId(p[1]), b = RemapAction.byId(p[3]);
        return new Rule(a < 0 ? RemapAction.DEFAULT : a, p[2], b < 0 ? RemapAction.DEFAULT : b, p[4], p[5]);
    }

    static String prefKey(long key) { return PREFIX + RemapKey.id(key); }

    /** The button of a saved entry name, or INVALID. */
    static long keyOfPref(String prefKey) {
        if (prefKey == null || !prefKey.startsWith(PREFIX)) return RemapKey.INVALID;
        return RemapKey.parse(prefKey.substring(PREFIX.length()));
    }

    // ---------------------------------------------------------------- load and save

    /**
     * Builds a table from everything saved. Entries that do not read back (bad version, bad id,
     * a protected button, a rule made only of nonsense) are skipped. At most MAX_RULES are kept,
     * in entry name order, so the result does not depend on the order the map hands them over.
     */
    static RemapTable load(Map<String, ?> all) {
        if (all == null || all.isEmpty()) return EMPTY;
        ArrayList<String> names = new ArrayList<>();
        for (String k : all.keySet()) if (k != null && k.startsWith(PREFIX)) names.add(k);
        Collections.sort(names);
        LinkedHashMap<Long, Rule> found = new LinkedHashMap<>();
        for (String name : names) {
            if (found.size() >= MAX_RULES) break;
            Object v = all.get(name);
            if (!(v instanceof String)) continue;
            long key = keyOfPref(name);
            if (!RemapKey.remappable(key)) continue;
            Rule r = parse((String) v);
            if (r == null) continue;
            found.put(key, r.fix(key));
        }
        long[] ks = new long[found.size()];
        int n = 0;
        for (Long k : found.keySet()) ks[n++] = k;
        Arrays.sort(ks);
        Rule[] rs = new Rule[ks.length];
        for (int i = 0; i < ks.length; i++) rs[i] = found.get(ks[i]);
        return new RemapTable(ks, rs);
    }

    /** Where rules and switches are written (SharedPreferences.Editor in the app, a map in the tests). */
    interface Sink {
        void put(String key, String value);
        void putBool(String key, boolean value);
        void remove(String key);
    }

    /** Saves one button's rule. @return false (nothing written) for a button that cannot be remapped. */
    static boolean save(Sink s, long key, Rule r) {
        if (!RemapKey.remappable(key) || r == null) return false;
        s.put(prefKey(key), write(r.copy().fix(key)));
        return true;
    }

    static void clear(Sink s, long key) {
        if (RemapKey.valid(key)) s.remove(prefKey(key));
    }

    /** Removes every rule and turns every switch off. @return how many rules were removed. */
    static int resetAll(Sink s, Map<String, ?> all) {
        int n = 0;
        if (all != null) {
            for (String k : new ArrayList<>(all.keySet())) {
                if (k != null && k.startsWith(PREFIX)) { s.remove(k); n++; }
            }
        }
        s.putBool(KEY_ADV, false);
        s.putBool(KEY_NAV, false);
        s.putBool(KEY_GAMES, false);
        s.putBool(KEY_HOME, false);
        return n;
    }

    /**
     * The escape: removes the rules of the navigation buttons (D-pad, OK, Back and the Enter
     * and Escape keys) and turns SYSTEM WIDE NAVIGATION REMAP off. Other rules stay.
     * @return how many rules were removed.
     */
    static int clearNav(Sink s, Map<String, ?> all) {
        int n = 0;
        if (all != null) {
            for (String k : new ArrayList<>(all.keySet())) {
                if (k == null || !k.startsWith(PREFIX)) continue;
                long key = keyOfPref(k);
                if (!RemapKey.valid(key) || RemapKey.nav(key)) { s.remove(k); n++; }   // also drops entries that do not even parse
            }
        }
        s.putBool(KEY_NAV, false);
        n += clearHome(s, all);
        return n;
    }

    /** 2.17: removes the Home rule and turns the Home remap off. The escape, the Home escape and RESET ALL REMAPS all use it. */
    static int clearHome(Sink s, Map<String, ?> all) {
        int n = 0;
        if (all != null) {
            for (String k : new ArrayList<>(all.keySet())) {
                if (k == null || !k.startsWith(PREFIX)) continue;
                if (keyOfPref(k) == RemapKey.KC_HOME) { s.remove(k); n++; }
            }
        }
        s.putBool(KEY_HOME, false);
        return n;
    }

    /** Removes the rules of the advanced buttons and turns the advanced switches off (TURN OFF ADVANCED REMAP). */
    static int clearAdvanced(Sink s, Map<String, ?> all) {
        int n = 0;
        if (all != null) {
            for (String k : new ArrayList<>(all.keySet())) {
                if (k == null || !k.startsWith(PREFIX)) continue;
                long key = keyOfPref(k);
                int c = RemapKey.valid(key) ? RemapKey.cls(key) : RemapKey.NAV;
                if (c == RemapKey.NAV || c == RemapKey.ADVANCED) { s.remove(k); n++; }
            }
        }
        s.putBool(KEY_ADV, false);
        s.putBool(KEY_NAV, false);
        s.putBool(KEY_HOME, false);
        return n;
    }
}
