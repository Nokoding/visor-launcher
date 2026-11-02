package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 2.3: an animation and its options, Wallpaper Engine style. Built from a tiny text schema,
 * one option per line:
 *
 *   key | LABEL | TYPE | args | default
 *
 * TYPE is CHOICE, INT, FLOAT, BOOL or COLOR. args: CHOICE "A, B, C"; INT and FLOAT
 * "min..max/step unit" (unit optional); BOOL and COLOR nothing. Bad lines are skipped.
 * Values live in a Store (SharedPreferences on the box) under anim.ID.KEY as text, and a
 * theme file can set them with apply("key = value" lines), the same text. Pure Java.
 */
final class AnimSpec {

    interface Store {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    final String id, name;
    final ArrayList<AnimOption> options = new ArrayList<>();
    /** Schema lines that could not be read (for the tests and the log). */
    int skipped;

    AnimSpec(String id, String name) {
        this.id = id;
        this.name = name;
    }

    static AnimSpec parse(String id, String name, String schema) {
        AnimSpec s = new AnimSpec(id, name);
        if (schema == null) return s;
        for (String raw : schema.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            AnimOption o = option(line);
            if (o == null || s.get(o.key) != null) s.skipped++;
            else s.options.add(o);
        }
        return s;
    }

    static AnimOption option(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length != 5) return null;
        String key = f[0].trim(), label = f[1].trim().toUpperCase(Locale.ROOT), type = f[2].trim().toUpperCase(Locale.ROOT);
        String args = f[3].trim(), def = f[4].trim();
        if (!key.matches("[a-z][a-z0-9_.]*") || label.isEmpty()) return null;
        AnimOption o;
        try {
            switch (type) {
                case "CHOICE": {
                    String[] c = args.split(",");
                    for (int i = 0; i < c.length; i++) c[i] = c[i].trim().toUpperCase(Locale.ROOT);
                    if (c.length < 2) return null;
                    for (String x : c) if (x.isEmpty()) return null;
                    o = new AnimOption(key, label, AnimOption.CHOICE, 0, 0, 1, c, "", 0);
                    break;
                }
                case "INT":
                case "FLOAT": {
                    boolean fl = type.equals("FLOAT");
                    String range = args, unit = "";
                    int sp = args.indexOf(' ');
                    if (sp > 0) { range = args.substring(0, sp); unit = args.substring(sp + 1).trim().toUpperCase(Locale.ROOT); }
                    int dots = range.indexOf(".."), slash = range.indexOf('/');
                    if (dots <= 0) return null;
                    double min = Double.parseDouble(range.substring(0, dots));
                    double max = Double.parseDouble(slash > dots ? range.substring(dots + 2, slash) : range.substring(dots + 2));
                    double step = slash > dots ? Double.parseDouble(range.substring(slash + 1)) : (fl ? 0.01 : 1);
                    if (!(max >= min) || !(step > 0)) return null;
                    o = new AnimOption(key, label, fl ? AnimOption.FLOAT_RANGE : AnimOption.INT_RANGE, min, max, step, null, unit, min);
                    break;
                }
                case "BOOL":
                    o = new AnimOption(key, label, AnimOption.BOOL, 0, 1, 1, null, "", 0);
                    break;
                case "COLOR":
                    o = new AnimOption(key, label, AnimOption.COLOR, 0, 0, 1, null, "", 0xFFFF2E97L);
                    break;
                default:
                    return null;
            }
        } catch (RuntimeException bad) {
            return null;
        }
        if (!o.parse(def)) return null;
        // the parsed default becomes the option's default
        return new AnimOption(o.key, o.label, o.type, o.min, o.max, o.step, o.choices, o.unit, o.value);
    }

    AnimOption get(String key) {
        for (AnimOption o : options) if (o.key.equals(key)) return o;
        return null;
    }

    String prefKey(AnimOption o) { return "anim." + id + "." + o.key; }

    /** Reads every option from the store. Missing or bad values keep the default. */
    void load(Store st) {
        for (AnimOption o : options) {
            o.reset();
            String v = st == null ? null : st.get(prefKey(o));
            if (v != null && !o.parse(v)) o.reset();
        }
    }

    void save(Store st, AnimOption o) { if (st != null && o != null) st.put(prefKey(o), o.text()); }

    void resetAll(Store st) {
        for (AnimOption o : options) {
            o.reset();
            if (st != null) st.remove(prefKey(o));
        }
    }

    /**
     * Theme file style: "key = value" lines, # comments. Unknown keys and bad values are ignored.
     * @return how many options were set.
     */
    int apply(String text) {
        if (text == null) return 0;
        int n = 0;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            AnimOption o = get(line.substring(0, eq).trim());
            if (o != null && o.parse(line.substring(eq + 1))) n++;
        }
        return n;
    }

    /** The current values as "key = value" lines (what a theme file would hold). */
    String dump() {
        StringBuilder b = new StringBuilder();
        for (AnimOption o : options) b.append(o.key).append(" = ").append(o.text()).append('\n');
        return b.toString();
    }
}
