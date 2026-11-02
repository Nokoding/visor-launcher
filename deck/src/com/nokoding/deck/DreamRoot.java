package com.nokoding.deck;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2.7: Deck's screensaver screen. DeckDream (the system screensaver) and the preview inside Deck
 * (Setup, LOOK, SCREENSAVER, PREVIEW SCREENSAVER) both show this. It builds its views and decodes the
 * wallpaper in start() and drops all of it in stop(): the skyline bitmaps, the decoded wallpaper and its
 * worker thread. One decoded wallpaper at a time: Deck's own is released before this one is loaded.
 *
 * The frame work is a Handler at about 15 frames a second, and only while something moves (the slow pan
 * of the background, or the clock block gliding to its next place). The clock ticks on the second (or
 * the minute). A stats line, if on, refreshes every 5 seconds.
 */
@SuppressLint("ViewConstructor")
final class DreamRoot extends FrameLayout {
    interface Host {
        /** The user did something that wakes the screensaver. */
        void wake();
    }

    static final long FRAME_MS = 66;
    static final double PAN_PX_PER_S = 14.0;

    private final SharedPreferences prefs;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final DreamRules.Pointer pointer = new DreamRules.Pointer();
    private Panorama pano;
    private WallpaperView wall;
    private DreamHud hud;
    private ExecutorService work;
    private boolean started, motion, reduce, statsOn;
    private int gen;
    private long startUptime, lastFrame;
    private double panX, panY;
    private int panPct = 100;
    private SimpleDateFormat fmtDate;
    private ActivityManager am;

    DreamRoot(Context c, SharedPreferences prefs, Host host) {
        super(c);
        this.prefs = prefs;
        this.host = host;
        setBackgroundColor(0xFF000000);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    boolean running() { return started; }

    // ------------------------------------------------------------------ start and stop

    void start() {
        if (started) return;
        started = true;
        gen++;
        startUptime = SystemClock.uptimeMillis();
        pointer.reset();
        reduce = prefs.getBoolean("reduce", false);
        int style = DreamRules.clampStyle(prefs.getInt("saverStyle", DreamRules.CLOCK_AND_WALLPAPER));
        int dim = DreamRules.clampDim(prefs.getInt("saverDim", DreamRules.DIM_DEFAULT));
        motion = prefs.getBoolean("saverMotion", true) && !reduce;
        statsOn = prefs.getBoolean("saverStats", false) && DreamRules.hasClock(style);
        am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        fmtDate = new SimpleDateFormat("EEE dd MMM", Locale.getDefault());
        fmtDate.setTimeZone(TimeZone.getDefault());

        removeAllViews();
        String sel = Wallpapers.normalize(prefs.getString("wallpaper", Wallpapers.SKYLINE));
        boolean bg = DreamRules.hasWallpaper(style) && !sel.equals(Wallpapers.OFF);
        if (bg) {
            pano = new Panorama(getContext());
            pano.scanlines = false;
            addView(pano, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (Wallpapers.isPack(sel)) {
                pano.setPlain(true);
                wall = new WallpaperView(getContext());
                wall.setReduce(reduce);
                addView(wall, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                loadPack(Wallpapers.packName(sel), dim);
            }
        }
        hud = new DreamHud(getContext());
        hud.showClock = DreamRules.hasClock(style);
        hud.solidBackground = !bg;
        hud.dimPct = bg ? dim : 0;
        hud.glide = motion;
        hud.is24 = prefs.getBoolean("clock24", false);
        hud.seconds = prefs.getBoolean("clockSec", false);
        hud.zoneId = prefs.getString("clock2", "");
        hud.begin(startUptime, System.currentTimeMillis());
        addView(hud, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        tickClock();
        h.postDelayed(slotTick, BurnIn.PERIOD_MS + 20);
        if (statsOn) tickStats();
        lastFrame = SystemClock.uptimeMillis();
        if (motion && bg) h.postDelayed(frame, FRAME_MS);
    }

    /** Frees everything: the callbacks, the decoded wallpaper, the skyline bitmaps and the worker thread. */
    void stop() {
        if (!started) return;
        started = false;
        gen++;
        h.removeCallbacksAndMessages(null);
        if (wall != null) { wall.setRunning(false); wall.release(); }
        if (pano != null) pano.setPlain(true);
        removeAllViews();
        wall = null;
        pano = null;
        hud = null;
        ExecutorService w = work;
        work = null;
        if (w != null) w.shutdownNow();
    }

    private void loadPack(final String name, final int dim) {
        final int my = gen;
        final File dir = packsDir();
        final boolean rd = reduce;
        final int viewW = Ui.W;
        work = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(() -> { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run(); }, "deck-dream-work");
            t.setDaemon(true);
            return t;
        });
        try {
            work.execute(() -> {
                WallpaperScene sc = null;
                AnimSpec opts = null;
                try {
                    File pd = Wallpapers.packDir(dir, name);
                    WallpaperSpec spec = Wallpapers.read(pd);
                    if (spec.error == null) {
                        opts = WallpaperOptions.build(name, spec);
                        opts.load(new PrefStore(prefs));
                        sc = WallpaperLoader.load(pd, spec, opts, viewW, rd);
                    }
                } catch (Throwable ignored) {
                    sc = null;
                }
                final WallpaperScene scene = sc;
                final AnimSpec o = opts;
                h.post(() -> {
                    if (gen != my || !started || wall == null) { if (scene != null) scene.release(); return; }
                    if (scene == null) { if (pano != null) pano.setPlain(false); return; }   // a bad pack: the skyline
                    wall.setScene(scene);
                    wall.setLook(o, Ui.T.a);
                    wall.setDimOverride(dim, Ui.T.a);
                    panPct = WallpaperOptions.pct(o, "pan", 100);
                    wall.setRunning(true);
                    if (hud != null) hud.dimPct = 0;   // the wallpaper draws the dim itself
                    if (motion) { h.removeCallbacks(frame); h.postDelayed(frame, FRAME_MS); }
                });
            });
        } catch (Throwable t) {
            if (pano != null) pano.setPlain(false);
        }
    }

    private File packsDir() {
        try {
            File ext = getContext().getExternalFilesDir(null);
            return ext == null ? null : new File(ext, "wallpapers");
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ticks

    private final Runnable clockTickR = new Runnable() { @Override public void run() { tickClock(); } };

    private void tickClock() {
        if (!started || hud == null) return;
        long now = System.currentTimeMillis();
        long off = TimeZone.getDefault().getOffset(now);
        hud.setTime(now, off, fmtDate.format(new Date(now)).toUpperCase(Locale.ROOT).replace(".", ""));
        h.removeCallbacks(clockTickR);
        h.postDelayed(clockTickR, ClockFmt.nextDelay(now, off, hud.seconds));
    }

    /** At each 60 seconds the clock block moves: make sure a frame is drawn then, even when nothing else moves. */
    private final Runnable slotTick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (hud != null) hud.invalidate();
            long el = SystemClock.uptimeMillis() - startUptime;
            h.postDelayed(this, BurnIn.PERIOD_MS - (el % BurnIn.PERIOD_MS) + 20);
        }
    };

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!started) return;
            long now = SystemClock.uptimeMillis();
            double dt = Math.min(0.25, (now - lastFrame) / 1000.0);
            lastFrame = now;
            panX += PAN_PX_PER_S * Ui.u * (panPct / 100.0) * dt;
            panY = Math.sin((now - startUptime) / 17000.0) * 30.0 * Ui.u;
            if (pano != null && !pano.isPlain()) pano.setPan(panX, panY);
            if (wall != null) wall.setPan(panX, panY);
            // the block glides for 10 s after each 60 s mark
            if (hud != null && hud.glide) {
                long el = now - startUptime;
                if (el >= BurnIn.PERIOD_MS && el % BurnIn.PERIOD_MS < BurnIn.GLIDE_MS) hud.invalidate();
            }
            h.postDelayed(this, FRAME_MS);
        }
    };

    private final Runnable statsR = new Runnable() { @Override public void run() { tickStats(); } };

    private void tickStats() {
        if (!started || hud == null || !statsOn) return;
        StringBuilder b = new StringBuilder("LINK ");
        String link = "OFF";
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) link = nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ETH" : (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "WIFI" : "NET");
        } catch (Throwable ignored) {}
        b.append(link);
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                long total = mi.totalMem >> 20, used = (mi.totalMem - mi.availMem) >> 20;
                b.append("   RAM ").append(total > 0 ? used * 100 / total : 0).append("%  ").append(used).append('/').append(total).append(" MB");
            }
        } catch (Throwable ignored) {}
        hud.setStats(b.toString());
        h.postDelayed(statsR, 5000);
    }

    // ------------------------------------------------------------------ input (the wake rules)

    /** @return true when this key wakes the screensaver. Inside the first 600 ms nothing does. */
    boolean wakeKey(KeyEvent e) { return !DreamRules.grace(SystemClock.uptimeMillis() - startUptime); }

    boolean wakeTouch(MotionEvent e) { return !DreamRules.grace(SystemClock.uptimeMillis() - startUptime); }

    /** Gamepad sticks, triggers and the hat beyond the deadzone, pointer movement and scrolling. */
    boolean wakeMotion(MotionEvent e) {
        if (DreamRules.grace(SystemClock.uptimeMillis() - startUptime)) return false;
        int src = e.getSource();
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            return DreamRules.stickWakes(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                    e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ),
                    e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y),
                    e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_RTRIGGER));
        }
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) return true;
        return pointer.moved(e.getX(), e.getY());
    }

    /** The system stopped showing this window (another app came to the front): finish after a short wait. */
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE && started) {
            h.postDelayed(hiddenCheck, 1500);
        } else {
            h.removeCallbacks(hiddenCheck);
        }
    }

    private final Runnable hiddenCheck = new Runnable() {
        @Override public void run() {
            if (started && getWindowVisibility() != View.VISIBLE) host.wake();
        }
    };

    void themeChanged() {
        if (hud != null) hud.themeChanged();
        if (pano != null) pano.themeChanged();
    }
}
