package com.nokoding.deck;

import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

/**
 * 2.7: one decoded wallpaper. Holds the bitmaps (one per layer, or a single image, or the first
 * frame of an animation) or the animated drawable, and the per layer numbers worked out from the
 * pack's spec and the current option values. Deck keeps one of these at a time (HomeActivity, or the
 * screensaver while it runs). release() drops every reference so the memory can go.
 */
final class WallpaperScene {
    final WallpaperSpec spec;
    final AnimSpec opts;
    Bitmap[] bitmaps = new Bitmap[0];
    /** An AnimatedImageDrawable, or null. When set, bitmaps is empty and animW by animH is its decoded size. */
    Drawable anim;
    int animW, animH;
    /** 2.17: a video wallpaper's file and its size after rotation. bitmaps holds its first frame (shown until the video plays). */
    java.io.File video;
    int videoW, videoH;
    /** Bytes the decoder was planned to use, for the status line. */
    long bytes;
    /** What happened while loading, for the status line. */
    String note = "";

    // per layer, resolved from the spec and the options (see resolve)
    float[] par = new float[0], scl = new float[0], opa = new float[0];
    boolean[] show = new boolean[0];
    PorterDuffColorFilter[] filter = new PorterDuffColorFilter[0];
    private int[] filterColor = new int[0];

    WallpaperScene(WallpaperSpec spec, AnimSpec opts) {
        this.spec = spec;
        this.opts = opts;
    }

    int layerCount() { return anim != null ? 1 : bitmaps.length; }

    boolean isVideo() { return video != null; }

    void setBitmaps(Bitmap[] b) {
        bitmaps = b;
        int n = b.length;
        par = new float[n]; scl = new float[n]; opa = new float[n];
        show = new boolean[n];
        filter = new PorterDuffColorFilter[n];
        filterColor = new int[n];
        resolve();
    }

    /** Reads the layer numbers from the spec and the current option values. Call after an option changes. */
    void resolve() {
        if (anim != null) {
            ensure(1);
            par[0] = 0.5f; scl[0] = 1f; opa[0] = 1f; show[0] = true;
            return;
        }
        int n = bitmaps.length;
        ensure(n);
        if (spec.type == WallpaperSpec.LAYERS) {
            // layers that failed to decode are not in the list, so the index follows the loaded layers
            int k = 0;
            for (WallpaperSpec.Layer l : spec.layers) {
                if (k >= n) break;
                par[k] = (float) WallpaperSpec.value(l.parallax, opts, 0, WallpaperSpec.PARALLAX_MAX);
                scl[k] = (float) WallpaperSpec.value(l.scale, opts, 1, WallpaperSpec.SCALE_MAX);
                opa[k] = (float) WallpaperSpec.value(l.opacity, opts, 0, 1);
                show[k] = WallpaperSpec.layerShows(l, opts) && opa[k] > 0.003f;
                int tint = WallpaperSpec.layerTint(l, opts);
                setFilter(k, tint, (float) l.tintMix);
                k++;
            }
        } else {
            for (int i = 0; i < n; i++) { par[i] = 0.5f; scl[i] = 1f; opa[i] = 1f; show[i] = bitmaps[i] != null; }
        }
    }

    private void ensure(int n) {
        if (par.length >= n) return;
        par = new float[n]; scl = new float[n]; opa = new float[n];
        show = new boolean[n];
        filter = new PorterDuffColorFilter[n];
        filterColor = new int[n];
    }

    private void setFilter(int i, int color, float mix) {
        if ((color >>> 24) == 0 || mix <= 0f) { filter[i] = null; filterColor[i] = 0; return; }
        int c = Ui.alpha(color | 0xFF000000, Math.max(0f, Math.min(1f, mix)));
        if (filter[i] != null && filterColor[i] == c) return;
        filterColor[i] = c;
        filter[i] = new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_ATOP);
    }

    /** Drops every reference. The bitmaps are left to the garbage collector (a live display list may still draw them). */
    void release() {
        video = null;
        Drawable a = anim;
        anim = null;
        if (a != null) {
            try { ((android.graphics.drawable.AnimatedImageDrawable) a).stop(); } catch (Throwable ignored) {}
            a.setCallback(null);
        }
        bitmaps = new Bitmap[0];
        par = new float[0]; scl = new float[0]; opa = new float[0];
        show = new boolean[0];
        filter = new PorterDuffColorFilter[0];
        filterColor = new int[0];
    }
}
