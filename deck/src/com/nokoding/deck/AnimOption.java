package com.nokoding.deck;

import java.util.Locale;

/**
 * 2.3: one option of an animation (see AnimSpec). Typed, bounded and with a default, so Setup
 * can draw a row for it and a theme file can later set it by key. Values are stored as text:
 * CHOICE by name ("TYPE"), INT_RANGE and FLOAT_RANGE as numbers ("90", "1.25"), BOOL as
 * "on" or "off", COLOR as "#RRGGBB" or "#AARRGGBB". Bad text is refused and the value kept.
 * Pure Java.
 */
final class AnimOption {
    static final int CHOICE = 0, INT_RANGE = 1, FLOAT_RANGE = 2, BOOL = 3, COLOR = 4;
    static final String[] TYPE_NAMES = {"CHOICE", "INT", "FLOAT", "BOOL", "COLOR"};
    /** COLOR rows step through these (the built in theme accents). */
    static final int[] PALETTE = {0xFFFF2E97, 0xFF22E4FF, 0xFFB8FF3D, 0xFFFF7A1A, 0xFFFFC93D, 0xFFEDEDF7};

    final String key, label, unit;
    final int type;
    final double min, max, step, def;
    final String[] choices;
    double value;

    AnimOption(String key, String label, int type, double min, double max, double step, String[] choices, String unit, double def) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.choices = choices == null ? new String[0] : choices;
        this.unit = unit == null ? "" : unit;
        if (type == CHOICE) { min = 0; max = Math.max(0, this.choices.length - 1); step = 1; }
        if (type == BOOL) { min = 0; max = 1; step = 1; }
        if (type == COLOR) { min = 0; max = 0xFFFFFFFFL; step = 1; }
        this.min = min;
        this.max = Math.max(min, max);
        this.step = step > 0 ? step : (type == FLOAT_RANGE ? 0.01 : 1);
        this.def = clampSnap(def);
        this.value = this.def;
    }

    int i() { return (int) Math.round(value); }

    float f() { return (float) value; }

    boolean on() { return value >= 0.5; }

    /** Color as an ARGB int. */
    int color() { return (int) (long) value; }

    String choice() { return choices.length == 0 ? "" : choices[Math.max(0, Math.min(choices.length - 1, i()))]; }

    void reset() { value = def; }

    double clampSnap(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        if (type == COLOR) return (double) (((long) v) & 0xFFFFFFFFL);
        v = Math.max(min, Math.min(max, v));
        double k = Math.round((v - min) / step);
        v = min + k * step;
        v = Math.max(min, Math.min(max, v));
        return type == FLOAT_RANGE ? Math.round(v * 1000.0) / 1000.0 : Math.round(v);
    }

    /** Set from stored or theme text. @return false if the text did not fit this option. */
    boolean parse(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        try {
            switch (type) {
                case CHOICE:
                    for (int k = 0; k < choices.length; k++) {
                        if (choices[k].equalsIgnoreCase(t) || choices[k].replace(' ', '_').equalsIgnoreCase(t)) { value = k; return true; }
                    }
                    return false;
                case BOOL: {
                    String l = t.toLowerCase(Locale.ROOT);
                    if (l.equals("on") || l.equals("true") || l.equals("yes") || l.equals("1")) { value = 1; return true; }
                    if (l.equals("off") || l.equals("false") || l.equals("no") || l.equals("0")) { value = 0; return true; }
                    return false;
                }
                case COLOR: {
                    if (!t.startsWith("#")) return false;
                    String h = t.substring(1);
                    if (h.length() != 6 && h.length() != 8) return false;
                    long c = Long.parseLong(h, 16);
                    if (h.length() == 6) c |= 0xFF000000L;
                    value = c & 0xFFFFFFFFL;
                    return true;
                }
                default: {
                    String num = t.toLowerCase(Locale.ROOT);
                    if (num.endsWith("x")) num = num.substring(0, num.length() - 1).trim();
                    double v = Double.parseDouble(num);
                    if (Double.isNaN(v) || Double.isInfinite(v)) return false;
                    value = clampSnap(v);
                    return true;
                }
            }
        } catch (RuntimeException bad) {
            return false;
        }
    }

    /** The stored form. */
    String text() {
        switch (type) {
            case CHOICE: return choice();
            case BOOL: return on() ? "on" : "off";
            case COLOR: return String.format(Locale.ROOT, "#%08X", color());
            case FLOAT_RANGE: return trim(value);
            default: return Integer.toString(i());
        }
    }

    /** What a Setup row shows. */
    String display() {
        switch (type) {
            case CHOICE: return choice();
            case BOOL: return on() ? "ON" : "OFF";
            case COLOR: return String.format(Locale.ROOT, "#%06X", color() & 0xFFFFFF);
            case FLOAT_RANGE: return String.format(Locale.ROOT, "%.2f", value) + unit;
            default: return unit.isEmpty() ? Integer.toString(i()) : i() + " " + unit;
        }
    }

    /** Number of steps a values row shows (CHOICE, BOOL, FLOAT_RANGE, COLOR). */
    int steps() {
        switch (type) {
            case CHOICE: return choices.length;
            case BOOL: return 2;
            case COLOR: return PALETTE.length;
            default: return (int) Math.round((max - min) / step) + 1;
        }
    }

    int stepIndex() {
        if (type == COLOR) {
            for (int k = 0; k < PALETTE.length; k++) if (PALETTE[k] == color()) return k;
            return 0;
        }
        return (int) Math.round((value - min) / step);
    }

    void setStep(int k) {
        int n = steps();
        k = Math.max(0, Math.min(n - 1, k));
        if (type == COLOR) value = PALETTE[k] & 0xFFFFFFFFL;
        else value = clampSnap(min + k * step);
    }

    /** Text for step k, for a values row. */
    String stepText(int k) {
        double keep = value;
        setStep(k);
        String s = display();
        value = keep;
        return s;
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }
}
