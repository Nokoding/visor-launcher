package com.nokoding.deck;

import java.util.function.LongFunction;

/**
 * 2.9 button combos: two buttons held together run an action (L1 + R1 opens the PS menu).
 * Pure Java, no Android types, so the JVM tests cover all of it.
 *
 * Which buttons may be in a combo: the spare remote buttons (RemapKey.STANDARD, including a
 * learned scan code button) and ten gamepad buttons (X, Y, L1, R1, L2, R2, both stick clicks,
 * START, SELECT). The gamepad buttons are protected for single remaps, but a combo needs two
 * buttons, so one stray press never does anything. D-pad, OK, Back, Home, Power, the PS button,
 * A, B and typing keys can never be in a combo.
 *
 * ComboTable is an immutable snapshot: with() and without() return a new table. It is saved as
 * one plain ASCII line of at most 2000 characters:
 *
 *   v1;ID_A+ID_B|ACTION_ID|ARG|LABEL;ID_A+ID_B|ACTION_ID|ARG|LABEL
 *
 * Entries are separated by ';', fields by '|'. ARG is a package name for LAUNCH APP and empty
 * for every other action. Saved text that does not read back cleanly is dropped entry by entry,
 * the rest is kept, and parse never throws.
 */
final class ComboTable {
    static final ComboTable EMPTY = new ComboTable(new Combo[0]);
    static final int MAX = 12;
    static final String VERSION = "v1";
    static final String PREF = "combos";
    static final int MAX_TEXT = 2000, MAX_LABEL = 40;

    private final Combo[] items;

    private ComboTable(Combo[] items) { this.items = items; }

    // ---------------------------------------------------------------- which buttons

    /** True for a spare remote button, a learned scan code button, and the ten gamepad buttons X to SELECT. */
    static boolean allowedKey(long key) {
        if (!RemapKey.valid(key)) return false;
        if (RemapKey.isScan(key)) return RemapKey.cls(key) == RemapKey.STANDARD;
        int c = RemapKey.code(key);
        if (c == 99 || c == 100 || (c >= 102 && c <= 109)) return true;
        return RemapKey.cls(key) == RemapKey.STANDARD;
    }

    /** Plain capital reason a button cannot be in a combo, or null when it can. */
    static String whyNot(long key) {
        if (!RemapKey.valid(key)) return "NOT A VALID BUTTON";
        if (allowedKey(key)) return null;
        int c = RemapKey.code(key);
        if (RemapKey.nav(key) || c == 96 || c == 97) return "THAT BUTTON IS NEEDED TO MOVE AROUND";
        if (c == 3 || c == 110 || c == 26 || c == 223 || c == 224 || c == 276) return "THE PS BUTTON AND HOME CANNOT BE USED";
        if (RemapKey.cls(key) == RemapKey.ADVANCED) return "THAT BUTTON NEEDS ADVANCED REMAP";
        return "THAT BUTTON CANNOT BE USED IN A COMBO";
    }

    // ---------------------------------------------------------------- one combo

    /** Two buttons (a is the smaller key, so the order they were pressed in does not matter) and what they do. */
    static final class Combo {
        final long a, b;
        int action;
        String arg = "";
        String label = "";

        Combo(long k1, long k2) {
            if (k1 <= k2) { a = k1; b = k2; } else { a = k2; b = k1; }
        }

        Combo copy() {
            Combo c = new Combo(a, b);
            c.action = action;
            c.arg = arg;
            c.label = label;
            return c;
        }
    }

    /**
     * True if this action may run from a combo: a real action (not DEFAULT, not DO NOTHING, not an
     * INSIDE DECK key), LAUNCH APP only with a plain package name, and allowed for both buttons.
     */
    static boolean validAction(int action, String arg, long a, long b) {
        if (!RemapAction.valid(action)) return false;
        if (action == RemapAction.DEFAULT || action == RemapAction.NOTHING) return false;
        if (RemapAction.deckKey(action)) return false;
        if (action == RemapAction.APP && !PsRules.plainPackage(arg)) return false;
        return RemapAction.allowedFor(action, a) && RemapAction.allowedFor(action, b);
    }

    // ---------------------------------------------------------------- reading

    int size() { return items.length; }

    /** A copy of entry i, or null if i is out of range. */
    Combo get(int i) { return i >= 0 && i < items.length ? items[i].copy() : null; }

    /** Entry index of the pair in either order, or -1. No allocation. */
    int indexOf(long k1, long k2) {
        long a = Math.min(k1, k2), b = Math.max(k1, k2);
        for (int i = 0; i < items.length; i++) if (items[i].a == a && items[i].b == b) return i;
        return -1;
    }

    int actionAt(int i) { return items[i].action; }

    String argAt(int i) { return items[i].arg; }

    /** A copy of the combo for this pair (either order), or null. */
    Combo find(long k1, long k2) {
        int i = indexOf(k1, k2);
        return i < 0 ? null : items[i].copy();
    }

    /** True if the button is a member of some combo. No allocation. */
    boolean involves(long key) {
        for (int i = 0; i < items.length; i++) if (items[i].a == key || items[i].b == key) return true;
        return false;
    }

    // ---------------------------------------------------------------- editing

    /** True if this pair may be added: two different allowed buttons, and room if the pair is new. */
    static boolean canAdd(ComboTable t, long a, long b) {
        if (t == null) t = EMPTY;
        if (a == b || !allowedKey(a) || !allowedKey(b)) return false;
        return t.indexOf(a, b) >= 0 || t.items.length < MAX;
    }

    /** True if with(c) would store c: canAdd, a valid action, and the whole saved line stays within 2000 characters. */
    static boolean canSet(ComboTable t, Combo c) {
        if (t == null) t = EMPTY;
        if (c == null || !canAdd(t, c.a, c.b) || !validAction(c.action, c.arg, c.a, c.b)) return false;
        return t.build(norm(c)).write().length() <= MAX_TEXT;
    }

    /** A new table with the combo added, or replacing the one for the same pair. This table unchanged if canSet is false. */
    ComboTable with(Combo c) {
        if (!canSet(this, c)) return this;
        return build(norm(c));
    }

    /** A new table without the pair (either order). This table unchanged if the pair is not there. */
    ComboTable without(long k1, long k2) {
        int at = indexOf(k1, k2);
        if (at < 0) return this;
        if (items.length == 1) return EMPTY;
        Combo[] n = new Combo[items.length - 1];
        for (int i = 0, j = 0; i < items.length; i++) if (i != at) n[j++] = items[i];
        return new ComboTable(n);
    }

    private static Combo norm(Combo c) {
        Combo n = c.copy();
        if (n.action != RemapAction.APP) n.arg = "";
        n.arg = n.arg == null ? "" : n.arg;
        n.label = cleanLabel(n.label);
        return n;
    }

    private static String cleanLabel(String s) {
        String l = RemapTable.cleanLabel(s);
        return l.length() > MAX_LABEL ? l.substring(0, MAX_LABEL).trim() : l;
    }

    private ComboTable build(Combo c) {
        int at = indexOf(c.a, c.b);
        if (at >= 0) {
            Combo[] n = items.clone();
            n[at] = c;
            return new ComboTable(n);
        }
        Combo[] n = java.util.Arrays.copyOf(items, items.length + 1);
        n[items.length] = c;
        return new ComboTable(n);
    }

    // ---------------------------------------------------------------- saving

    /** The saved line, or "" when empty. */
    String save() { return items.length == 0 ? "" : write(); }

    private String write() {
        StringBuilder b = new StringBuilder(VERSION);
        for (Combo c : items) {
            b.append(';').append(RemapKey.id(c.a)).append('+').append(RemapKey.id(c.b))
                    .append('|').append(RemapAction.id(c.action))
                    .append('|').append(c.action == RemapAction.APP ? c.arg : "")
                    .append('|').append(c.label);
        }
        return b.toString();
    }

    /** Reads a saved line. Never throws. A bad entry is dropped and the others are kept. */
    static ComboTable parse(String s) {
        try {
            return parseLine(s);
        } catch (RuntimeException e) {
            return EMPTY;
        }
    }

    private static ComboTable parseLine(String s) {
        if (s == null || s.isEmpty() || s.length() > MAX_TEXT) return EMPTY;
        String[] entries = s.split(";", -1);
        if (!VERSION.equals(entries[0])) return EMPTY;
        ComboTable t = EMPTY;
        for (int i = 1; i < entries.length && t.items.length < MAX; i++) {
            Combo c = parseEntry(entries[i]);
            if (c == null || t.indexOf(c.a, c.b) >= 0) continue;
            t = t.build(c);
        }
        return t;
    }

    private static Combo parseEntry(String e) {
        String[] f = e.split("\\|", -1);
        if (f.length != 4) return null;
        int plus = f[0].indexOf('+');
        if (plus < 0 || f[0].indexOf('+', plus + 1) >= 0) return null;
        long k1 = RemapKey.parse(f[0].substring(0, plus)), k2 = RemapKey.parse(f[0].substring(plus + 1));
        if (k1 == k2 || !allowedKey(k1) || !allowedKey(k2)) return null;
        int action = RemapAction.byId(f[1]);
        if (action < 0) return null;
        String arg = action == RemapAction.APP ? f[2] : "";
        if (!validAction(action, arg, k1, k2)) return null;
        Combo c = new Combo(k1, k2);
        c.action = action;
        c.arg = arg;
        c.label = cleanLabel(f[3]);
        return c;
    }

    // ---------------------------------------------------------------- words

    /** "L1 + R1". The namer gives each button's shown name (a learned label, say); null or a failing namer uses RemapKey.name. */
    static String describe(Combo c, LongFunction<String> namer) {
        return nameOf(c.a, namer) + " + " + nameOf(c.b, namer);
    }

    /** "L1 + R1  >  OPEN VISOR PS MENU"; LAUNCH APP carries the package after a colon. */
    static String line(Combo c, LongFunction<String> namer) {
        String act = RemapAction.name(c.action);
        if (c.action == RemapAction.APP && c.arg != null && !c.arg.isEmpty()) act = act + ": " + c.arg;
        return describe(c, namer) + "  >  " + act;
    }

    private static String nameOf(long key, LongFunction<String> namer) {
        String n = null;
        if (namer != null) {
            try { n = namer.apply(key); } catch (RuntimeException ignored) { n = null; }
        }
        return n == null || n.isEmpty() ? RemapKey.name(key) : n;
    }
}
