package com.nokoding.deck;

/**
 * 2.7: the layer math for wallpapers. Where a layer's image goes for a camera offset, so that the
 * edges of the picture never show. Pure Java.
 *
 * The layer is first scaled to cover the view (the bigger of view width over image width and view
 * height over image height). Its own scale and the ZOOM option then make it bigger. The extra size
 * on each side is its overscan, and the picture may move by at most that much. The camera offset
 * is unbounded (every press adds to it), so it is folded with a sine: the picture follows the
 * camera one to one near the middle, slows near the end of its travel, comes back, and never goes
 * past the overscan. If the layer is not big enough for the travel the layer's parallax wants, it
 * is scaled up (to 2x at most), so "scale" in a pack is a minimum, not an exact size.
 */
final class ParallaxMath {
    private ParallaxMath() {}

    /** The travel each side wanted for parallax 1.0, as a share of the view size. */
    static final double TRAVEL = 0.05;
    static final double MAX_AUTO_SCALE = 2.0;
    /** Tint 100 percent mixes in this much of the accent. */
    static final double TINT_MAX = 0.6;

    /** Where to draw: the image scaled by scale, its top left at (left, top), size w by h, all in view px. */
    static final class Out {
        double scale = 1, left, top, w, h;
        boolean valid;
    }

    /** limit * sin(v / limit): v near zero passes 1 to 1, the result never leaves -limit..limit. */
    static double shift(double v, double limit) {
        if (!(limit > 0) || Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return limit * Math.sin(v / limit);
    }

    /**
     * Fills o. vw, vh = the view; iw, ih = the image as decoded; userScale = layer scale times ZOOM
     * (at least 1); parallax = the layer's parallax times PARALLAX STRENGTH (0 to 4); panMul = PAN SPEED
     * as a factor; camX, camY = the camera offset in px.
     */
    static void place(Out o, int vw, int vh, int iw, int ih, double userScale, double parallax, double panMul, double camX, double camY) {
        o.valid = false;
        if (vw <= 0 || vh <= 0 || iw <= 0 || ih <= 0) {
            o.scale = 1; o.left = 0; o.top = 0; o.w = Math.max(0, vw); o.h = Math.max(0, vh);
            return;
        }
        if (Double.isNaN(userScale) || userScale < 1) userScale = 1;
        if (Double.isNaN(parallax) || parallax < 0) parallax = 0;
        if (parallax > 4) parallax = 4;
        if (Double.isNaN(panMul) || panMul < 0) panMul = 0;
        double cover = Math.max(vw / (double) iw, vh / (double) ih);
        double wantX = TRAVEL * vw * Math.min(parallax, 2.0), wantY = TRAVEL * vh * Math.min(parallax, 2.0);
        double kx = (vw + 2 * wantX) / (iw * cover), ky = (vh + 2 * wantY) / (ih * cover);
        double k = Math.max(userScale, Math.min(Math.max(kx, ky), MAX_AUTO_SCALE));
        double w = iw * cover * k, h = ih * cover * k;
        double ovX = Math.max(0, (w - vw) / 2), ovY = Math.max(0, (h - vh) / 2);
        double sx = shift(camX * panMul * parallax, ovX), sy = shift(camY * panMul * parallax, ovY);
        o.scale = cover * k;
        o.w = w;
        o.h = h;
        o.left = (vw - w) / 2 + sx;
        o.top = (vh - h) / 2 + sy;
        o.valid = true;
    }

    /** True when the picture placed by o covers the whole view (a tiny rounding allowance). */
    static boolean covers(Out o, int vw, int vh) {
        return o.valid && o.left <= 0.001 && o.top <= 0.001 && o.left + o.w >= vw - 0.001 && o.top + o.h >= vh - 0.001;
    }

    /**
     * One color that does the TINT and the DIM in a single full screen draw: first the accent mixed in
     * by tintPct (0 to 100, times TINT_MAX), then black by dimPct (0 to 80). Returns 0 for no overlay.
     * The result drawn over a pixel equals tint first, dim second, to within one level.
     */
    static int overlay(int dimPct, int tintPct, int accent) {
        double d = Math.max(0, Math.min(80, dimPct)) / 100.0;
        double t = Math.max(0, Math.min(100, tintPct)) / 100.0 * TINT_MAX;
        double a = 1 - (1 - t) * (1 - d);
        if (a < 0.002) return 0;
        double k = t * (1 - d) / a;
        int r = (int) Math.round(((accent >> 16) & 255) * k), g = (int) Math.round(((accent >> 8) & 255) * k), b = (int) Math.round((accent & 255) * k);
        int al = (int) Math.round(a * 255);
        return (al << 24) | (r << 16) | (g << 8) | b;
    }
}
