package com.nokoding.deck;

import java.util.Locale;

/**
 * 2.7: the options every wallpaper pack gets from Deck itself, on the 2.3 AnimSpec schema: PARALLAX
 * STRENGTH, PAN SPEED, DIM, TINT and ZOOM, and for animated packs PLAYBACK SPEED and FRAME RATE CAP.
 * The pack's own options (from wallpaper.json) follow them. Values live in the Store under
 * anim.wp.PACKNAME.KEY, the same place and style as the animation options. Pure Java.
 */
final class WallpaperOptions {
    private WallpaperOptions() {}

    static final String COMMON =
            "parallax | PARALLAX STRENGTH | INT | 0..200/10 % | 100\n"
          + "pan | PAN SPEED | INT | 0..200/25 % | 100\n"
          + "dim | DIM | INT | 0..80/5 % | 25\n"
          + "tint | TINT | INT | 0..100/5 % | 0\n"
          + "zoom | ZOOM | INT | 100..150/5 % | 100\n";
    static final String[] COMMON_KEYS = {"parallax", "pan", "dim", "tint", "zoom"};
    static final String[] ANIMATED_KEYS = {"speed", "fps"};

    /** The AnimSpec id for a pack: wp.NAME. */
    static String id(String packName) { return "wp." + PackZip.cleanName(packName); }

    /**
     * The full option list for a pack: the common ones, PLAYBACK SPEED and FRAME RATE CAP for an animated
     * pack, then the pack's own. The values are not loaded here: call load(store) on the result.
     */
    static AnimSpec build(String packName, WallpaperSpec spec) {
        AnimSpec s = AnimSpec.parse(id(packName), packName, COMMON);
        if (spec != null && spec.type == WallpaperSpec.ANIMATED) {
            int pct = (int) Math.round(spec.speed * 100);
            AnimOption sp = AnimSpec.option("speed | PLAYBACK SPEED | INT | 25..100/25 % | " + pct);
            AnimOption fp = AnimSpec.option("fps | FRAME RATE CAP | INT | 5..30/5 FPS | " + spec.fps);
            if (sp != null) s.options.add(sp);
            if (fp != null) s.options.add(fp);
        }
        if (spec != null && spec.options != null) for (AnimOption o : spec.options.options) if (s.get(o.key) == null) s.options.add(o);
        return s;
    }

    /** True for an option Deck adds itself (common or animated). */
    static boolean isCommon(String key) {
        for (String k : COMMON_KEYS) if (k.equals(key)) return true;
        for (String k : ANIMATED_KEYS) if (k.equals(key)) return true;
        return false;
    }

    static int pct(AnimSpec s, String key, int def) {
        AnimOption o = s == null ? null : s.get(key);
        return o == null ? def : o.i();
    }

    /** The wallpaper's look as plain numbers, so the view and the screensaver do not read AnimSpec every frame. */
    static final class Look {
        int parallaxPct = 100, panPct = 100, dimPct = 0, tintPct = 0, zoomPct = 100, speedPct = 100, fps = 20;
        Look set(AnimSpec s) {
            parallaxPct = pct(s, "parallax", 100);
            panPct = pct(s, "pan", 100);
            dimPct = pct(s, "dim", 0);
            tintPct = pct(s, "tint", 0);
            zoomPct = pct(s, "zoom", 100);
            speedPct = pct(s, "speed", 100);
            fps = pct(s, "fps", 20);
            return this;
        }
    }

    /** One line for the status row: the values that differ from the defaults, or DEFAULTS. */
    static String summary(AnimSpec s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (AnimOption o : s.options) {
            if (Math.abs(o.value - o.def) > 1e-9) {
                if (b.length() > 0) b.append("  //  ");
                b.append(o.label.toUpperCase(Locale.ROOT)).append(' ').append(o.display());
            }
        }
        return b.length() == 0 ? "DEFAULT OPTIONS" : b.toString();
    }
}
