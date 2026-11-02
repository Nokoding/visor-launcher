package com.nokoding.deck;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/** Shared scale, theme and drawing helpers. All layout is written in 1920x1080 design pixels. */
final class Ui {
    private Ui() {}

    static float u = 1f;
    static int W = 1920, H = 1080;

    // 2.1 fonts (all SIL OFL, see docs/licenses). These start as system fonts and are replaced
    // once by loadFonts(). If a file fails to load, that role keeps the system font.
    /** Readouts, stats, clock, values: Share Tech Mono. */
    static Typeface MONO = Typeface.MONOSPACE;
    static Typeface MONO_B = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    /** UI text (tile, chip and row labels): Rajdhani SemiBold and Medium. */
    static Typeface COND_B = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    static Typeface COND = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    /** Headings (Orbitron Bold) and letter spaced caps labels (Orbitron Medium). */
    static Typeface HEAD = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    static Typeface HEAD_M = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    /** Letter spacing in em for Orbitron labels. Zero while the system fallback is in use. */
    static float HEAD_SPACING = 0f, LABEL_SPACING = 0f;
    static boolean fontsTried;
    static String fontStatus = "SYSTEM";

    /** Loads the bundled fonts once per process. Safe to call again. */
    static void loadFonts(android.content.res.AssetManager am) {
        if (fontsTried) return;
        fontsTried = true;
        int ok = 0;
        Typeface t;
        if ((t = font(am, "fonts/share-tech-mono.ttf")) != null) { MONO = t; MONO_B = Typeface.create(t, Typeface.BOLD); ok++; }
        if ((t = font(am, "fonts/rajdhani-semibold-latin.ttf")) != null) { COND_B = t; ok++; }
        if ((t = font(am, "fonts/rajdhani-medium-latin.ttf")) != null) { COND = t; ok++; }
        if ((t = font(am, "fonts/orbitron-bold.ttf")) != null) { HEAD = t; HEAD_SPACING = 0.04f; ok++; }
        if ((t = font(am, "fonts/orbitron-medium.ttf")) != null) { HEAD_M = t; LABEL_SPACING = 0.08f; ok++; }
        fontStatus = ok == 5 ? "ORBITRON, RAJDHANI, SHARE TECH MONO" : (ok == 0 ? "SYSTEM (FONT FILES FAILED)" : ok + "/5 LOADED, REST SYSTEM");
    }

    private static Typeface font(android.content.res.AssetManager am, String path) {
        try {
            Typeface t = Typeface.createFromAsset(am, path);
            return t == null || t == Typeface.DEFAULT ? null : t;
        } catch (Throwable e) {
            return null;
        }
    }

    static final class Theme {
        final String name;
        final int a, b, bg0, bg1, panel, text, dim;
        Theme(String name, int a, int b, int bg0, int bg1, int panel, int text, int dim) {
            this.name = name; this.a = a; this.b = b; this.bg0 = bg0; this.bg1 = bg1;
            this.panel = panel; this.text = text; this.dim = dim;
        }
    }

    static final Theme[] THEMES = {
        new Theme("SYNTHWAVE", 0xFFFF2E97, 0xFFA855F7, 0xFF07040D, 0xFF170A2C, 0xFF120A21, 0xFFF4EEFF, 0xFFA99DC6),
        new Theme("GLACIER",   0xFF22E4FF, 0xFF4C6FFF, 0xFF03070D, 0xFF0A1730, 0xFF0A1424, 0xFFEAF6FF, 0xFF95ABC4),
        new Theme("TOXIC",     0xFFB8FF3D, 0xFF18D982, 0xFF040904, 0xFF0C1C10, 0xFF0B160E, 0xFFF0FFE8, 0xFF9FBA9C),
        new Theme("EMBER",     0xFFFF7A1A, 0xFFFF2D55, 0xFF0B0504, 0xFF200C08, 0xFF190B08, 0xFFFFF1EA, 0xFFC6A398),
        new Theme("SOLAR",     0xFFFFC93D, 0xFF2CE6C4, 0xFF080704, 0xFF1B170A, 0xFF16130A, 0xFFFFF8E6, 0xFFBFB291),
        new Theme("GHOST",     0xFFEDEDF7, 0xFF8C93B0, 0xFF060608, 0xFF15161D, 0xFF111218, 0xFFF7F7FB, 0xFF9C9FAF),
        null, // CUSTOM, filled from settings
    };
    static final int CUSTOM = 6;
    /** Reserved for lock failure, danger and error states only. */
    static final int DANGER = 0xFFFF3B3B;

    static { THEMES[CUSTOM] = custom(0xFFFF2E97, 0xFFA855F7); }

    static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{((h % 360f) + 360f) % 360f, clamp(s, 0f, 1f), clamp(v, 0f, 1f)});
    }

    /** Builds a full dark theme around two user picked accents. */
    static Theme custom(int a, int b) {
        float[] hs = new float[3];
        Color.colorToHSV(a, hs);
        float hue = hs[0];
        return new Theme("CUSTOM", a | 0xFF000000, b | 0xFF000000,
                hsv(hue, 0.55f, 0.05f), hsv(hue, 0.6f, 0.17f), hsv(hue, 0.5f, 0.13f),
                mix(0xFFFFFFFF, a | 0xFF000000, 0.06f), mix(0xFFA8A8B8, a | 0xFF000000, 0.25f));
    }

    static Theme T = THEMES[0];

    static void init(int w, int h) {
        W = Math.max(1, w);
        H = Math.max(1, h);
        u = Math.min(W / 1920f, H / 1080f);
        if (u <= 0f) u = 1f;
    }

    static float px(float design) { return design * u; }

    static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, Math.round(Color.alpha(color) * a)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    static int mix(int c1, int c2, float t) {
        float s = 1f - t;
        return Color.argb(
                Math.round(Color.alpha(c1) * s + Color.alpha(c2) * t),
                Math.round(Color.red(c1) * s + Color.red(c2) * t),
                Math.round(Color.green(c1) * s + Color.green(c2) * t),
                Math.round(Color.blue(c1) * s + Color.blue(c2) * t));
    }

    static Paint fill() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    static Paint stroke(float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeJoin(Paint.Join.MITER);
        return p;
    }

    static Paint text(Typeface tf, float sizePx) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(sizePx);
        return p;
    }

    /**
     * Angular HUD panel: big 45 degree cut on the top left and bottom right corners,
     * small cut on the other two. Always convex, so it is valid as a shadow outline.
     */
    static Path cut(Path p, float l, float t, float r, float b, float big, float small) {
        p.reset();
        float maxCut = Math.min(r - l, b - t) * 0.45f;
        big = Math.min(big, maxCut);
        small = Math.min(small, maxCut);
        p.moveTo(l + big, t);
        p.lineTo(r - small, t);
        p.lineTo(r, t + small);
        p.lineTo(r, b - big);
        p.lineTo(r - big, b);
        p.lineTo(l + small, b);
        p.lineTo(l, b - small);
        p.lineTo(l, t + big);
        p.close();
        return p;
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static int themeIndex(String name) {
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].name.equals(name)) return i;
        return 0;
    }
}
