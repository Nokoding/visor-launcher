package com.nokoding.deck;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.File;

/**
 * 2.17 video wallpaper player (experimental, off by default). A TextureView behind the wallpaper view plays one muted
 * looping .mp4 with MediaPlayer. WallpaperView decides when it runs: only while Deck is in front, never under Reduce
 * effects, one video at a time. The player is released whenever it must not run, so no decoder is held in the
 * background. Any error stops it quietly and the wallpaper falls back to the video's first frame (WallpaperView).
 * The file was checked against VideoLimits by the loader before this sees it.
 */
final class VideoLayer extends TextureView implements TextureView.SurfaceTextureListener {

    interface Listener { void onVideoLive(boolean live); }

    private MediaPlayer mp;
    private Surface surface;
    private File file;
    private boolean want, failed;
    private Listener listener;
    private final Matrix m = new Matrix();
    private final float[] tr = new float[4];

    VideoLayer(Context c) {
        super(c);
        setSurfaceTextureListener(this);
        setVisibility(View.GONE);
        setAlpha(0f);
    }

    void setListener(Listener l) { listener = l; }

    /** Plays this file as soon as the surface and the run switch allow. Replaces any earlier video. */
    void setSource(File f) {
        stopPlayer();
        file = f;
        failed = false;
        setVisibility(f == null ? View.GONE : View.VISIBLE);
        update();
    }

    void clear() { setSource(null); }

    boolean hasSource() { return file != null; }

    void setRunning(boolean run) {
        want = run;
        update();
    }

    /** The view's transform for the pan camera (see VideoMath). Does nothing for bad sizes. */
    void place(int viewW, int viewH, int videoW, int videoH, double scale, double left, double top) {
        if (!VideoMath.transform(tr, viewW, viewH, videoW, videoH, scale, left, top)) return;
        m.setScale(tr[0], tr[1]);
        m.postTranslate(tr[2], tr[3]);
        setTransform(m);
    }

    private void update() {
        if (file != null && want && !failed && surface != null) startPlayer(); else stopPlayer();
    }

    private void startPlayer() {
        if (mp != null) return;
        try {
            final MediaPlayer p = new MediaPlayer();
            mp = p;
            p.setSurface(surface);
            p.setDataSource(file.getPath());
            p.setLooping(true);
            p.setVolume(0f, 0f);
            p.setOnPreparedListener(x -> { if (mp == x) { try { x.start(); } catch (Throwable t) { fail(); } } });
            p.setOnInfoListener((x, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mp == x) {
                    setAlpha(1f);
                    if (listener != null) listener.onVideoLive(true);
                }
                return false;
            });
            p.setOnErrorListener((x, what, extra) -> { fail(); return true; });
            p.prepareAsync();
        } catch (Throwable t) {
            fail();
        }
    }

    private void fail() {
        failed = true;
        stopPlayer();
    }

    private void stopPlayer() {
        MediaPlayer p = mp;
        mp = null;
        setAlpha(0f);
        if (p != null) {
            try { p.setOnPreparedListener(null); p.setOnInfoListener(null); p.setOnErrorListener(null); } catch (Throwable ignored) {}
            try { p.release(); } catch (Throwable ignored) {}
        }
        if (listener != null) listener.onVideoLive(false);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        update();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        stopPlayer();
        if (surface != null) { try { surface.release(); } catch (Throwable ignored) {} surface = null; }
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    @Override protected void onDetachedFromWindow() {
        stopPlayer();
        super.onDetachedFromWindow();
    }
}
