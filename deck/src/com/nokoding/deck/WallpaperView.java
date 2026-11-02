package com.nokoding.deck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * 2.7: draws a loaded wallpaper (WallpaperScene) behind everything. Layers move with the pan camera by
 * their parallax (ParallaxMath keeps the edges hidden), an animated wallpaper plays at a capped frame
 * rate, and one overlay color does the DIM and the TINT in a single draw. Nothing is allocated while
 * drawing. With Reduce effects on there is no parallax and no animation. Used by Deck's home screen and by
 * the screensaver.
 */
final class WallpaperView extends View {
    private WallpaperScene scene;
    private final ParallaxMath.Out out = new ParallaxMath.Out();
    private final Matrix m = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private double camX, camY;
    private boolean reduce, wantRun;
    private int overlay;
    private final WallpaperOptions.Look look = new WallpaperOptions.Look();
    // 2.17 video: the layer behind this view plays the video, this view only draws the dim over it
    private VideoLayer videoLayer;
    private boolean videoLive;
    // animated playback
    private long lastInv = -100000;
    private boolean invPending;
    private final Runnable invRun = new Runnable() {
        @Override public void run() {
            invPending = false;
            lastInv = SystemClock.uptimeMillis();
            invalidate();
        }
    };
    private final Runnable duty = new Runnable() {
        @Override public void run() {
            Drawable a = scene == null ? null : scene.anim;
            if (!(a instanceof AnimatedImageDrawable) || !wantRun || reduce || AnimPlay.full(look.speedPct)) return;
            AnimatedImageDrawable d = (AnimatedImageDrawable) a;
            if (d.isRunning()) {
                d.stop();
                postDelayed(this, AnimPlay.holdMs(look.speedPct));
            } else {
                d.start();
                postDelayed(this, AnimPlay.runMs(look.speedPct));
            }
        }
    };

    WallpaperView(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    WallpaperScene scene() { return scene; }

    /** 2.17: the player behind this view. Without one (the screensaver) a video wallpaper shows its first frame. */
    void setVideoLayer(VideoLayer v) {
        videoLayer = v;
        if (v != null) v.setListener(live -> { if (videoLive != live) { videoLive = live; invalidate(); } });
        applyVideo();
    }

    private void applyVideo() {
        VideoLayer v = videoLayer;
        if (v == null) return;
        WallpaperScene s = scene;
        if (s != null && s.video != null && !reduce) {
            v.setSource(s.video);
            v.setRunning(wantRun);
            placeVideo();
        } else {
            videoLive = false;
            v.clear();
        }
    }

    private void placeVideo() {
        VideoLayer v = videoLayer;
        WallpaperScene s = scene;
        int vw = getWidth(), vh = getHeight();
        if (v == null || s == null || s.video == null || vw <= 0 || vh <= 0) return;
        double cx = reduce ? 0 : camX, cy = reduce ? 0 : camY;
        ParallaxMath.place(out, vw, vh, s.videoW, s.videoH, look.zoomPct / 100.0, 0.5 * look.parallaxPct / 100.0, look.panPct / 100.0, cx, cy);
        v.place(vw, vh, s.videoW, s.videoH, out.scale, out.left, out.top);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        placeVideo();
    }

    /** Shows a loaded scene (null clears it and frees it). The previous scene is released first. */
    void setScene(WallpaperScene s) {
        WallpaperScene old = scene;
        scene = null;
        removeCallbacks(invRun);
        removeCallbacks(duty);
        invPending = false;
        if (old != null && old != s) old.release();
        scene = s;
        if (s != null && s.anim != null) s.anim.setCallback(this);
        videoLive = false;
        applyVideo();
        applyRun();
        invalidate();
    }

    /** Frees the scene and the memory behind it. */
    void release() { setScene(null); }

    boolean hasScene() { return scene != null; }

    void setReduce(boolean r) {
        if (reduce == r) return;
        reduce = r;
        applyVideo();
        applyRun();
        invalidate();
    }

    /** The camera position in px (the same numbers the skyline gets). Ignored under Reduce effects. */
    void setPan(double x, double y) {
        if (scene == null) return;
        if (Math.abs(x - camX) < 0.05 && Math.abs(y - camY) < 0.05) return;
        camX = x;
        camY = y;
        placeVideo();
        invalidate();
    }

    /** Takes the option values (dim, tint, zoom, parallax, speed, frame cap) and the accent for the tint. */
    void setLook(AnimSpec opts, int accent) {
        look.set(opts);
        overlay = ParallaxMath.overlay(look.dimPct, look.tintPct, accent);
        if (scene != null) { scene.resolve(); applyRun(); }
        placeVideo();
        invalidate();
    }

    /** Overrides the dim with the screensaver's own (percent). The wallpaper's own tint stays. */
    void setDimOverride(int dimPct, int accent) {
        overlay = ParallaxMath.overlay(Math.max(0, Math.min(95, dimPct)), look.tintPct, accent);
        invalidate();
    }

    /** True while the wallpaper should play (Deck in front, or the screensaver showing). */
    void setRunning(boolean run) {
        if (wantRun == run) return;
        wantRun = run;
        if (videoLayer != null) videoLayer.setRunning(run && !reduce);   // 2.17: the decoder is released whenever Deck is not in front
        applyRun();
    }

    private void applyRun() {
        Drawable a = scene == null ? null : scene.anim;
        if (!(a instanceof AnimatedImageDrawable)) return;
        AnimatedImageDrawable d = (AnimatedImageDrawable) a;
        removeCallbacks(duty);
        if (wantRun && !reduce) {
            if (!d.isRunning()) d.start();
            if (!AnimPlay.full(look.speedPct)) {
                postDelayed(duty, AnimPlay.runMs(look.speedPct));
            }
        } else if (d.isRunning()) {
            d.stop();
        }
    }

    // ---- animated drawable callbacks: let a redraw through only at the frame rate cap

    @Override protected boolean verifyDrawable(Drawable who) {
        return (scene != null && who == scene.anim) || super.verifyDrawable(who);
    }

    @Override public void invalidateDrawable(Drawable who) {
        if (scene == null || who != scene.anim) { super.invalidateDrawable(who); return; }
        if (invPending) return;
        long now = SystemClock.uptimeMillis();
        long wait = AnimPlay.wait(now, lastInv, look.fps);
        if (wait <= 0) {
            lastInv = now;
            invalidate();
        } else {
            invPending = true;
            postDelayed(invRun, wait);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyRun();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(invRun);
        removeCallbacks(duty);
        invPending = false;
        Drawable a = scene == null ? null : scene.anim;
        if (a instanceof AnimatedImageDrawable && ((AnimatedImageDrawable) a).isRunning()) ((AnimatedImageDrawable) a).stop();
    }

    @Override
    protected void onDraw(Canvas c) {
        WallpaperScene s = scene;
        int vw = getWidth(), vh = getHeight();
        if (s == null || vw <= 0 || vh <= 0) return;
        if (videoLive) {   // the video is playing behind this view: only the dim goes on top
            if (overlay != 0) c.drawColor(overlay);
            return;
        }
        double cx = reduce ? 0 : camX, cy = reduce ? 0 : camY;
        double strength = look.parallaxPct / 100.0, pan = look.panPct / 100.0, zoom = look.zoomPct / 100.0;
        if (s.anim != null) {
            ParallaxMath.place(out, vw, vh, s.animW, s.animH, zoom, 0.5 * strength, pan, cx, cy);
            s.anim.setBounds(0, 0, s.animW, s.animH);
            int save = c.save();
            c.translate((float) out.left, (float) out.top);
            c.scale((float) out.scale, (float) out.scale);
            s.anim.draw(c);
            c.restoreToCount(save);
        } else {
            for (int i = 0; i < s.bitmaps.length; i++) {
                Bitmap b = s.bitmaps[i];
                if (b == null || b.isRecycled() || !s.show[i]) continue;
                ParallaxMath.place(out, vw, vh, b.getWidth(), b.getHeight(), s.scl[i] * zoom, s.par[i] * strength, pan, cx, cy);
                m.setScale((float) out.scale, (float) out.scale);
                m.postTranslate((float) out.left, (float) out.top);
                paint.setAlpha(Math.round(s.opa[i] * 255f));
                paint.setColorFilter(s.filter[i]);
                c.drawBitmap(b, m, paint);
            }
            paint.setColorFilter(null);
            paint.setAlpha(255);
        }
        if (overlay != 0) c.drawColor(overlay);
    }
}
