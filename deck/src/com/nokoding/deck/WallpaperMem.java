package com.nokoding.deck;

/**
 * 2.7: how much memory a wallpaper may use, worked out before anything is decoded. The box has
 * 1.4 GB in total, so Deck keeps one decoded wallpaper at a time and refuses a pack that would
 * not fit. Pure Java.
 *
 * Static images and layers: BitmapFactory with an inSampleSize near the screen width times 1.15.
 * Animated GIF or WebP: ImageDecoder at a size no bigger than 1280x720, with about 24 MB for the
 * decoder's frame buffers (it keeps a few full size frames, so the estimate counts three).
 */
final class WallpaperMem {
    private WallpaperMem() {}

    static final double TARGET_FACTOR = 1.15;
    static final int MAX_SAMPLE = 16;
    /** All the layers of one wallpaper together (the GPU keeps a second copy once drawn, so this is half the real peak). */
    static final long STATIC_CAP = 32L * 1024 * 1024;
    static final long ANIM_CAP = 24L * 1024 * 1024;
    static final int ANIM_MAX_W = 1280, ANIM_MAX_H = 720, ANIM_MIN_W = 160, ANIM_FRAME_BUFFERS = 3;
    /** A header that claims more than this many pixels is refused without decoding. */
    static final long MAX_PIXELS = 64L * 1000 * 1000;

    static int targetWidth(int viewW) { return (int) Math.round(Math.max(1, viewW) * TARGET_FACTOR); }

    /** The largest power of two sample size that keeps the width at or above targetW (1 if the image is small). */
    static int sample(int iw, int ih, int targetW) {
        if (iw <= 0 || ih <= 0 || targetW <= 0) return 1;
        int s = 1;
        while (s < MAX_SAMPLE && iw / (s * 2) >= targetW) s *= 2;
        return s;
    }

    /** Decoded size: the sample size rounds up, as BitmapFactory does. */
    static int sampled(int v, int sample) { return (v + sample - 1) / sample; }

    static long bytes(int w, int h, boolean alpha) { return (long) Math.max(0, w) * Math.max(0, h) * (alpha ? 4 : 2); }

    static boolean absurd(int iw, int ih) { return iw <= 0 || ih <= 0 || (long) iw * ih > MAX_PIXELS; }

    /**
     * Sample sizes for a set of layers so that together they fit cap. Every layer starts at its own best
     * sample size. If the total is still over the cap, every sample size doubles until it fits. Returns
     * null when it cannot fit even at MAX_SAMPLE, or a layer is absurd. alphaPossible = false only for
     * formats that cannot have alpha (JPEG), which decode at 2 bytes a pixel.
     */
    static int[] plan(int[] iw, int[] ih, boolean[] alphaPossible, int viewW, long cap) {
        int n = iw.length;
        if (n == 0 || ih.length != n || alphaPossible.length != n) return null;
        int[] s = new int[n];
        int t = targetWidth(viewW);
        for (int i = 0; i < n; i++) {
            if (absurd(iw[i], ih[i])) return null;
            s[i] = sample(iw[i], ih[i], t);
        }
        for (int round = 0; round < 8; round++) {
            if (total(iw, ih, alphaPossible, s) <= cap) return s;
            boolean grew = false;
            for (int i = 0; i < n; i++) if (s[i] < MAX_SAMPLE) { s[i] *= 2; grew = true; }
            if (!grew) return null;
        }
        return null;
    }

    static long total(int[] iw, int[] ih, boolean[] alphaPossible, int[] s) {
        long sum = 0;
        for (int i = 0; i < iw.length; i++) sum += bytes(sampled(iw[i], s[i]), sampled(ih[i], s[i]), alphaPossible[i]);
        return sum;
    }

    // ---- animated

    static long animBytes(int w, int h) { return (long) Math.max(0, w) * Math.max(0, h) * 4 * ANIM_FRAME_BUFFERS; }

    /**
     * The decode size for an animated image: inside 1280x720, same shape, never bigger than the file,
     * then smaller in 25 percent steps until the estimate fits cap. out = {w, h}. Returns false when
     * the file is absurd or it still does not fit at 160 px wide (the caller then uses the first frame).
     */
    static boolean animTarget(int iw, int ih, long cap, int[] out) {
        if (absurd(iw, ih)) return false;
        double k = Math.min(1.0, Math.min(ANIM_MAX_W / (double) iw, ANIM_MAX_H / (double) ih));
        for (int i = 0; i < 24; i++) {
            int w = Math.max(1, (int) Math.round(iw * k)), h = Math.max(1, (int) Math.round(ih * k));
            if (animBytes(w, h) <= cap) { out[0] = w; out[1] = h; return true; }
            if (w <= ANIM_MIN_W) return false;
            k *= 0.75;
        }
        return false;
    }

    /** The first frame (or a static decode) of an animated file, at a size that fits the static cap. */
    static boolean frameTarget(int iw, int ih, int viewW, int[] out) {
        if (absurd(iw, ih)) return false;
        int s = sample(iw, ih, targetWidth(viewW));
        out[0] = sampled(iw, s);
        out[1] = sampled(ih, s);
        return bytes(out[0], out[1], true) <= STATIC_CAP;
    }
}
