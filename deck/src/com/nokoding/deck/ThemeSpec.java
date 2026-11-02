package com.nokoding.deck;

import java.util.Locale;

import org.json.JSONObject;

/**
 * 2.17 theme editor: one colour theme as plain data, and its small JSON file. Pure Java (org.json only).
 *
 * File (themes folder, one theme per file, at most 8 KB):
 *   {"format":1,"name":"Neon Tea","colors":{"accent":"#FF2E97","accent2":"#A855F7","background":"#07040D",
 *    "panel":"#120A21","text":"#F4EEFF"}}
 * Optional colours: "background2" and "dim" (worked out from the others when missing). Unknown keys are ignored.
 * Reading is defensive and never throws: wrong types, bad colours, a missing required colour, a wrong format
 * number or a huge file give null. The warning colour is not part of a theme on purpose: it is reserved for lock
 * failure and danger, so no theme can hide it.
 */
final class ThemeSpec {
    static final int FORMAT = 1;
    static final int MAX_FILE = 8 * 1024, MAX_NAME = 24;

    String name = "CUSTOM";
    int a, b, bg0, bg1, panel, text, dim;

    ThemeSpec() {}

    ThemeSpec copy() {
        ThemeSpec s = new ThemeSpec();
        s.name = name; s.a = a; s.b = b; s.bg0 = bg0; s.bg1 = bg1; s.panel = panel; s.text = text; s.dim = dim;
        return s;
    }

    /** A theme from the five editable colours, the other two worked out. */
    static ThemeSpec of(String name, int a, int b, int bg0, int panel, int text) {
        ThemeSpec s = new ThemeSpec();
        s.name = safeName(name);
        s.a = opaque(a); s.b = opaque(b); s.bg0 = opaque(bg0); s.panel = opaque(panel); s.text = opaque(text);
        s.bg1 = mix(s.bg0, s.a, 0.14f);
        s.dim = mix(s.text, s.bg0, 0.42f);
        return s;
    }

    /** A theme with all seven colours given (used to carry an existing theme over). */
    static ThemeSpec full(String name, int a, int b, int bg0, int bg1, int panel, int text, int dim) {
        ThemeSpec s = of(name, a, b, bg0, panel, text);
        s.bg1 = opaque(bg1);
        s.dim = opaque(dim);
        return s;
    }

    static int opaque(int c) { return c | 0xFF000000; }

    static int mix(int c1, int c2, float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int bl = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- colour text and contrast

    /** "#RRGGBB" in capitals. */
    static String hex(int c) { return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF); }

    /** The colour of "#RRGGBB" or "RRGGBB" (exactly six hex digits), opaque, or 0 for anything else. */
    static int parseHex(String s) {
        if (s == null) return 0;
        String h = s.startsWith("#") ? s.substring(1) : s;
        if (h.length() != 6) return 0;
        int v = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(h.charAt(i), 16);
            if (d < 0) return 0;
            v = (v << 4) | d;
        }
        return 0xFF000000 | v;
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    static double luminance(int c) {
        double r = lin(((c >> 16) & 255) / 255.0), g = lin(((c >> 8) & 255) / 255.0), b = lin((c & 255) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }

    /** WCAG contrast ratio, 1 (same) to 21 (black on white). */
    static double contrast(int c1, int c2) {
        double l1 = luminance(c1), l2 = luminance(c2);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** What is wrong with the readability of this theme, in plain capitals, or null when it is fine. */
    String warning() {
        if (contrast(text, bg0) < 4.5) return "TEXT IS HARD TO READ ON THE BACKGROUND";
        if (contrast(text, panel) < 4.5) return "TEXT IS HARD TO READ ON THE PANELS";
        if (contrast(a, bg0) < 3.0) return "THE ACCENT IS HARD TO SEE ON THE BACKGROUND";
        if (contrast(dim, bg0) < 2.5) return "THE QUIET TEXT IS TOO FAINT";
        return null;
    }

    // ---------------------------------------------------------------- the file

    /** Letters, digits, space, dot, dash and underscore, at most 24, trimmed and capitals. Empty becomes CUSTOM. */
    static String safeName(String n) {
        if (n == null) return "CUSTOM";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length() && b.length() < MAX_NAME; i++) {
            char c = n.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == '-' || c == '_') b.append(c);
        }
        String s = b.toString().trim().toUpperCase(Locale.ROOT);
        while (!s.isEmpty() && !Character.isLetterOrDigit(s.charAt(0))) s = s.substring(1);   // no leading dot or dash
        return s.isEmpty() ? "CUSTOM" : s;
    }

    /** True if safeName would keep the whole text (for file names: no path, no surprises). */
    static boolean cleanName(String n) {
        return n != null && !n.isEmpty() && n.length() <= MAX_NAME && Character.isLetterOrDigit(n.charAt(0)) && safeName(n).equals(n.trim().toUpperCase(Locale.ROOT)) && n.equals(n.trim());
    }

    String toJson() {
        StringBuilder s = new StringBuilder(300);
        s.append("{\"format\":").append(FORMAT).append(",\"name\":").append(JSONObject.quote(name)).append(",\"colors\":{")
                .append("\"accent\":\"").append(hex(a)).append("\",\"accent2\":\"").append(hex(b)).append("\",")
                .append("\"background\":\"").append(hex(bg0)).append("\",\"background2\":\"").append(hex(bg1)).append("\",")
                .append("\"panel\":\"").append(hex(panel)).append("\",\"text\":\"").append(hex(text)).append("\",")
                .append("\"dim\":\"").append(hex(dim)).append("\"}}");
        return s.toString();
    }

    /** Reads a theme file. Never throws. @return null when the text is not a usable theme. */
    static ThemeSpec parse(String json) {
        if (json == null || json.isEmpty() || json.length() > MAX_FILE) return null;
        try {
            JSONObject root = new JSONObject(json);
            Object f = root.opt("format");
            if (!(f instanceof Number) || ((Number) f).intValue() != FORMAT || ((Number) f).doubleValue() != FORMAT) return null;
            Object nm = root.opt("name");
            String name = nm instanceof String ? safeName((String) nm) : "CUSTOM";
            Object co = root.opt("colors");
            if (!(co instanceof JSONObject)) return null;
            JSONObject c = (JSONObject) co;
            int a = col(c.opt("accent")), b = col(c.opt("accent2")), bg = col(c.opt("background")), pn = col(c.opt("panel")), tx = col(c.opt("text"));
            if (a == 0 || b == 0 || bg == 0 || pn == 0 || tx == 0) return null;
            ThemeSpec s = of(name, a, b, bg, pn, tx);
            if (c.has("background2")) { int v = col(c.opt("background2")); if (v == 0) return null; s.bg1 = v; }
            if (c.has("dim")) { int v = col(c.opt("dim")); if (v == 0) return null; s.dim = v; }
            return s;
        } catch (org.json.JSONException e) {
            return null;
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }

    private static int col(Object o) { return o instanceof String ? parseHex((String) o) : 0; }

    boolean sameColors(ThemeSpec o) {
        return o != null && a == o.a && b == o.b && bg0 == o.bg0 && bg1 == o.bg1 && panel == o.panel && text == o.text && dim == o.dim;
    }
}
